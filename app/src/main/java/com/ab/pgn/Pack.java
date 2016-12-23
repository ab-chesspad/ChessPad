package com.ab.pgn;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * pack position into long[3] (byte[24])
 *   64 bit - 'index' array - 8 x 8 bits, 1 for a piece, 0 for empty
 *  100 bit - all pieces except kings:
 *           10 pieces (except kings) are packed with 3-number groups, each group into 10-bit array
 *   12 bit - both kings positions
 *    3 bit - en passant
 *    6 bit - position flags
 * =185 bit total
 *
 * <p/>
 *
 * Used to validate 3-fold repetition and board serialization
 * https://en.wikipedia.org/wiki/Threefold_repetition
 * ... for a position to be considered the same, each player must have the same set of legal moves
 * each time, including the possible rights to castle and capture en passant.
 * Created by Alexander Bootman on 8/4/16.
 */
public class Pack {
    private static final int PACK_SIZE = 3;              // longs.length

    //                                       .   K  Q  B  N  R  P  ?        k  q  b  n  r  p
    //                                       0   1  2  3  4  5  6  7    8   9  a  b  c  d  e
    public static final int[] PACK_CODES = {-1, -1, 0, 1, 2, 3, 4, -1, -1, -1, 5, 6, 7, 8, 9};
    public static final int[] REVERSED_PACK_CODES = {Config.WHITE_QUEEN, Config.WHITE_BISHOP, Config.WHITE_KNIGHT, Config.WHITE_ROOK, Config.WHITE_PAWN,
            Config.BLACK_QUEEN, Config.BLACK_BISHOP, Config.BLACK_KNIGHT, Config.BLACK_ROOK, Config.BLACK_PAWN};

    private long[] longs = new long[PACK_SIZE];

    public Pack(Board board) throws IOException {
        BitStream.Writer writer = new BitStream.Writer();
        pack(board, writer);
        byte[] buf = writer.getBits();
        int longLen = 2;
        longs = new long[longLen + 1];

        int n = -1;
        for (int i = 0; i < longs.length; ++i) {
            longs[i] = 0;
            int shift = 0;
            for (int j = 0; j < 8; ++j) {
                if(++n < buf.length) {
                    longs[i] |= ((long) buf[n] & 0x0ff) << shift;
                    shift += 8;
                }
            }
        }
    }

    public static void pack(Board board, BitStream.Writer writer) throws IOException {
        List<Integer> values = new LinkedList<>();
        int val = 0;
        int factor = 1;
        for (int j = 0; j < Config.BOARD_SIZE; j++) {
            int mask = 1;
            int buf = 0;
            for (int i = 0; i < Config.BOARD_SIZE; i++) {
                int code = PACK_CODES[board.getPiece(i, j)];
                if (code >= 0) {
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
        if(factor != 1) {
            values.add(val);
        }

        for(int v : values) {
            writer.write(v, 10);    // copy 3-decimal-digits number in 10-bit array
        }

        writer.write(board.wKing.x, 3);
        writer.write(board.wKing.y, 3);
        writer.write(board.bKing.x, 3);
        writer.write(board.bKing.y, 3);
        writer.write(board.enpass, 3);
        writer.write(board.flags & Config.POSITION_FLAGS, 6);
    }

    public long[] getBits() {
        return longs;
    }

    public Pack(long[] longs) {
        this.longs = longs;
    }

    public Board unpack() throws IOException {
        byte[] bits = new byte[longs.length * 8];
        for (int i = 0; i < longs.length; ++i) {
            long one = longs[i];
            int n = 8 * i - 1;
            for (int j = 0; j < 8; ++j) {
                ++n;
                bits[n] = (byte) (one & 0x0ff);
                one >>>= 8;
            }
        }

        BitStream.Reader reader = new BitStream.Reader(bits);
        return unpack(reader);
    }

    public static Board unpack(BitStream.Reader reader) throws IOException {
        Board board = new Board();
        board.toEmpty();

        byte[] pieceBits = new byte[Config.BOARD_SIZE];
        for (int j = 0; j < Config.BOARD_SIZE; j++) {
            pieceBits[j] = (byte)(reader.read(8) & 0x0ff);
        }

        int val = 0;
        int factor = 3;
        for (int j = 0; j < Config.BOARD_SIZE; j++) {
            int mask = 1;
            for (int i = 0; i < Config.BOARD_SIZE; i++) {
                if((pieceBits[j] & mask) != 0) {
                    if (factor == 3) {
                        // copy 3-decimal-digits number in 10-bit array
                        val = reader.read(10);
                        factor = 0;
                    }
                    int code = val % 10;
                    int piece = REVERSED_PACK_CODES[code];
                    board.setPiece(i, j, piece);
                    val /= 10;
                    ++factor;
                }
                mask <<= 1;
            }
        }

        board.wKing.x = reader.read(3);
        board.wKing.y = reader.read(3);
        board.setPiece(board.wKing, Config.WHITE_KING);
        board.bKing.x = reader.read(3);
        board.bKing.y = reader.read(3);
        board.setPiece(board.bKing, Config.BLACK_KING);

        board.enpass = reader.read(3);
        board.flags = reader.read(6);
        return board;
    }

    @Override
    public boolean equals(Object that) {
        if (!(that instanceof Pack)) {
            return false;
        }
        return Arrays.equals(this.longs, ((Pack) that).longs);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(longs);
    }
}
