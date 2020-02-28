package com.ab.pgn.fics;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * Created by Alexander Bootman on 5/1/19.
 */
class TimesealSocket extends Socket {
    private final long initialTime = System.currentTimeMillis();
    private final Lock writeLock = new ReentrantLock(true);

    public TimesealSocket(String address, int port, String initialTimesealString) throws IOException {
        super(address, port);
        try {
            if(!initialTimesealString.endsWith("\n")) {
                initialTimesealString += "\n";
            }
            send(initialTimesealString);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private void sendAck() throws IOException {
        send("\0029\n");
    }

    private void send(String msg) throws IOException {
        try {
            writeLock.lock();
            getOutputStream().write(msg.getBytes());
        } finally {
            writeLock.unlock();
        }
    }

    public OutputStream getOutputStream() throws IOException {
        return new CryptOutputStream(super.getOutputStream());
    }

    public InputStream getInputStream() {
        return new CryptInputStream();
    }

    private class CryptOutputStream extends OutputStream {
        private final byte[] buffer;
        private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        private final OutputStream outputStreamToDecorate;
        private final byte[] timesealKey = "Timestamp (FICS) v1.0 - programmed by Henrik Gram.".getBytes();

        CryptOutputStream(OutputStream outputstream) {
            buffer = new byte[10000];
            outputStreamToDecorate = outputstream;
        }

        @Override
        public void write(int i) throws IOException {
            if (i == 0x0a) {
                // \n, carriage return
                synchronized (TimesealSocket.this) {
                    int resultLength = crypt(byteArrayOutputStream.toByteArray(),
                            System.currentTimeMillis() - initialTime);
                    outputStreamToDecorate.write(buffer, 0, resultLength);
                    outputStreamToDecorate.flush();
                    byteArrayOutputStream.reset();
                }
            } else {
                byteArrayOutputStream.write(i);
            }
        }

        private int crypt(byte[] stringToWriteBytes, long timestamp) {
            int bytesInLength = stringToWriteBytes.length;
            System.arraycopy(stringToWriteBytes, 0, buffer, 0, stringToWriteBytes.length);
            buffer[bytesInLength++] = 24;
            byte[] abyte1 = Long.toString(timestamp).getBytes();
            System.arraycopy(abyte1, 0, buffer, bytesInLength, abyte1.length);
            bytesInLength += abyte1.length;
            buffer[bytesInLength++] = 25;
            int j = bytesInLength;
            for (bytesInLength += 12 - bytesInLength % 12; j < bytesInLength;) {
                buffer[j++] = 49;
            }

            for (int k = 0; k < bytesInLength; k++) {
                buffer[k] |= 0x80;
            }

            for (int i1 = 0; i1 < bytesInLength; i1 += 12) {
                byte byte0 = buffer[i1 + 11];
                buffer[i1 + 11] = buffer[i1];
                buffer[i1] = byte0;
                byte0 = buffer[i1 + 9];
                buffer[i1 + 9] = buffer[i1 + 2];
                buffer[i1 + 2] = byte0;
                byte0 = buffer[i1 + 7];
                buffer[i1 + 7] = buffer[i1 + 4];
                buffer[i1 + 4] = byte0;
            }

            int l1 = 0;
            for (int j1 = 0; j1 < bytesInLength; j1++) {
                buffer[j1] ^= timesealKey[l1];
                l1 = (l1 + 1) % timesealKey.length;
            }

            for (int k1 = 0; k1 < bytesInLength; k1++) {
                buffer[k1] -= 32;
            }

            buffer[bytesInLength++] = -128;
            buffer[bytesInLength++] = 10;
            return bytesInLength;
        }
    }

    private class CryptInputStream extends InputStream {
        final byte[] internalBuffer = new byte[40000];

        @Override
        public int read() throws IOException {
            return TimesealSocket.this.getInputStream().read();
        }

        @Override
        public int read(byte[] buffer) throws IOException {
            return read(buffer, 0, buffer.length);
        }

        @Override
        public int read(byte[] buffer, int offset, int maxLength) throws IOException {
            int readLen = TimesealSocket.super.getInputStream().read(internalBuffer);
            if(readLen > 0) {
                byte[] buf = handleTimeseal(internalBuffer, readLen);
                readLen = buf.length;
                if (readLen > maxLength) {
                    readLen = maxLength;
                }
                System.arraycopy(buf, 0, buffer, offset, readLen);
            }
            return readLen;
        }

        /**
         * Handles sending the timeseal ack.
         */
        private byte[] handleTimeseal(byte[] internalBuffer, int readLen) throws IOException {
            // optimize?
            String result = new String(internalBuffer, 0, readLen);
            while (result.contains("[G]\0")) {
                /**
                 * You have to ack each [G]\0! This was the major timeseal bug. Not
                 * all were acked!
                 */
                sendAck();
                result = result.replaceFirst("\\[G]\0", "");
            }
            return result.getBytes();
        }

    }
}
