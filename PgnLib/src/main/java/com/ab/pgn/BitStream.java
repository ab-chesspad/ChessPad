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

 * BitStream.Reader and BitStream.Writer
 * Created by Alexander Bootman on 8/13/16.
*/
package com.ab.pgn;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public abstract class BitStream implements Closeable {
    private static final boolean DEBUG = false;

    final byte[] bits = new byte[1];
    int bitIndex;

    int bitCount;     // debug
    List<DebugTrace> traceList;

    public int getBitCount() {
        return bitCount;
    }

    public static class Writer extends  BitStream implements Flushable {
        private OutputStream os;

        public Writer(OutputStream os) {
            this.os = os;
            reset();
            if (DEBUG) {
                traceList = new LinkedList<>();
            }
        }

        public Writer() {
            this(new ByteArrayOutputStream());
        }

        public void reset() {
            if (os instanceof ByteArrayOutputStream) {
                os = new ByteArrayOutputStream();
                bitIndex = 0;
//            } else {
//                // exception?
            }
            bitCount = 0;
        }

        public void write(int _val, int writeBits) throws IOException {
            if (traceList != null) {
                traceList.add(new DebugTrace(bitCount, writeBits, new Config.PGNException("trace")));
            }
            bitCount += writeBits;
            if (writeBits <= 0) {
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
                if (writeBits >= 0) {
                    flush();
                }
            }
        }

        @Override
        public void flush() throws IOException {
            if (bitIndex > 0) {
                os.write(bits);
            }
            bitIndex = 0;
            bits[0] = 0;
        }

        // assuming string.length < 64K byte long
        // to speed up we just write string bytes to os
        public void writeString(String val) throws IOException {
            if (val == null) {
                write(0, 16);
                return;
            }
            byte[] bytes = val.getBytes();
            int length = bytes.length;
            if (length > 0x0ffff) {
                // throw exception?
                length = 0x0ffff;
            }
            write(length, 16);
            if (length > 0) {
                flush();
                os.write(bytes, 0, length);
                bitCount = ((bitCount + 7) / 8 + bytes.length) * 8;
            }
        }

        // assuming list.size() < 64K byte long
        public void writeList(List<String> list) throws IOException {
            int length = list.size();
            if (length > 0x0ffff) {
                // throw exception?
                length = 0x0ffff;
            }
            write(length, 16);
            for (String item : list) {
                writeString(item);
            }
        }

        public byte[] getBits() throws IOException {
            if (os instanceof ByteArrayOutputStream) {
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
            this.traceList = writer.traceList;
        }

        public int read(int readBits) throws IOException {
            int val = 0;
            if (readBits <= 0) {
                return val;
            }

            if (traceList != null) {
                DebugTrace debugTrace = traceList.remove(0);
                if (debugTrace.length != readBits || debugTrace.bitCount != bitCount) {
                    debugTrace.e.printStackTrace();
                    String msg = String.format(Locale.US, "BitStream mismatch, wrote %d at %d bits, read %d at %d bits",
                        debugTrace.length, debugTrace.bitCount, readBits, bitCount
                    );
                    throw new IOException(msg);
                }
            }
            bitCount += readBits;
            int totalLen = 0;
            while (readBits > 0) {
                if (bitIndex > 7) {
                    if (is.read(bits) < 0) {
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
            if (len == 0) {
                return null;        // distinguish between null & empty string?
            }
            int readBytes = is.read(bytes);
            if (readBytes != len) {
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
            if (len == 0) {
                return;
            }
            for (int i = 0; i < len; ++i) {
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

    private static class DebugTrace {
        int bitCount, length;
        Config.PGNException e;

        DebugTrace(int bitCount, int length, Config.PGNException e) {
            this.bitCount = bitCount;
            this.length = length;
            this.e = e;
        }
    }
}
