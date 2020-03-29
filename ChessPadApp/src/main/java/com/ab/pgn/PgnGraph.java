package com.ab.pgn;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;

/**
 * replacement for PgnTree as of v0.3
 * Created by Alexander Bootman on 10/29/17.
 */
public class PgnGraph {
/*
    private static String DEBUG_MOVE = "Kxh8";
/*/
    private static final String DEBUG_MOVE = null;
//*/
    public static final boolean PARSE_MOVES_ANYWAY = true;      // parse moves even if FEN has issues, e. g. in https://github.com/xinyangz/chess-tactics-pgn/
    public static boolean DEBUG = false;    // todo: config
    private static final PgnLogger logger = PgnLogger.getLogger(PgnGraph.class);

    enum MergeState {
        Search,
        Merge,
        Skip,
    }

    Move rootMove = new Move(Config.FLAGS_NULL_MOVE); // can hold initial comment
    CpFile.Item pgn;                                 // tags and moveText
    private boolean modified;
    public final LinkedList<Move> moveLine = new LinkedList<>();
    Map<Pack, Board> positions = new HashMap<>();

    transient private String parsingError;
    transient private int parsingErrorNum;

    public PgnGraph() {
        try {
            init(new Board(), null);
        } catch (Config.PGNException e) {
            logger.error(e.getMessage()); // should never happen
        }
    }

    public PgnGraph(Board initBoard) throws Config.PGNException {
        init(initBoard, null);
    }

    private void init(Board initBoard, CpFile.Item pgn) throws Config.PGNException {
        initBoard.setMove(null);
        if(pgn == null) {
            this.pgn = new CpFile.Item("dummy");
        } else {
            this.pgn = pgn;
        }
        modified = false;
        rootMove.packData = initBoard.pack();
        positions.put(new Pack(rootMove.packData), initBoard);
        moveLine.add(rootMove);
    }

    public PgnGraph(CpFile.Item item, CpFile.ProgressObserver progressObserver) throws Config.PGNException {
        Board initBoard;
        Date start = new Date();
        String fen = item.getFen();
        if (fen == null) {
            initBoard = new Board();
        } else {
            initBoard = new Board(fen);
            parsingErrorNum = initBoard.validateSetup();
        }
        init(initBoard, item);
        if (PARSE_MOVES_ANYWAY || parsingErrorNum == 0) {
            try {
                parseMoves(item.getMoveText(), progressObserver);
            } catch (Exception e) {
                logger.error(e.getMessage());
                parsingError = e.getMessage();
            }
        }
        Date end = new Date();
        printDuration("Pgn loaded", start, end);
        modified = false;
    }

    public void parseMoves(String moves, CpFile.ProgressObserver progressObserver) throws Config.PGNException {
        PgnParser.parseMoves(moves, new CpMoveTextHandler(this), progressObserver);
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

    void serializeGraph(DataOutputStream os, int versionCode) throws Config.PGNException {
        if(!Config.USE_BIT_STREAMS) {
            for (Map.Entry<Pack, Board> entry : positions.entrySet()) {
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

                // 2. serialize tags, modified
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
    }

    private void serializeGraph(DataOutputStream os, Move move) throws Config.PGNException {
        if(!Config.USE_BIT_STREAMS) {
            Board nextBoard = this.getBoard(move.packData);
            int flags = 0;
            Move nextMove = nextBoard.getMove();
            if (nextMove != null) {
                flags |= Move.HAS_NEXT_MOVE;
            }
            Move variation = move.getVariation();
            if (variation != null) {
                flags |= Move.HAS_VARIATION;
            }
            move.serialize(os, flags);
            if (DEBUG) {
                logger.debug(String.format(Locale.getDefault(), "writer %s, %d", move.toCommentedString(), os.size()));
            }
            if (nextBoard.wasVisited()) {
                if (DEBUG) {
                    logger.debug(String.format("writer visited %s, skip", move.toCommentedString()));
                }
                if (variation != null) {
                    serializeGraph(os, variation);
                }
                return;
            }
            nextBoard.setVisited(true);
            if (nextMove != null) {
                serializeGraph(os, nextMove);
            }
            if (variation != null) {
                serializeGraph(os, variation);
            }
        }
    }

    public PgnGraph(DataInputStream is, int versionCode, CpFile.ProgressObserver progressObserver) throws Config.PGNException {
        if(!Config.USE_BIT_STREAMS) {
            unserializeGraph(is, versionCode, progressObserver);
        }
    }

    private void unserializeGraph(DataInputStream is, int versionCode, CpFile.ProgressObserver progressObserver) throws Config.PGNException {
        if(!Config.USE_BIT_STREAMS) {
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

                pgn = (CpFile.Item) CpFile.unserialize(is);
                modified = is.read() == 1;
                Date end = new Date();
                printDuration("Pgn unserialized", start, end);
            } catch (Exception e) {
                throw new Config.PGNException(e);
            }
        }
    }

    private Move unserializeGraph(DataInputStream is, Board previousBoard, CpFile.ProgressObserver progressObserver, int totalLen) throws Config.PGNException {
        if(Config.USE_BIT_STREAMS) {
            return null;
        } else {
            try {
                Move move = new Move(is, previousBoard);
                if (progressObserver != null && totalLen > 0) {
                    int progress = (totalLen - is.available()) * 100 / totalLen;
                    progressObserver.setProgress(progress);
                }
                if (DEBUG & (totalLen - is.available()) == 186) {
                    logger.debug(String.format(Locale.getDefault(), "reader %s, %d\n%s", move.toCommentedString(), (totalLen - is.available()), previousBoard.toString()));
                }
                if (positions.size() == 0) {
                    // set up rootMove
                    rootMove = move;
                    rootMove.packData = previousBoard.pack();
                    positions.put(new Pack(rootMove.packData), previousBoard);
                    moveLine.addLast(rootMove);
                } else {
                    if (!this.addMove(move, previousBoard)) {
                        if (move.hasVariation()) {
                            move.variation = unserializeGraph(is, previousBoard, progressObserver, totalLen);
                        }
                        return move;
                    }
                }
                if (move.hasNextMove()) {
                    Board nextBoard = positions.get(new Pack(move.packData));
                    unserializeGraph(is, nextBoard, progressObserver, totalLen);
                }
                if (move.hasVariation()) {
                    move.variation = unserializeGraph(is, previousBoard, progressObserver, totalLen);
                }
                logger.debug(String.format("reader %s complete", move.toCommentedString()));
                move.cleanupSerializationFlags();
                return move;
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
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
            logger.debug(String.format(Locale.getDefault(), "writer board %d", writer.bitCount));
            serializeGraph(writer, rootMove);

            // 2. serialize tags, modified
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
                    logger.debug(String.format(Locale.getDefault(), "writer E %d", writer.bitCount));
                }
            } else {
                writer.write(1, 1);
                move.serialize(writer, false);
                Board nextBoard = this.getBoard(move.packData);
                if(DEBUG) {
                    logger.debug(String.format(Locale.getDefault(), "writer %s %d\n%s", move.toCommentedString(), writer.bitCount, nextBoard.toString()));
                }
                if(DEBUG_MOVE != null && DEBUG_MOVE.equals(move.toCommentedString())) {
                    System.out.println(String.format("writer %s\n%s", move.toCommentedString(), nextBoard.toString()));
                }
                if (nextBoard.wasVisited()) {
                    if(DEBUG) {
                        logger.debug(String.format(Locale.getDefault(), "writer visited %s %d, skip", move.toCommentedString(), writer.bitCount));
                    }
                    serializeGraph(writer, move.getVariation());
                    return;
                }
                nextBoard.setVisited(true);
                serializeGraph(writer, nextBoard.getMove());
                serializeGraph(writer, move.getVariation());
                if(DEBUG) {
                    logger.debug(String.format(Locale.getDefault(), "writer %s complete %d", move.toCommentedString(), writer.bitCount));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public PgnGraph(BitStream.Reader reader, int versionCode, CpFile.ProgressObserver progressObserver) throws Config.PGNException {
        unserializeGraph(reader, versionCode, progressObserver);
    }

    private void unserializeGraph(final BitStream.Reader reader, int versionCode, CpFile.ProgressObserver progressObserver) throws Config.PGNException {
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
            logger.debug(String.format(Locale.getDefault(), "reader board %d", reader.bitCount));
            unserializeGraph(reader, board, progressObserver, totalLen);

            pgn = (CpFile.Item) CpFile.unserialize(reader);
            modified = reader.read(1) == 1;
            Date end = new Date();
            printDuration("Pgn unserialized", start, end);
        } catch (Exception e) {
            throw new Config.PGNException(e);
        }
    }

    private Move unserializeGraph(BitStream.Reader reader, Board previousBoard, CpFile.ProgressObserver progressObserver, int totalLen) throws Config.PGNException {
        try {
            if (reader.read(1) == 0) {
                if(DEBUG) {
                    logger.debug(String.format(Locale.getDefault(), "reader E %d", reader.bitCount));
                }
                return null;
            }
            Move move = new Move(reader, previousBoard, false);
            if(progressObserver != null && totalLen > 0) {
                int progress = (totalLen - reader.available()) * 100 / totalLen;
                progressObserver.setProgress(progress);
            }
            if(DEBUG) {
                logger.debug(String.format(Locale.getDefault(), "reader %s %d\n%s", move.toCommentedString(), reader.bitCount, previousBoard.toString()));
            }
            if(DEBUG_MOVE != null && DEBUG_MOVE.equals(move.toCommentedString())) {
                System.out.println(String.format(Locale.getDefault(), "reader %s %d", move.toCommentedString(), reader.bitCount));
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
            if(DEBUG) {
                logger.debug(String.format(Locale.getDefault(), "reader %s complete %d", move.toCommentedString(), reader.bitCount));
            }
            return move;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public void serializeMoveLine(BitStream.Writer writer, int versionCode) throws Config.PGNException {
        try {
            writer.write(versionCode, 4);
            writer.write(moveLine.size() - 1, 10);
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

    public void unserializeMoveLine(BitStream.Reader reader, int versionCode) throws Config.PGNException {
        try {
            int oldVersionCode;
            if (versionCode != (oldVersionCode = reader.read(4))) {
                throw new Config.PGNException(String.format("Old serialization %d ignored", oldVersionCode));
            }
            moveLine.clear();
            moveLine.add(rootMove);
            int moveLineSize = reader.read(10);
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
        return pgn.getTitle();
    }

    public void setTags(List<Pair<String, String>> newTags) {
        List<Pair<String, String>> tags = pgn.getTags();
        if(!this.modified) {
            boolean modified = tags.size() != newTags.size();
            if (!modified) {
                int i = -1;
                for (Pair<String, String> tag : tags) {
                    Pair<String, String> newTag = newTags.get(++i);
                    if (!newTag.first.equals(tag.first)
                            || !newTag.second.equals(tag.second)) {
                        modified = true;
                        break;
                    }
                }
            }
            this.modified = modified;
        }
        pgn.setTags(newTags);
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

    private Board getBoard(int[] packData) {
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
        if (newComment == null) {
            newComment = "";
        }
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

    public CpFile.Item getPgn() {
        return pgn;
    }

    public boolean isDeletable() {
        return !pgn.getParent().isRoot();
    }

    // needed for serialization
    public void setPgn(CpFile.Item pgn) {
        this.pgn = pgn;
    }

    private void prepareToPgn() {
        if (!this.getInitBoard().equals(new Board())) {
            String fen = getInitBoard().toFEN();
            pgn.setFen(fen);
        }
        pgn.setMoveText(this.toPgn());
    }

    public void save(boolean updateMoves, CpFile.ProgressObserver progressObserver) throws Config.PGNException {
        if (pgn != null) {
            Date start = new Date();
            if (updateMoves) {
                prepareToPgn();
            }
            pgn.save(progressObserver);
            printDuration("Pgn saved", start, new Date());
            modified = false;
        }
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean onlyIfMoves) {
        prepareToPgn();
        String moveText = pgn.getMoveText();
        if(onlyIfMoves && moveText.isEmpty()) {
            return "";
        }
        return new String(pgn.tagsToString(true, true)) + "\n\n" + moveText + "\n";
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

    public boolean isNullMoveValid() {
        if((getCurrentMove().moveFlags & Config.FLAGS_NULL_MOVE) != 0) {
            return false;   // two null moves are not allowed
        }
/* it looks that in all other situations null move is ok
        Move nextMove = rootMove;
        for(Move move : moveLine) {
            Board board = getBoard(move);
            if (board == null) {
                // how can this happen?
                return false;
            }
            Move m = board.getMove();
            if(m == null) {
                return false;   // last move, null move is not ok
            }
            if(!nextMove.isSameAs(move)) {
                return true;    // we are within a variant, null move is ok
            }
            nextMove = m;
        }
//*/
        return true;    // not last move, null move is ok
    }

    void toEnd() {
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
    public boolean validatePgnMove(Move newMove) {
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
            int dy;
            int hisPawn;
            fromX = newMove.getToX();
            if((newMove.moveFlags & Config.FLAGS_BLACK_MOVE) == 0) {
                dy = 1;
                hisPawn = Config.BLACK_PAWN;
            } else {
                dy = -1;
                hisPawn = Config.WHITE_PAWN;
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
                newMove.moveFlags |= Config.FLAGS_CAPTURE;
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

    // board after requested move!
    private static String getMoveNum(Board board) {
        if(board == null) {
            return "";
        }
        int plyNum = board.getPlyNum();
        return "" + ((plyNum + 1) / 2) + ". "
                + ((plyNum & 1) == 1 ? "" : "... ");
    }

    private String getNumberedMove(Move move) {
        if(moveLine.size() == 1) {
            return "";
        }
        return getMoveNum(getBoard(move.packData)) + move.toString();
    }

    public String getNumberedMove() {
        if(moveLine.size() <= 1) {
            return "";
        }
        return getNumberedMove(getCurrentMove());
    }

    public String getMoveNum(Move move) {
        return getMoveNum(getBoard(move.packData));
    }

    public List<String> getMovesText() {
        List<String> res = new LinkedList<>();
        List<Move> moves = this.moveLine;
        StringBuilder sb = new StringBuilder();
        Board board = this.getInitBoard();
        boolean showMoveNum = true;
        for( Move move : moves.subList(1, moves.size())) {
            if(!showMoveNum) {
                showMoveNum = (board.getFlags() & Config.FLAGS_BLACK_MOVE) == 0;
            }
            if (showMoveNum) {
                sb.append(this.getMoveNum(move));
            }
            sb.append(move.toString());
            if((board.getFlags() & Config.FLAGS_BLACK_MOVE) != 0) {
                res.add(new String(sb).trim());
                sb.delete(0, sb.length());
            }
            showMoveNum = false;
            board = this.getBoard(move.packData);
        }
        if(sb.length() > 0) {
            res.add(new String(sb));
        }
        return res;
    }


    public void addMove(Move newMove) throws Config.PGNException {
        addMove(newMove, null);
    }

    // todo: simplify!
    // return true if the new position is added to positions
    private boolean addMove(Move newMove, Board prevBoard) throws Config.PGNException {
        Board board;
        if(prevBoard == null) {
            board = getBoard();
        } else {
            board = prevBoard;
        }
        Board newBoard = board.clone();
        if(DEBUG_MOVE != null && DEBUG_MOVE.equals(newMove.toString().trim())) {
            System.out.println(String.format("addMove %s", newMove.toCommentedString()));
        }
        newBoard.doMove(newMove);
        newMove.packData = newBoard.pack();
        if(isRepetition(newMove)) {
            newMove.moveFlags |= Config.FLAGS_REPETITION;
        }
        Move move;
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

    public void addUserMove(Move move) throws Config.PGNException {
        addMove(move);
        Board board = getBoard();
        Board tmp = board.clone();
        tmp.invertFlags(Config.FLAGS_BLACK_MOVE);
        Square target;
        if((tmp.getFlags() & Config.FLAGS_BLACK_MOVE) == 0) {
            target = tmp.getBKing();
        } else {
            target = tmp.getWKing();
        }
        Move checkMove;
        if((checkMove = tmp.findAttack(target, null)) == null) {
            if(board.validateStalemate()) {
                move.moveFlags |= Config.FLAGS_STALEMATE;
                if(DEBUG) {
                    logger.debug(String.format("stalemate after %s\n%s", move.toString(), board.toString()));
                }
            }
        } else {
            checkMove.moveFlags &= ~Config.FLAGS_BLACK_MOVE;
            checkMove.moveFlags |= tmp.getFlags() & Config.FLAGS_BLACK_MOVE;
            if(tmp.validateCheckmate(checkMove)) {
                move.moveFlags |= Config.FLAGS_CHECKMATE;
            } else {
                move.moveFlags |= Config.FLAGS_CHECK;
            }
        }
    }

    // delete last in moveLine, it can be a variation
    public void delCurrentMove() {
        if (moveLine.size() <= 1) {
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
                if (m == null) {
                    return;     // exception? how can this happen?
                }
            }
            pm.variation = m.variation;
        }

        move2Del.setVariation(null);    // do not delete its variations
        delPositionsAfter(move2Del);
        modified = true;
    }

    private void delPositionsAfter(Move move) {
        if (move == null) {
            return;
        }
        Board board = getBoard(move);
        if(DEBUG) {
            logger.debug(String.format("delPositionsAfter %s, %s\n%s", board.getInMoves(), move.toString(), board.toString()));
        }
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
            if(DEBUG) {
                logger.debug(String.format("delete %s\n%s", move.toString(), board.toString()));
            }
            positions.remove(new Pack(move.packData));
            delPositionsAfter(move.getVariation());
            move = board.getMove();
        }
    }

    public boolean hasMoves() {
        return positions.size() > 1;
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
        String resStr = res + " *";
        if(DEBUG) {
            Date end = new Date();
            printDuration("toPgn", start, end);
        }
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
            if(board == null) {
                logger.error(String.format("%s\n%s -> null board", prevBoard.toString(), move.toString()));
                break;
            }
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

    static String withIndent(String str, int offset) {
        if( offset == 0) {
            return str;
        }
        String sOffset = String.format("%1$" + offset + "s", "");
        return String.format("%s%s", sOffset, str);
    }

    private static class TraverseData {
        Board prevBoard;
        Move move;
        String pgnText;

        TraverseData() {
        }
    }

    private boolean isRepetition(Move move) {
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

    public void merge(final MergeData mergeData, CpFile.ProgressObserver progressObserver) throws Config.PGNException {
        final CpFile.ProgressNotifier progressNotifier = new CpFile.ProgressNotifier(progressObserver);
        final Move mergeMove = this.getCurrentMove();
        final int[] offset = {0};
        final int[] index = {0};
        mergeData.merged = 0;
        final CpFile.Pgn pgn = new CpFile.Pgn(mergeData.pgnPath);
        ((CpFile.Dir)pgn.getParent()).walkThroughGrandChildren(pgn, new CpFile.EntryHandler() {
            @Override
            public boolean handle(CpFile entry, BufferedReader br) {
                entry.offset = offset[0];
                index[0] = entry.getIndex();
                int index1 = entry.getIndex() + 1;  // start and end 1-based
                if(index1 >= mergeData.start) {
                    if(merge(mergeMove, (CpFile.Item)entry, mergeData)) {
                        ++mergeData.merged;
                    }
                }
                return mergeData.end == -1 || index1 < mergeData.end;
            }

            @Override
            public boolean getMoveText(CpFile entry) {
                int index1 = entry.getIndex() + 1;  // start and end 1-based
                return index1 >= mergeData.start;
            }

            @Override
            public boolean addOffset(int length, int totalLength) {
                offset[0] += length;
                boolean done;
                if(mergeData.end == -1) {
                    done = progressNotifier.setOffset(offset[0], pgn.getLength());
                } else {
                    done = progressNotifier.setOffset(index[0], mergeData.end - mergeData.start + 1);
                }
                return done;
            }

            @Override
            public boolean skip(CpFile entry) {
                int index1 = entry.getIndex() + 1;  // start and end 1-based
                return index1 < mergeData.start;
            }
        });
    }

    private final int[] testPackData = new int[] {0x1820E7AF, 0xEDEB1404, 0x6F040783, 0xE3789B0F, 0xE7EFF74D, 0xBC489F9F};
    private final Pack testPack = new Pack(testPackData);
//    private Board getTestBoard() {
//        return this.getBoard(testPackData);
//    }

    static void modifyStatisticsComment(Move move, String result) {
        if (result == null) {
            return;
        }

        int[] counts = new int[3];
        switch (result) {
            case "1-0":
                ++counts[0];
                break;
            case "0-1":
                ++counts[1];
                break;
            case "1/2-1/2":
                ++counts[2];
                break;
            default:
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
                int count;
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

    // merge by position, even if plyNum is different
    private boolean merge(Move mergeMove, final CpFile.Item item, final MergeData mergeData) {
        final Pack mergePack = new Pack(mergeMove.packData);
        Board mergeBoard = PgnGraph.this.positions.get(mergePack);
        final int mergedPlyNum = mergeBoard.getPlyNum();
        final boolean[] merged = {false};
        final MergeState initialMergeState = mergeData.onNewItem(item);
        try {
            final PgnGraph mergeCandidate = new PgnGraph();
            PgnParser.parseMoves(item.getMoveText(), new CpMoveTextHandler(mergeCandidate) {
                int skipVariantLevel = 0;
                Move newMove;
                boolean addComment = false;
                MergeState mergeState = initialMergeState;

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

                    Pack pack;
                    switch (mergeState) {
                        case Search:
                            pack = new Pack(currentMove.packData);
                            if (mergePack.equalPosition(pack)) {
                                mergeState = MergeState.Merge;
                                if(DEBUG) {
                                    System.out.print(mergeState.toString());
                                }
                                addComment = true;
                                merged[0] = true;
                                Board candidateBoard = pgnGraph.getBoard(currentMove);
                                candidateBoard.setPlyNum(mergedPlyNum);     // equalize mergeBoard.plyNum and candidateBoard.plyNum
                                currentMove.packData = candidateBoard.pack();
                                pgnGraph.positions.put(new Pack(currentMove.packData), candidateBoard);   // store copy
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
                            }
                            break;

                        case Merge:
                            Move prevMove = pgnGraph.moveLine.get(pgnGraph.moveLine.size() - 2);
                            pack = new Pack(prevMove.packData);
                            Board prevBoard = PgnGraph.this.positions.get(pack);
                            if(mergeData.maxPlys > 0 && prevBoard.getPlyNum() >= mergeData.maxPlys) {
                                return false;   // abort
                            }
                            if(prevBoard == null) {
                                String msg = String.format("When merging cannot find position \n%s after %s for %s",
                                        pgnGraph.getBoard(), prevMove.toString(true), currentMove.toString(true));
                                logger.error(msg);
                                mergeState = MergeState.Skip;
                                skipVariantLevel = variations.size();
                                return skipVariantLevel > 0;
                            }
                            newMove = currentMove.clone();
                            String commonComment = mergeData.getCommonComment();
                            if(commonComment != null) {
                                newMove.comment = commonComment;
                            } else {
                                if (prevBoard.getMove() != null) {
                                    addComment = true;
                                }
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
                                    modifyStatisticsComment(newMove, item.getTag(Config.TAG_Result));
                                } else {
                                    String tag;
                                    if ((newMove.moveFlags & Config.FLAGS_BLACK_MOVE) == 0) {
                                        tag = Config.TAG_White;
                                    } else {
                                        tag = Config.TAG_Black;
                                    }
                                    String comment = "";
                                    String sep;

                                    String tag1 = item.getTag(tag);
                                    if (tag1 != null) {
                                        String[] parts = tag1.split(",\\s*");
                                        comment = parts[0];
                                        if (parts.length == 1) {
                                            if (DEBUG) {
                                                logger.debug("no last-name, first-name");
                                            }
                                            parts = tag.split("\\s+");
                                            comment = parts[parts.length - 1];
                                        }
                                    }
                                    if (comment.isEmpty() || comment.equals(Config.TAG_UNKNOWN_VALUE)) {
                                        comment = "";
                                        sep = "";
                                    } else {
                                        sep = ", ";
                                    }
                                    tag = item.getTag(Config.TAG_Result);
                                    if (tag != null && !tag.isEmpty() && !tag.equals(Config.TAG_UNKNOWN_VALUE)) {
                                        comment += sep + tag;
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
            }, (progress) -> false);
        } catch (Exception e) {
            logger.error(e.getMessage());
            parsingError = e.getMessage();
        }
        return merged[0];
    }

    private static class CpMoveTextHandler implements PgnParser.MoveTextHandler {
        final PgnGraph pgnGraph;
        Move newMove;
        boolean startVariation;                     // flag for pgn parsing
        final LinkedList<Pair<Move, Move>> variations = new LinkedList<>(); // stack for pgn parsing

        CpMoveTextHandler(PgnGraph pgnGraph) {
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
            while(variationPair.second != pgnGraph.moveLine.removeLast()) {}
            pgnGraph.moveLine.addLast(variationPair.first);
        }
    }

    public static class MergeData {
        final int maxAnnotationLen = 1024;      // for future use, constant so far
        final boolean withStatistics = true;    // for future use, constant so far
        public boolean annotate;
        public int start, end, merged;
        public String pgnPath;
        public int maxPlys;

        public MergeData() {
            init(null);
        }

        public MergeData(CpFile target) {
            init(target);
        }

        public MergeData(CpFile.Item target) {
            init(target.getParent());
        }

        private void init(CpFile target) {
            start = -1;
            end = -1;
            maxPlys = -1;
            if(target != null) {
                pgnPath = target.getAbsolutePath();
            }
        }

        // return initial MergeState
        public MergeState onNewItem(CpFile.Item item) {
            return MergeState.Search;
        }

        public String getCommonComment() {
            return null;
        }

        public boolean isMergeSetupOk() {
            if(pgnPath == null) {
                return false;
            }
            if (!CpFile.isPgnOk(pgnPath)) {
                return false;
            }
            return end == -1 || start <= end;
        }

        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            try {
                writer.write(start, 16);
                writer.write(end, 16);
                writer.write(maxPlys, 16);
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
                if(start == 0x0ffff) {
                    start = -1;
                }
                end = reader.read(16);
                if(end == 0x0ffff) {
                    end = -1;
                }
                maxPlys = reader.read(16);
                if(maxPlys == 0x0ffff) {
                    maxPlys = -1;
                }
                annotate = reader.read(1) == 1;
                pgnPath = reader.readString();
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }
    }
}
