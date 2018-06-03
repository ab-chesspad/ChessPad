package com.ab.pgn;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.*;

/**
 * replavement for PgnTree as of v0.3
 * Created by Alexander Bootman on 10/29/17.
 */
public class PgnGraph {
    public static boolean DEBUG = false;    // todo: config
    final static PgnLogger logger = PgnLogger.getLogger(PgnGraph.class);

    enum MergeState {
        Search,
        Merge,
        Skip,
    }

    protected Move rootMove = new Move(Config.FLAGS_NULL_MOVE);     // can hold initial comment
    protected PgnItem.Item pgn;                 // headers and moveText
    boolean modified;
    LinkedList<Move> moveLine = new LinkedList<>();
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
            pgn.addHeader(new Pair<String, String>(str, "?"));
        }
    }

    public PgnGraph(PgnItem.Item item, PgnItem.ProgressObserver progressObserver) throws Config.PGNException {
        Board initBoard;
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
        modified = false;
    }

    public String getParsingError() {
        return parsingError;
    }

    public int getParsingErrorNum() {
        return parsingErrorNum;
    }

    public void serialize(BitStream.Writer writer) throws Config.PGNException {
        try {
            // 1. serialize positions
            writer.write(positions.size(), 32);
            for(Map.Entry<Pack, Board> entry : positions.entrySet()) {
                Board board = entry.getValue();
                board.serialize(writer);
                Move move = board.getMove();
                while(move != null) {
                    writer.write(1, 1);
                    move.serialize(writer);
                    move = move.getVariation();
                }
                writer.write(0, 1);
            }

            // 2. serialize line of moves
            writer.write(moveLine.size(), 9);   // max 256 2-ply moves
            for(Move move : moveLine) {
                move.serialize(writer);
            }

            // 3. serialize headers, modified
            pgn.serialize(writer);
            if (modified) {
                writer.write(1, 1);
            } else {
                writer.write(0, 1);
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public PgnGraph(BitStream.Reader reader) throws Config.PGNException {
        try {
            // 1. unserialize positions
            int positionsSize = reader.read(32);
            for( int i = 0; i < positionsSize; ++i) {
                Board board = new Board(reader);
                Pack pack = new Pack(board.pack());
                positions.put(pack, board);
                Move prevMove = null;
                while (reader.read(1) == 1) {
                    Move move = new Move(reader, board);
                    if (prevMove == null) {
                        board.setMove(move);
                    } else {
                        prevMove.variation = move;
                    }
                    prevMove = move;
                }
            }

            // 2. unserialize line of moves
            int moveLineSize = reader.read(9);
            Board board = null;
            for( int i = 0; i < moveLineSize; ++i) {
                Move move = new Move(reader);
                if(board != null) {
                    move.piece = board.getPiece(move.from);
                }
                board = this.getBoard(move);
                moveLine.addLast(move);
            }
            rootMove = moveLine.getFirst();

            // 3. unserialize headers, modified
            pgn = (PgnItem.Item) PgnItem.unserialize(reader);
            if (reader.read(1) == 1) {
                modified = true;
            }
        } catch (IOException e) {
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
        return positions.get(new Pack(moveLine.getFirst().packData));
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
                logger.debug(String.format("comment %s -> %s\n%s", move.toString(), newComment, this.getBoard().toString()));
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

    public void save(boolean updateMoves, PgnItem.ProgressObserver progressObserver) throws Config.PGNException {
        if (pgn != null) {
            if (updateMoves) {
                if (!this.getInitBoard().equals(new Board()) && pgn.getHeader(Config.HEADER_FEN) == null) {
                    String fen = getInitBoard().toFEN();
                    pgn.headers.add(new Pair<String, String>(Config.HEADER_FEN, fen));
                }
                pgn.setMoveText(this.toPgn());
            }
            pgn.save(progressObserver);
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
            logger.debug(String.format("toPrev %s\n%s", getCurrentMove().toString(), this.getBoard().toString()));
        }
    }

    public void toPrevVar() {
        while(moveLine.size() > 1) {
            moveLine.removeLast();
            Board board = getBoard();
            if(board.getMove().getVariation() != null) {
                break;
            }
        }
        if(DEBUG) {
            logger.debug(String.format("toPrev %s\n%s", getCurrentMove().toString(), this.getBoard().toString()));
        }
    }

    public void toEnd() {
        Board board = getBoard();
        if(board == null) {
            // quick and dirty
            board = getInitBoard();
        }
        Board prevBoard = null;
        Move move;
        while((move = board.getMove()) != null) {
            moveLine.addLast(move);
            board = getBoard();
        }
    }

    public void toVariation(Move variation) {
        Board board = getBoard();
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
        return getCurrentMove().glyph;
    }

    public void setGlyph(int glyth) {
        if (!okToSetGlyph()) {
            return;
        }
        if (getCurrentMove().glyph != glyth) {
            getCurrentMove().glyph = glyth;
            modified = true;
        }
    }

    public boolean okToSetGlyph() {
        return !isInit();
    }


    public boolean isEnd() {
        Board board = getBoard();
        return board.getMove() == null;
    }

    public List<Move> getVariations() {
        Board board = getBoard();
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
        return getBoard().getFlags();
    }

    // complete move, needs validation
    public boolean validateUserMove(Move newMove) {
        Board board = getBoard();
        int piece = board.getPiece(newMove.from);
        if(piece != newMove.piece) {
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
                for (test.from.y = 0; test.from.y < Config.BOARD_SIZE; test.from.y++) {
                    for (test.from.x = 0; test.from.x < Config.BOARD_SIZE; test.from.x++) {
                        if (board.getPiece(test.from) == newMove.piece &&
                                !(test.from.x == newMove.from.x && test.from.y == newMove.from.y)) {
                            if (board.validatePgnMove(test, Config.VALIDATE_PGN_MOVE)) {
                                if(test.from.x != newMove.from.x) {
                                    newMove.moveFlags |= Config.FLAGS_X_AMBIG;
                                } else if(test.from.y != newMove.from.y) {
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
                newMove.from = this.getBoard().getWKing();
            } else {
                newMove.from = this.getBoard().getBKing();
            }
            return true;
        }
        if(newMove.getColorlessPiece() == Config.PAWN) {
            int dy, lastY;
            int hisPawn;
            if((newMove.moveFlags & Config.FLAGS_BLACK_MOVE) == 0) {
                dy = 1;
                lastY = Config.BOARD_SIZE - 1;
                hisPawn = Config.BLACK_PAWN;
            } else {
                dy = -1;
                hisPawn = Config.WHITE_PAWN;
                lastY = 0;
            }
            newMove.moveFlags &= ~(Config.FLAGS_ENPASSANT_OK | Config.FLAGS_PROMOTION);
            if(newMove.to.y == lastY) {
                newMove.moveFlags |= Config.FLAGS_PROMOTION;
            }
            if(newMove.from.x == -1) {
                newMove.from.x = newMove.to.x;
            }
            newMove.from.y = newMove.to.y - dy;
            if(this.getBoard().getPiece(newMove.from) == Config.EMPTY) {
                newMove.from.y -= dy;
                if(this.getBoard().getPiece(newMove.to.x - 1, newMove.to.y) == hisPawn ||
                        this.getBoard().getPiece(newMove.to.x + 1, newMove.to.y) == hisPawn ) {
                    newMove.moveFlags |= Config.FLAGS_ENPASSANT_OK;
                }
            }
            if(newMove.from.x != newMove.to.x &&
                    this.getBoard().getPiece(newMove.to) == Config.EMPTY &&
                    this.getBoard().getPiece(newMove.to.x, newMove.from.y) == hisPawn ) {
                newMove.moveFlags |= Config.FLAGS_CAPTURE | Config.FLAGS_ENPASSANT;
            }
        }

        // find newMove.from by validating newMove
        int x0 = 0, x1 = Config.BOARD_SIZE - 1, y0 = 0, y1 = Config.BOARD_SIZE - 1;
        if (newMove.piece == Config.WHITE_PAWN) {
            ++y0;
        } else if (newMove.piece == Config.BLACK_PAWN) {
            --y1;
        }
        if (newMove.from.x >= 0)
            x0 = x1 = newMove.from.x;
        if (newMove.from.y >= 0)
            y0 = y1 = newMove.from.y;

        for (newMove.from.y = y0; newMove.from.y <= y1; newMove.from.y++) {
            for (newMove.from.x = x0; newMove.from.x <= x1; newMove.from.x++) {
                if (this.getBoard().getPiece(newMove.from) == newMove.piece) {
                    if (getBoard().validatePgnMove(newMove, Config.VALIDATE_PGN_MOVE)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static String getMoveNum(Board board) {
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
    public void addMove(Move newMove, Board prevBoard) throws Config.PGNException {
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
                        prev.variation = newMove;
                    }
                    // replace
                    newMove.variation = move.variation;
                    newMove.comment = move.comment;
                    newMove.glyph = move.glyph;
                    break;
                }
                prev = move;
                move = move.variation;
            }
            if(move == null) {
                logger.debug(String.format("move %s%s, replacing:\n%s", getMoveNum(newMove), newMove.toString(), oldBoard.toString()));
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
            logger.debug(String.format("move %s, old in=%s, new in=%s", getNumberedMove(newMove), oldBoard.getInMoves(), newBoard.getInMoves()));
            if(prevBoard == null) {
                moveLine.addLast(newMove);
            }
            modified = true;
            return;
        }

        if((move = board.getMove()) == null) {
            board.setMove(newMove);
        } else {
            if(move.isSameAs(newMove)) {
                board.setMove(newMove);
                newMove.comment = move.comment;
                newMove.glyph = move.glyph;
            } else {
                Move variation;
                while((variation = move.getVariation()) != null) {
                    if(variation.isSameAs(newMove)) {
                        move.setVariation(newMove);
                        newMove.comment = variation.comment;
                        newMove.glyph = variation.glyph;
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

    private String toPgn(Board prevBoard, Move move) {
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
                if (move.glyph > 0) {
                    sb.append(Config.PGN_GLYPH).append(move.glyph).append(" ");
                }
            }
            if(move.comment != null && !move.comment.isEmpty()) {
                sb.append(Config.COMMENT_OPEN).append(move.comment).append(Config.COMMENT_CLOSE).append(" ");
            }

            if(move.variation != null) {
                if(!skipVariant) {
                    TraverseData td = new TraverseData();
                    td.prevBoard = prevBoard;
                    td.move = move.variation;
                    td.pgnText = new String(sb);
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

    public String toPgn() {
        for(Map.Entry<Pack, Board> entry : positions.entrySet()) {
            entry.getValue().setVisited(false);
        }
        String pgn = toPgn(null, rootMove);

        // quick and dirty:
        StringBuilder res = new StringBuilder(pgn.length() + 1000);
        int index = 0;
        while(index < pgn.length()) {
            int e = pgn.indexOf(" ", index + Config.PGN_OUTPUT_LINE_SIZE);
            if(e == -1) {
                e = pgn.length();
            }
            res.append(pgn.substring(index, e)).append("\n");
            index = e;
        }
        return new String(res);
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

    private class TraverseData {
        Board prevBoard;
        Move move;
        String pgnText;
    }

    public boolean isRepetition(Move move) {
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

    public int merge(final PgnItem.Pgn pgn, final int start, final int end, final boolean annotate,
                      PgnItem.ProgressObserver progressObserver) throws Config.PGNException {
        final PgnItem.ProgressNotifier progressNotifier = new PgnItem.ProgressNotifier(progressObserver);
        final int[] merged = {0};
        final Move mergeMove = this.getCurrentMove();
        final int[] offset = {0};
        final int[] index = {0};
        ((PgnItem.Dir)pgn.getParent()).walkThroughGrandChildren(pgn, new PgnItem.EntryHandler() {
            @Override
            public boolean handle(PgnItem entry, BufferedReader br) throws Config.PGNException {
                entry.offset = offset[0];
                index[0] = entry.getIndex();
                int index1 = entry.getIndex() + 1;  // start and end 1-based
                if(index1 >= start) {
                    if(merge(mergeMove, (PgnItem.Item)entry, annotate)) {
                        ++merged[0];
                    }
                }
                return end == -1 || index1 < end;
            }

            @Override
            public boolean getMoveText(PgnItem entry) {
                int index1 = entry.getIndex() + 1;  // start and end 1-based
                return index1 >= start;
            }

            @Override
            public void addOffset(int length, int totalLength) {
                offset[0] += length;
                if(end == -1) {
                    progressNotifier.setOffset(offset[0], pgn.getLength());
                } else {
                    progressNotifier.setOffset(index[0], end - start + 1);
                }
            }
        });
        return merged[0];
    }

    public boolean merge(final Move mergeMove, final PgnItem.Item item, final boolean annotate) throws Config.PGNException {
        final Pack mergePack = new Pack(mergeMove.packData);
        while(!mergeMove.equals(moveLine.getLast())) {
            moveLine.removeLast();
        }
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
                        value += "; " + newMove.comment;
                    }
                    newMove.comment = value;
                }

                @Override
                public void onGlyph(String value) {
                    if(mergeState != MergeState.Merge) {
                        return;
                    }
                    newMove.glyph = Integer.valueOf(value.substring(1));
                }

                @Override
                public boolean onMove(String moveText) throws Config.PGNException {
                    super.onMove(moveText);
                    Move currentMove = pgnGraph.getCurrentMove();

                    if(DEBUG) {
                        System.out.print(String.format("\n%" + (variations.size() + 1) + "s%s %s, level=%s ", "",
                                pgnGraph.getNumberedMove(currentMove), mergeState.toString(), variations.size()));
                    }

                    Move prevMove = pgnGraph.moveLine.get(pgnGraph.moveLine.size() - 2);
                    Pack pack = new Pack(prevMove.packData);

                    switch (mergeState) {
                        case Search:
//                            Pack pack = new Pack(currentMove.packData);
                            if (mergePack.equals(pack)) {
                                mergeState = MergeState.Merge;
                                if(DEBUG) {
                                    System.out.print(mergeState.toString());
                                }
                                addComment = true;
                                merged[0] = true;
                                // fall through!
                            } else {
                                Board board = pgnGraph.getBoard();
                                int numberOfPieces = new Pack(currentMove.packData).getNumberOfPieces();
                                if (numberOfPieces < mergePack.getNumberOfPieces()) {
                                    mergeState = MergeState.Skip;
                                    if(DEBUG) {
                                        System.out.print(mergeState.toString());
                                    }
                                    skipVariantLevel = variations.size();
//                                    return false;   // handle only pgns without variations
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
                            Board currentBoard = PgnGraph.this.getBoard(currentMove);
                            if(currentBoard == null) {
                                PgnGraph.this.addMove(newMove, prevBoard);
                            } else {
                                // find move in current graph?
                                newMove = currentMove.clone();
                            }
                            if (annotate && addComment) {
                                String tag;
                                if ((newMove.moveFlags & Config.FLAGS_BLACK_MOVE) == 0) {
                                    tag = Config.HEADER_White;
                                } else {
                                    tag = Config.HEADER_Black;
                                }
                                String comment = "";
                                String sep = "";

                                String header = item.getHeader(tag);
                                if(header != null) {
                                    String[] parts = header.split(",\\s*");
                                    comment = parts[0];
                                    if (parts.length == 1) {
                                        logger.debug("no last-name, first-name");
                                        parts = header.split("\\s+");
                                        comment = parts[parts.length - 1];
                                    }
                                }
                                if(comment.isEmpty() || comment.equals(Config.HEADER_UNKNOWN_VALUE)) {
                                    comment = "";
                                    sep = "";
                                } else {
                                    sep = ", ";
                                }
                                header = item.getHeader(Config.HEADER_Result);
                                if(header != null && !header.isEmpty() && !header.equals(Config.HEADER_UNKNOWN_VALUE)) {
                                    comment += sep + header;
                                }
                                onComment(comment);
                            }
                            addComment = false;
                            break;

                        case Skip:
                            break;

                    }
                    return true;
                }

//                @Override
//                public void onVariantOpen() {
//                    super.onVariantOpen();
//                }

                @Override
                public void onVariantClose() {
                    super.onVariantClose();
                    if(mergeState == MergeState.Skip) {
                        if (skipVariantLevel > variations.size()) {
                            mergeState = MergeState.Search;
                            skipVariantLevel = 0;
                        }
                    }
//
//                        case Merge:
//                            Pair<Move, Move> variationPair = variations.getLast();
//                            while (variationPair.second != PgnGraph.this.moveLine.removeLast()) ;
//                            PgnGraph.this.moveLine.addLast(variationPair.first);
//                            break;
//                    }
//                    Pair<Move, Move> variationPair = variations.removeLast();
//                    while (variationPair.second != pgnGraph.moveLine.removeLast()) ;
//                    pgnGraph.moveLine.addLast(variationPair.first);
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
            newMove.glyph = Integer.valueOf(value.substring(1));
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
            // resore move line to prior to variation start
//            logger.debug(String.format("onVariantClose %s\n%s", newMove.toString(), pgnGraph.getBoard().toString()));
            Pair<Move, Move> variationPair = variations.removeLast();
            while(variationPair.second != pgnGraph.moveLine.removeLast());
            pgnGraph.moveLine.addLast(variationPair.first);
        }
    }

}
