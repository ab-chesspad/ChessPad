package com.ab.pgn;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * unit tests
 * Created by Alexander Bootman on 8/13/16.
 */
public class BitStreamTest {

    @Test
    public void testInt() throws IOException {
        // value, mask pairs
        Square[] data = {new Square(20, 5), new Square(999, 10), new Square(423, 10), new Square(36, 4), new Square(625, 10), new Square(999, 10),};

        BitStream.Writer writer = new BitStream.Writer();
        BitStream.Reader reader;
        byte[] buf;

        int val = -1;
        writer.write(val, 32);
        buf = writer.getBits();
        reader = new BitStream.Reader(buf);
        Assert.assertEquals(reader.read(32), val);
        writer.reset();

        for (Square item : data) {
            writer.write(item.x, item.y);
        }
        writer.flush();
        buf = writer.getBits();
        reader = new BitStream.Reader(buf);
        for (Square item : data) {
            int v = reader.read(item.y);
            int mask = (1 << item.y) - 1;
            Assert.assertEquals(item.x & mask, v);
        }
        System.out.println("finish");
    }

    @Test
    public void testString() throws IOException {
        BitStream.Writer writer = new BitStream.Writer();
        int i = 25;
        writer.write(i, 5);
        String s = "this is a very very very very very long string. this is a very very very very very long string. this is a very very very very very long string.";
        writer.writeString(s);
        writer.write(i, 5);
        writer.flush();
        byte[] buf = writer.getBits();
        BitStream.Reader reader = new BitStream.Reader(buf);
        int j = reader.read(5);
        Assert.assertEquals(i, j);
        String r = reader.readString();
        Assert.assertEquals(s, r);
        j = reader.read(5);
        Assert.assertEquals(i, j);
    }

    @Test
    public void testList() throws IOException {
        final List<String> src = Arrays.asList("foo", "bar", "string");
        BitStream.Writer writer = new BitStream.Writer();
        int i = 25;
        writer.write(i, 5);
        writer.writeList(src);
        int j = 27;
        writer.write(j, 5);

        writer.flush();
        byte[] buf = writer.getBits();
        BitStream.Reader reader = new BitStream.Reader(buf);
        Assert.assertEquals(i, reader.read(5));

        List<String> trg = new ArrayList<>();
        reader.readList(trg);
        Assert.assertEquals(src.size(), trg.size());
        int k = 0;
        for(String item : src) {
            Assert.assertEquals(item, trg.get(k++));
        }
        Assert.assertEquals(j, reader.read(5));
    }


//    private Random random = new Random(System.currentTimeMillis());
    private final Random random = new Random(1);
    private byte[] puzzleBitmask;
    private int totalPuzzles;

    private int getBit(int index) {
        int ind = index / 8;
        int bit = index - ind * 8;
        int mask = 1 << bit;
        int res = puzzleBitmask[ind] & mask;
        if(res == 0) {
            puzzleBitmask[ind] |= mask;
        }
        return res;
    }

    private int getNextPuzzleIndex() {
        int index;
        while((getBit(index = random.nextInt(totalPuzzles))) != 0);     // no body
        return index;
    }

    private void setPuzzles(int lastIndex) {
        totalPuzzles = lastIndex + 1;
        puzzleBitmask = new byte[(totalPuzzles + 7) / 8];
    }

    @Test
    public void testBits() {
        int lastIndex = 12;
        setPuzzles(lastIndex);
        for(int i = 0; i <= lastIndex; ++i) {
            int index = getNextPuzzleIndex();
            System.out.println(index);
        }
        System.out.println("done");
    }
}