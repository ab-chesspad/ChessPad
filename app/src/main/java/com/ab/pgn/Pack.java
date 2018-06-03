package com.ab.pgn;

import java.io.IOException;
import java.util.Arrays;

/**
 * pack data holder
 * Created by Alexander Bootman on 8/6/16.
 */
public class Pack {
    private static int[] equalityMask = new int[Board.PACK_SIZE];
    private int numberOfPieces = -1;
    static {
        for(int i = 0; i < Board.PACK_SIZE; ++i) {
            if(i == 2) {
                equalityMask[i] = ~Board.MOVE_NUMBER_MASK;
            } else {
                equalityMask[i] = -1;
            }
        }
    }

    private int[] ints = new int[Board.PACK_SIZE];

    public Pack(int[] ints) {
        this.ints = ints;
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

    public int getNumberOfPieces() {
        if(numberOfPieces < 0) {
            numberOfPieces = getNumberOfPieces(ints);
        }
        return numberOfPieces;
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

    public boolean equalPosition(Pack that) {
        for (int i = 0; i < Board.PACK_SIZE; ++i) {
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
