package com.ab.pgn;

import java.io.IOException;
import java.util.StringTokenizer;

/**
 * Chess board with pieces, validation
 * Created by Alexander Bootman on 8/6g425g/16.
 */
public class Board {
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

    public int[][] board = new int[Config.BOARD_SIZE][Config.BOARD_SIZE];
    public int plyNum;
    public int reversiblePlyNum;
    transient public int flags;         // before next move
    transient public int enpass;        // x-coord only
    transient public Square wKing = new Square();
    transient public Square bKing = new Square();

    public Board() {
        toInit();
    }

    public Board(int[][] board) {
        this.board = board;
    }

    public void serialize(BitStream.Writer writer) throws IOException {
        Pack.pack(this, writer);
        writer.write(this.plyNum, 9);
        writer.write(this.reversiblePlyNum, 9);
    }

    public Board(BitStream.Reader reader) throws IOException {
        Board tmp = Pack.unpack(reader);
        tmp.plyNum = reader.read(9);
        tmp.reversiblePlyNum = reader.read(9);
        copy(tmp, this);
    }

    public int getXSize() {
        return board[0].length;
    }

    public int getYSize() {
        return board.length;
    }

    public void copy(Board src, Board trg) {
        trg.copyBoard(src.board);
        trg.flags = src.flags;
        trg.plyNum = src.plyNum;
        trg.reversiblePlyNum = src.reversiblePlyNum;
        trg.enpass = src.enpass;
        trg.wKing = src.wKing.clone();
        trg.bKing = src.bKing.clone();
    }

    public void toInit() {
        copyBoard(init);
        flags = Config.INIT_POSITION_FLAGS;
        plyNum =
        reversiblePlyNum = 0;
        enpass = 0;
        wKing = new Square(4, 0);
        bKing = new Square(4, 7);
    }

    public void toEmpty() {
        copyBoard(empty);
        flags = 0;
        plyNum =
        reversiblePlyNum = 0;
        enpass = 0;
        wKing = new Square();
        bKing = new Square();
    }

    @Override
    public String toString() {
        String res = "";

        res += "   a b c d e f g h\n";
        for (int j = board.length - 1; j >= 0; j--) {
            res += (j + 1) + " ";
            for (int i = 0; i < board[j].length; i++) {
                char ch;
                int piece = board[j][i];
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
    public Board clone() {
        Board board = new Board();
        board.copyBoard(this.board);
        board.flags = this.flags;
        board.plyNum = this.plyNum;
        board.reversiblePlyNum = this.reversiblePlyNum;
        board.enpass = this.enpass;
        board.wKing = this.wKing.clone();
        board.bKing = this.bKing.clone();
        return board;
    }

    public void copyBoard(int[][] from) {
        for (int i = 0; i < from.length; i++) {
            System.arraycopy(from[i], 0, board[i], 0, from[i].length);
        }
    }

    /**
     * @param fen described in http://en.wikipedia.org/wiki/Forsyth-Edwards_Notation
     */
    public Board(String fen) {
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
                        this.wKing = new Square(i, j);
                    }
                    if (piece == Config.BLACK_KING) {
                        this.bKing = new Square(i, j);
                    }
                    ++i;
                }
            }
        }

        if (st.hasMoreTokens()) {
            String turn = st.nextToken();
            if (turn.equals("b")) {
                this.flags |= Config.FLAGS_BLACK_MOVE;      // previous move was black
            }
        }

        if (st.hasMoreTokens()) {
            String castle = st.nextToken();
            if (castle.indexOf('K') >= 0) {
                this.flags |= Config.FLAGS_W_KING_OK;
            }
            if (castle.indexOf('Q') >= 0) {
                this.flags |= Config.FLAGS_W_QUEEN_OK;
            }
            if (castle.indexOf('k') >= 0) {
                this.flags |= Config.FLAGS_B_KING_OK;
            }
            if (castle.indexOf('q') >= 0) {
                this.flags |= Config.FLAGS_B_QUEEN_OK;
            }
        }

        if (st.hasMoreTokens()) {
            String enpass = st.nextToken();
            if (!enpass.equals("-")) {
                this.enpass = calcEnpass(enpass);
                this.flags |= Config.FLAGS_ENPASSANT_OK;
            }
        }

        if (st.hasMoreTokens()) {
            String reversible_ply_num = st.nextToken();
            this.reversiblePlyNum = Integer.parseInt(reversible_ply_num);
        }

        if (st.hasMoreTokens()) {
            this.plyNum = 2 * (Integer.parseInt(st.nextToken()) - 1);
            if ((this.flags & Config.FLAGS_BLACK_MOVE) != 0) {
                ++this.plyNum;
            }
        }
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

        if ((this.flags & Config.FLAGS_BLACK_MOVE) != 0) {
            sb.append(" b ");
        } else {
            sb.append(" w ");
        }

        if ((this.flags & Config.INIT_POSITION_FLAGS) == 0) {
            sb.append("-");
        } else {
            String castle = "";
            if ((this.flags & Config.FLAGS_W_KING_OK) != 0) {
                sb.append("K");
            }
            if ((this.flags & Config.FLAGS_W_QUEEN_OK) != 0) {
                sb.append("Q");
            }
            if ((this.flags & Config.FLAGS_B_KING_OK) != 0) {
                sb.append("k");
            }
            if ((this.flags & Config.FLAGS_B_QUEEN_OK) != 0) {
                sb.append("q");
            }
        }
        sb.append(" ");

        if ((this.flags & Config.FLAGS_ENPASSANT_OK) == 0) {
            sb.append("-");
        } else {
            int y;
            if ((this.flags & Config.FLAGS_BLACK_MOVE) == 0) {
                y = 5;
            } else {
                y = 2;
            }
            sb.append(Square.x2String(this.enpass)).append(Square.y2String(y));
        }
        sb.append(" ").append(this.reversiblePlyNum);
        sb.append(" ").append((1 + this.plyNum / 2));
        return new String(sb);
    }

    private static int calcEnpass(String square) {
        return (int) (square.charAt(0) - 'a');
    }

    public int getPiece(int x, int y) {
        if(x < 0 || y < 0 || y >= getYSize() || x >= getXSize()) {
            return Config.EMPTY;
        }
        return board[y][x];
    }

    public int getPiece(Square square) {
        return getPiece(square.x, square.y);
    }

    public void setPiece(int x, int y, int piece) {
        int oldPiece = board[y][x];
        board[y][x] = piece;

        if((oldPiece & ~Config.BLACK) == Config.KING ) {
            // can be result of invalid setup, find new king locations
            wKing = bKing = null;
            for (y = 0; y < Config.BOARD_SIZE; y++) {
                for (x = 0; x < Config.BOARD_SIZE; x++) {
                    piece = this.getPiece(x, y);
                    if(piece == Config.WHITE_KING) {
                        wKing = new Square(x, y);
                        if(bKing != null) {
                            return;
                        }
                    }
                    if(piece == Config.BLACK_KING) {
                        bKing = new Square(x, y);
                        if(wKing != null) {
                            return;
                        }
                    }
                }
            }
        } else {
            if(piece == Config.WHITE_KING) {
                wKing = new Square(x, y);
            }
            if(piece == Config.BLACK_KING) {
                bKing = new Square(x, y);
            }
        }
    }

    public void setPiece(Square square, int piece) {
        setPiece(square.x, square.y, piece);
    }

    public Move newMove() {
        return new Move(this.flags & Config.POSITION_FLAGS);
    }

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
                int index = piece & ~Config.BLACK;
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
                if(index == Config.KING) {
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
        for (int i = Config.QUEEN; i <= Config.ROOK; ++i) {
            for(int color = 0; color < 2; ++color) {
                int e = count[i][color] - count[i][3];
                if(e > 0) {
                    extra[color] += e;
                    if(extra[color] > count[Config.PAWN][3]) {
                        return 17 + color;           // Impossible pieces
                    }
                }
            }
        }

        if ((this.flags & Config.INIT_POSITION_FLAGS & ~getPositionFlags()) != 0) {
            return 13;
        }
        if ((this.flags & Config.FLAGS_ENPASSANT_OK) != 0) {
            if (this.reversiblePlyNum > 0) {
                return 11;
            }
            if(this.getEnpass().getY() == -1) {
                return 14;
            }
        }

        // verify if the other king is checked:
        Move probe = newMove();
        if ((this.flags & Config.FLAGS_BLACK_MOVE) == 0) {
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

    public Square getEnpass() {
        Square sq = new Square(this.enpass, -1);

        if((this.flags & Config.FLAGS_ENPASSANT_OK) == 0) {
            return new Square();        // invalid square
        }
        int pawn, otherPawn;
        int y, y1;
        if((this.flags & Config.FLAGS_BLACK_MOVE) == 0) {
            y = 4;
            y1 = 6;
            sq.y = 5;
            pawn = Config.WHITE_PAWN;
            otherPawn = Config.BLACK_PAWN;
        } else {
            y = 3;
            y1 = 1;
            sq.y = 2;
            pawn = Config.BLACK_PAWN;
            otherPawn = Config.WHITE_PAWN;
        }

        if (pawn == this.getPiece(sq.x - 1, y) || pawn == this.getPiece(sq.x + 1, y)) {
            if(otherPawn == this.getPiece(sq.x, y)
                && Config.EMPTY == this.getPiece(sq)
                && Config.EMPTY == this.getPiece(sq.x, y1)) {
                return sq;
            }
        }
        return new Square();        // invalid square
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
        move.snapshot = tmp;

        // 3. validate own king is checked
        Square target;
        if ((move.piece & Config.BLACK) == 0) {
            target = tmp.wKing;
        } else {
            target = tmp.bKing;
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
            if (this.getPiece(move.to) != Config.EMPTY && (pieceTaken & Config.BLACK) != (move.moveFlags & Config.BLACK)) {
                ok = true;
            } else if (this.enpass == move.to.x) {
                pieceTaken = this.getPiece(move.to.x, move.to.y - d);
                if (pieceTaken == hisPawn) {
                    move.pieceTaken = pieceTaken;
                    move.moveFlags |= Config.FLAGS_ENPASSANT;
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
                    if((this.flags & Config.FLAGS_W_QUEEN_OK) == 0) {
                        return false;
                    }
                    rook.x = 0;
                } else if( (this.flags & Config.FLAGS_W_KING_OK) == 0 ) {
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
                    if((this.flags & Config.FLAGS_B_QUEEN_OK) == 0) {
                        return false;
                    }
                    rook.x = 0;
                } else if( (this.flags & Config.FLAGS_B_KING_OK) == 0 ) {
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

        move.snapshot = tmp;                                        // reuse tmp
        return true;
    }

    // true means not checked, move is ok
    public boolean validateOwnKingCheck(Move move) {
        Board tmp = this.clone();
        tmp.doMove(move);
        Square sq;
        if((tmp.flags & Config.FLAGS_BLACK_MOVE) == 0) {
            sq = tmp.bKing;
        } else {
            sq = tmp.wKing;
        }
        return tmp.findAttack(sq, null) == null;
    }

    // check if any piece attacks trg square, not necessarily a valid move
    Move findAttack(Square trg, Square except) {
        Move probe = newMove();
        if(except != null) {
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
                if (probe.piece == Config.EMPTY || (this.flags & Config.BLACK) != (probe.piece & Config.BLACK)) {
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
        this.flags ^= Config.FLAGS_BLACK_MOVE;
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
                            this.flags ^= Config.FLAGS_BLACK_MOVE;
                            return false;
                        }
                    }
                }
            }
        }
        this.flags ^= Config.FLAGS_BLACK_MOVE;

        // 2. verify if it is a double-check, skip verified squares
        // my move
        if(findAttack(move.to, move.from) != null) {
            return true;
        }

        // 3. verify if the attacking piece can be blocked/captured
        // his move
        this.flags ^= Config.FLAGS_BLACK_MOVE;
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
                    if (probe.piece == Config.EMPTY || (this.flags & Config.BLACK) != (probe.piece & Config.BLACK)) {
                        continue;
                    }
                    if (validatePgnMove(probe, Config.VALIDATE_USER_MOVE)) {
                            //System.out.println(String.format("check attack %s - yes", probe));
                        this.flags ^= Config.FLAGS_BLACK_MOVE;
                        return false;
                    }
                }
            }
        }
        boolean res = true;

        // 4. special case: mating move by pawn that can be taken by en passant
        if((this.flags & Config.FLAGS_ENPASSANT_OK) != 0) {
            probe = this.newMove();
            if((this.flags & Config.FLAGS_BLACK_MOVE) != 0) {
                probe.from.y = 3;
                probe.to.y = 2;
                probe.piece = Config.BLACK_PAWN;
            } else {
                probe.from.y = 4;
                probe.to.y = 5;
                probe.piece = Config.WHITE_PAWN;
            }
            probe.to.x = this.enpass;
            if(getPiece(this.enpass - 1, probe.from.y) == probe.piece) {
                probe.from.x = this.enpass - 1;
                if (validatePgnMove(probe, Config.VALIDATE_USER_MOVE)) {
                    res = false;
                }
            }
            if( getPiece(this.enpass + 1, probe.from.y) == probe.piece) {
                probe.from.x = this.enpass + 1;
                if (validatePgnMove(probe, Config.VALIDATE_USER_MOVE)) {
                    res = false;
                }
            }
        }
        this.flags ^= Config.FLAGS_BLACK_MOVE;
        return res;
    }

    // move pieces on board
    public void doMove(Move move) {
        ++this.plyNum;
        if ((move.moveFlags & Config.FLAGS_NULL_MOVE) != 0) {
            this.flags ^= Config.FLAGS_BLACK_MOVE;
            return;
        }

        move.pieceTaken = this.getPiece(move.to);

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
            this.enpass = move.to.x;
        } else {
            this.enpass = 0;
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
            this.wKing = move.to.clone();
            this.flags &= ~(Config.FLAGS_W_QUEEN_OK | Config.FLAGS_W_KING_OK);
        } else if (move.piece == Config.BLACK_KING) {
            this.bKing = move.to.clone();
            this.flags &= ~(Config.FLAGS_B_QUEEN_OK | Config.FLAGS_B_KING_OK);
        } else if (move.piece == Config.WHITE_ROOK) {
            if (move.from.x == 0) {
                move.moveFlags &= ~Config.FLAGS_W_QUEEN_OK;
            } else if (move.from.x == 7) {
                move.moveFlags &= ~Config.FLAGS_W_KING_OK;
            }
        } else if (move.piece == Config.BLACK_ROOK) {
            if (move.from.x == 0) {
                move.moveFlags &= ~Config.FLAGS_B_QUEEN_OK;
            } else if (move.from.x == 7) {
                move.moveFlags &= ~Config.FLAGS_B_KING_OK;
            }
        }
        if(move.getColorlessPiece() == Config.PAWN || move.pieceTaken != Config.EMPTY) {
            this.reversiblePlyNum = 0;
        } else {
            ++this.reversiblePlyNum;
        }

        this.flags &= ~Config.FLAGS_ENPASSANT_OK;
        this.flags |= move.moveFlags & Config.FLAGS_ENPASSANT_OK;
        this.flags ^= Config.FLAGS_BLACK_MOVE;
    }
}
