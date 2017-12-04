package com.ab.pgn;

import java.io.IOException;
import java.util.StringTokenizer;

/**
 * Chess _board with pieces, validation
 * PgnGraph node
 * Created by Alexander Bootman on 8/6/16.
 */
public class Board {
    public static boolean DEBUG = true;
    final static PgnLogger logger = PgnLogger.getLogger(Board.class);
    final static int
            COORD_MASK = 0x0007,
            COORD_LENGTH = 3,
            FLAGS_OFFSET = 0,
            FLAGS_MASK = 0x007f,
            FLAGS_LENGTH = 7,
            ENPASS_OFFSET = FLAGS_OFFSET + FLAGS_LENGTH,            //  7
            WK_X_OFFSET = ENPASS_OFFSET + COORD_LENGTH,             // 10
            WK_Y_OFFSET = WK_X_OFFSET + COORD_LENGTH,               // 13
            BK_X_OFFSET = WK_Y_OFFSET + COORD_LENGTH,               // 16
            BK_Y_OFFSET = BK_X_OFFSET + COORD_LENGTH,               // 19

            BOARD_DATA_PACK_LENGTH = BK_Y_OFFSET + COORD_LENGTH,    // 22

            REVERSIBLE_PLY_NUM_OFFSET = BK_Y_OFFSET + COORD_LENGTH,
            REVERSIBLE_PLY_NUM_LENGTH = 7,
            REVERSIBLE_PLY_NUM_MASK = 0x007f,

            dummy_int = 0;

    static final int[][] init = {
            {Config.WHITE_ROOK, Config.WHITE_KNIGHT, Config.WHITE_BISHOP, Config.WHITE_QUEEN, Config.WHITE_KING, Config.WHITE_BISHOP, Config.WHITE_KNIGHT, Config.WHITE_ROOK},
            {Config.WHITE_PAWN, Config.WHITE_PAWN, Config.WHITE_PAWN, Config.WHITE_PAWN, Config.WHITE_PAWN, Config.WHITE_PAWN, Config.WHITE_PAWN, Config.WHITE_PAWN},
            {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
            {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
            {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
            {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
            {Config.BLACK_PAWN, Config.BLACK_PAWN, Config.BLACK_PAWN, Config.BLACK_PAWN, Config.BLACK_PAWN, Config.BLACK_PAWN, Config.BLACK_PAWN, Config.BLACK_PAWN},
            {Config.BLACK_ROOK, Config.BLACK_KNIGHT, Config.BLACK_BISHOP, Config.BLACK_QUEEN, Config.BLACK_KING, Config.BLACK_BISHOP, Config.BLACK_KNIGHT, Config.BLACK_ROOK},
    };

    static final int[][] empty = {
            {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
            {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
            {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
            {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
            {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
            {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
            {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
            {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
    };

    private int[] board = new int[Config.BOARD_SIZE];

    protected int plyNum;
    protected int boardData;        // enpassant x-coord (3), 8-bit flags (8), kings (12)? == 23

    private Move move;              // moves made in this position

    public Board() {
        toInit();
    }

    public Board(int[][] pieces) {
        copyBoard(pieces);
    }

    public void serialize(BitStream.Writer writer) throws Config.PGNException {
        try {
            Pack.pack(this, 0, writer);
            writer.write(this.plyNum, 9);
            writer.write(this.getReversiblePlyNum(), 9);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public Board(BitStream.Reader reader) throws Config.PGNException {
        try {
            Board tmp = Pack.unpack(reader);
            tmp.validate(null);
            tmp.plyNum = reader.read(9);
            tmp.setReversiblePlyNum(reader.read(9));
            copy(tmp, this);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public void setFlags(int value) {
        boardData = Util.setValue(boardData, value, FLAGS_MASK, FLAGS_OFFSET);
    }

    public void raiseFlags(int value) {
        boardData = Util.setBits(boardData, value, FLAGS_MASK, FLAGS_OFFSET);
    }

    public void clearFlags(int value) {
        boardData = Util.clearBits(boardData, value, FLAGS_MASK, FLAGS_OFFSET);
    }

    public void invertFlags(int value) {
        boardData = Util.invertBits(boardData, value, FLAGS_MASK, FLAGS_OFFSET);
    }

    public int getFlags() {
        return Util.getValue(boardData, FLAGS_MASK, FLAGS_OFFSET);
    }

    public void setEnpassantX(int enpass) {
        boardData = Util.setValue(boardData, enpass, COORD_MASK, ENPASS_OFFSET);
    }

    public void setEnpassant(Square enpass) {
        setEnpassantX(enpass.getX());
    }

    public int getEnpassantX() {
        if((this.getFlags() & Config.FLAGS_ENPASSANT_OK) == 0) {
            return -1;
        }
        return Util.getValue(boardData, COORD_MASK, ENPASS_OFFSET);
    }

    private static int calcEnpass(String square) {
        return (int) (square.charAt(0) - 'a');
    }

    public Square getEnpassant() {
        Square square = new Square();
        int flags = this.getFlags();
        if((flags & Config.FLAGS_ENPASSANT_OK) == 0) {
            return square;        // invalid square
        }
        int enpass = getEnpassantX();
        int pawn, otherPawn;
        int y, y1;
        if((flags & Config.FLAGS_BLACK_MOVE) == 0) {
            y = 4;
            y1 = 6;
            square.y = 5;
            pawn = Config.WHITE_PAWN;
            otherPawn = Config.BLACK_PAWN;
        } else {
            y = 3;
            y1 = 1;
            square.y = 2;
            pawn = Config.BLACK_PAWN;
            otherPawn = Config.WHITE_PAWN;
        }

        if (pawn == this.getPiece(enpass - 1, y) || pawn == this.getPiece(enpass + 1, y)) {
            if(otherPawn == this.getPiece(enpass, y)
                    && Config.EMPTY == this.getPiece(enpass, square.y)
                    && Config.EMPTY == this.getPiece(enpass, y1)) {
                square.setX(enpass);
                return square;
            }
        }
        return square;
    }

    public void setWKingX(int x) {
        boardData = Util.setValue(boardData, x, COORD_MASK, WK_X_OFFSET);
    }

    public void setWKingY(int y) {
        boardData = Util.setValue(boardData, y, COORD_MASK, WK_Y_OFFSET);
    }

    public void setWKing(int x, int y) {
        setWKingX(x);
        setWKingY(y);
    }

    public void setWKing(Square wKing) {
        setWKingX(wKing.getX());
        setWKingY(wKing.getY());
    }

    public int getWKingX() {
        return Util.getValue(boardData, COORD_MASK, WK_X_OFFSET);
    }

    public int getWKingY() {
        return Util.getValue(boardData, COORD_MASK, WK_Y_OFFSET);
    }

    public Square getWKing() {
        return new Square(getWKingX(), getWKingY());
    }


    public void setBKingX(int x) {
        boardData = Util.setValue(boardData, x, COORD_MASK, BK_X_OFFSET);
    }

    public void setBKingY(int y) {
        boardData = Util.setValue(boardData, y, COORD_MASK, BK_Y_OFFSET);
    }

    public void setBKing(int x, int y) {
        setBKingX(x);
        setBKingY(y);
    }

    public void setBKing(Square bKing) {
        setBKingX(bKing.getX());
        setBKingY(bKing.getY());
    }

    public int getBKingX() {
        return Util.getValue(boardData, COORD_MASK, BK_X_OFFSET);
    }

    public int getBKingY() {
        return Util.getValue(boardData, COORD_MASK, BK_Y_OFFSET);
    }

    public Square getBKing() {
        return new Square(getBKingX(), getBKingY());
    }

    public void setReversiblePlyNum(int x) {
        boardData = Util.setValue(boardData, x, REVERSIBLE_PLY_NUM_MASK, REVERSIBLE_PLY_NUM_OFFSET);
    }

    public int getReversiblePlyNum() {
        return Util.getValue(boardData, REVERSIBLE_PLY_NUM_MASK, REVERSIBLE_PLY_NUM_OFFSET);
    }

    public int getPlyNum() {
        return plyNum;
    }

    public void validate(Move move) {
        if(DEBUG) {
            int err = validateSetup();
            if(err != 0) {
                String s = "null";
                if(move != null) {
                    s = move.toString(true);
                }
                String msg = String.format("_board error %s on %s:\n%s", err, s, toString());
                logger.debug(msg);
//                throw new Config.PGNException(msg);
            }
        }
    }
    public int getXSize() {
        return Config.BOARD_SIZE;
    }

    public int getYSize() {
        return Config.BOARD_SIZE;
    }

    public Move getMove() {
        return move;
    }

    public void setMove(Move move) {
        this.move = move;
    }

    public void copy(Board src, Board trg) {
        trg.copyBoard(src.board);
        trg.plyNum = src.plyNum;
        trg.boardData = src.boardData;
        trg.setWKing(src.getWKing());
        trg.setBKing(src.getBKing());
    }

    public void toInit() {
        setWKing(-1, -1);   // ??
        setBKing(-1, -1);   // ??
        copyBoard(init);
        setFlags(Config.INIT_POSITION_FLAGS);
    }

    public void toEmpty() {
        setWKing(-1, -1);   // ??
        setBKing(-1, -1);   // ??
        copyBoard(empty);
        setFlags(0);
    }

    @Override
    public String toString() {
        String res = "";

        res += "   a b c d e f g h\n";
        for (int j = Config.BOARD_SIZE - 1; j >= 0; j--) {
            res += (j + 1) + " ";
            for (int i = 0; i < Config.BOARD_SIZE; i++) {
                char ch;
                int piece = getPiece(i, j);
                ch = Config.FEN_PIECES.charAt(piece);
                if (ch == ' ') {
                    ch = '.';
                }
                res += " " + ch;
            }
            res += "  " + (j + 1) + "\n";
        }
        res += "   a b c d e f g h\n";
        return res;
    }

    @Override
    public boolean equals(Object that) {
        if (!(that instanceof Board)) {
            return false;
        }
        Pack thisPack, thatPack;
        try {
            thisPack = new Pack(this, 0);
        } catch (Config.PGNException e) {
            logger.error(String.format("Position invalid for packing %s", this.toString()), e);
            return false;
        }
        try {
            thatPack = new Pack((Board) that, 0);
            return thisPack.equalPosition(thatPack);
        } catch (Config.PGNException e) {
            logger.error(String.format("Position invalid for packing %s", that.toString()), e);
            return false;
        }
    }

    @Override
    public Board clone() {
        Board board = new Board();
        board.copyBoard(this.board);
        board.plyNum = this.plyNum;
        board.boardData = this.boardData;
        board.setWKing(this.getWKing());
        board.setBKing(this.getBKing());
        return board;
    }

    public void copyBoard(int[][] from) {
        for (int j = 0; j < from.length; ++j) {
            int line = 0;
            for (int i = from[j].length - 1; i >= 0; --i) {
                int piece = from[j][i];
                line = (line << 4) + piece;
                if(piece == Config.WHITE_KING) {
                    setWKing(i, j);
                }
                if(piece == Config.BLACK_KING) {
                    setBKing(i, j);
                }
            }
            board[j] = line;
        }
    }

    public void copyBoard(int[] from) {
        System.arraycopy(from, 0, this.board, 0, from.length);
    }

    /**
     * @param fen described in http://en.wikipedia.org/wiki/Forsyth-Edwards_Notation
     */
    public Board(String fen) throws Config.PGNException {
        this.toEmpty();
        StringTokenizer st = new StringTokenizer(fen, "/ ");
        for (int j = Config.BOARD_SIZE - 1; j >= 0; j--) {
            if (!st.hasMoreTokens()) {
                throw new Config.PGNException("invalid FEN " + fen);
            }
            String line = st.nextToken();
            int i = 0;
            for (int k = 0; k < line.length(); k++) {
                char ch = line.charAt(k);
                if (Character.isDigit(ch)) {
                    i += ch - '0';  // empty squares
                } else {
                    int piece = Config.FEN_PIECES.indexOf(ch);
                    this.setPiece(i, j, piece);
                    if (piece == Config.WHITE_KING) {
                        this.setWKing(i, j);
                    }
                    if (piece == Config.BLACK_KING) {
                        this.setBKing(i, j);
                    }
                    ++i;
                }
            }
        }

        int flags = 0;
        if (st.hasMoreTokens()) {
            String turn = st.nextToken();
            if (turn.equals("b")) {
                flags |= Config.FLAGS_BLACK_MOVE;      // previous move was black
            }
        }

        if (st.hasMoreTokens()) {
            String castle = st.nextToken();
            if (castle.indexOf('K') >= 0) {
                flags |= Config.FLAGS_W_KING_OK;
            }
            if (castle.indexOf('Q') >= 0) {
                flags |= Config.FLAGS_W_QUEEN_OK;
            }
            if (castle.indexOf('k') >= 0) {
                flags |= Config.FLAGS_B_KING_OK;
            }
            if (castle.indexOf('q') >= 0) {
                flags |= Config.FLAGS_B_QUEEN_OK;
            }
        }

        if (st.hasMoreTokens()) {
            String enpass = st.nextToken();
            if (!enpass.equals("-")) {
                this.setEnpassantX(calcEnpass(enpass));
                flags |= Config.FLAGS_ENPASSANT_OK;
            }
        }

        if (st.hasMoreTokens()) {
            String reversible_ply_num = st.nextToken();
            this.setReversiblePlyNum(Integer.parseInt(reversible_ply_num));
        }

        if (st.hasMoreTokens()) {
            this.plyNum = 2 * (Integer.parseInt(st.nextToken()) - 1);
            if ((flags & Config.FLAGS_BLACK_MOVE) != 0) {
                ++this.plyNum;
            }
        }
        this.setFlags(flags);
    }

    /**
     * described in http://en.wikipedia.org/wiki/Forsyth-Edwards_Notation
     *
     * @return FEN
     */
    public String toFEN() {
        StringBuilder sb = new StringBuilder();
        String sep = "";

        for (int j = Config.BOARD_SIZE - 1; j >= 0; j--) {
            int empty = 0;
            sb.append(sep);
            sep = "/";
            for (int i = 0; i < Config.BOARD_SIZE; i++) {
                int piece = this.getPiece(i, j);
                if (piece == Config.EMPTY) {
                    ++empty;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                    }
                    sb.append(Config.FEN_PIECES.charAt(piece));
                    empty = 0;
                }
            }
            if (empty > 0) {
                sb.append(empty);
            }
        }

        if ((this.getFlags() & Config.FLAGS_BLACK_MOVE) != 0) {
            sb.append(" b ");
        } else {
            sb.append(" w ");
        }

        if ((this.getFlags() & Config.INIT_POSITION_FLAGS) == 0) {
            sb.append("-");
        } else {
            if ((this.getFlags() & Config.FLAGS_W_KING_OK) != 0) {
                sb.append("K");
            }
            if ((this.getFlags() & Config.FLAGS_W_QUEEN_OK) != 0) {
                sb.append("Q");
            }
            if ((this.getFlags() & Config.FLAGS_B_KING_OK) != 0) {
                sb.append("k");
            }
            if ((this.getFlags() & Config.FLAGS_B_QUEEN_OK) != 0) {
                sb.append("q");
            }
        }
        sb.append(" ");

        if ((this.getFlags() & Config.FLAGS_ENPASSANT_OK) == 0) {
            sb.append("-");
        } else {
            sb.append(this.getEnpassant());
        }
        sb.append(" ").append(this.getReversiblePlyNum());
        sb.append(" ").append((1 + this.plyNum / 2));
        return new String(sb);
    }

    public int getPiece(int x, int y) {
        if(x < 0 || y < 0 || y >= getYSize() || x >= getXSize()) {
            return Config.EMPTY;
        }
        return (board[y] >> (4 * x)) & 0x0f;
    }

    public int getPiece(Square square) {
        return getPiece(square.x, square.y);
    }

    public void setPiece(int x, int y, int piece) {
        int mask = 0x0f << (4 * x);
        board[y] &= ~mask;
        board[y] |= piece << (4 * x);

        if(piece == Config.WHITE_KING) {
            setWKing(x, y);
        }
        if(piece == Config.BLACK_KING) {
            setBKing(x, y);
        }
    }

    public void setPiece(Square square, int piece) {
        setPiece(square.x, square.y, piece);
    }

    public Move newMove() {
        return new Move(this.getFlags());
    }

    // heavily depends on piece values!!
    public int validateSetup() {
        int y, x;
        int[][] count = {
                // w, b, max, init
                {0, 0, 16, 16}, // total
                {0, 0, 1, 1},  // k
                {0, 0, 9, 1},  // q
                {0, 0, 10, 2}, // b
                {0, 0, 10, 2}, // n
                {0, 0, 10, 2}, // r
                {0, 0, 8, 8},  // p
        };

        Square[] king = new Square[2];

        for (y = 0; y < Config.BOARD_SIZE; y++) {
            for (x = 0; x < Config.BOARD_SIZE; x++) {
                int piece = this.getPiece(x, y);
                if (piece == Config.EMPTY) {
                    continue;
                }
                int color;
                if ((piece & Config.BLACK) == 0) {
                    color = 0;
                } else {
                    color = 1;
                }
                int index = (piece & ~Config.BLACK) / 2;
                if(index == Config.PAWN) {
                    if(y == 0 || y == Config.BOARD_SIZE - 1) {
                        return 9 + color;           // Row for pawn!
                    }
                }

                if (++count[0][color] > count[0][2]) {
                    return 15 + color;           // >16 pieces
                }
                if (++count[index][color] > count[index][2]) {
                    return 17 + color;           // Impossible pieces
                }
                if(index * 2 == Config.KING) {
                    king[color] = new Square(x, y);
                }
            }
        }

        if(king[0] == null) {
            return 7;                       // no white king
        }
        if(king[1] == null) {
            return 8;                       // no black king
        }

        int extra[] = new int[2];
        for (int i = Config.QUEEN / 2; i <= Config.ROOK / 2; ++i) {
            for(int color = 0; color < 2; ++color) {
                int e = count[i][color] - count[i][3];
                if(e > 0) {
                    extra[color] += e;
                    if(extra[color] > count[Config.PAWN / 2][3]) {
                        return 17 + color;           // Impossible pieces
                    }
                }
            }
        }

        if ((this.getFlags() & Config.INIT_POSITION_FLAGS & ~getPositionFlags()) != 0) {
            return 13;
        }
        if ((this.getFlags() & Config.FLAGS_ENPASSANT_OK) != 0) {
            if (this.getReversiblePlyNum() > 0) {
                return 11;
            }
            if(this.getEnpassantX() == -1) {
                return 14;
            }
        }

        // verify if the other king is checked:
        Move probe = newMove();
        if ((this.getFlags() & Config.FLAGS_BLACK_MOVE) == 0) {
            probe.to = king[1];
        } else {
            probe.to = king[0];
        }
        return findAttack(probe.to, null) == null ? 0 : 12;
    }

    private int getPositionFlags() {
        int flags = 0;
        if (this.getPiece(4, 0) == Config.WHITE_KING) {
            if (this.getPiece(0, 0) == Config.WHITE_ROOK) {
                flags |= Config.FLAGS_W_QUEEN_OK;
            }
            if (this.getPiece(7, 0) == Config.WHITE_ROOK) {
                flags |= Config.FLAGS_W_KING_OK;
            }
        }
        if (this.getPiece(4, 7) == Config.BLACK_KING) {
            if (this.getPiece(0, 7) == Config.BLACK_ROOK) {
                flags |= Config.FLAGS_B_QUEEN_OK;
            }
            if (this.getPiece(7, 7) == Config.BLACK_ROOK)
                flags |= Config.FLAGS_B_KING_OK;
        }
        return flags;
    }

    // for pgn ambiguous moves QBNR and pawn, K for attack
    boolean validatePgnMove(Move move, int options) {
        int dy = move.to.y - move.from.y;
        int dx = move.to.x - move.from.x;
        int ady = Math.abs(dy);
        int adx = Math.abs(dx);

        // 1. validate piece move
        int colorlessPiece = move.getColorlessPiece();
        if (colorlessPiece == Config.KNIGHT) {
            if (adx * ady != 2) {
                return false;       // error
            }
        } else {
            if (!(dx == 0 || dy == 0 || adx == ady)) {
                return false;        // must be straight line
            }
            if (colorlessPiece == Config.PAWN) {
                if (!validatePawnMove(move, options)) {
                    return false;    // error
                }
            } else if (colorlessPiece == Config.KING) {
                if (adx > 1 || ady > 1) {
                    return false;    // error, must be next square
                }
            } else
            if (colorlessPiece == Config.BISHOP) {
                if (dx == 0 || dy == 0) {
                    return false;    // error, must be diagonal
                }
            } else if (colorlessPiece == Config.ROOK) {
                if (dx != 0 && dy != 0) {
                    return false;    // error, must be vertical or horizontal
                }
            }

            // 2. validate obstruction
            if (this.isObstructed(move)) {
                return false;        // must be no pieces on the way
            }
        }

        if (colorlessPiece != Config.PAWN) {
            move.moveFlags &= ~Config.FLAGS_ENPASSANT_OK;
        }

        if ((options & Config.VALIDATE_CHECK) != 0) {
            return true;
        }

        Board tmp = this.clone();
        tmp.doMove(move);
        tmp.validate(move);

        // 3. validate own king is checked
        Square target;
        if ((move.piece & Config.BLACK) == 0) {
            target = tmp.getWKing();
        } else {
            target = tmp.getBKing();
        }
        return tmp.findAttack(target, null) == null;
    }

    // e4, dxe5, c1=Q, dxe8=R
    private boolean validatePawnMove(Move move, int options) {
        int start_y;
        int final_y;
        int d;
        int hisPawn;
        if ((move.piece & Config.BLACK) != 0) {
            start_y = 6;
            final_y = 0;
            d = -1;
            hisPawn = Config.WHITE_PAWN;
        } else {
            start_y = 1;
            final_y = 7;
            d = 1;
            hisPawn = Config.BLACK_PAWN;
        }
        move.moveFlags &= ~Config.FLAGS_ENPASSANT_OK;

        int dy = move.to.y - move.from.y;
        int adx = Math.abs(move.to.x - move.from.x);
        if (dy != d) {
            if (dy == 2 * d) {
                // initial 2-square move
                if (adx == 0 && move.from.y == start_y
                    && this.getPiece(move.from.x, start_y + d) == Config.EMPTY
                    && this.getPiece(move.to) == Config.EMPTY) {
                    if (this.getPiece(move.to.x - 1, move.to.y) == hisPawn
                        || this.getPiece(move.to.x + 1, move.to.y) == hisPawn) {
                        move.moveFlags |= Config.FLAGS_ENPASSANT_OK;
                    }
                    return true;
                }
            }
            return false;
        }

        boolean ok = false;
        if (adx == 0) {
            // regular 1-square move
            if (this.getPiece(move.to) == Config.EMPTY) {
                ok = true;
            }
        } else if (adx == 1) {
            int pieceTaken = this.getPiece(move.to);
            if (pieceTaken != Config.EMPTY && (pieceTaken & Config.BLACK) != (this.getPiece(move.from) & Config.BLACK)) {
                ok = true;
            } else if (this.getEnpassantX() == move.to.x) {
                pieceTaken = this.getPiece(move.to.x, move.to.y - d);
                if (pieceTaken == hisPawn) {
                    move.moveFlags |= Config.FLAGS_ENPASSANT | Config.FLAGS_CAPTURE;
                    ok = true;
                }
            }
        }

        if (ok && move.to.y == final_y) {
            if((options & (Config.VALIDATE_USER_MOVE | Config.VALIDATE_CHECK)) == 0) {
                int piecePromoted = move.piecePromoted & ~Config.PIECE_COLOR;
                if (piecePromoted < Config.QUEEN || piecePromoted > Config.ROOK) {
                    return false;
                }
            }
            move.moveFlags |= Config.FLAGS_PROMOTION;
        }
        return ok;
    }

    private boolean isObstructed(Move move) {
        int y = move.to.y - move.from.y;
        int dy = (y == 0) ? 0 : (y > 0) ? 1 : -1;
        int x = move.to.x - move.from.x;
        int dx = (x == 0) ? 0 : (x > 0) ? 1 : -1;

        int n = y;
        if (y == 0) {
            n = x;
        }
        if (n == 0)
            return false;

        n = Math.abs(n) - 1;
        y = move.from.y;
        x = move.from.x;
        for (int i = 0; i < n; ++i) {
            y += dy;
            x += dx;
            if (getPiece(x, y) != Config.EMPTY) {
                return true;
            }
        }
        return false;
    }

    boolean validateKingMove(Move move) {
        int dy = move.to.y - move.from.y;
        int dx = move.to.x - move.from.x;
        int ady = Math.abs(dy);
        int adx = Math.abs(dx);

        Square rook = new Square(-1, move.to.y);

        int clearX0, clearX1, checkedX0, checkedX1;
        if( adx <= 1 && ady <= 1 ) {
            checkedX0 =
            checkedX1 = move.to.x;
        } else {
            move.moveFlags |= Config.FLAGS_CASTLE;
            // validate castle
            if( dy != 0 || adx != 2 )
                return false;
            if((move.piece & Config.BLACK) == 0) {
                if( dx == -2 ) {
                    if((this.getFlags() & Config.FLAGS_W_QUEEN_OK) == 0) {
                        return false;
                    }
                    rook.x = 0;
                } else if( (this.getFlags() & Config.FLAGS_W_KING_OK) == 0 ) {
                    return false;
                } else {
                    rook.x = 7;
                }
                rook.y = 0;
                if(getPiece(rook) != Config.WHITE_ROOK) {
                    return false;
                }
            } else {
                if( dx == -2 ) {
                    if((this.getFlags() & Config.FLAGS_B_QUEEN_OK) == 0) {
                        return false;
                    }
                    rook.x = 0;
                } else if( (this.getFlags() & Config.FLAGS_B_KING_OK) == 0 ) {
                    return false;
                } else {
                    rook.x = 7;
                }
                rook.y = 7;
                if(getPiece(rook) != Config.BLACK_ROOK) {
                    return false;
                }
            }

            if(rook.x == 0) {
                clearX0 = 1;
                clearX1 = 3;
                checkedX0 = 2;
                checkedX1 = 4;
            } else {
                clearX0 = 5;
                clearX1 = 6;
                checkedX0 = 4;
                checkedX1 = 6;
            }
            // validate obstruction
            for(int x = clearX0; x <= clearX1; ++x) {
                if(getPiece(x, move.to.y) != Config.EMPTY) {
                    return false;                               // a piece between King and Rook
                }
            }
        }

        // validate if King or the next square is checked
        Board tmp = this.clone();
        tmp.doMove(move);
        for(int x = checkedX0; x <= checkedX1; ++x) {
            Square target = new Square(x, move.to.y);
            if(tmp.findAttack(target, null) != null) {
                return false;                                   // checked
            }
        }

        return true;
    }

    // true means not checked, move is ok
    public boolean validateOwnKingCheck(Move move) {
        Board tmp = this.clone();
        tmp.doMove(move);
        Square sq;
        if((tmp.getFlags() & Config.FLAGS_BLACK_MOVE) == 0) {
            sq = tmp.getBKing();
        } else {
            sq = tmp.getWKing();
        }
        return tmp.findAttack(sq, null) == null;
    }

    // check if any piece attacks trg square, not necessarily a valid move
    Move findAttack(Square trg, Square except) {
        Move probe = newMove();
        if(except == null) {
            probe.from = new Square(0, 0);
        } else {
            probe.from = except.clone();
            ++probe.from.x;
            if( probe.from.x == Config.BOARD_SIZE ) {
                probe.from.x = 0;
                ++probe.from.y;
            }
        }
        int trgPiece = getPiece(trg);
        probe.to = trg;
        for (; probe.from.y < Config.BOARD_SIZE; ++probe.from.y) {
            for (; probe.from.x < Config.BOARD_SIZE; ++probe.from.x) {
                probe.piece = getPiece(probe.from);
                if (probe.piece == Config.EMPTY || (this.getFlags() & Config.BLACK) != (probe.piece & Config.BLACK)) {
                    continue;
                }
                if (trgPiece == Config.EMPTY || (trgPiece & Config.BLACK) != (probe.piece & Config.BLACK)) {
                    if (validatePgnMove(probe, Config.VALIDATE_CHECK)) {
                        return probe;
                    }
                }
            }
            probe.from.x = 0;
        }
        return null;
    }

    // move contains the attacking piece
    boolean validateCheckmate(Move move) {
        Move probe = new Move(move.moveFlags ^ Config.FLAGS_BLACK_MOVE);

        // 1. validate king moves
        // his move
        this.invertFlags(Config.FLAGS_BLACK_MOVE);
        probe.piece = this.getPiece(move.to);
        probe.from = move.to.clone();

        for( probe.to.y = probe.from.y - 1; probe.to.y <= probe.from.y + 1; ++probe.to.y ) {
            if( probe.to.y < 0 )
                continue;
            if( probe.to.y >= Config.BOARD_SIZE )
                break;
            for( probe.to.x = probe.from.x - 1; probe.to.x <= probe.from.x + 1; ++probe.to.x ) {
                if( probe.to.x < 0 )
                    continue;
                if( probe.to.x >= Config.BOARD_SIZE )
                    break;
                int piece = getPiece(probe.to);
                if( piece == Config.EMPTY || (piece & Config.BLACK) != (probe.piece & Config.BLACK)) {
                    if (validateKingMove(probe)) {
                        if(validateOwnKingCheck(probe)) {
                            this.invertFlags(Config.FLAGS_BLACK_MOVE);
                            return false;
                        }
                    }
                }
            }
        }
        this.invertFlags(Config.FLAGS_BLACK_MOVE);

        // 2. verify if it is a double-check, skip verified squares
        // my move
        if(findAttack(move.to, move.from) != null) {
            return true;
        }

        // 3. verify if the attacking piece can be blocked/captured
        // his move
        this.invertFlags(Config.FLAGS_BLACK_MOVE);
        probe = move.clone();
        probe.moveFlags ^= Config.FLAGS_BLACK_MOVE;
        int dx = 0, dy = 0, n = 1;
        if((move.piece & ~Config.BLACK) == Config.KNIGHT) {
            probe.to.x = probe.from.x;
            probe.to.y = probe.from.y;
        } else {
            if (probe.to.x < probe.from.x) {
                dx = 1;
                n = probe.from.x - probe.to.x;
            } else if (probe.to.x > probe.from.x) {
                dx = -1;
                n = probe.to.x - probe.from.x;
            }
            if (probe.to.y < probe.from.y) {
                dy = 1;
                n = probe.from.y - probe.to.y;
            } else if (probe.to.y > probe.from.y) {
                dy = -1;
                n = probe.to.y - probe.from.y;
            }
        }

        for(int i = 0; i < n; ++i) {
            probe.to.x += dx;
            probe.to.y += dy;
            for (probe.from.y = 0; probe.from.y < Config.BOARD_SIZE; ++probe.from.y) {
                for (probe.from.x = 0; probe.from.x < Config.BOARD_SIZE; ++probe.from.x) {
                    probe.piece = getPiece(probe.from);
                    if (probe.piece == Config.EMPTY || (this.getFlags() & Config.BLACK) != (probe.piece & Config.BLACK)) {
                        continue;
                    }
                    if (validatePgnMove(probe, Config.VALIDATE_USER_MOVE)) {
                        this.invertFlags(Config.FLAGS_BLACK_MOVE);
                        return false;
                    }
                }
            }
        }
        boolean res = true;

        // 4. special case: mating move by pawn that can be taken by en passant
        if((this.getFlags() & Config.FLAGS_ENPASSANT_OK) != 0) {
            probe = this.newMove();
            if((this.getFlags() & Config.FLAGS_BLACK_MOVE) != 0) {
                probe.from.y = 3;
                probe.to.y = 2;
                probe.piece = Config.BLACK_PAWN;
            } else {
                probe.from.y = 4;
                probe.to.y = 5;
                probe.piece = Config.WHITE_PAWN;
            }
            int enpass = this.getEnpassantX();
            probe.to.x = enpass;
            if(getPiece(enpass - 1, probe.from.y) == probe.piece) {
                probe.from.x = enpass - 1;
                if (validatePgnMove(probe, Config.VALIDATE_USER_MOVE)) {
                    res = false;
                }
            }
            if( getPiece(enpass + 1, probe.from.y) == probe.piece) {
                probe.from.x = enpass + 1;
                if (validatePgnMove(probe, Config.VALIDATE_USER_MOVE)) {
                    res = false;
                }
            }
        }
        this.invertFlags(Config.FLAGS_BLACK_MOVE);
        return res;
    }

    // move pieces on board
    public void doMove(Move move) {
        ++this.plyNum;
        if ((move.moveFlags & Config.FLAGS_NULL_MOVE) != 0) {
            this.invertFlags(Config.FLAGS_BLACK_MOVE);
            return;
        }

        if(this.getPiece(move.to) != Config.EMPTY) { // can be empty: en passant
            move.moveFlags |= Config.FLAGS_CAPTURE;
        }

        setPiece(move.from, Config.EMPTY);
        if ((move.piecePromoted == Config.EMPTY)) {
            setPiece(move.to, move.piece);
        } else {
            setPiece(move.to, move.piecePromoted);
        }

        if ((move.moveFlags & Config.FLAGS_ENPASSANT) != 0) {
            if (move.to.y == 5) {
                // white move
                setPiece(move.to.x, 4, Config.EMPTY);
            } else if (move.to.y == 2) {
                // black move
                setPiece(move.to.x, 3, Config.EMPTY);
            }
        }
        if ((move.moveFlags & Config.FLAGS_ENPASSANT_OK) != 0) {
            this.setEnpassantX(move.to.x);
        }

        if ((move.moveFlags & Config.FLAGS_CASTLE) != 0) {
            int x0, x1;
            if (move.to.x == 2) {
                x0 = 0;
                x1 = 3;         // queen side
            } else {
                x0 = 7;
                x1 = 5;         // king side
            }
            int rook = getPiece(x0, move.to.y);
            setPiece(x0, move.to.y, Config.EMPTY);
            setPiece(x1, move.to.y, rook);
        }

        if (move.piece == Config.WHITE_KING) {
            this.setWKing(move.to);
            this.clearFlags(Config.FLAGS_W_QUEEN_OK | Config.FLAGS_W_KING_OK);
        } else if (move.piece == Config.BLACK_KING) {
            this.setBKing(move.to);
            this.clearFlags(Config.FLAGS_B_QUEEN_OK | Config.FLAGS_B_KING_OK);
        } else if (move.piece == Config.WHITE_ROOK) {
            if (move.from.x == 0) {
                this.clearFlags(Config.FLAGS_W_QUEEN_OK);
            } else if (move.from.x == 7) {
                this.clearFlags(Config.FLAGS_W_KING_OK);
            }
        } else if (move.piece == Config.BLACK_ROOK) {
            if (move.from.x == 0) {
                this.clearFlags(Config.FLAGS_B_QUEEN_OK);
            } else if (move.from.x == 7) {
                this.clearFlags(Config.FLAGS_B_KING_OK);
            }
        }
        if(move.getColorlessPiece() == Config.PAWN || (move.moveFlags & Config.FLAGS_CAPTURE) != 0) {
            this.setReversiblePlyNum(0);
        } else {
            this.setReversiblePlyNum(getReversiblePlyNum() + 1);
        }

        this.clearFlags(Config.FLAGS_ENPASSANT_OK);
        this.raiseFlags(move.moveFlags & Config.FLAGS_ENPASSANT_OK);
        this.invertFlags(Config.FLAGS_BLACK_MOVE);
    }

}
