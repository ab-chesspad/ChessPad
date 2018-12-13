package com.ab.pgn;

import android.annotation.SuppressLint;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * replavement for PgnTree as of v0.3
 * Created by Alexander Bootman on 10/29/17.
 */
public class PgnGraph {
/*
    public static String DEBUG_MOVE = "Nc6";
/*/
    public static String DEBUG_MOVE = null;
//*/
    public static boolean DEBUG = false;    // todo: config
    final static PgnLogger logger = PgnLogger.getLogger(PgnGraph.class);

    enum MergeState {
        Search,
        Merge,
        Skip,
    }

    protected Move rootMove = new Move(Config.FLAGS_NULL_MOVE); // can hold initial comment
    protected PgnItem.Item pgn;                                 // headers and moveText
    boolean modified;
    public LinkedList<Move> moveLine = new LinkedList<>();
    Map<Pack, Board> positions = new HashMap<>();

    transient private String parsingError;
    transient private int parsingErrorNum;

    public PgnGraph() throws Config.PGNException {
        init(new Board(), null);
    }

    public PgnGraph(Board initBoard) throws Config.PGNException {
        init(initBoard, null);
    }

    private void init(Board initBoard, PgnItem.Item pgn) throws Config.PGNException {
        if(pgn == null) {
            pgn = new PgnItem.Item("dummy");
        } else if(pgn.headers == null ) {
            pgn.headers = new LinkedList<>();
        }
        this.pgn = pgn;
        if(pgn.headers.size() == 0 ) {
            setSTR();
        }
        modified = false;
        parsingError = null;
        parsingErrorNum = 0;
        rootMove.packData = initBoard.pack();
        positions.put(new Pack(rootMove.packData), initBoard);
        moveLine.add(rootMove);
    }

    private void setSTR() {
        for (String str : Config.STR) {
            pgn.addHeader(new Pair<>(str, "?"));
        }
    }

    public PgnGraph(PgnItem.Item item, PgnItem.ProgressObserver progressObserver) throws Config.PGNException {
        Board initBoard;
        Date start = new Date();
        String fen = item.getHeader(Config.HEADER_FEN);
        if (fen == null) {
            initBoard = new Board();
        } else {
            initBoard = new Board(fen);
            parsingErrorNum = initBoard.validateSetup();
        }

        if (parsingErrorNum == 0) {
            init(initBoard, item);
            try {
                PgnParser.parseMoves(item.getMoveText(), new CpMoveTextHandler(this), progressObserver);
            } catch (Exception e) {
                logger.error(e.getMessage());
                parsingError = e.getMessage();
            }
        }
        Date end = new Date();
        printDuration("Pgn loaded", start, end);
        modified = false;
    }

    private void printDuration(String msg, Date start, Date end) {
        long duration = (end.getTime() - start.getTime()) / 1000;
        long minutes = duration / 60;
        long seconds = duration % 60;
        String m = String.format(Locale.getDefault(), "%s, duration %02d:%02d", msg, minutes, seconds);
        logger.debug(m);
    }

    public String getParsingError() {
        return parsingError;
    }

    public int getParsingErrorNum() {
        return parsingErrorNum;
    }

    public void serializeGraph(DataOutputStream os, int versionCode) throws Config.PGNException {
        for(Map.Entry<Pack, Board> entry : positions.entrySet()) {
            entry.getValue().setVisited(false);
        }
        try {
            os.write(versionCode);  // single byte
            os.writeInt(positions.size());
            Date start = new Date();
            Board board = getInitBoard();
            board.serialize(os);
            // 1. serialize movessave
            serializeGraph(os, rootMove);

            // 2. serialize headers, modified
            pgn.serialize(os);
            if (modified) {
                os.write(1);    // single byte
            } else {
                os.write(0);
            }
            os.flush();
            Date end = new Date();
            printDuration("Pgn serialized", start, end);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    private void serializeGraph(DataOutputStream os, Move move) throws Config.PGNException {
        Board nextBoard = this.getBoard(move.packData);
        int flags = 0;
        Move nextMove = nextBoard.getMove();
        if(nextMove != null) {
            flags |= Move.HAS_NEXT_MOVE;
        }
        Move variation = move.getVariation();
        if(variation != null) {
            flags |= Move.HAS_VARIATION;
        }
        move.serialize(os, flags);
        if(DEBUG) {
            logger.debug(String.format("writer %s, %d", move.toCommentedString(), os.size()));
        }
        if (nextBoard.wasVisited()) {
            if(DEBUG) {
                logger.debug(String.format("writer visited %s, skip", move.toCommentedString()));
            }
            if(variation != null) {
                serializeGraph(os, variation);
            }
            return;
        }
        nextBoard.setVisited(true);
        if(nextMove != null) {
            serializeGraph(os, nextMove);
        }
        if(variation != null) {
            serializeGraph(os, variation);
        }
    }

    public PgnGraph(DataInputStream is, int versionCode, PgnItem.ProgressObserver progressObserver) throws Config.PGNException {
        unserializeGraph(is, versionCode, progressObserver);
    }

    public void unserializeGraph(DataInputStream is, int versionCode, PgnItem.ProgressObserver progressObserver) throws Config.PGNException {
        try {
            int totalLen = is.available();
            int oldVersionCode;
            if (versionCode != (oldVersionCode = is.read())) {  // single byte
                throw new Config.PGNException(String.format("Old serialization %d ignored", oldVersionCode));
            }
            int positionsSize = is.readInt();
            Date start = new Date();
            // remove defaults
            moveLine.clear();
            positions = new HashMap<>(positionsSize);
            Board board = new Board(is);
            logger.debug(String.format("reader board %s", board.toString()));
            unserializeGraph(is, board, progressObserver, totalLen);

            pgn = (PgnItem.Item) PgnItem.unserialize(is);
            if (is.read() == 1) {
                modified = true;
            } else {
                modified = false;
            }
            Date end = new Date();
            printDuration("Pgn unserialized", start, end);
        } catch (Exception e) {
            throw new Config.PGNException(e);
        }
    }

    private Move unserializeGraph(DataInputStream is, Board previousBoard, PgnItem.ProgressObserver progressObserver, int totalLen) throws Config.PGNException {
        try {
            Move move = new Move(is, previousBoard);
            if(progressObserver != null && totalLen > 0) {
                int progress = (totalLen - is.available()) * 100 / totalLen;
                progressObserver.setProgress(progress);
            }
            if(DEBUG & (totalLen - is.available()) == 186) {
                logger.debug(String.format("reader %s, %d\n%s", move.toCommentedString(), (totalLen - is.available()), previousBoard.toString()));
            }
            if(positions.size() == 0) {
                // set up rootMove
                rootMove = move;
                rootMove.packData = previousBoard.pack();
                positions.put(new Pack(rootMove.packData), previousBoard);
                moveLine.addLast(rootMove);
            } else {
                if (!this.addMove(move, previousBoard)) {
                    if(move.hasVariation()) {
                        move.variation = unserializeGraph(is, previousBoard, progressObserver, totalLen);
                    }
                    return move;
                }
            }
            if(move.hasNextMove()) {
                Board nextBoard = positions.get(new Pack(move.packData));
                unserializeGraph(is, nextBoard, progressObserver, totalLen);
            }
            if(move.hasVariation()) {
                move.variation = unserializeGraph(is, previousBoard, progressObserver, totalLen);
            }
            logger.debug(String.format("reader %s complete", move.toCommentedString()));
            move.cleanupSerializationFlags();
            return move;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public void serializeGraph(final BitStream.Writer writer, int versionCode) throws Config.PGNException {
        for(Map.Entry<Pack, Board> entry : positions.entrySet()) {
            entry.getValue().setVisited(false);
        }
        try {
            writer.write(versionCode, 4);
            Date start = new Date();
            Board board = getInitBoard();
            board.serialize(writer);
            // 1. serialize positions
            logger.debug(String.format("writer board %d", writer.bitCount));
            serializeGraph(writer, rootMove);

            // 2. serialize headers, modified
            pgn.serialize(writer);
            if (modified) {
                writer.write(1, 1);
            } else {
                writer.write(0, 1);
            }
            Date end = new Date();
            printDuration("Pgn serialized", start, end);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    private void serializeGraph(BitStream.Writer writer, Move move) throws Config.PGNException {
        try {
            if (move == null) {
                writer.write(0, 1);
                if(DEBUG) {
                    logger.debug(String.format("writer E %d", writer.bitCount));
                }
            } else {
                writer.write(1, 1);
                move.serialize(writer, false);
                Board nextBoard = this.getBoard(move.packData);
                if(DEBUG) {
                    logger.debug(String.format("writer %s %d\n%s", move.toCommentedString(), writer.bitCount, nextBoard.toString()));
                }
                if(DEBUG_MOVE != null && DEBUG_MOVE.equals(move.toCommentedString())) {
                    System.out.println(String.format("writer %s\n%s", move.toCommentedString(), nextBoard.toString()));
                }
                if (nextBoard.wasVisited()) {
                    if(DEBUG) {
                        logger.debug(String.format("writer visited %s %d, skip", move.toCommentedString(), writer.bitCount));
                    }
                    serializeGraph(writer, move.getVariation());
                    return;
                }
                nextBoard.setVisited(true);
                serializeGraph(writer, nextBoard.getMove());
                serializeGraph(writer, move.getVariation());
                logger.debug(String.format("writer %s complete %d", move.toCommentedString(), writer.bitCount));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public PgnGraph(BitStream.Reader reader, int versionCode, PgnItem.ProgressObserver progressObserver) throws Config.PGNException {
        unserializeGraph(reader, versionCode, progressObserver);
    }

    public void unserializeGraph(final BitStream.Reader reader, int versionCode, PgnItem.ProgressObserver progressObserver) throws Config.PGNException {
        try {
            int oldVersionCode;
            if (versionCode != (oldVersionCode = reader.read(4))) {
                throw new Config.PGNException(String.format("Old serialization %d ignored", oldVersionCode));
            }
            int totalLen = reader.available();
            Date start = new Date();
            // remove defaults
            moveLine.clear();
            positions.clear();
            Board board = new Board(reader);
            logger.debug(String.format("reader board %d", reader.bitCount));
            unserializeGraph(reader, board, progressObserver, totalLen);

            pgn = (PgnItem.Item) PgnItem.unserialize(reader);
            if (reader.read(1) == 1) {
                modified = true;
            } else {
                modified = false;
            }
            Date end = new Date();
            printDuration("Pgn unserialized", start, end);
        } catch (Exception e) {
            throw new Config.PGNException(e);
        }
    }

    private Move unserializeGraph(BitStream.Reader reader, Board previousBoard, PgnItem.ProgressObserver progressObserver, int totalLen) throws Config.PGNException {
        try {
            if (reader.read(1) == 0) {
                if(DEBUG) {
                    logger.debug(String.format("reader E %d", reader.bitCount));
                }
                return null;
            }
            Move move = new Move(reader, previousBoard, false);
            if(progressObserver != null && totalLen > 0) {
                int progress = (totalLen - reader.available()) * 100 / totalLen;
                progressObserver.setProgress(progress);
            }
            if(DEBUG) {
                logger.debug(String.format("reader %s %d\n%s", move.toCommentedString(), reader.bitCount, previousBoard.toString()));
            }
            if(DEBUG_MOVE != null && DEBUG_MOVE.equals(move.toCommentedString())) {
                System.out.println(String.format("reader %s %d", move.toCommentedString(), reader.bitCount));
            }
            if(positions.size() == 0) {
                // set up rootMove
                rootMove = move;
                rootMove.packData = previousBoard.pack();
                positions.put(new Pack(rootMove.packData), previousBoard);
                moveLine.addLast(rootMove);
            } else {
                if (!this.addMove(move, previousBoard)) {
                    move.variation = unserializeGraph(reader, previousBoard, progressObserver, totalLen);
                    return move;
                }
            }
            Board nextBoard = positions.get(new Pack(move.packData));
            unserializeGraph(reader, nextBoard, progressObserver, totalLen);
            move.variation = unserializeGraph(reader, previousBoard, progressObserver, totalLen);
            logger.debug(String.format("reader %s complete %d", move.toCommentedString(), reader.bitCount));
            return move;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public void serializeMoveLine(BitStream.Writer writer, int versionCode) throws Config.PGNException {
        try {
            writer.write(versionCode, 4);
            writer.write(moveLine.size() - 1, 9);   // max 256 2-ply moves
            boolean skip = true;
            for(Move move : moveLine) {
                if(skip) {
                    skip = false;
                    continue;
                }
                move.serialize(writer);
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    @SuppressLint("DefaultLocale")
    public void unserializeMoveLine(BitStream.Reader reader, int versionCode) throws Config.PGNException {
        try {
            int oldVersionCode;
            if (versionCode != (oldVersionCode = reader.read(4))) {
                throw new Config.PGNException(String.format("Old serialization %d ignored", oldVersionCode));
            }
            moveLine.clear();
            int moveLineSize = reader.read(9);
            Board board = getInitBoard();
            for( int i = 0; i < moveLineSize; ++i) {
                Move move = new Move(reader, board);
                board = this.getBoard(move);
                moveLine.addLast(move);
            }
        } catch (Exception e) {
            if(moveLine.isEmpty()) {
                moveLine.add(rootMove);
            }
            throw new Config.PGNException(e);
        }
    }

    public String getTitle() {
        return PgnItem.getTitle(pgn.getHeaders(), pgn.index);
    }

    public void setHeaders(List<Pair<String, String>> headers) {
        String fen = pgn.getHeader(Config.HEADER_FEN);
        pgn.setHeaders(headers);
        if(fen != null) {
            pgn.addHeader(new Pair<>(Config.HEADER_FEN, fen));
        }
        modified = true;
    }

    public void toInit() {
        while(moveLine.size() > 1) {
            moveLine.removeLast();
        }
    }

    public Board getInitBoard() {
        return positions.get(new Pack(rootMove.packData));
    }

    public Board getBoard() {
        return positions.get(new Pack(getCurrentMove().packData));
    }

    public Board getBoard(int[] packData) {
        return positions.get(new Pack(packData));
    }

    public Board getBoard(Move move) {
        return getBoard(move.packData);
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public String getComment() {
        Move move = moveLine.getLast();
        return move.comment;
    }

    public void setComment(String newComment) {
        Move move = getCurrentMove();

        String oldComment = move.comment;
        if (oldComment == null) {
            oldComment = "";
        }
        if (!newComment.equals(oldComment)) {
            if(DEBUG) {
                Board board = this.getBoard();
                if(board != null) {
                    logger.debug(String.format("comment %s -> %s\n%s", move.toString(), newComment, board.toString()));
                }
            }
            move.comment = newComment;
            if (move.comment.isEmpty()) {
                move.comment = null;
            }
            modified = true;
        }
    }

    public PgnItem.Item getPgn() {
        return pgn;
    }

    // needed for 'unserialization'
    public void setPgn(PgnItem.Item pgn) {
        this.pgn = pgn;
    }

    public void save(boolean updateMoves, PgnItem.ProgressObserver progressObserver) throws Config.PGNException {
        if (pgn != null) {
            Date start = new Date();
            if (updateMoves) {
                if (!this.getInitBoard().equals(new Board()) && pgn.getHeader(Config.HEADER_FEN) == null) {
                    String fen = getInitBoard().toFEN();
                    pgn.headers.add(new Pair<>(Config.HEADER_FEN, fen));
                }
                pgn.setMoveText(this.toPgn());
            }
            pgn.save(progressObserver);
            printDuration("Pgn saved", start, new Date());
            modified = false;
        }
    }

    public void toNext() {
        Board board = getBoard();
        if(board != null) {
            // sanity check
            moveLine.addLast(board.getMove());
            if (DEBUG) {
                logger.debug(String.format("toNext %s\n%s", getCurrentMove().toString(), this.getBoard().toString()));
            }
        }
    }

    public void toPrev() {
        if(moveLine.size() > 1) {
            moveLine.removeLast();
        }
        if(DEBUG) {
            Board board = getBoard();
            if(board == null) {
                return;
            }
            logger.debug(String.format("toPrev %s\n%s", getCurrentMove().toString(), board.toString()));
        }
    }

    public void toPrevVar() {
        while(moveLine.size() > 1) {
            moveLine.removeLast();
            Board board = getBoard();
            if(board == null) {
                return;
            }
            if(board.getMove().getVariation() != null) {
                break;
            }
        }
        if(DEBUG) {
            logger.debug(String.format("toPrev %s\n%s", getCurrentMove().toString(), getBoard().toString()));
        }
    }

    public void toEnd() {
        Board board = getBoard();
        if(board == null) {
            // quick and dirty
            board = getInitBoard();
        }
        Move move;
        while((move = board.getMove()) != null) {
            moveLine.addLast(move);
            board = getBoard();
            if(board == null) {
                return;
            }
        }
    }

    public void toVariation(Move variation) {
        Board board = getBoard();
        if(board == null) {
            return;
        }
        Move move = board.getMove();
        while(move != null) {
            if(variation == move) {
                break;
            }
            move = move.getVariation();
        }
        if(move != null) {
            moveLine.addLast(move);
        }
    }

    public Move getNextMove(Move move) {
        Board board = getBoard(move);
        if(board == null) {
            // sanity check, should never happen
            return null;
        }
        return board.getMove();
    }

    public Move getCurrentMove() {
        return moveLine.getLast();
    }

    public boolean isInit() {
        return moveLine.size() == 1;
    }

    public int getGlyph() {
        return getCurrentMove().getGlyph();
    }

    public void setGlyph(int glyth) {
        if (!okToSetGlyph()) {
            return;
        }
        if (getCurrentMove().getGlyph() != glyth) {
            getCurrentMove().setGlyph(glyth);
            modified = true;
        }
    }

    public boolean okToSetGlyph() {
        return !isInit();
    }


    public boolean isEnd() {
        Board board = getBoard();
        return board == null || board.getMove() == null;
    }

    public List<Move> getVariations() {
        Board board = getBoard();
        if(board == null) {
            return null;
        }
        Move move = board.getMove();
        Move variation;
        if(move == null || (variation = move.getVariation()) == null) {
            return null;
        }
        List<Move> res = new LinkedList<>();
        res.add(move);
        do {
            res.add(variation);
            variation = variation.variation;
        } while(variation != null);
        return res;
    }

    public int getFlags() {
        Board board = getBoard();
        if(board == null) {
            return 0;
        }
        return board.getFlags();
    }

    // complete move, needs validation
    public boolean validateUserMove(Move newMove) {
        Board board = getBoard();
        int piece = board.getPiece(newMove.getFrom());
        if(piece != newMove.getPiece()) {
            return false;
        }
        if((board.getFlags() & Config.BLACK) != (piece & Config.BLACK)) {
            return false;
        }
        piece = newMove.getColorlessPiece();
        if(piece == Config.KING) {
            if(!board.validateKingMove(newMove)) {
                return false;
            }
            return board.validateOwnKingCheck(newMove);
        }

        if (board.validatePgnMove(newMove, Config.VALIDATE_USER_MOVE)) {
            if(piece != Config.PAWN) {
                // check ambiguity to set moveFlags
                Move test = newMove.clone();
                int testFrom_x, testFrom_y;
                for (testFrom_y = 0; testFrom_y < Config.BOARD_SIZE; testFrom_y++) {
                    for (testFrom_x = 0; testFrom_x < Config.BOARD_SIZE; testFrom_x++) {
                        if (board.getPiece(testFrom_x, testFrom_y) == newMove.getPiece() &&
                                !(testFrom_x == newMove.getFromX() && testFrom_y == newMove.getFromY())) {
                            test.setFrom(testFrom_x, testFrom_y);
                            if (board.validatePgnMove(test, Config.VALIDATE_PGN_MOVE)) {
                                if(testFrom_x != newMove.getFromX()) {
                                    newMove.moveFlags |= Config.FLAGS_X_AMBIG;
                                } else if(testFrom_y != newMove.getFromY()) {
                                    newMove.moveFlags |= Config.FLAGS_Y_AMBIG;
                                }
                            }
                        }
                    }
                }

            }
            return true;
        }
        return false;
    }

    // incomplete move, find move.from, return false if cannot find it
    // do not check if the move is legal
    public boolean validatePgnMove(Move newMove) throws Config.PGNException {
        if (newMove.isNullMove()) {
            return true;
        }
        if(newMove.getColorlessPiece() == Config.KING) {
            if((newMove.moveFlags & Config.FLAGS_BLACK_MOVE) == 0) {
                newMove.setFrom(this.getBoard().getWKing());
            } else {
                newMove.setFrom(this.getBoard().getBKing());
            }
            return true;
        }
        int fromX, fromY;
        if(newMove.getColorlessPiece() == Config.PAWN) {
            int dy, lastY;
            int hisPawn;
            fromX = newMove.getToX();
            if((newMove.moveFlags & Config.FLAGS_BLACK_MOVE) == 0) {
                dy = 1;
                lastY = Config.BOARD_SIZE - 1;
                hisPawn = Config.BLACK_PAWN;
            } else {
                dy = -1;
                hisPawn = Config.WHITE_PAWN;
                lastY = 0;
            }
            if(newMove.getToY() == lastY) {
                if((newMove.moveFlags & Config.FLAGS_PROMOTION) == 0){
                    return false;
                }
            }

            if(!newMove.isFromXSet()) {
                newMove.setFromX(newMove.getToX());
            }
            newMove.moveFlags &= ~Config.FLAGS_ENPASSANT_OK;
            fromY = newMove.getToY() - dy;
            if(this.getBoard().getPiece(fromX, fromY) == Config.EMPTY) {
                fromY -= dy;
                if(this.getBoard().getPiece(newMove.getToX() - 1, newMove.getToY()) == hisPawn ||
                        this.getBoard().getPiece(newMove.getToX() + 1, newMove.getToY()) == hisPawn ) {
                    newMove.moveFlags |= Config.FLAGS_ENPASSANT_OK;
                }
            }
            if(fromY != newMove.getToX() &&
                    this.getBoard().getPiece(newMove.getTo()) == Config.EMPTY &&
                    this.getBoard().getPiece(newMove.getToX(), fromY) == hisPawn ) {
                newMove.moveFlags |= Config.FLAGS_CAPTURE | Config.FLAGS_ENPASSANT;
            }
        }

        // find newMove.from by validating newMove
        int x0 = 0, x1 = Config.BOARD_SIZE - 1, y0 = 0, y1 = Config.BOARD_SIZE - 1;
        if (newMove.getPiece() == Config.WHITE_PAWN) {
            ++y0;
        } else if (newMove.getPiece() == Config.BLACK_PAWN) {
            --y1;
        }
        if (newMove.isFromXSet())
            x0 = x1 = newMove.getFromX();
        if (newMove.isFromYSet())
            y0 = y1 = newMove.getFromY();

        for (fromY = y0; fromY <= y1; fromY++) {
            for (fromX = x0; fromX <= x1; fromX++) {
                if (this.getBoard().getPiece(fromX, fromY) == newMove.getPiece()) {
                    newMove.setFrom(fromX, fromY);
                    if (getBoard().validatePgnMove(newMove, Config.VALIDATE_PGN_MOVE)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static String getMoveNum(Board board) {
        if(board == null) {
            return "";
        }
        int plyNum = board.getPlyNum();
        return "" + ((plyNum + 1) / 2) + ". "
                + ((plyNum & 1) == 1 ? "" : "... ");
    }

    public String getNumberedMove(Move move) {
        if(moveLine.size() == 1) {
            return "";
        }
        return getMoveNum(getBoard(move.packData)) + move.toString();
    }

    public String getNumberedMove() {
        return getNumberedMove(getCurrentMove());
    }

    public String getMoveNum(Move move) {
        return getMoveNum(getBoard(move.packData));
    }

    public void addMove(Move newMove) throws Config.PGNException {
        addMove(newMove, null);
    }

    // todo: simplify!
    // return true if the new position is added to positions
    public boolean addMove(Move newMove, Board prevBoard) throws Config.PGNException {
        Board board;
        if(prevBoard == null) {
            board = getBoard();
        } else {
            board = prevBoard;
        }
        Board newBoard = board.clone();
        newBoard.doMove(newMove);
        newMove.packData = newBoard.pack();
        if(isRepetition(newMove)) {
            newMove.moveFlags |= Config.FLAGS_REPETITION;
        }
        Move move;
        if(DEBUG_MOVE != null && DEBUG_MOVE.equals(newMove.toCommentedString())) {
            System.out.println(String.format("addMove %s", newMove.toCommentedString()));
        }
        Board oldBoard = positions.put(new Pack(newMove.packData), newBoard);
        if(oldBoard != null) {
            // position occurred already
            newBoard.setMove(oldBoard.getMove());
            // find if the same move exists already
            move = board.getMove();     // move from previous position
            Move prev = null;
            while(move != null) {
                if(move.isSameAs(newMove)) {
                    if(prev == null) {
                        board.setMove(newMove);
                    } else {
                        // cannot leave the old move because newMove is referred in the caller
                        prev.variation = newMove;
                    }
                    // replace
                    newMove.variation = move.variation;
                    newMove.comment = move.comment;
                    newMove.setGlyph(move.getGlyph());
                    break;
                }
                prev = move;
                move = move.variation;
            }
            if(move == null) {
                if(DEBUG) {
                    logger.debug(String.format("move %s%s, replacing:\n%s", getMoveNum(newMove), newMove.toString(), oldBoard.toString()));
                }
                if(prev == null) {
                    board.setMove(newMove);
                } else {
                    newMove.variation = prev.variation;
                    prev.variation = newMove;
                }
                newBoard.setInMoves(oldBoard.getInMoves() + 1);
            } else {
                newBoard.setInMoves(oldBoard.getInMoves());
            }
            if(DEBUG) {
                logger.debug(String.format("move %s, old in=%s, new in=%s", getNumberedMove(newMove), oldBoard.getInMoves(), newBoard.getInMoves()));
            }
            if(prevBoard == null) {
                moveLine.addLast(newMove);
            }
            modified = true;
            return false;
        }

        if((move = board.getMove()) == null) {
            board.setMove(newMove);
        } else {
            if(move.isSameAs(newMove)) {
                board.setMove(newMove);
                newMove.comment = move.comment;
                newMove.setGlyph(move.getGlyph());
            } else {
                Move variation;
                while((variation = move.getVariation()) != null) {
                    if(variation.isSameAs(newMove)) {
                        move.setVariation(newMove);
                        newMove.comment = variation.comment;
                        newMove.setGlyph(variation.getGlyph());
                        break;
                    }
                    move = variation;
                }
                if(variation == null) {
                    move.variation = newMove;
                }
            }
        }
        if(prevBoard == null) {
            moveLine.addLast(newMove);
        }
        modified = true;
        return true;
    }

    public void addUserMove(Move move)  throws Config.PGNException {
        addMove(move);
        Board board = getBoard().clone();
        board.invertFlags(Config.FLAGS_BLACK_MOVE);
        Square target;
        if((board.getFlags() & Config.FLAGS_BLACK_MOVE) == 0) {
            target = board.getBKing();
        } else {
            target = board.getWKing();
        }
        Move checkMove;
        if((checkMove = board.findAttack(target, null)) != null) {
            checkMove.moveFlags &= ~Config.FLAGS_BLACK_MOVE;
            checkMove.moveFlags |= board.getFlags() & Config.FLAGS_BLACK_MOVE;
            if(board.validateCheckmate(checkMove)) {
                move.moveFlags |= Config.FLAGS_CHECKMATE;
            } else {
                move.moveFlags |= Config.FLAGS_CHECK;
            }
        }
    }

    // delete last in moveLine, it can be a variation
    public void delCurrentMove() {
        if(moveLine.size() <= 1) {
            return;     // exception? rootMove cannot be deleted
        }
        Move move2Del = moveLine.removeLast();
        Move prevMove = getCurrentMove();
        Board prevBoard = getBoard(prevMove);
        Move m = prevBoard.getMove();
        if (move2Del.isSameAs(m)) {
            prevBoard.setMove(move2Del.variation);
        } else {
            Move pm = null;
            while (!move2Del.isSameAs(m)) {
                pm = m;
                m = m.variation;
            }
            pm.variation = m.variation;
        }

        delPositionsAfter(move2Del);
        modified = true;
    }

    private void delPositionsAfter(Move move) {
        if (move == null) {
            return;
        }
        Board board = getBoard(move);
        logger.debug(String.format("delPositionsAfter %s, %s\n%s", board.getInMoves(), move.toString(), board.toString()));
        while(move != null) {
            board = getBoard(move);
            if(board == null) {
                // should never happen
                logger.error(String.format("board == null after %s", move.toString()));
                return;
            }
            if(board.getInMoves() > 0) {
                board.incrementInMoves(-1);
                logger.debug(String.format("decrement inMoves to %s, %s\n%s", board.getInMoves(), move.toString(), board.toString()));
                return;
            }
            logger.debug(String.format("delete %s\n%s", move.toString(), board.toString()));
            positions.remove(new Pack(move.packData));
            delPositionsAfter(move.getVariation());
            move = board.getMove();
        }
    }

    public String toPgn() {
        Date start = new Date();
        for(Map.Entry<Pack, Board> entry : positions.entrySet()) {
            entry.getValue().setVisited(false);
        }
        String pgn = toPgn(null, rootMove);

        // quick and dirty:
        StringBuilder res = new StringBuilder(pgn.length() * 13 / 10);
        int index = 0;
        while(index < pgn.length()) {
            int e = pgn.indexOf(" ", index + Config.PGN_OUTPUT_LINE_SIZE);
            if(e == -1) {
                e = pgn.length();
            }
            res.append(pgn.substring(index, e)).append("\n");
            index = e;
        }
        String resStr = new String(res);
        Date end = new Date();
        printDuration("toPgn", start, end);
        return resStr;
    }

    private String toPgn(Board prevBoard, Move _move) {
        Move move = _move;
        LinkedList<TraverseData> pgnParts = new LinkedList<>();
        StringBuilder sb = new StringBuilder();
        Board board = prevBoard;
        boolean skipVariant = true;    // skip 1st variant because it will be handled in the caller
        boolean showMoveNum = true;
        while (move != null) {
            // trace current line till the end or visited vertex
            if(move != rootMove) {
                if(!showMoveNum) {
                    showMoveNum = (board.getFlags() & Config.FLAGS_BLACK_MOVE) == 0;
                }
                if (showMoveNum) {
                    sb.append(getMoveNum(move));
                }
                showMoveNum = false;
                sb.append(move.toString());
                if (move.getGlyph() > 0) {
                    sb.append(Config.PGN_GLYPH).append(move.getGlyph()).append(" ");
                }
            }
            if(move.comment != null && !move.comment.isEmpty()) {
                sb.append(Config.COMMENT_OPEN).append(move.comment).append(Config.COMMENT_CLOSE).append(" ");
            }

            if(DEBUG_MOVE != null && DEBUG_MOVE.equals(move.toCommentedString())) {
                System.out.println(String.format("writer %s\n%s", move.toCommentedString(), prevBoard.toString()));
            }

            if(move.variation != null) {
                if(!skipVariant) {
                    TraverseData td = new TraverseData();
                    td.prevBoard = prevBoard;
                    td.move = move.variation;
                    td.pgnText = new String(sb);
                    if(DEBUG_MOVE != null && DEBUG_MOVE.equals(td.move.toCommentedString())) {
                        System.out.println(String.format("writer %s\n%s", move.toCommentedString(), prevBoard.toString()));
                    }
                    pgnParts.addLast(td);
                    sb = new StringBuilder();
                    showMoveNum = true;
                }
            }
            prevBoard = board;
            board = this.getBoard(move.packData);
            if(board.wasVisited()) {
                break;
            }
            board.setVisited(true);
            move = board.getMove();
            skipVariant = false;        // skip only variant of the 1st move
        }

        while(pgnParts.size() > 0) {
            TraverseData td = pgnParts.removeLast();
            move = td.move;
            prevBoard = td.prevBoard;
            StringBuilder vsb = new StringBuilder();
            while(move != null) {
                String variation = toPgn(prevBoard, move);
                vsb.append("(");
                vsb.append(variation);
                vsb.append(") ");
                move = move.variation;
            }

            // in reversed order:
            sb.insert(0, vsb);
            sb.insert(0, td.pgnText);
        }
        return new String(sb);
    }

    // sanity check
    int getNumberOfMissingVertices() {
        int count = 0;
        for(Map.Entry<Pack, Board> entry : positions.entrySet()) {
            if (!entry.getValue().wasVisited()) {
                // should never happen
                String msg = String.format("missed position: \n%s", entry.getValue().toString());
                logger.error(msg);
                ++count;
            }
        }
        return count;
    }

    public static String withIndent(String str, int offset) {
        if( offset == 0) {
            return str;
        }
        String sOffset = String.format("%1$" + offset + "s", "");
        return String.format("%s%s", sOffset, str);
    }

    private class TraverseData {
        Board prevBoard;
        Move move;
        Board nextBoard;
        boolean variantFirstMove = true;

        String pgnText;

        TraverseData() {
        }

        TraverseData(Board prevBoard, Move move) {
            this.prevBoard = prevBoard;
            this.move = move;
            this.nextBoard = PgnGraph.this.getBoard(move.packData);
        }

        TraverseData(TraverseData that) {
            this.prevBoard = that.prevBoard;
            this.move = that.move;
            this.nextBoard = that.nextBoard;
        }
    }

    public boolean isRepetition(Move move) {
        if(moveLine.size() == 0) {
            return false;
        }
        int count = 0;
        Pack searchedPack = new Pack(move.packData);
        ListIterator<Move> it = moveLine.listIterator(moveLine.size() - 1);
        while(it.hasPrevious()) {
            move = it.previous();
            if(move.isNullMove() && move != rootMove) {
                break;
            }
            if(searchedPack.equalPosition(new Pack(move.packData))) {
                if(++count == 2) {
                    return true;
                }
            }
            Board b = getBoard(move);
            if(b.getReversiblePlyNum() == 0) {
                break;
            }
        }
        return false;
    }

    public void merge(final MergeData mergeData, PgnItem.ProgressObserver progressObserver) throws Config.PGNException {
        final PgnItem.ProgressNotifier progressNotifier = new PgnItem.ProgressNotifier(progressObserver);
        final Move mergeMove = this.getCurrentMove();
        final int[] offset = {0};
        final int[] index = {0};
        mergeData.merged = 0;
        final PgnItem.Pgn pgn = new PgnItem.Pgn(mergeData.pgnPath);
        ((PgnItem.Dir)pgn.getParent()).walkThroughGrandChildren(pgn, new PgnItem.EntryHandler() {
            @Override
            public boolean handle(PgnItem entry, BufferedReader br) throws Config.PGNException {
                entry.offset = offset[0];
                index[0] = entry.getIndex();
                int index1 = entry.getIndex() + 1;  // start and end 1-based
                if(index1 >= mergeData.start) {
                    if(merge(mergeMove, (PgnItem.Item)entry, mergeData)) {
                        ++mergeData.merged;
                    }
                }
                return mergeData.end == -1 || index1 < mergeData.end;
            }

            @Override
            public boolean getMoveText(PgnItem entry) {
                int index1 = entry.getIndex() + 1;  // start and end 1-based
                return index1 >= mergeData.start;
            }

            @Override
            public void addOffset(int length, int totalLength) {
                offset[0] += length;
                if(mergeData.end == -1) {
                    progressNotifier.setOffset(offset[0], pgn.getLength());
                } else {
                    progressNotifier.setOffset(index[0], mergeData.end - mergeData.start + 1);
                }
            }
        });
    }

    private final int[] testPackData = new int[] {0x1820E7AF, 0xEDEB1404, 0x6F040783, 0xE3789B0F, 0xE7EFF74D, 0xBC489F9F};
    private final Pack testPack = new Pack(testPackData);
    private Board getTestBoard() {
        return this.getBoard(testPackData);
    }

    static void modifyStatisticsComment(Move move, String result) {
        if (result == null) {
            return;
        }

        int[] counts = new int[3];
        if(result.equals("1-0")) {
            ++counts[0];
        } else if(result.equals("0-1")) {
            ++counts[1];
        } else if(result.equals("1/2-1/2")) {
            ++counts[2];
        } else {
            return;    // unknown result
        }

        StringBuilder comment = new StringBuilder();
        if(move.comment == null) {
            comment.append("w=").append(counts[0]);
            comment.append("; ").append("b=").append(counts[1]);
            comment.append("; ").append("d=").append(counts[2]);
            move.comment = new String(comment);
            return;
        }
        String sep = "";
        // expecting {sadcbiwubd; w=123; b=5; d=3; abc; qeaidbaidc}
        String[] parts = move.comment.split("; ");
        for(String part : parts) {
            try {
                int count = 0;
                if (part.startsWith("w=")) {
                    count = Integer.valueOf(part.substring(2)) + counts[0];
                    comment.append(sep).append("w=").append(count);
                } else if (part.startsWith("b=")) {
                    count= Integer.valueOf(part.substring(2)) + counts[1];
                    comment.append(sep).append("b=").append(count);
                } else if (part.startsWith("d=")) {
                    count = Integer.valueOf(part.substring(2)) + counts[2];
                    comment.append(sep).append("d=").append(count);
                } else {
                    comment.append(sep).append(part);
                }
            } catch(Exception e) {
                // invalid format
                comment.append(sep).append(part);
            }
            sep = "; ";
        }
        move.comment = new String(comment);
    }

    public boolean merge(final Move mergeMove, final PgnItem.Item item, final MergeData mergeData) throws Config.PGNException {
        final Pack mergePack = new Pack(mergeMove.packData);
        final boolean[] merged = {false};
        try {
            final PgnGraph mergeCandidate = new PgnGraph();
            PgnParser.parseMoves(item.getMoveText(), new CpMoveTextHandler(mergeCandidate) {
                int skipVariantLevel = 0;
                Move newMove;
                boolean addComment = false;
                MergeState mergeState = MergeState.Search;

                @Override
                public void onComment(String value) {
                    if(mergeState != MergeState.Merge|| value == null || value.isEmpty()) {
                        return;
                    }
                    if(newMove.comment != null && !newMove.comment.isEmpty()) {
                        if(newMove.comment.contains(value)) {
                            return;
                        }
                        value += "; " + newMove.comment;
                    }
                    newMove.comment = value;
                }

                @Override
                public void onGlyph(String value) {
                    if(mergeState != MergeState.Merge) {
                        return;
                    }
                    newMove.setGlyph(Integer.valueOf(value.substring(1)));
                }

                @Override
                public boolean onMove(String moveText) throws Config.PGNException {
                    super.onMove(moveText);
                    Move currentMove = pgnGraph.getCurrentMove();

                    if(DEBUG_MOVE != null && DEBUG_MOVE.equals(moveText.trim())) {
                        System.out.println(String.format("writer %s", moveText));
                    }
                    if(DEBUG) {
                        System.out.print(String.format("\n%" + (variations.size() + 1) + "s%s %s, level=%s ", "",
                                pgnGraph.getNumberedMove(currentMove), mergeState.toString(), variations.size()));
                    }


                    Move prevMove = pgnGraph.moveLine.get(pgnGraph.moveLine.size() - 2);
                    Pack pack = new Pack(prevMove.packData);

                    switch (mergeState) {
                        case Search:
                            if (mergePack.equals(pack)) {
                                mergeState = MergeState.Merge;
                                if(DEBUG) {
                                    System.out.print(mergeState.toString());
                                }
                                addComment = true;
                                merged[0] = true;
                                // fall through!
                            } else {
                                int numberOfPieces = new Pack(currentMove.packData).getNumberOfPieces();
                                if (numberOfPieces < mergePack.getNumberOfPieces()) {
                                    mergeState = MergeState.Skip;
                                    if(DEBUG) {
                                        System.out.print(mergeState.toString());
                                    }
                                    skipVariantLevel = variations.size();
                                    return skipVariantLevel > 0;
                                }
                                break;
                            }

                        case Merge:
                            Board prevBoard = PgnGraph.this.positions.get(pack);
                            if(prevBoard == null) {
                                String msg = String.format("When merging cannot find position \n%s after %s for %s",
                                        pgnGraph.getBoard(), prevMove.toString(true), currentMove.toString(true));
                                logger.error(msg);
                                mergeState = MergeState.Skip;
                                skipVariantLevel = variations.size();
                                return skipVariantLevel > 0;
                            }
                            newMove = currentMove.clone();
                            if(prevBoard.getMove() != null) {
                                addComment = true;
                            }
                            PgnGraph.this.addMove(newMove, prevBoard);
                            if(DEBUG && testPack.equals(new Pack(newMove.packData))) {
                                logger.debug(newMove.toCommentedString());
                            }

                            int commentLen = 0;
                            if(newMove.comment != null) {
                                commentLen = newMove.comment.length();
                            }
                            if (addComment && mergeData.annotate && commentLen < mergeData.maxAnnotationLen) {
                                if(mergeData.withStatistics) {
                                    modifyStatisticsComment(newMove, item.getHeader(Config.HEADER_Result));
                                } else {
                                    String tag;
                                    if ((newMove.moveFlags & Config.FLAGS_BLACK_MOVE) == 0) {
                                        tag = Config.HEADER_White;
                                    } else {
                                        tag = Config.HEADER_Black;
                                    }
                                    String comment = "";
                                    String sep = "";

                                    String header = item.getHeader(tag);
                                    if (header != null) {
                                        String[] parts = header.split(",\\s*");
                                        comment = parts[0];
                                        if (parts.length == 1) {
                                            if (DEBUG) {
                                                logger.debug("no last-name, first-name");
                                            }
                                            parts = header.split("\\s+");
                                            comment = parts[parts.length - 1];
                                        }
                                    }
                                    if (comment.isEmpty() || comment.equals(Config.HEADER_UNKNOWN_VALUE)) {
                                        comment = "";
                                        sep = "";
                                    } else {
                                        sep = ", ";
                                    }
                                    header = item.getHeader(Config.HEADER_Result);
                                    if (header != null && !header.isEmpty() && !header.equals(Config.HEADER_UNKNOWN_VALUE)) {
                                        comment += sep + header;
                                    }
                                    onComment(comment);
                                }
                            }
                            addComment = false;
                            break;

                        case Skip:
                            break;

                    }
                    return true;
                }

                @Override
                public void onVariantClose() {
                    super.onVariantClose();
                    if(mergeState == MergeState.Skip) {
                        if (skipVariantLevel > variations.size()) {
                            mergeState = MergeState.Search;
                            skipVariantLevel = 0;
                        }
                    }
                }
            }, new PgnItem.ProgressObserver() {
                @Override
                public void setProgress(int progress) {
//                    logger.debug(String.format("\t offset=%s", progress));
                }
            });
        } catch (Exception e) {
            logger.error(e.getMessage());
            parsingError = e.getMessage();
        }
        return merged[0];
    }

    private static class CpMoveTextHandler implements PgnParser.MoveTextHandler {
        protected PgnGraph pgnGraph;
        protected Move newMove;
        protected boolean startVariation;                     // flag for pgn parsing
        protected LinkedList<Pair<Move, Move>> variations = new LinkedList<>(); // stack for pgn parsing

        public CpMoveTextHandler(PgnGraph pgnGraph) throws Config.PGNException {
            this.pgnGraph = pgnGraph;
            newMove = pgnGraph.rootMove;     // to put initial comment
        }

        @Override
        public void onComment(String value) {
            if(newMove.comment != null && !newMove.comment.isEmpty()) {
                value += "; " + newMove.comment;
            }
            newMove.comment = value;
        }

        @Override
        public void onGlyph(String value) {
            newMove.setGlyph(Integer.valueOf(value.substring(1)));
        }

        @Override
        public boolean onMove(String moveText) throws Config.PGNException {
            Move lastMove = null;
            if(startVariation) {
                lastMove = pgnGraph.moveLine.removeLast();
            }

            Board board = pgnGraph.getBoard();
            if(board == null) {
                board = null;
            }
            newMove = new Move(board.getFlags() & Config.FLAGS_BLACK_MOVE);
            Util.parseMove(newMove, moveText);

            if (!pgnGraph.validatePgnMove(newMove)) {
                Board snapshot = board.clone();
                snapshot.incrementPlyNum(1);   // ??
                String msg = String.format("invalid move %s%s for:\n%s", getMoveNum(snapshot), newMove.toString(), board.toString());
                logger.error(msg);
                throw new Config.PGNException(msg);
            }

            if(startVariation) {
                if((board.getMove()) == null) {
                    // should never happen
                    String msg = String.format("invalid variation %s%s for:\n%s", getMoveNum(board), newMove.toString(), board.toString());
                    logger.error(msg);
                }
                Pair<Move, Move> variationPair = new Pair<>(lastMove, newMove);
                variations.addLast(variationPair);
            }
            pgnGraph.addMove(newMove);
            startVariation = false;
            return true;
        }

        @Override
        public void onVariantOpen() {
            startVariation = true;
        }

        @Override
        public void onVariantClose() {
            // restore move line to prior to variation start
            Board board = pgnGraph.getBoard();
            if(board == null) {
                return;
            }
            if(DEBUG) {
                logger.debug(String.format("onVariantClose %s\n%s", newMove.toString(), board.toString()));
            }
            Pair<Move, Move> variationPair = variations.removeLast();
            while(variationPair.second != pgnGraph.moveLine.removeLast());
            pgnGraph.moveLine.addLast(variationPair.first);
        }
    }

    public static class MergeData {
        public boolean annotate;
        public int maxAnnotationLen = 1024;     // for future use, constant so far
        public boolean withStatistics = true;   // for future use, constant so far
        public int start, end, merged;
        public String pgnPath;

        public MergeData(PgnItem target) {
            init(target);
        }

        public MergeData(PgnItem.Item target) {
            init(target.getParent());
        }

        private void init(PgnItem target) {
            start = -1;
            end = -1;
            pgnPath = target.getAbsolutePath();
        }

        public boolean isMergeSetupOk() {
            if (!pgnPath.endsWith(PgnItem.EXT_PGN)) {
                return false;
            }
            return end == -1 || start <= end;
        }

        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            try {
                writer.write(start, 16);
                writer.write(end, 16);
                if(annotate) {
                    writer.write(1, 1);
                } else {
                    writer.write(0, 1);
                }
                writer.writeString(pgnPath);
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        public MergeData(BitStream.Reader reader) throws Config.PGNException {
            try {
                start = reader.read(16);
                end = reader.read(16);
                annotate = reader.read(1) == 1;
                pgnPath = reader.readString();
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }
    }
}
