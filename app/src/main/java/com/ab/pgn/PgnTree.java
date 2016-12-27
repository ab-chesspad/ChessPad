package com.ab.pgn;

//import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * tree of game moves
 * Created by Alexander Bootman on 8/6/16.
 */
public class PgnTree {
    transient protected final String DEBUG_TAG = this.getClass().getName();

    protected Move root = new Move(Config.FLAGS_NULL_MOVE);    // fake move to store init board and comments
    protected Move currentMove = root;
    protected PgnItem.Item pgn;                   // headers and moveText
    private boolean modified;       // todo!

    transient private boolean startVariation;                     // flag for pgn parsing
    transient private List<Move> variations = new LinkedList<>(); // stack for pgn parsing

//    transient final Logger logger = Logger.getLogger(this.getClass());

    public PgnTree() {
        init();
    }

    public PgnTree(Board initBoard) throws IOException {
        init();
        root.snapshot = initBoard.clone();
        root.pack = new Pack(root.snapshot);
    }

    private void init() {
        pgn = new PgnItem.Item("dummy");
        setSTR();
        modified = false;
    }

    private void setSTR() {
        for(String str : Config.STR) {
            pgn.addHeader(new Pair<String, String>(str, "?"));
        }
    }

    public PgnTree(PgnItem.Item item) throws IOException {
        this.pgn = item;
        String fen = pgn.getHeader(Config.HEADER_FEN);
        if (fen == null) {
            root.snapshot = new Board();
        } else {
            root.snapshot = new Board(fen);
        }
        root.pack = new Pack(root.snapshot);

        new MoveParser(this).parse(item.getMoveText());
        setSTR();
        modified = false;
    }

    public void serialize(BitStream.Writer writer) throws IOException {
        currentMove.moveFlags |= Config.FLAGS_CURRENT_MOVE;     // set
        // 1. serialize root.snapshot
        root.snapshot.serialize(writer);

        // 2. serialize tree of moves
        writer.writeString(root.comment);
        serializeTree(root.nextMove, writer);

        // 3. serialize headers, modified, reversed, animation
        pgn.serialize(writer);
        if (modified) {
            writer.write(1, 1);
        } else {
            writer.write(0, 1);
        }
        currentMove.moveFlags &= ~Config.FLAGS_CURRENT_MOVE;    // clear
    }

    private void serializeTree(Move move, BitStream.Writer writer) throws IOException {
        if (move == null) {
            writer.write(0, 1);
        } else {
            writer.write(1, 1);
            move.serialize(writer);
            serializeTree(move.nextMove, writer);
            serializeTree(move.variation, writer);
        }
    }

    public PgnTree(BitStream.Reader reader) throws IOException {
        root.snapshot = new Board(reader);
        root.pack = new Pack(root.snapshot);

        root.comment = reader.readString();
        root.nextMove = unserializeTree(reader, root);

        pgn = (PgnItem.Item) PgnItem.unserialize(reader);
        if (reader.read(1) == 1) {
            modified = true;
        }
    }

    private Move unserializeTree(BitStream.Reader reader, Move prevMove) throws IOException {
        if (reader.read(1) == 0) {
            return null;
        }
        Move move = new Move(reader, prevMove.snapshot);
        move.prevMove = prevMove;
        if ((move.moveFlags & Config.FLAGS_CURRENT_MOVE) != 0) {
            currentMove = move;
            currentMove.moveFlags &= ~Config.FLAGS_CURRENT_MOVE;    // clear
        }
        move.nextMove = unserializeTree(reader, move);
        move.variation = unserializeTree(reader, prevMove);
        return move;
    }

    public boolean isModified() {
        return modified;
    }

    public void setModified(boolean modified) {
        this.modified = modified;
    }

    public PgnItem.Item getPgn() {
        return pgn;
    }

    public void save() throws IOException {
        if (pgn != null) {
            if(!this.getInitBoard().equals(new Board()) && pgn.getHeader(Config.HEADER_FEN) == null) {
                String fen = getInitBoard().toFEN();
                pgn.headers.add(new Pair<String, String>(Config.HEADER_FEN, fen));
            }
            pgn.setMoveText(this.toPgn());
            pgn.save();
            modified = false;
        }
    }

    public String getTitle() {
        return PgnItem.getTitle(pgn.getHeaders());
    }

    public Board getInitBoard() {
        return root.snapshot;
    }

    public Board getBoard() {
        if (currentMove == null) {
            return getInitBoard();
        }
        return currentMove.snapshot;
    }

    public int getFlags() {
        if (currentMove == null) {
            return root.snapshot.flags;
        }
        return currentMove.snapshot.flags;
    }

    public String getComment() {
        if (currentMove.comment == null) {
            return "";
        }
        return "" + currentMove.comment;
    }

    public void setComment(String newComment) {
//        logger.debug(newComment);
        Move move = currentMove;
        if (currentMove == null) {
            move = root;
        }
        String oldComment = move.comment;
        if (oldComment == null) {
            oldComment = "";
        }
        if (!newComment.equals(oldComment)) {
            move.comment = newComment;
            if (move.comment.isEmpty()) {
                move.comment = null;
            }
            modified = true;
        }
    }

    public int getGlyph() {
        return currentMove.glyph;
    }

    public void setGlyph(int glyth) {
        if (currentMove == root) {
            return;
        }
        if (currentMove.glyph != glyth) {
            currentMove.glyph = glyth;
            modified = true;
        }
    }

    public boolean okToSetGlyph() {
        return currentMove != root;
    }

    public void setHeaders(List<Pair<String, String>> headers) {
        String fen = pgn.getHeader(Config.HEADER_FEN);
        if(fen != null) {
            headers.add(new Pair<>(Config.HEADER_FEN, fen));
        }
        pgn.setHeaders(headers);
        modified = true;
    }

    // return variant path so we can later restore position
    public List<Move> getPath() {
        List<Move> path = new LinkedList<>();
        Move _currentMove = currentMove;
        while(_currentMove != root) {
            Move main = _currentMove.prevMove.nextMove;
            if(main != _currentMove || main.variation != null) {
                path.add(0, _currentMove);
            }
            _currentMove = _currentMove.prevMove;
        }
        return path;
    }

    // incomplete move, find move.from
    public boolean addPgnMove(Move newMove) throws IOException {
        newMove.moveFlags |= getFlags();
        if (newMove.isNullMove()) {
            addMove(newMove);       // no validation
            return true;
        }
        if(newMove.getColorlessPiece() == Config.KING) {
            if((newMove.moveFlags & Config.FLAGS_BLACK_MOVE) == 0) {
                newMove.from = this.getBoard().wKing.clone();
            } else {
                newMove.from = this.getBoard().bKing.clone();
            }
            addMove(newMove);       // no validation
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
                newMove.moveFlags |= Config.FLAGS_ENPASSANT;
            }
            addMove(newMove);       // no validation
            return true;
        }

        // find newMove.from by validating newMove, then add it
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
                        addMove(newMove);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    protected void addMove(Move move) throws IOException {
        Board board;
        if (move.snapshot == null) {
            board = getBoard().clone();
            board.doMove(move);
            move.snapshot = board;
        } else {
            board = move.snapshot;
        }

        move.pack = new Pack(move.snapshot);
        move.prevMove = currentMove;
        board.flags &= ~Config.FLAGS_REPETITION;
        if(move.getColorlessPiece() != Config.PAWN && move.pieceTaken == Config.EMPTY && isRepetition(move)) {
            board.flags |= Config.FLAGS_REPETITION;
        }

//        logger.debug(this.getBoard().toString());
        if(startVariation) {
            Move lastVariation = currentMove.nextMove;
            variations.add(lastVariation);
            // usually there are very few variations
            while(lastVariation.variation != null) {
                lastVariation = lastVariation.variation;
            }
            lastVariation.variation = move;
        } else {
            currentMove.nextMove = move;
        }
        currentMove = move;
        startVariation = false;
        modified = true;
    }

    // complete move, needs validation
    public boolean validateUserMove(Move newMove) {
        newMove.moveFlags = getFlags();
        int piece = newMove.getColorlessPiece();
        if(piece == Config.KING) {
            if(!getBoard().validateKingMove(newMove)) {
                return false;
            }
            return getBoard().validateOwnKingCheck(newMove);
        }

        if (getBoard().validatePgnMove(newMove, Config.VALIDATE_USER_MOVE)) {
            if(piece != Config.PAWN) {
                // check ambiguity to set moveFlags
                Move test = newMove.clone();
                for (test.from.y = 0; test.from.y < Config.BOARD_SIZE; test.from.y++) {
                    for (test.from.x = 0; test.from.x < Config.BOARD_SIZE; test.from.x++) {
                        Board board = this.getBoard();
                        if (board.getPiece(test.from) == newMove.piece &&
                                !(test.from.x == newMove.from.x && test.from.y == newMove.from.y)) {
                            if (board.validatePgnMove(test, Config.VALIDATE_PGN_MOVE)) {
                                if(!board.validateOwnKingCheck(test)) {
                                    return false;
                                }
                                if(test.from.x != newMove.from.x) {
                                    newMove.moveFlags |= Config.FLAGS_X_AMBIG;
                                }
                                if(test.from.y != newMove.from.y) {
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

    public void addUserMove(Move move) throws IOException {
        // check nextMove != null and create variation if different
        Move sibling = currentMove.nextMove;
        if(sibling == null) {
            addMove(move);
        } else {
            boolean add = false;
            while(!sibling.equals(move)) {
                if(sibling.variation == null) {
                    add = true;
                    break;
                }
                sibling = sibling.variation;
            }
            if(add) {
                startVariation = true;
                addMove(move);
            } else {
                currentMove = sibling;             // user made move that already exists
            }
        }
        Board board = move.snapshot;
        board.flags ^= Config.FLAGS_BLACK_MOVE;
        Square target;
        if ((move.piece & Config.BLACK) == 0) {
            target = board.bKing;
        } else {
            target = board.wKing;
        }
        Move checkMove;
        if((checkMove = board.findAttack(target, null)) != null) {
            if(board.validateCheckmate(checkMove)) {
                move.moveFlags |= Config.FLAGS_CHECKMATE;
            } else {
                move.moveFlags |= Config.FLAGS_CHECK;
            }
        }
        board.flags ^= Config.FLAGS_BLACK_MOVE;
    }

    public void openVariation(boolean takeBack) {
        takeBack();
        startVariation = true;
    }

    public void closeVariation() {
        currentMove = variations.remove(variations.size() - 1);
    }

    public boolean isRepetition(Move move) {
        int count = 0;
        Pack searchedPack = move.pack;
        boolean stop = false;
        while(move != null && !stop) {
            if(move.isNullMove()) {
                stop = true;
            }
            if(searchedPack.equals(move.pack)) {
                if(++count == 3) {
                    return true;
                }
            }
            if(move.snapshot.reversiblePlyNum == 0) {
                break;
            }
            move = move.prevMove;
        }
        return false;
    }

    public void takeBack() {
        if (currentMove == root) {
            return; // error?
        }
        currentMove = currentMove.prevMove;
    }

    public void toInit() {
        currentMove = root;
    }

    public boolean isInit() {
        return currentMove == root;
    }

    public void toEnd() {
        while(currentMove.nextMove != null) {
            currentMove = currentMove.nextMove;
        }
    }

    public boolean isEnd() {
        return currentMove.nextMove == null;
    }

    public void toNext() {
        if(currentMove.nextMove != null) {
            currentMove = currentMove.nextMove;
        }
    }
    public List<Move> getVariations() {
        if(currentMove.nextMove == null) {
            return null;
        }
        Move variation = currentMove.nextMove;
        if(variation.variation == null) {
            return null;
        }
        List<Move> res = new LinkedList<>();
        do {
            res.add(variation);
            variation = variation.variation;
        } while(variation != null);
        return res;
    }

    public void toVariation(Move selectedVariation) {
        if(currentMove.nextMove == null) {
            return;
        }
        Move variation = currentMove.nextMove;
        do {
            if(variation == selectedVariation) {
                currentMove = variation;
                break;
            }
            variation = variation.variation;
        } while(variation != null);
    }

    public void toPrev() {
        if(currentMove.prevMove != null) {
            currentMove = currentMove.prevMove;
        }
    }

    public void toPrevVar() {
        do {
            currentMove = currentMove.prevMove;
            if(getVariations() != null) {
                break;
            }
        } while(currentMove.prevMove != null);
    }

    public String getCurrentMove() {
        if(currentMove == root) {
            return "";
        }
        return currentMove.toNumString();
    }

    public Square getCurrentToSquare() {
        if(currentMove == root) {
            return new Square();
        }
        return currentMove.to;

    }

    public void delCurrentMove() {
        Move prevMove = currentMove.prevMove;
        if(prevMove.nextMove == currentMove) {
            // just remove currentMove
            prevMove.nextMove = currentMove.variation;
        } else {
            // remove variation
            Move variation = prevMove.nextMove;
            while(variation.variation != currentMove) {
                variation = variation.variation;
            }
            variation.variation = currentMove.variation;
        }
        currentMove = prevMove;
    }

    public String toPgn() {
        StringBuilder sb = new StringBuilder();
        if(root.comment != null) {
            commentToPgn(root, sb);
        }
        subtreeToPgn(root.nextMove, true, sb);
        sb.append("\n\n");
        return new String(sb);
    }

    private void commentToPgn(Move move, StringBuilder sb) {
        if(move.comment != null && !move.comment.isEmpty()) {
            sb.append(Config.COMMENT_OPEN).append(move.comment).append(Config.COMMENT_CLOSE).append(" ");
        }
    }

    private void moveNumToPgn(Move move, StringBuilder sb) {
        sb.append((move.snapshot.plyNum + 1) / 2).append(". ");
        if((move.moveFlags & Config.BLACK) != 0) {
            sb.append("... ");
        }
    }

    private Move subtreeToPgn(Move move, boolean mainLine, StringBuilder sb) {
        boolean insertMoveNum = true;
        Move top;
        if(mainLine) {
            top = null;
        } else {
            top = move;
        }

        while(move != null) {
            if(insertMoveNum) {
                moveNumToPgn(move, sb);
            }
            sb.append(move.toString(false));
            if(move.glyph > 0) {
                sb.append(Config.PGN_GLYPH).append(move.glyph).append(" ");
            }
            commentToPgn(move, sb);
            if(move.variation != null) {
                Move variation = move.variation;
                while(variation != null && top != move) {
                    sb.append(Config.VARIANT_OPEN);
                    variation = subtreeToPgn(variation, false, sb);
                    sb.append(Config.VARIANT_CLOSE);
                    insertMoveNum = true;
                }
            } else {
                insertMoveNum = (move.snapshot.plyNum % 2) == 0;
            }
            move = move.nextMove;
        }
        if(top == null) {
            return null;
        }
        return top.variation;
    }
}
