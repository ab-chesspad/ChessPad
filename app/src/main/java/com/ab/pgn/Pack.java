package com.ab.pgn;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * pack position into int[6] (byte[24])
 *   64 bit - 'index' array - 8 x 8 bits, 1 for a piece, 0 for empty
 *  100 bit - all pieces except kings:
 *           10 pieces (except kings) are packed with 3-number groups, each group into 10-bit array
 *   12 bit - both kings positions
 *    3 bit - en passant
 *    7 bit - position flags
 * =186 bit total
 *  added 6-bit ply number to use in hashCode() but not in equalPosition()
 *  assuming no variant merge after 64 moves
 * 6 * 32 = 192 bit for int[6]
 * <p/>
 *
 * To validate 3-fold repetition, position identification, board serialization
 * https://en.wikipedia.org/wiki/Threefold_repetition
 * ... for a position to be considered the same, each player must have the same set of legal moves
 * each time, including the possible rights to castle and capture en passant.
 * Created by Alexander Bootman on 8/6/16.
 */
public class Pack {
    private static final int
        PACK_SIZE = 6,              // ints.length
        PACK_PIECE_ADJUSTMENT = 4,  // wq->0, bq->1, wr->2, etc.
        MOVE_NUMBER_LENGTH = 6,
        MOVE_NUMBER_MASK = 0x03f,
        dummy_int = 0;

    private static int[] equalityMask = new int[PACK_SIZE];
    static {
        for(int i = 0; i < PACK_SIZE; ++i) {
            if(i == 2) {
                equalityMask[i] = ~MOVE_NUMBER_MASK;
            } else {
                equalityMask[i] = -1;
            }
        }
    }

    private int[] ints = new int[PACK_SIZE];

    public Pack(int[] ints) {
        this.ints = ints;
    }

    public Pack(Board board, int plyNum) throws Config.PGNException {
        ints = pack(board, plyNum);
    }

    public void serialize(BitStream.Writer writer) throws Config.PGNException {
        try {
            for (int i = 0; i < ints.length; ++i) {
                writer.write(ints[i], 32);
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public Pack(BitStream.Reader reader) throws Config.PGNException {
        try {
            for (int i = 0; i < ints.length; ++i) {
                ints[i] = reader.read(32);
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public int[] getPackData() {
        return ints;
    }

    public static int[] pack(Board board, int plyNum) throws Config.PGNException {
        try {
            int[] ints = new int[PACK_SIZE];
            BitStream.Writer writer = new BitStream.Writer();
            pack(board, plyNum, writer);
            byte[] buf = writer.getBits();

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
            return  ints;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public static void packBoard(Board board, int plyNum, BitStream.Writer writer) throws Config.PGNException {
        try {
            int pieces = 0;
            List<Integer> values = new LinkedList<>();
            int val = 0;
            int factor = 1;
            for (int j = 0; j < Config.BOARD_SIZE; j++) {
                int mask = 1;
                int buf = 0;
                for (int i = 0; i < Config.BOARD_SIZE; i++) {
                    int code = board.getPiece(i, j) - PACK_PIECE_ADJUSTMENT;
                    if (code >= 0) {
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
            writer.write(board.plyNum, MOVE_NUMBER_LENGTH);
            writer.write(board.boardData, Board.BOARD_DATA_PACK_LENGTH);
            for (int v : values) {
                writer.write(v, 10);    // copy 3-decimal-digits number in 10-bit array
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public static void pack(Board board, int plyNum, BitStream.Writer writer) throws Config.PGNException {
        packBoard(board, plyNum, writer);
    }

    public int getNumberOfPieces() {
        return getNumberOfPieces(ints);
    }

    public static int getNumberOfPieces(int[] ints) {
        long bits = ((long)ints[1] << 32) | ((long)ints[0] & 0x0ffffffffL);
        int pieces = 0;
        while(bits != 0) {
            ++pieces;
            bits &= bits - 1;
        }
        return pieces;
    }

    public Board unpack() throws Config.PGNException {
        return unpack(ints);
    }

    public static Board unpack(int[] ints) throws Config.PGNException {
        try {
            byte[] bits = new byte[ints.length * 4];
            for (int i = 0; i < ints.length / 2; ++i) {
                long one = ((long)ints[2 * i + 1] << 32) | ((long)ints[2 * i] & 0x0ffffffffL);
                int n = 8 * i - 1;
                for (int j = 0; j < 8; ++j) {
                    ++n;
                    bits[n] = (byte) (one & 0x0ff);
                    one >>>= 8;
                }
            }

            BitStream.Reader reader = new BitStream.Reader(bits);
            return unpack(reader);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public static Board unpackBoard(BitStream.Reader reader) throws Config.PGNException {
        try {
            Board board = new Board();
            board.toEmpty();

            byte[] pieceBits = new byte[Config.BOARD_SIZE];
            for (int j = 0; j < Config.BOARD_SIZE; j++) {
                pieceBits[j] = (byte) (reader.read(8) & 0x0ff);
            }

            board.plyNum = reader.read(MOVE_NUMBER_LENGTH);
            board.boardData = reader.read(Board.BOARD_DATA_PACK_LENGTH);

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

    public static Board unpack(BitStream.Reader reader) throws Config.PGNException {
        Board board = unpackBoard(reader);
        board.setPiece(board.getWKing(), Config.WHITE_KING);
        board.setPiece(board.getBKing(), Config.BLACK_KING);
        return board;
    }

    public boolean equalPosition(Pack that) {
        for (int i = 0; i < PACK_SIZE; ++i) {
            if ((this.ints[i] & equalityMask[i]) != ((that).ints[i] & equalityMask[i])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object that) {
        if (!(that instanceof Pack)) {
            return false;
        }
        return Arrays.equals(this.ints, ((Pack) that).ints);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(ints);
    }
}
