package com.ab.pgn;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * unit tests
 * Created by Alexander Bootman on 8/13/16.
 */
public class BitStreamTest {

    @Test
    public void testInt() throws IOException {
        // value, mask pairs
        Square[] data = {new Square(20,5), new Square(999,10), new Square(423, 10), new Square(36, 4), new Square(625, 10), new Square(999,10), };

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
        System.out.println("done");
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

}
