package com.ab.pgn.dgtboard;

import com.ab.pgn.BitStream;
import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Move;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.PgnLogger;
import com.ab.pgn.Setup;
import com.ab.pgn.Square;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 *
 * Created by Alexander Bootman on 1/27/19.
 */

public class DgtBoardPad {
    public static boolean DEBUG = false;
    public static final int BOARD_UPDATE_TIMEOUT_MSEC = 1000;

    public static final int LOCAL_VERSION_CODE = 0;
    public enum BoardStatus {
        None,
        SetupMess,
        Game,
    };

    private final PgnLogger logger = PgnLogger.getLogger(this.getClass());
    private final Board initBoard = new Board();

    private BoardStatus boardStatus = BoardStatus.None;
    private String outputDir;
    private final DgtBoardWatcher dgtBoardWatcher;
    private final DgtBoardObserver dgtBoardObserver;

    private String recordedGames = "";
    private PgnGraph pgnGraph;
    private Setup setup;
    private boolean invertedBoard;

    private List<DgtBoardWatcher.BoardMessageMoveChunk> chunks = new LinkedList<>();
    private DgtBoardWatcher.BoardMessageMoveChunk expectedChunk;
    private Square expectedPromotion;
    private Move incompleteMove = null;
    private String errorMsg;

    public DgtBoardPad(DgtBoardIO dgtBoardIO, String outputDir, DgtBoardObserver dgtBoardObserver) throws Config.PGNException {
        this.outputDir = outputDir;
        this.dgtBoardObserver = dgtBoardObserver;
        dgtBoardWatcher = new DgtBoardWatcher(dgtBoardIO, new DgtBoardWatcher.BoardMessageConsumer() {
            @Override
            public void consume(DgtBoardWatcher.BoardMessage boardMessage) {
                updatePad(boardMessage);
            }
        });
        _resume();
    }

    public void close() {
        // todo ?
    }

    public void resume() throws Config.PGNException {
        try {
            dgtBoardWatcher.start();
        } catch (IOException e) {
            throw  new Config.PGNException(e);
        }
        _resume();
    }

    public void _resume() throws Config.PGNException {
        if(pgnGraph == null) {
            pgnGraph = new PgnGraph();
        }
        newSetup(true);
    }

    public void stop() {
        dgtBoardWatcher.stop();
        appendGame();
        if(!recordedGames.isEmpty()) {
            String fname = String.format("rec-%s.pgn", new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()));
            File f = new File(new File(outputDir), fname);
            try {
                FileOutputStream fos = new FileOutputStream(f);
                byte[] buf = recordedGames.getBytes("UTF-8");
                fos.write(buf, 0, buf.length);
                fos.close();
            } catch (IOException e) {
                errorMsg = e.getMessage();
                logger.error(errorMsg, e);
            }
        }
        recordedGames = "";
    }

    public void finish() {
        stop();
        dgtBoardWatcher.finish();
    }

    public void serialize(BitStream.Writer writer) throws Config.PGNException {
        try {
            writer.writeString(recordedGames);  // assuming it is < 64K long!
            setup.serialize(writer);
            pgnGraph.serializeGraph(writer, LOCAL_VERSION_CODE);
            pgnGraph.serializeMoveLine(writer, LOCAL_VERSION_CODE);
            if(invertedBoard) {
                writer.write(1, 1);
            } else {
                writer.write(0, 1);
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public void unserialize(BitStream.Reader reader) throws Config.PGNException {
        try {
            recordedGames = reader.readString();    // assuming it is < 64K long!
            if(recordedGames == null) {
                recordedGames = "";
            }
            setup = new Setup(reader);
            pgnGraph = new PgnGraph(reader, LOCAL_VERSION_CODE, null);
            pgnGraph.unserializeMoveLine(reader, LOCAL_VERSION_CODE);
            invertedBoard = reader.read(1) == 1;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public void turnBoard() {
        invertedBoard = !invertedBoard;
        Board invertedBoard = setup.getBoard().invert();
        setup.setBoard(invertedBoard);
    }

/*
    private void requestBoardDump() {
        try {
            dgtBoardWatcher.requestBoardDump();
        } catch (IOException e) {
            errorMsg = e.getMessage();
            logger.error(errorMsg, e);
            setBoardStatus(BoardStatus.SetupMess, false);
        }
    }
*/

    public void setBoardStatus(BoardStatus boardStatus, boolean preserveGame) {
        logger.debug(String.format("updateBoard: status:%s", boardStatus.toString()));
        this.boardStatus = boardStatus;
        if(boardStatus == BoardStatus.SetupMess) {
            newSetup(false);
            dgtBoardWatcher.dumpBoardLoopStart(BOARD_UPDATE_TIMEOUT_MSEC);
//            dgtBoardWatcher.requestBoardDump();
        } else {
            dgtBoardWatcher.dumpBoardLoopStop();
            if(preserveGame) {
                resetChunks();
            } else {
                newGame();
            }
        }
        if(dgtBoardObserver != null) {
            dgtBoardObserver.update((byte) 0x00);
        }
    }

    public BoardStatus getBoardStatus() {
        return boardStatus;
    }

    public Setup getSetup() {
        return setup;
    }

    public Board getBoard() {
        if(boardStatus == BoardStatus.SetupMess) {
            return setup.getBoard();
        }
        return pgnGraph.getBoard();
    }

    public List<Pair<String, String>> getHeaders() {
        if(boardStatus == BoardStatus.SetupMess) {
            return setup.getHeaders();
        }
        return pgnGraph.getPgn().getHeaders();
    }

    public void setHeaders(List<Pair<String, String>> headers) {
        if(boardStatus == BoardStatus.SetupMess) {
            setup.setHeaders(headers);
        } else {
            pgnGraph.getPgn().setHeaders(headers);
        }
    }

    public List<String> getMovesText() {
        if(boardStatus == BoardStatus.SetupMess) {
            return new LinkedList<>();
        }
        return pgnGraph.getMovesText();
    }

    private static String piece2String(int piece) {
        final String[] map = {"x", "", "WHITE_KING", "BLACK_KING", "WHITE_QUEEN", "BLACK_QUEEN", "WHITE_BISHOP", "BLACK_BISHOP",
                "WHITE_KNIGHT", "BLACK_KNIGHT", "WHITE_ROOK", "BLACK_ROOK", "WHITE_PAWN", "BLACK_PAWN"};
        return map[piece];
    }

    private DgtBoardWatcher.BoardMessage oldBoardMessage = null;

    private synchronized void updatePad(DgtBoardWatcher.BoardMessage boardMessage) {
        errorMsg = null;
        if(boardMessage.equals(oldBoardMessage)) {
            // DGT board occasionally ignores commands (or caches the results?) so we may want to send each command twice.
            // To fix the case when the board returns both messages, we ignore the second one if it is identical.
            return;
        }
        oldBoardMessage = boardMessage;
        if(boardMessage instanceof DgtBoardWatcher.BoardMessageInfo) {
            logger.debug(String.format("updatePad: 0x%s, %s", Integer.toHexString(boardMessage.getMsgId() & 0x0ff), ((DgtBoardWatcher.BoardMessageInfo) boardMessage).text));
        } else if(boardMessage instanceof DgtBoardWatcher.BoardMessagePosition) {
            updateBoard(((DgtBoardWatcher.BoardMessagePosition)boardMessage).board);
        } else if(boardMessage instanceof DgtBoardWatcher.BoardMessageMoveChunk) {
            acceptMoveChunk((DgtBoardWatcher.BoardMessageMoveChunk) boardMessage);
        }
        if(dgtBoardObserver != null) {
            dgtBoardObserver.update(boardMessage.getMsgId());
        }
    }

    private void updateBoard(Board board) {
        if(boardStatus == BoardStatus.Game) {
            return;     // ignore
        }
        Board invertedBoard = board.invert();
        if(this.invertedBoard) {
            setup.setBoardPosition(invertedBoard);
            invertedBoard = board;
            board = setup.getBoard();
        } else {
            setup.setBoardPosition(board);
        }

        if(board.samePosition(initBoard)) {
            toInit();
            return;
        }
        if(invertedBoard.samePosition(initBoard)) {
            turnBoard();
            toInit();
            return;
        }

        if(boardStatus == BoardStatus.Game) {
            return;
        }

        // search for previous occurrences:
        BoardStatus boardStatus = searchPgnGraph(board);
        if(boardStatus == BoardStatus.SetupMess) {
            boardStatus = searchPgnGraph(invertedBoard);
            if(boardStatus == BoardStatus.Game) {
                turnBoard();
            }
        }
        if(boardStatus != this.boardStatus) {
            setBoardStatus(boardStatus, true);
        }
    }

    /*
    search for an existing position in the game,
    then for a move that can create setup position from an existing position
     */
    private BoardStatus searchPgnGraph(Board searchBoard) {
        BoardStatus boardStatus = BoardStatus.SetupMess;
        int index = pgnGraph.moveLine.size();
        if(index <= 1) {
            return boardStatus;
        }
        Move move = null;
        ListIterator<Move> li = pgnGraph.moveLine.subList(1, index).listIterator(index - 1);
        while(li.hasPrevious()) {
            move = li.previous();
            Board board = pgnGraph.getBoard(move);
            if(board.samePosition(pgnGraph.getInitBoard())) {
                // sanity check, should not be here
                logger.error(String.format("%s\n%s", move, board));
                break;
            }
            if(DEBUG) {
                logger.debug(String.format("checking %s\n%s", move, board));
            }
            if(searchBoard.samePosition(board)) {
                boardStatus = BoardStatus.Game;
                move = null;
                break;
            }

            move = board.findMove(searchBoard);
            if(move != null) {
                boardStatus = BoardStatus.Game;
                break;
            }
            --index;
        }

        if(boardStatus == BoardStatus.Game) {
            pgnGraph.moveLine.subList(index, pgnGraph.moveLine.size()).clear();
        }
        if(move != null) {
            addMove(move);
        }

        return boardStatus;
    }

    private void toInit() {
        setup.getBoard().setFlags(Config.INIT_POSITION_FLAGS);
        setup.getBoard().setReversiblePlyNum(0);
        setup.getBoard().setPlyNum(0);
        setBoardStatus(BoardStatus.Game, false);
    }

    private synchronized void newSetup(boolean increment) {
        setup = new Setup(pgnGraph);
        String pgn = pgnGraph.toPgn();
        if(!pgn.isEmpty()) {
            increment = true;
        }

        if(increment) {
            List<Pair<String, String>> headers = new LinkedList<>();
            String save = null;
            for (int i = 0; i < pgnGraph.getPgn().getHeaders().size(); ++i) {
                Pair<String, String> header = pgnGraph.getPgn().getHeaders().get(i);
                switch (header.first) {
                    case Config.HEADER_Round:
                        int round = 0;
                        try {
                            round = Integer.valueOf(header.second);
                        } catch (Exception e) {
                            // ignore
                        }
                        ++round;
                        headers.add(new Pair<>(Config.HEADER_Round, "" + round));
                        break;

                    case Config.HEADER_Date:
                        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                        headers.add(new Pair<>(Config.HEADER_Date, date));
                        break;

                    case Config.HEADER_White:
                        if (save != null) {
                            // preserve order
                            headers.add(new Pair<>(Config.HEADER_Black, header.second));
                            headers.add(new Pair<>(Config.HEADER_White, save));
                        }
                        save = header.second;
                        break;

                    case Config.HEADER_Black:
                        if (save != null) {
                            // preserve order
                            headers.add(new Pair<>(Config.HEADER_White, header.second));
                            headers.add(new Pair<>(Config.HEADER_Black, save));
                        }
                        save = header.second;
                        break;

                    default:
                        headers.add(new Pair<>(header.first, header.second));
                        break;
                }
            }
            setup.setHeaders(headers);
        }
        resetChunks();
    }

    private void resetChunks() {
        chunks.clear();
        expectedChunk = null;
        expectedPromotion = null;
        errorMsg = null;
    }

    private void appendGame() {
        String pgn = pgnGraph.toPgn();
        if(!pgn.isEmpty()) {
            recordedGames += new String(pgnGraph.getPgn().headersToString(true, true)) + "\n\n" + pgn + "\n";
            logger.debug(String.format("recordedGames:\n%s", recordedGames));
        }
    }

    private void newGame() {
        appendGame();
        resetChunks();

        try {
            pgnGraph = setup.toPgnGraph();
        } catch (Config.PGNException e) {
            errorMsg = e.getMessage();
            logger.error(errorMsg, e);
            setBoardStatus(BoardStatus.SetupMess, true);
        }
    }

    private void acceptMoveChunk(DgtBoardWatcher.BoardMessageMoveChunk moveChunk) {
        /*
        1. simple move:
            1.1. my piece taken off
            1.2. my piece put on

        2. capture:
            2.1.1. my piece taken off
            2.1.2. my piece replaces his (in single chunk) : xb6(P), Pa7 -> b6xa7  : 1. bxa7, skipping xa7(p)

            2.2.1. his piece taken off
            2.2.2. my piece taken off
            2.2.3. my piece put on

        3. promotion:
            3.1.1. my pawn taken off
            3.1.2  my pawn put on
            3.1.3  my pawn taken off
            3.1.3  my promoted piece put on

            3.1.1. my pawn taken off
            3.1.2  my promoted piece put on

            3.1.1  my promoted piece put on
            3.1.2. my pawn taken off

        4. promotion with capture:
            4.1.1. his piece taken off
            4.1.2. my pawn taken off
            4.1.3  my pawn put on
            4.1.4  my pawn taken off
            4.1.5  my promoted piece put on

            4.2.1. his piece taken off
            4.2.2. my pawn taken off
            4.2.3  my promoted piece put on

            4.3.1. my pawn taken off
            4.3.2. his piece taken off and my pawn put on
            4.3.3  my pawn taken off
            4.3.4  my promoted piece put on

            4.4.1. my pawn and his piece taken off - get 2 chunks, same as 4.1 and 4.2

        5. en passant:
            5.1.1. my pawn taken off
            5.1.2. my pawn put on
            5.1.3. his pawn taken off

            5.2.1. my pawn taken off
            5.2.2. his pawn taken off
            5.2.3. my pawn put on

            5.2.1. his pawn taken off
            5.2.2. my pawn taken off
            5.2.3. my pawn put on

        6. castling:
            6.1.1. king off
            6.1.2. king on
            6.1.3. rook off
            6.1.4. rook on

            It seems that all other options are not legal, will not waste my time on them.

            Still:
            6.2.1. rook off - castling started with Rook, FIDE illegal, USCF Rule 10I2
            6.2.2. rook on
            6.2.3. king off
            6.2.4. king on

        */
        if(boardStatus == BoardStatus.SetupMess) {
//            requestBoardDump();
            return;     // ignore
        }

        if(invertedBoard) {
            moveChunk.square.setX(7 - moveChunk.square.getX());
            moveChunk.square.setY(7 - moveChunk.square.getY());
        }
        if(DEBUG) {
            logger.debug(moveChunk.square.toString() + " " + piece2String(moveChunk.piece));
        }
        // boardStatus == BoardStatus.Gage, recording:
        if(expectedPromotion != null) {
            if (moveChunk.square.equals(expectedPromotion) && moveChunk.piece != Config.EMPTY) {
                int color;
                if (expectedPromotion.getY() == 7) {
                    color = Config.WHITE;
                } else {
                    color = Config.BLACK;
                }
                if ((moveChunk.piece & Config.PIECE_COLOR) == color) {
                    incompleteMove.setPiecePromoted(moveChunk.piece);
                    if (pgnGraph.validateUserMove(incompleteMove)) {
                        addMove(incompleteMove);
                        return;
                    }
                }
            }
            chunks.add(moveChunk);
            return;
        }

        if(moveChunk.equals(expectedChunk)) {
            addMove(incompleteMove);
            return;
        }
        chunks.add(moveChunk);
        if(chunks.size() >= 2) {
            try {
                createMove();
            } catch (Config.PGNException e) {
                errorMsg = e.getMessage();
                logger.error(errorMsg, e);
                setBoardStatus(BoardStatus.SetupMess, true);
            }
        }
    }

    private void createMove() throws Config.PGNException {
        String msg = "";
        String sep = "";
        if(DEBUG) {
            msg = "createMove() ";
            sep = "";
        }
        int piecesOff = 0;
        int piecesOn = 0;
        Board board = pgnGraph.getBoard();
        int oldPiece = -1;
        DgtBoardWatcher.BoardMessageMoveChunk pieceChunk = null;
        DgtBoardWatcher.BoardMessageMoveChunk emptyChunk = null;
        DgtBoardWatcher.BoardMessageMoveChunk anyEmptyChunk = null;
        for(DgtBoardWatcher.BoardMessageMoveChunk chunk : chunks) {
            if(DEBUG) {
                msg += sep + chunk.toString();
                if (chunk.piece == Config.EMPTY) {
                    msg += String.format("(%c)", Config.FEN_PIECES.charAt(pgnGraph.getBoard().getPiece(chunk.square)));
                }
                sep = ", ";
            }
            if(chunk.piece == Config.EMPTY) {
                ++piecesOff;
                int piece = board.getPiece(chunk.square);
                anyEmptyChunk = chunk;
                if((board.getFlags() & Config.FLAGS_BLACK_MOVE) == (piece & Config.PIECE_COLOR)) {
                    emptyChunk = chunk;
                    oldPiece = piece;
                }
            } else {
                ++piecesOn;
                pieceChunk = chunk;
            }
        }

        if(piecesOn == 0) {
            if (DEBUG) {
                logger.debug(msg);
            }
            return;
        } else if(piecesOn > 1) {
            setBoardStatus(BoardStatus.SetupMess, true);
            if (DEBUG) {
                logger.debug(msg);
            }
            return;
        } else if(piecesOff == 1) {
            if(oldPiece == pieceChunk.piece && pieceChunk.square.equals(emptyChunk.square)) {
                if (DEBUG) {
                    logger.debug(msg + " adjust?");
                }
                chunks.clear();
                return;
            }
        }

        if(piecesOff >= 3) {
            setBoardStatus(BoardStatus.SetupMess, true);
            msg += " -> err";
            logger.debug(msg);
            return;
        }

        if((pieceChunk.piece & Config.PIECE_COLOR) != (board.getFlags() & Config.FLAGS_BLACK_MOVE) &&
                (pieceChunk.piece & ~Config.PIECE_COLOR) == Config.KING && anyEmptyChunk.square.getX() == 4 && (pieceChunk.square.getX() == 2 || pieceChunk.square.getX() == 6)) {
            // check castling started with Rook
            int y = -1;
            int rook;
            if (pieceChunk.piece == Config.WHITE_KING) {
                y = 0;
                rook = Config.WHITE_ROOK;
            } else {
                y = 7;
                rook = Config.BLACK_ROOK;
            }
            int x = anyEmptyChunk.square.getX() + (pieceChunk.square.getX() - anyEmptyChunk.square.getX()) / 2;
            if (pgnGraph.moveLine.size() > 1 && board.getPiece(x, y) == rook) {
                Board _board = pgnGraph.getBoard(pgnGraph.moveLine.get(pgnGraph.moveLine.size() - 2));
                Move move = new Move(_board, anyEmptyChunk.square, pieceChunk.square);
                if (_board.validateKingMove(move)) {
                    pgnGraph.delCurrentMove();
                    addMove(move);
                    incompleteMove = null;
                    msg += " -> castling started with Rook";
                    logger.debug(msg);
                    return;
                }
            }
            setBoardStatus(BoardStatus.SetupMess, true);
            msg += " -> err";
            logger.debug(msg);
            return;
        }

        Move move = board.newMove();
        if(oldPiece == pieceChunk.piece ||
                oldPiece == Config.WHITE_PAWN && pieceChunk.square.getY() == 7 ||
                oldPiece == Config.BLACK_PAWN && pieceChunk.square.getY() == 0
                ) {
            if (oldPiece != pieceChunk.piece) {
                move.setPiecePromoted(pieceChunk.piece);
            }
        } else if((oldPiece & ~Config.PIECE_COLOR) == Config.KING && (pieceChunk.piece & ~Config.PIECE_COLOR) == Config.ROOK ||
                (oldPiece & ~Config.PIECE_COLOR) == Config.KING && (pieceChunk.piece & ~Config.PIECE_COLOR) == Config.ROOK) {

        } else {
            msg += " -> ??";
            logger.debug(msg);
            setBoardStatus(BoardStatus.SetupMess, true);
            return;
        }
        move.setPiece(oldPiece);
        move.setTo(pieceChunk.square);

        for(DgtBoardWatcher.BoardMessageMoveChunk chunk : chunks) {
            if(chunk.piece != Config.EMPTY) {
                continue;
            }
            move.setFrom(chunk.square);
            if (pgnGraph.validateUserMove(move)) {
                if((move.moveFlags & Config.FLAGS_CASTLE) != 0) {
                    incompleteMove = move;
                    setChunksForCastling(move);
                } else if((move.moveFlags & Config.FLAGS_ENPASSANT) != 0) {
                    if(setChunksForEnpassant(move)) {
                        incompleteMove = move;
                    } else {
                        addMove(move);
                        incompleteMove = null;
                    }
                } else if((move.moveFlags & Config.FLAGS_PROMOTION) != 0) {
                    if(oldPiece != pieceChunk.piece) {
                        addMove(move);
                        incompleteMove = null;
                    } else {
                        setChunksForPromotion(move);
                        incompleteMove = move;
                    }
                } else {
                    addMove(move);
                    incompleteMove = null;
                }
                if(DEBUG) {
                    msg += " -> " + move.toString(true);
                    if (incompleteMove != null) {
                        msg += " incompl";
                    }
                    msg += " : " + pgnGraph.toPgn();
                    logger.debug(msg);
                }
                return;
            }
        }
    }

    private void setChunksForCastling(Move move) {
        int x;
        int piece;
        if(move.getPiece() == Config.WHITE_KING) {
            piece = Config.WHITE_ROOK;
        } else {
            piece = Config.BLACK_ROOK;
        }
        if(move.getTo().x == 6) {
            x = 5;  // 0-0
        } else {
            x = 3;  // 0-0-0
        }
        expectedChunk = new DgtBoardWatcher.BoardMessageMoveChunk(x, move.getFrom().y, piece);
        chunks.clear();
    }

    private boolean setChunksForEnpassant(Move move) {
        DgtBoardWatcher.BoardMessageMoveChunk expectedChunk = new DgtBoardWatcher.BoardMessageMoveChunk(move.getTo().x, move.getFrom().y, Config.EMPTY);
        for(DgtBoardWatcher.BoardMessageMoveChunk chunk : chunks) {
            if(chunk.equals(expectedChunk)) {
                return false;   // no chunks expected, move is complete
            }
        }
        this.expectedChunk = expectedChunk;
        chunks.clear();
        return true;
    }

    private void setChunksForPromotion(Move move) {
        expectedPromotion = move.getTo();
        chunks.clear();
    }

    private void addMove(Move move) {
        try {
            pgnGraph.addUserMove(move);
            resetChunks();
            logger.debug(String.format("%s\n%s", move.toString(), pgnGraph.getBoard().toString()));
        } catch (Config.PGNException e) {
            e.printStackTrace();
            errorMsg = e.getMessage();
            setBoardStatus(BoardStatus.SetupMess, true);
        }
        chunks.clear();
    }

    public interface DgtBoardObserver {
        void update(byte msgId);
    }

}
