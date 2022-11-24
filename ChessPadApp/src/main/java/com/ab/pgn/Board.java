/*
     Copyright (C) 2021-2022	Alexander Bootman, alexbootman@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

* Chess _board with pieces, validation
* PgnGraph vertex
* Created by Alexander Bootman on 8/6/16.
*/
package com.ab.pgn;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

public class Board {
    public static boolean DEBUG = false;
    private final static PgnLogger logger = PgnLogger.getLogger(Board.class);
    final static int
        // boardData:
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

        // using in toPgn
        VERTEX_VISITED_OFFSET = BOARD_DATA_PACK_LENGTH,         // 22
        VERTEX_VISITED_LENGTH = 1,
        VERTEX_VISITED_MASK = 0x1,

        VERTEX_SERIALIZATION_VISITED_OFFSET = VERTEX_VISITED_OFFSET + VERTEX_VISITED_LENGTH,         // 23

        // boardCounts:
        PLY_NUM_OFFSET = 0,
        PLY_NUM_LENGTH = 9,
        PLY_NUM_MASK = 0x01ff,
        REVERSIBLE_PLY_NUM_OFFSET = PLY_NUM_OFFSET + PLY_NUM_LENGTH,    //  9
        REVERSIBLE_PLY_NUM_LENGTH = 7,
        REVERSIBLE_PLY_NUM_MASK = 0x007f,

        IN_MOVES_OFFSET = REVERSIBLE_PLY_NUM_OFFSET + REVERSIBLE_PLY_NUM_LENGTH,    // 16
        IN_MOVES_LENGTH = 3,
        IN_MOVES_MASK = 0x7,

        BOARD_COUNTS_PACK_LENGTH = IN_MOVES_OFFSET + IN_MOVES_LENGTH,               // 19

        dummy_int = 0;

    public static final int[][] init = {
        {Config.WHITE_ROOK, Config.WHITE_KNIGHT, Config.WHITE_BISHOP, Config.WHITE_QUEEN, Config.WHITE_KING, Config.WHITE_BISHOP, Config.WHITE_KNIGHT, Config.WHITE_ROOK},
        {Config.WHITE_PAWN, Config.WHITE_PAWN, Config.WHITE_PAWN, Config.WHITE_PAWN, Config.WHITE_PAWN, Config.WHITE_PAWN, Config.WHITE_PAWN, Config.WHITE_PAWN},
        {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
        {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
        {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
        {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
        {Config.BLACK_PAWN, Config.BLACK_PAWN, Config.BLACK_PAWN, Config.BLACK_PAWN, Config.BLACK_PAWN, Config.BLACK_PAWN, Config.BLACK_PAWN, Config.BLACK_PAWN},
        {Config.BLACK_ROOK, Config.BLACK_KNIGHT, Config.BLACK_BISHOP, Config.BLACK_QUEEN, Config.BLACK_KING, Config.BLACK_BISHOP, Config.BLACK_KNIGHT, Config.BLACK_ROOK},
    };

    private static final int[][] empty = {
        {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
        {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
        {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
        {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
        {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
        {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
        {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
        {Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY, Config.EMPTY},
    };

    private final int[] board = new int[Config.BOARD_SIZE];
    private final int xSize, ySize;

    private int boardCounts;      // plynum, reversable plynum
    private int boardData;        // enpassant x-coord (3), 7-bit flags (7), kings (12) == 22

    private Move move;              // moves made in this position

/* uncomment to emulate OOM
    int[] debugData = new int[4 * 1024];
//*/

    public Board() {
        ySize =
        xSize = Config.BOARD_SIZE;
        toInit();
    }

    public Board(int[][] pieces) {
        ySize = pieces.length;
        xSize = pieces[0].length;
        copyBoard(pieces);
    }

    public void serialize(BitStream.Writer writer) throws Config.PGNException {
        try {
            pack(writer);
            writer.write(this.boardCounts, BOARD_COUNTS_PACK_LENGTH);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public Board(BitStream.Reader reader) throws Config.PGNException {
        ySize =
        xSize = Config.BOARD_SIZE;
        try {
            Board tmp = unpack(reader);
            tmp.boardCounts = reader.read(BOARD_COUNTS_PACK_LENGTH);
            tmp.validate(null);
            copy(tmp);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    // not necessarily legal position
    public void serializeAnyBoard(BitStream.Writer writer) throws Config.PGNException {
        try {
            this.pack(writer);
            List<Square> wKings = new LinkedList<>();
            List<Square> bKings = new LinkedList<>();
            for (int x = 0; x < Config.BOARD_SIZE; x++) {
                for (int y = 0; y < Config.BOARD_SIZE; y++) {
                    int piece = this.getPiece(x, y);
                    if (piece == Config.WHITE_KING) {
                        wKings.add(new Square(x, y));
                    }
                    if (piece == Config.BLACK_KING) {
                        bKings.add(new Square(x, y));
                    }
                }
            }
            writer.write(wKings.size(), 6);
            for (Square sq : wKings) {
                sq.serialize(writer);
            }
            writer.write(bKings.size(), 6);
            for (Square sq : bKings) {
                sq.serialize(writer);
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    // not necessarily legal position
    static Board unserializeAnyBoard(BitStream.Reader reader) throws Config.PGNException {
        try {
            Board board = Board.unpackWithoutKings(reader);
            for (int x = 0; x < Config.BOARD_SIZE; x++) {
                for (int y = 0; y < Config.BOARD_SIZE; y++) {
                    int piece = board.getPiece(x, y);
                    if (piece == Config.WHITE_KING) {
                        board.setPiece(x, y, Config.EMPTY);
                    }
                    if (piece == Config.BLACK_KING) {
                        board.setPiece(x, y, Config.EMPTY);
                    }
                }
            }

            int n = reader.read(6);
            for (int i = 0; i < n; ++i) {
                Square sq = new Square(reader);
                board.setPiece(sq, Config.WHITE_KING);
            }
            n = reader.read(6);
            for (int i = 0; i < n; ++i) {
                Square sq = new Square(reader);
                board.setPiece(sq, Config.BLACK_KING);
            }
            return  board;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public void setFlags(int value) {
        boardData = Util.setValue(boardData, value, FLAGS_MASK, FLAGS_OFFSET);
    }

    void raiseFlags(int value) {
        boardData = Util.setBits(boardData, value, FLAGS_MASK, FLAGS_OFFSET);
    }

    void clearFlags(int value) {
        boardData = Util.clearBits(boardData, value, FLAGS_MASK, FLAGS_OFFSET);
    }

    void invertFlags(int value) {
        boardData = Util.invertBits(boardData, value, FLAGS_MASK, FLAGS_OFFSET);
    }

    public int getFlags() {
        return Util.getValue(boardData, FLAGS_MASK, FLAGS_OFFSET);
    }

    private static int calcEnpass(String square) {
        return square.charAt(0) - 'a';
    }

    void setEnpassant(Square enpass) {
        setEnpassantX(enpass.getX());
    }

    private void setEnpassantX(int enpass) {
        boardData = Util.setValue(boardData, enpass, COORD_MASK, ENPASS_OFFSET);
    }

    int getEnpassantX() {
        if ((this.getFlags() & Config.FLAGS_ENPASSANT_OK) == 0) {
            return -1;
        }
        return Util.getValue(boardData, COORD_MASK, ENPASS_OFFSET);
    }

    Square getEnpassant() {
        Square square = new Square();
        int flags = this.getFlags();
        if ((flags & Config.FLAGS_ENPASSANT_OK) == 0) {
            return square;        // invalid square
        }
        int enpass = getEnpassantX();
        int pawn, otherPawn;
        int y, y1;
        if ((flags & Config.FLAGS_BLACK_MOVE) == 0) {
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
            if (otherPawn == this.getPiece(enpass, y)
                    && Config.EMPTY == this.getPiece(enpass, square.y)
                    && Config.EMPTY == this.getPiece(enpass, y1)) {
                square.setX(enpass);
                return square;
            }
        }
        return square;
    }

    private void setWKingX(int x) {
        boardData = Util.setValue(boardData, x, COORD_MASK, WK_X_OFFSET);
    }

    private void setWKingY(int y) {
        boardData = Util.setValue(boardData, y, COORD_MASK, WK_Y_OFFSET);
    }

    void setWKing(int x, int y) {
        setWKingX(x);
        setWKingY(y);
    }

    private void setWKing(Square wKing) {
        setWKing(wKing.getX(), wKing.getY());
    }

    int getWKingX() {
        return Util.getValue(boardData, COORD_MASK, WK_X_OFFSET);
    }

    int getWKingY() {
        return Util.getValue(boardData, COORD_MASK, WK_Y_OFFSET);
    }

    Square getWKing() {
        return new Square(getWKingX(), getWKingY());
    }

    private void setBKingX(int x) {
        boardData = Util.setValue(boardData, x, COORD_MASK, BK_X_OFFSET);
    }

    private void setBKingY(int y) {
        boardData = Util.setValue(boardData, y, COORD_MASK, BK_Y_OFFSET);
    }

    void setBKing(int x, int y) {
        setBKingX(x);
        setBKingY(y);
    }

    private void setBKing(Square bKing) {
        setBKing(bKing.getX(), bKing.getY());
    }

    int getBKingX() {
        return Util.getValue(boardData, COORD_MASK, BK_X_OFFSET);
    }

    int getBKingY() {
        return Util.getValue(boardData, COORD_MASK, BK_Y_OFFSET);
    }

    Square getBKing() {
        return new Square(getBKingX(), getBKingY());
    }

    public void setPlyNum(int x) {
        boardCounts = Util.setValue(boardCounts, x, PLY_NUM_MASK, PLY_NUM_OFFSET);
    }

    public int getPlyNum() {
        return Util.getValue(boardCounts, PLY_NUM_MASK, PLY_NUM_OFFSET);
    }

    void incrementPlyNum(int x) {
        boardCounts = Util.incrementValue(boardCounts, x, PLY_NUM_MASK, PLY_NUM_OFFSET);
    }

    public void setReversiblePlyNum(int x) {
        boardCounts = Util.setValue(boardCounts, x, REVERSIBLE_PLY_NUM_MASK, REVERSIBLE_PLY_NUM_OFFSET);
    }

    private void incrementReversiblePlyNum(int x) {
        boardCounts = Util.incrementValue(boardCounts, x, REVERSIBLE_PLY_NUM_MASK, REVERSIBLE_PLY_NUM_OFFSET);
    }

    public int getReversiblePlyNum() {
        return Util.getValue(boardCounts, REVERSIBLE_PLY_NUM_MASK, REVERSIBLE_PLY_NUM_OFFSET);
    }

    void setInMoves(int x) {
        boardCounts = Util.setValue(boardCounts, x, IN_MOVES_MASK, IN_MOVES_OFFSET);
    }

    void incrementInMoves(int x) {
        boardCounts = Util.incrementValue(boardCounts, x, IN_MOVES_MASK, IN_MOVES_OFFSET);
    }

    int getInMoves() {
        return Util.getValue(boardCounts, IN_MOVES_MASK, IN_MOVES_OFFSET);
    }

    private void validate(Move move) {
        if (DEBUG) {
            int err = validateSetup();
            if (err != 0) {
                String s = "null";
                if (move != null) {
                    s = move.toString(true);
                }
                String msg = String.format("board error %s on %s:\n%s", err, s, toString());
                logger.debug(msg);
//                throw new Config.PGNException(msg);
            }
        }
    }

    public int getXSize() {
        return xSize;
    }

    public int getYSize() {
        return ySize;
    }

    public Move getMove() {
        return move;
    }

    public void setMove(Move move) {
        this.move = move;
    }

    void setVisited(boolean visited) {
        int flag = visited ? 1 : 0;
        boardData = Util.setValue(boardData, flag, VERTEX_VISITED_MASK, VERTEX_VISITED_OFFSET);
//System.out.printf("%08x\n", boardData);
    }

    boolean getVisited() {
        return Util.getValue(boardData, VERTEX_VISITED_MASK, VERTEX_VISITED_OFFSET) == 1;
    }

    void setSerialized(boolean visited) {
        int flag = visited ? 1 : 0;
        boardData = Util.setValue(boardData, flag, VERTEX_VISITED_MASK, VERTEX_SERIALIZATION_VISITED_OFFSET);
//System.out.printf("%08x\n", boardData);
    }

    boolean getSerialized() {
        return Util.getValue(boardData, VERTEX_VISITED_MASK, VERTEX_SERIALIZATION_VISITED_OFFSET) == 1;
    }

    private void copy(Board src) {
        this.boardCounts = src.boardCounts;
        this.boardData = src.boardData;
        copyPosition(src);
    }

    void copyPosition(Board src) {
        this.copyBoard(src.board);
        this.setWKing(src.getWKing());
        this.setBKing(src.getBKing());
    }

    public boolean samePosition(Board src) {
        return Arrays.equals(this.board, src.board);
    }

    private void toInit() {
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
        StringBuilder sb = new StringBuilder(256);
        sb.append("   a b c d e f g h".substring(0, 2 * getXSize() + 2)).append("\n");
        for (int j = getYSize() - 1; j >= 0; j--) {
            sb.append(j + 1).append(" ");
            for (int i = 0; i < getXSize(); i++) {
                char ch;
                int piece = getPiece(i, j);
                ch = Config.FEN_PIECES.charAt(piece);
                if (ch == ' ') {
                    ch = '.';
                }
                sb.append(" ").append(ch);
            }
            sb.append("  ").append(j + 1).append("\n");
        }
        sb.append("   a b c d e f g h".substring(0, 2 * getXSize() + 2)).append("\n");
        return sb.toString();
    }

    @Override
    public boolean equals(Object that) {
        if (!(that instanceof Board)) {
            return false;
        }
        Pack thisPack, thatPack;
        try {
            thisPack = new Pack(this.pack());
        } catch (Config.PGNException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
        try {
            thatPack = new Pack(((Board) that).pack());
            return thisPack.equalPosition(thatPack);
        } catch (Config.PGNException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Board clone() {
        Board board = new Board();
        board.copyBoard(this.board);
        board.boardCounts = this.boardCounts;
        board.boardData = this.boardData;
        board.setInMoves(0);
        board.setWKing(this.getWKing());
        board.setBKing(this.getBKing());
        return board;
    }

    private void copyBoard(int[][] from) {
        for (int j = 0; j < from.length; ++j) {
            int line = 0;
            for (int i = from[j].length - 1; i >= 0; --i) {
                int piece = from[j][i];
                line = (line << 4) + piece;
                if (piece == Config.WHITE_KING) {
                    setWKing(i, j);
                }
                if (piece == Config.BLACK_KING) {
                    setBKing(i, j);
                }
            }
            board[j] = line;
        }
    }

    private void copyBoard(int[] from) {
        System.arraycopy(from, 0, this.board, 0, from.length);
    }

    /**
     * @param fen described in http://en.wikipedia.org/wiki/Forsyth-Edwards_Notation
     */
    public Board(String fen) throws Config.PGNException {
        ySize =
        xSize = Config.BOARD_SIZE;
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
            // this is the next move number!
            int fullMoveNum = Integer.parseInt(st.nextToken());
            if (fullMoveNum < 1) {
                fullMoveNum = 1;    // fix for invalid fen, e.g. 4kb1r/p2n1ppp/4q3/4p1B1/4P3/1Q6/PPP2PPP/2KR4 w k - 1 0
            }
            int plyNum = 2 * (fullMoveNum - 1);
            if ((flags & Config.FLAGS_BLACK_MOVE) != 0) {
                ++plyNum;
            }
            this.setPlyNum(plyNum);
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
        sb.append(" ").append((1 + this.getPlyNum() / 2));
        return new String(sb);
    }

    public int getPiece(int x, int y) {
        if (x < 0 || y < 0 || y >= getYSize() || x >= getXSize()) {
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

        if (piece == Config.WHITE_KING) {
            setWKing(x, y);
        }
        if (piece == Config.BLACK_KING) {
            setBKing(x, y);
        }
    }

    public void setPiece(Square square, int piece) {
        setPiece(square.x, square.y, piece);
    }

    public Move newMove() {
        return new Move(this.getFlags());
    }

    public int validateSetup() {
        return validateSetup(false);
    }

    // heavily depends on piece values!!
    // if correct == true, try to correct (flags) errors
    public int validateSetup(boolean correct) {
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
                if ((piece & ~Config.BLACK) == Config.PAWN) {
                    if (y == 0 || y == Config.BOARD_SIZE - 1) {
                        return 9 + color;           // Row for pawn!
                    }
                }

                if (++count[0][color] > count[0][2]) {
                    return 15 + color;           // >16 pieces
                }
                if (++count[index][color] > count[index][2]) {
                    return 17 + color;           // Impossible number of pieces
                }
                if ((piece & ~Config.BLACK) == Config.KING) {
                    king[color] = new Square(x, y);
                }
            }
        }

        if (king[0] == null) {
            return 7;                       // no white king
        }
        if (king[1] == null) {
            return 8;                       // no black king
        }

        int[] extra = new int[2];
        for (int i = Config.QUEEN / 2; i <= Config.ROOK / 2; ++i) {
            for (int color = 0; color < 2; ++color) {
                int e = count[i][color] - count[i][3];
                if (e > 0) {
                    extra[color] += e;
                    if (extra[color] > count[Config.PAWN / 2][3] - count[Config.PAWN / 2][color]) {
                        return 17 + color;           // Impossible pieces - more extra pieces than missing pawns
                    }
                }
            }
        }

        int flags = this.getFlags() & Config.INIT_POSITION_FLAGS & ~getPositionFlags();
        if (DEBUG) {
            logger.debug(String.format("validateSetup board flags=0x%s, positionFlags=0x%s, res=0x%s", Integer.toHexString(getFlags()),
                    Integer.toHexString(getPositionFlags()), flags));
        }
        if (flags != 0) {
            if (correct) {
                this.clearFlags(flags);
            } else {
                return 13;
            }
        }
        if ((this.getFlags() & Config.FLAGS_ENPASSANT_OK) != 0) {
            if (this.getReversiblePlyNum() > 0) {
                return 11;
            }
            if (this.getEnpassant().getX() == -1) {
                return 14;
            }
        }

        // verify if the other king is checked:
        int i;
        if ((this.getFlags() & Config.FLAGS_BLACK_MOVE) == 0) {
            i = 1;
        } else {
            i = 0;
        }
        return findAttack(king[i], null) == null ? 0 : 12;
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
        int dy = move.getToY() - move.getFromY();
        int dx = move.getToX() - move.getFromX();
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
            } else if (colorlessPiece == Config.BISHOP) {
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
        if ((move.getPiece() & Config.BLACK) == 0) {
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
        if ((move.getPiece() & Config.BLACK) != 0) {
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

        int dy = move.getToY() - move.getFromY();
        int adx = Math.abs(move.getToX() - move.getFromX());
        if (dy != d) {
            if (dy == 2 * d) {
                // initial 2-square move
                if (adx == 0 && move.getFromY() == start_y
                        && this.getPiece(move.getFromX(), start_y + d) == Config.EMPTY
                        && this.getPiece(move.getTo()) == Config.EMPTY) {
                    if (this.getPiece(move.getToX() - 1, move.getToY()) == hisPawn
                            || this.getPiece(move.getToX() + 1, move.getToY()) == hisPawn) {
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
            if (this.getPiece(move.getTo()) == Config.EMPTY) {
                ok = true;
            }
        } else if (adx == 1) {
            int pieceTaken = this.getPiece(move.getTo());
            if (pieceTaken != Config.EMPTY && (pieceTaken & Config.BLACK) != (this.getPiece(move.getFrom()) & Config.BLACK)) {
                ok = true;
            } else if (this.getEnpassantX() == move.getToX()) {
                pieceTaken = this.getPiece(move.getToX(), move.getToY() - d);
                if (pieceTaken == hisPawn) {
                    move.moveFlags |= Config.FLAGS_CAPTURE;     // en passant
                    ok = true;
                }
            }
        }

        if (ok && move.getToY() == final_y) {
            if ((options & (Config.VALIDATE_USER_MOVE | Config.VALIDATE_CHECK)) == 0) {
                int piecePromoted = move.getPiecePromoted() & ~Config.PIECE_COLOR;
                if (piecePromoted < Config.QUEEN || piecePromoted > Config.ROOK) {
                    return false;
                }
            }
        }
        return ok;
    }

    private boolean isObstructed(Move move) {
        int y = move.getToY() - move.getFromY();
        int dy = (y == 0) ? 0 : (y > 0) ? 1 : -1;
        int x = move.getToX() - move.getFromX();
        int dx = (x == 0) ? 0 : (x > 0) ? 1 : -1;

        int n = y;
        if (y == 0) {
            n = x;
        }
        if (n == 0) {
            return true;    // for the sake of simplicity consider 0-distanced cells as obstructed
        }

        n = Math.abs(n) - 1;
        y = move.getFromY();
        x = move.getFromX();
        for (int i = 0; i < n; ++i) {
            y += dy;
            x += dx;
            if (getPiece(x, y) != Config.EMPTY) {
                return true;
            }
        }
        return false;
    }

    public boolean validateKingMove(Move move) {
        int dy = move.getToY() - move.getFromY();
        int dx = move.getToX() - move.getFromX();
        int ady = Math.abs(dy);
        int adx = Math.abs(dx);

        Square rook = new Square(-1, move.getToY());

        int clearX0, clearX1, checkedX0, checkedX1;
        if (adx <= 1 && ady <= 1) {
            checkedX0 =
                    checkedX1 = move.getToX();
        } else {
            move.moveFlags |= Config.FLAGS_CASTLE;
            // validate castle
            if (dy != 0 || adx != 2)
                return false;
            if ((move.getPiece() & Config.BLACK) == 0) {
                if (dx == -2) {
                    if ((this.getFlags() & Config.FLAGS_W_QUEEN_OK) == 0) {
                        return false;
                    }
                    rook.x = 0;
                } else if ((this.getFlags() & Config.FLAGS_W_KING_OK) == 0) {
                    return false;
                } else {
                    rook.x = 7;
                }
                rook.y = 0;
                if (getPiece(rook) != Config.WHITE_ROOK) {
                    return false;
                }
            } else {
                if (dx == -2) {
                    if ((this.getFlags() & Config.FLAGS_B_QUEEN_OK) == 0) {
                        return false;
                    }
                    rook.x = 0;
                } else if ((this.getFlags() & Config.FLAGS_B_KING_OK) == 0) {
                    return false;
                } else {
                    rook.x = 7;
                }
                rook.y = 7;
                if (getPiece(rook) != Config.BLACK_ROOK) {
                    return false;
                }
            }

            if (rook.x == 0) {
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
            for (int x = clearX0; x <= clearX1; ++x) {
                if (getPiece(x, move.getToY()) != Config.EMPTY) {
                    return false;                               // a piece between King and Rook
                }
            }
        }

        // validate if King or the next square is checked
        Board tmp = this.clone();
        tmp.doMove(move);
        for (int x = checkedX0; x <= checkedX1; ++x) {
            Square target = new Square(x, move.getToY());
            if (tmp.findAttack(target, null) != null) {
                return false;                                   // checked
            }
        }

        return true;
    }

    // true means not checked, move is ok
    boolean validateOwnKingCheck(Move move) {
        Board tmp = this.clone();
        tmp.doMove(move);
        Square sq;
        if ((tmp.getFlags() & Config.FLAGS_BLACK_MOVE) == 0) {
            sq = tmp.getBKing();
        } else {
            sq = tmp.getWKing();
        }
        return tmp.findAttack(sq, null) == null;
    }

    // check if any piece attacks trg square, not necessarily a valid move
    // except != null when verifying double-check
    Move findAttack(Square trg, Square except) {
        int probeX = 0, probeY = 0;
        if (except != null) {
            probeX = except.getX() + 1;
            probeY = except.getY();
            if (probeX == Config.BOARD_SIZE) {
                probeX = 0;
                ++probeY;
            }
        }
        int trgPiece = getPiece(trg);
        Move probeMove = newMove();
        probeMove.setTo(trg);
        for (; probeY < Config.BOARD_SIZE; ++probeY) {
            for (; probeX < Config.BOARD_SIZE; ++probeX) {
                int piece = getPiece(probeX, probeY);
                if (piece == Config.EMPTY || (this.getFlags() & Config.BLACK) != (piece & Config.BLACK)) {
                    continue;
                }
                if (trgPiece == Config.EMPTY || (trgPiece & Config.BLACK) != (piece & Config.BLACK)) {
                    probeMove.setPiece(getPiece(probeX, probeY));
                    probeMove.setFrom(probeX, probeY);
                    if (validatePgnMove(probeMove, Config.VALIDATE_CHECK)) {
                        return probeMove;
                    }
                }
            }
            probeX = 0;
        }
        return null;
    }

    // move contains the attacking piece
    boolean validateCheckmate(Move move) {
        Move probeMove = new Move(move.moveFlags ^ Config.FLAGS_BLACK_MOVE);
        int probeTo_x, probeTo_y;

        // 1. validate king moves
        // his move
        this.invertFlags(Config.FLAGS_BLACK_MOVE);
        int attackedPiece = this.getPiece(move.getTo());
        probeMove.setPiece(attackedPiece);
        probeMove.setFrom(move.getTo());

        for (probeTo_y = probeMove.getFromY() - 1; probeTo_y <= probeMove.getFromY() + 1; ++probeTo_y) {
            if (probeTo_y < 0) {
                continue;
            }
            if (probeTo_y >= Config.BOARD_SIZE) {
                break;
            }
            for (probeTo_x = probeMove.getFromX() - 1; probeTo_x <= probeMove.getFromX() + 1; ++probeTo_x) {
                if (probeTo_x < 0) {
                    continue;
                }
                if (probeTo_x >= Config.BOARD_SIZE) {
                    break;
                }
                int piece = getPiece(probeTo_x, probeTo_y);
                if (piece == Config.EMPTY || (piece & Config.BLACK) != (attackedPiece & Config.BLACK)) {
                    probeMove.setTo(probeTo_x, probeTo_y);
                    if (validateKingMove(probeMove)) {
                        if (validateOwnKingCheck(probeMove)) {
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
        if (findAttack(move.getTo(), move.getFrom()) != null) {
            return true;
        }

        // 3. verify if the attacking piece can be blocked/captured
        // his move
        this.invertFlags(Config.FLAGS_BLACK_MOVE);
        probeMove = move.clone();
        probeMove.moveFlags ^= Config.FLAGS_BLACK_MOVE;
        int dx = 0, dy = 0, n = 1;
        if ((move.getPiece() & ~Config.BLACK) == Config.KNIGHT) {
            probeMove.setToX(probeMove.getFromX());
            probeMove.setToY(probeMove.getFromY());
        } else {
            if (probeMove.getToX() < probeMove.getFromX()) {
                dx = 1;
                n = probeMove.getFromX() - probeMove.getToX();
            } else if (probeMove.getToX() > probeMove.getFromX()) {
                dx = -1;
                n = probeMove.getToX() - probeMove.getFromX();
            }
            if (probeMove.getToY() < probeMove.getFromY()) {
                dy = 1;
                n = probeMove.getFromY() - probeMove.getToY();
            } else if (probeMove.getToY() > probeMove.getFromY()) {
                dy = -1;
                n = probeMove.getToY() - probeMove.getFromY();
            }
        }

        int probeFrom_x, probeFrom_y;
        for (int i = 0; i < n; ++i) {
            probeMove.setToX(probeMove.getToX() + dx);
            probeMove.setToY(probeMove.getToY() + dy);
            for (probeFrom_y = 0; probeFrom_y < Config.BOARD_SIZE; ++probeFrom_y) {
                for (probeFrom_x = 0; probeFrom_x < Config.BOARD_SIZE; ++probeFrom_x) {
                    int piece = getPiece(probeFrom_x, probeFrom_y);
                    if (piece == Config.EMPTY || (this.getFlags() & Config.BLACK) != (piece & Config.BLACK)) {
                        continue;
                    }
                    probeMove.setFrom(probeFrom_x, probeFrom_y);
                    probeMove.setPiece(piece);
                    if (validatePgnMove(probeMove, Config.VALIDATE_USER_MOVE)) {
                        this.invertFlags(Config.FLAGS_BLACK_MOVE);
                        return false;
                    }
                }
            }
        }

        // 4. special case: mating move by pawn that can be taken by en passant
        if ((this.getFlags() & Config.FLAGS_ENPASSANT_OK) != 0) {
            probeMove = this.newMove();
            if ((this.getFlags() & Config.FLAGS_BLACK_MOVE) != 0) {
                probeMove.setFromY(3);
                probeMove.setToY(2);
                probeMove.setPiece(Config.BLACK_PAWN);
            } else {
                probeMove.setFromY(4);
                probeMove.setToY(5);
                probeMove.setPiece(Config.WHITE_PAWN);
            }
            int enpass = this.getEnpassantX();
            probeMove.setToX(enpass);
            if (getPiece(enpass - 1, probeMove.getFromY()) == probeMove.getPiece()) {
                probeMove.setFromX(enpass - 1);
                if (validatePgnMove(probeMove, Config.VALIDATE_USER_MOVE)) {
                    this.invertFlags(Config.FLAGS_BLACK_MOVE);
                    return false;
                }
            }
            if (getPiece(enpass + 1, probeMove.getFromY()) == probeMove.getPiece()) {
                probeMove.setFromX(enpass + 1);
                if (validatePgnMove(probeMove, Config.VALIDATE_USER_MOVE)) {
                    this.invertFlags(Config.FLAGS_BLACK_MOVE);
                    return false;
                }
            }
        }
        this.invertFlags(Config.FLAGS_BLACK_MOVE);
        return true;
    }

    boolean validateStalemate() {
        final int[][][] probePawnMoves = {
            {{0,1}, {-1,1}, {1,1}},             // white
            {{0, -1}, {-1, -1}, {1, -1}},       // black
        };
        final int[][] probeKnigthMoves = {{-1,-2}, {-2,-1}, {-2,1}, {-1,2}, {1,2}, {2,1}, {2,-1}, {1,-2}};
        final int[][] probeKingMoves = {{-1,0}, {-1,1}, {0,1}, {1,1}, {1,0}, {1,-1}, {0,-1}, {-1,-1}};
        final int[][] probeBishopMoves = {{-1,-1}, {-1,1}, {1,1}, {1,-1}};
        final int[][] probeRookMoves = {{-1,0}, {0,1}, {1,0}, {0,-1}};

        Board tmp = this.clone();
        int myColor = tmp.getFlags() & Config.BLACK;
        int hisColor = Config.BLACK - myColor;
        for (int i = 0; i < Config.BOARD_SIZE; ++i) {
            for (int j = 0; j < Config.BOARD_SIZE; ++j) {
                int piece = tmp.getPiece(i, j);
                if (piece == Config.EMPTY || (piece & Config.BLACK) == hisColor) {
                    continue;
                }
                Move move = tmp.newMove();
                int moveFlags = move.moveFlags;
                move.setFrom(i, j);
                move.setPiece(piece);
                int[][] probeMoves;
                switch (piece & ~Config.BLACK) {
                    case Config.PAWN:
                        probeMoves = probePawnMoves[myColor];       // ASSUMING Config.BLACK == 1!!
                        break;

                    case Config.KNIGHT:
                        probeMoves = probeKnigthMoves;
                        break;

                    case Config.ROOK:
                        probeMoves = probeRookMoves;
                        break;

                    case Config.BISHOP:
                        probeMoves = probeBishopMoves;
                        break;

                    default:
                        probeMoves = probeKingMoves;
                        break;
                }
                for (int[] probeMove : probeMoves) {
                    int x = i + probeMove[0];
                    if (x < 0 || x >= Config.BOARD_SIZE) {
                        continue;
                    }
                    int y = j + probeMove[1];
                    if (y < 0 || y >= Config.BOARD_SIZE) {
                        continue;
                    }
                    int trgPiece = tmp.getPiece(x, y);
                    if (trgPiece != Config.EMPTY && (trgPiece & Config.BLACK) == (piece & Config.BLACK)) {
                        continue;
                    }
                    move.setTo(x, y);
                    move.moveFlags = moveFlags;
                    if (DEBUG) {
                        logger.debug(String.format("stalemate probe %s\n%s", move.toString(), tmp.toString()));
                    }
                    if (tmp.validatePgnMove(move, Config.VALIDATE_USER_MOVE)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // move pieces on board
    void doMove(Move move) {
        this.incrementPlyNum(1);
        if ((move.moveFlags & Config.FLAGS_NULL_MOVE) != 0) {
            this.invertFlags(Config.FLAGS_BLACK_MOVE);
            return;
        }

        if (this.getPiece(move.getTo()) != Config.EMPTY) { // can be empty: en passant
            move.moveFlags |= Config.FLAGS_CAPTURE;
        }

        setPiece(move.getFrom(), Config.EMPTY);
        int toPiece = getPiece(move.getTo());
        if ((move.getPiecePromoted() == Config.EMPTY)) {
            setPiece(move.getTo(), move.getPiece());
        } else {
            setPiece(move.getTo(), move.getPiecePromoted());
        }

        if (move.getColorlessPiece() == Config.PAWN && move.getFromX() != move.getToX() && toPiece == Config.EMPTY) {
            // cannot use isEnPassant(move) because hisPawn is not taken out
            if (move.getToY() == 5) {
                // white move
                setPiece(move.getToX(), 4, Config.EMPTY);
            } else if (move.getToY() == 2) {
                // black move
                setPiece(move.getToX(), 3, Config.EMPTY);
            }
        }
        if ((move.moveFlags & Config.FLAGS_ENPASSANT_OK) != 0) {
            this.setEnpassantX(move.getToX());
        }

        if ((move.moveFlags & Config.FLAGS_CASTLE) != 0) {
            int x0, x1;
            if (move.getToX() == 2) {
                x0 = 0;
                x1 = 3;         // queen side
            } else {
                x0 = 7;
                x1 = 5;         // king side
            }
            int rook = getPiece(x0, move.getToY());
            setPiece(x0, move.getToY(), Config.EMPTY);
            setPiece(x1, move.getToY(), rook);
        }

        if (move.getPiece() == Config.WHITE_KING) {
            this.setWKing(move.getTo());
            this.clearFlags(Config.FLAGS_W_QUEEN_OK | Config.FLAGS_W_KING_OK);
        } else if (move.getPiece() == Config.BLACK_KING) {
            this.setBKing(move.getTo());
            this.clearFlags(Config.FLAGS_B_QUEEN_OK | Config.FLAGS_B_KING_OK);
        } else if (move.getPiece() == Config.WHITE_ROOK) {
            if (move.getFromX() == 0) {
                this.clearFlags(Config.FLAGS_W_QUEEN_OK);
            } else if (move.getFromX() == 7) {
                this.clearFlags(Config.FLAGS_W_KING_OK);
            }
        } else if (move.getPiece() == Config.BLACK_ROOK) {
            if (move.getFromX() == 0) {
                this.clearFlags(Config.FLAGS_B_QUEEN_OK);
            } else if (move.getFromX() == 7) {
                this.clearFlags(Config.FLAGS_B_KING_OK);
            }
        }

        if (move.getToY() == 7) {
            if (move.getToX() == 0) {
                this.clearFlags(Config.FLAGS_B_QUEEN_OK);
            }
            if (move.getToX() == 7) {
                this.clearFlags(Config.FLAGS_B_KING_OK);
            }
        }
        if (move.getToY() == 0) {
            if (move.getToX() == 0) {
                this.clearFlags(Config.FLAGS_W_QUEEN_OK);
            }
            if (move.getToX() == 7) {
                this.clearFlags(Config.FLAGS_W_KING_OK);
            }
        }

        if (move.getColorlessPiece() == Config.PAWN || (move.moveFlags & Config.FLAGS_CAPTURE) != 0) {
            this.setReversiblePlyNum(0);
        } else {
            this.incrementReversiblePlyNum(1);
        }

        this.clearFlags(Config.FLAGS_ENPASSANT_OK);
        this.raiseFlags(move.moveFlags & Config.FLAGS_ENPASSANT_OK);
        this.invertFlags(Config.FLAGS_BLACK_MOVE);
    }

    public boolean isEnPassant(Move move) {
        return move.getColorlessPiece() == Config.PAWN && move.getFromX() != move.getToX() && getPiece(move.getTo()) == Config.EMPTY;
    }

    /**
     * pack position into int[6] (byte[24])
     * 64 bit - 'index' array - 8 x 8 bits, 1 for a piece, 0 for empty
     * 100 bit - all pieces except kings:
     * 10 pieces (except kings) are packed with 3-number groups, each group into 10-bit array
     * 12 bit - both kings positions
     * 3 bit - en passant
     * 7 bit - position flags
     * =186 bit total
     * added 6-bit ply number to use in hashCode() but not in equalPosition()
     * assuming no variant merge after 64 moves
     * 6 * 32 = 192 bit for int[6]
     * <p/>
     * <p>
     * To validate 3-fold repetition, position identification, board serialization
     * https://en.wikipedia.org/wiki/Threefold_repetition
     * ... for a position to be considered the same, each player must have the same set of legal moves
     * each time, including the possible rights to castle and capture en passant.
     * Created by Alexander Bootman on 8/6/16.
     */
    static final int
            PACK_SIZE = 6,              // ints.length
            PACK_PIECE_ADJUSTMENT = 4,  // wq->0, bq->1, wr->2, etc.
            MOVE_NUMBER_LENGTH = 6,     // only 6-bit part
            MOVE_NUMBER_MASK = 0x03f,
            _dummy_int = 0;

    int[] pack() throws Config.PGNException {
        try {
            int[] ints = new int[PACK_SIZE];
            BitStream.Writer writer = new BitStream.Writer();
            pack(writer);
            byte[] buf = writer.getBits();
            if (buf.length > PACK_SIZE * 4) {
                throw new Config.PGNException(String.format("Invalid position to pack: \n%s", this.toString()));
            }

            int n = -1;
            for (int i = 0; i < ints.length; ++i) {
                ints[i] = 0;
                int shift = 0;
                for (int j = 0; j < 4; ++j) {
                    if (++n < buf.length) {
                        ints[i] |= ((int) buf[n] & 0x0ff) << shift;
                        shift += 8;
                    }
                }
            }
            return ints;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    static Board unpack(int[] ints) throws Config.PGNException {
        try {
            byte[] bits = new byte[ints.length * 4];
            for (int i = 0; i < ints.length / 2; ++i) {
                long one = ((long) ints[2 * i + 1] << 32) | ((long) ints[2 * i] & 0x0ffffffffL);
                int n = 8 * i - 1;
                for (int j = 0; j < 8; ++j) {
                    ++n;
                    bits[n] = (byte) (one & 0x0ff);
                    one >>>= 8;
                }
            }

            BitStream.Reader reader = new BitStream.Reader(bits);
            return unpack(reader);
        } catch(Exception e) {
            throw  new Config.PGNException(e);
        }
    }

    void pack(BitStream.Writer writer) throws Config.PGNException {
        try {
            int pieces = 0;
            List<Integer> values = new LinkedList<>();
            int val = 0;
            int factor = 1;
            for (int j = 0; j < Config.BOARD_SIZE; j++) {
                int mask = 1;
                int buf = 0;
                for (int i = 0; i < Config.BOARD_SIZE; i++) {
                    int code = this.getPiece(i, j) - PACK_PIECE_ADJUSTMENT;
                    if (code >= 0) {
                        // ignoring kings
                        ++pieces;
                        buf |= mask;
                        val += factor * code;
                        factor *= 10;
                        if (factor == 1000) {
                            values.add(val);    // store 3-decimal-digits number
                            factor = 1;
                            val = 0;
                        }
                    }
                    mask <<= 1;
                }
                writer.write(buf, 8);
            }
            if (factor != 1) {
                values.add(val);
            }
            writer.write(this.getPlyNum() / 2, Board.MOVE_NUMBER_LENGTH);
            writer.write(this.boardData, Board.BOARD_DATA_PACK_LENGTH);
            for (int v : values) {
                writer.write(v, 10);    // copy 3-decimal-digits number in 10-bit array
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    static Board unpackWithoutKings(BitStream.Reader reader) throws Config.PGNException {
        try {
            Board board = new Board();
            board.toEmpty();

            byte[] pieceBits = new byte[Config.BOARD_SIZE];
            for (int j = 0; j < Config.BOARD_SIZE; j++) {
                pieceBits[j] = (byte) (reader.read(8) & 0x0ff);
            }

            int plyNum = reader.read(Board.MOVE_NUMBER_LENGTH) * 2;
            board.boardData = reader.read(Board.BOARD_DATA_PACK_LENGTH);
            if ((board.getFlags() & Config.FLAGS_BLACK_MOVE) != 0) {
                ++plyNum;
            }
            board.setPlyNum(plyNum);

            int val = 0;
            int factor = 3;
            for (int j = 0; j < Config.BOARD_SIZE; j++) {
                int mask = 1;
                for (int i = 0; i < Config.BOARD_SIZE; i++) {
                    if ((pieceBits[j] & mask) != 0) {
                        if (factor == 3) {
                            // copy 3-decimal-digits number in 10-bit array
                            val = reader.read(10);
                            factor = 0;
                        }
                        int code = val % 10;
                        int piece = code + PACK_PIECE_ADJUSTMENT;
                        board.setPiece(i, j, piece);
                        val /= 10;
                        ++factor;
                    }
                    mask <<= 1;
                }
            }
            return board;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    static Board unpack(BitStream.Reader reader) throws Config.PGNException {
        Board board = unpackWithoutKings(reader);
        board.setPiece(board.getWKingX(), board.getWKingY(), Config.WHITE_KING);
        board.setPiece(board.getBKingX(), board.getBKingY(), Config.BLACK_KING);
        return board;
    }

        public Board invert() {
        Board trg = new Board();
        trg.toEmpty();
        trg.boardData = this.boardData;
        trg.boardCounts = this.boardCounts;
        for (int y = 0; y < Config.BOARD_SIZE; ++y) {
            for (int x = 0; x < Config.BOARD_SIZE; ++x) {
                int piece = this.getPiece(x, y);
                int i = Config.BOARD_SIZE - 1 - x;
                int j = Config.BOARD_SIZE - 1 - y;
                trg.setPiece(i, j, piece);
                if (piece == Config.WHITE_KING) {
                    trg.setWKing(i, j);
                }
                if (piece == Config.BLACK_KING) {
                    trg.setBKing(i, j);
                }
            }
        }
        return trg;
    }

    private int invertBoardFlags(int flags) {
        int res = flags;
        res ^= Config.FLAGS_BLACK_MOVE;
        res &= ~Config.INIT_POSITION_FLAGS;
        if ((flags & Config.FLAGS_W_KING_OK) != 0) {
            res |= Config.FLAGS_B_KING_OK;
        }
        if ((flags & Config.FLAGS_B_KING_OK) != 0) {
            res |= Config.FLAGS_W_KING_OK;
        }
        if ((flags & Config.FLAGS_W_QUEEN_OK) != 0) {
            res |= Config.FLAGS_B_QUEEN_OK;
        }
        if ((flags & Config.FLAGS_B_QUEEN_OK) != 0) {
            res |= Config.FLAGS_W_QUEEN_OK;
        }
        return res;
    }

    // search for a move that results with nextBoard
    // assumes that nextBoard is different
    public Move findMove(Board nextBoard) {
        List<Square> fromSquares = new LinkedList<>();
        List<Square> toSquares = new LinkedList<>();
        for (int y = 0; y < getYSize(); ++y) {
            int diff = board[y] ^ nextBoard.board[y];
            if (diff != 0) {
                if (DEBUG) {
                    logger.debug(String.format("y=%s: 0x%s", y, Integer.toHexString(diff)));
                }
                for (int i = 0; i < 4; ++i) {
                    if ((diff & 0x0f) != 0) {
                        int x = 2 * i;
                        Square square = new Square(x, y);
                        if (nextBoard.getPiece(square) == Config.EMPTY) {
                            fromSquares.add(square);
                        } else {
                            toSquares.add(square);
                        }
                    }
                    if ((diff & 0x0f0) != 0) {
                        int x = 2 * i + 1;
                        Square square = new Square(x, y);
                        if (nextBoard.getPiece(square) == Config.EMPTY) {
                            fromSquares.add(square);
                        } else {
                            toSquares.add(square);
                        }
                    }
                    diff >>= 8;
                }
            }
        }
        if (DEBUG) {
            logger.debug(fromSquares);
            logger.debug(toSquares);
        }
        Move move;
        if (toSquares.size() == 1 && fromSquares.size() == 1) {
            move = newMove();
            move.setFrom(fromSquares.get(0));
            move.setTo(toSquares.get(0));
            int piece = getPiece(move.getFrom());
            move.setPiece(piece);
            if (move.getPiece() == piece) {
                // same color
                if ((move.getPiece() & ~Config.PIECE_COLOR) == Config.PAWN) {
                    piece = nextBoard.getPiece(move.getTo());
                    if ((piece & ~Config.PIECE_COLOR) != Config.PAWN) {
                        move.setPiecePromoted(piece);
                    }
                }
                if (validatePgnMove(move, Config.VALIDATE_USER_MOVE)) {
                    return move;
                }
            }
        } else if (fromSquares.size() == 2 && (toSquares.size() == 1 || toSquares.size() == 2)) {
            for (int i = 0; i < fromSquares.size(); ++i) {
                Square fromSquare = fromSquares.get(i);
                for (Square toSquare : toSquares) {
                    move = newMove();
                    move.setFrom(fromSquare);
                    move.setTo(toSquare);
                    int piece = getPiece(move.getFrom());
                    move.setPiece(piece);
                    if (move.getPiece() != piece) {
                        // different color
                        continue;
                    }
                    boolean res = false;
                    if ((getPiece(fromSquare) & ~Config.PIECE_COLOR) == Config.KING) {
                        // must be castling
                        res = validateKingMove(move);
                    } else if ((getPiece(fromSquare) & ~Config.PIECE_COLOR) == Config.PAWN) {
                        // must be capture en passant
                        if ((move.getPiece() & ~Config.PIECE_COLOR) == Config.PAWN) {
                            piece = nextBoard.getPiece(move.getTo());
                            if ((piece & ~Config.PIECE_COLOR) != Config.PAWN) {
                                move.setPiecePromoted(piece);
                            }
                        }
                        res = validatePawnMove(move, Config.VALIDATE_USER_MOVE);
                    }
                    if (res) {
                        Board _board = clone();
                        _board.doMove(move);
                        if (_board.equals(nextBoard)) {
                            return move;
                        }
                    }
                }
            }
        }
        return null;
    }
}