package com.ab.pgn;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * BitStream.Reader and BitStream.Writer
 * Created by Alexander Bootman on 8/13/16.
 */
public abstract class BitStream implements Closeable {
    final byte[] bits = new byte[1];
    int bitIndex;

    int bitCount;     // debug

    public static class Writer extends  BitStream implements Flushable {
        private OutputStream os;

        public Writer(OutputStream os) {
            this.os = os;
            reset();
        }

        public Writer() {
            this(new ByteArrayOutputStream());
        }

        public void reset() {
            if(os instanceof ByteArrayOutputStream) {
                os = new ByteArrayOutputStream();
                bitIndex = 0;
//            } else {
//                // exception?
            }
            bitCount = 0;
        }

        public void write(int _val, int writeBits) throws IOException {
            bitCount += writeBits;
            if(writeBits <= 0) {
                throw new IOException("BitStream.write number of bits must be > 0");
            }
            long mask = (1L << writeBits) - 1;
            long val = _val & mask;
            val <<= bitIndex;
            while (writeBits > 0) {
                bits[0] |= val & 0x0ff;
                val >>>= 8;
                int newBitIndex = bitIndex + writeBits;
                writeBits -= 8 - bitIndex;
                bitIndex = newBitIndex;
                if(writeBits >= 0) {
                    flush();
                }
            }
        }

        @Override
        public void flush() throws IOException {
            if(bitIndex > 0) {
                os.write(bits);
            }
            bitIndex = 0;
            bits[0] = 0;
        }

        // assuming string.length < 64K byte long
        // to speed up we just write string bytes to os
        public void writeString(String val) throws IOException {
            if(val == null) {
                write(0, 16);
                return;
            }
            byte[] bytes = val.getBytes();
            int length = bytes.length;
            if(length > 0x0ffff) {
                // throw exception?
                length = 0x0ffff;
            }
            write(length, 16);
            if(length > 0) {
                flush();
                os.write(bytes, 0, length);
                bitCount = ((bitCount + 7) / 8 + bytes.length) * 8;
            }
        }

        // assuming list.size() < 64K byte long
        public void writeList(List<String> list) throws IOException {
            int length = list.size();
            if(length > 0x0ffff) {
                // throw exception?
                length = 0x0ffff;
            }
            write(length, 16);
            for(String item : list) {
                writeString(item);
            }
        }

        public byte[] getBits() throws IOException {
            if(os instanceof ByteArrayOutputStream) {
                flush();
                return ((ByteArrayOutputStream)os).toByteArray();
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            flush();
            os.close();
        }

        public long[] bits2LongArray() throws IOException {
            byte[] bits = getBits();
            int longLen = (bits.length + 7) / 8 / 8;
            long[] longs = new long[longLen];

            int n = -1;
            for (int i = 0; i < longs.length; ++i) {
                longs[i] = 0;
                for (int j = 0; j < 8; ++j) {
                    longs[i] = (longs[i] << 8) | ((int) bits[++n] & 0x0ff);
                }
            }
            return longs;
        }

        public int getBitCount() throws IOException {
            if(os instanceof ByteArrayOutputStream) {
                return getBits().length;
            }
            return 0;
        }
    }

    public static class Reader extends  BitStream {
        private final InputStream is;

        public Reader(InputStream is) {
            this.is = is;
            bitIndex = 8;
        }

        public Reader(byte[] bits) {
            this.is = new ByteArrayInputStream(bits);
            bitIndex = 8;
        }

        public Reader(Writer writer) throws IOException {
            this(writer.getBits());
        }

        public int read(int readBits) throws IOException {
            int val = 0;
            if (readBits <= 0) {
                return val;
            }

            bitCount += readBits;
            int totalLen = 0;
            while(readBits > 0) {
                if(bitIndex > 7) {
                    if(is.read(bits) < 0) {
                        throw new IOException("Reading beyond input BitStream eof");
                    }
                    bitIndex = 0;
                }
                int _val = (bits[0] & 0xff) >>> bitIndex;
                int len = readBits;
                if (len > 8 - bitIndex) {
                    len = 8 - bitIndex;
                }
                int mask = (1 << len) - 1;
                val |= (_val & mask) << totalLen;
                totalLen += len;
                readBits -= len;
                bitIndex += len;
            }
            return val;
        }

        // assuming string.length < 64K byte long
        public String readString() throws IOException {
            int len = read(16);
            byte[] bytes = new byte[len];
            if(len == 0) {
                return null;        // distinguish between null & empty string?
            }
            int readBytes = is.read(bytes);
            if(readBytes != len) {
                throw new IOException(String.format("Read %s bytes, expected %s", readBytes, len));
            }
            bitCount = ((bitCount + 7) / 8 + bytes.length) * 8;
            bitIndex = 8;           // force read is
            return new String(bytes);
        }

        // assuming list.size() < 64K byte long
        public void readList(List<String> list) throws IOException {
            // clear list?
            int len = read(16);
            if(len == 0) {
                return;
            }
            for(int i = 0; i < len; ++i) {
                list.add(readString());
            }
        }

        int available() throws IOException {
            return is.available();
        }

        @Override
        public void close() throws IOException {
            is.close();
        }
    }
}
