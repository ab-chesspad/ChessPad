/*
     Copyright (C) 2021	Alexander Bootman, alexbootman@gmail.com

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

 * Created by Alexander Bootman on 3/18/18.
*/
package com.ab.pgn.dgtboard;

import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.PgnLogger;
import com.ab.pgn.Square;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

class DgtBoardWatcher {
    private static final boolean DEBUG = false;
    private static final byte BOARD_MESSAGE_ERROR = 0x0;
    private static final byte GENERATED_CHUNK = 0x0;
    private static final byte[] WITH_LENGTH = {
        DgtBoardProtocol.DGT_MSG_BOARD_DUMP,
        DgtBoardProtocol.DGT_MSG_FIELD_UPDATE,
        DgtBoardProtocol.DGT_MSG_LOG_MOVES,
        DgtBoardProtocol.DGT_MSG_BUSADDRESS,
        DgtBoardProtocol.DGT_MSG_SERIALNR,
        DgtBoardProtocol.DGT_MSG_TRADEMARK,
        DgtBoardProtocol.DGT_MSG_VERSION,
        DgtBoardProtocol.DGT_MSG_SBI_TIME,
    };
    private static final byte MSG_BIT = Config.DGT_BOARD_MESSAGE_BIT;

    private static final boolean[] COMMANDS_WITH_LENGTH = new boolean[128];
    static {
        for (byte b : WITH_LENGTH) {
            COMMANDS_WITH_LENGTH[b & Config.DGT_BOARD_MESSAGE_MASK] = true;
        }
    }

    private final PgnLogger logger = PgnLogger.getLogger(this.getClass());
    private final DgtBoardIO dgtBoardIO;
    private final BoardMessageConsumer boardMessageConsumer;
    private ReadThread readThread;
    private WriteThread writeThread;
    private boolean passMessages;

    DgtBoardWatcher(DgtBoardIO dgtBoardIO, BoardMessageConsumer boardMessageConsumer) {
        logger.setIncludeTimeStamp(true);
        this.dgtBoardIO = dgtBoardIO;
        this.boardMessageConsumer = boardMessageConsumer;
        logger.debug(String.format("DgtBoardWatcher: dgtBoardIO %s", dgtBoardIO));
    }

    public void start() {
        logger.debug("start");
        if(readThread == null) {
            readThread = new ReadThread(boardMessageConsumer);
        }
        passMessages = true;
    }

    void stop() {
        logger.debug("stop");
        passMessages = false;
    }

    void finish() {
        stop();
        if(readThread != null) {
            readThread.finish();
        }
        readThread = null;
    }

    void dumpBoardLoopStart(int timeout) {
        if(readThread != null && writeThread == null) {
            writeThread = new WriteThread(DgtBoardProtocol.DGT_SEND_BRD, timeout);
        }
    }

    void dumpBoardLoopStop() {
        if(writeThread != null) {
            writeThread.finish();
            writeThread = null;
        }
    }

    public void requestBoardDump() throws IOException {
        dgtBoardIO.write(DgtBoardProtocol.DGT_SEND_BRD);
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    private static String bytesToHex(byte[] bytes, int offset, int length) {
        if (length == -1) {
            length = bytes.length - offset;
        }
        if (length == 0) {
            return "";
        }
        char[] hexChars = new char[length * 2];
        for (int j = 0; j < length; j++) {
            int v = bytes[offset + j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private static String bytesToString(byte[] bytes, int offset, int length) {
        if (length == -1) {
            length = bytes.length - offset;
        }
        if (length == 0) {
            return "";
        }
        char[] chars = new char[length * 2];
        for (int j = 0; j < length; j++) {
            int v = bytes[offset + j] & 0xFF;
            if (v >= 0x20 && v <= 0x7e) {
                chars[j] = (char) v;
            } else {
                chars[j] = '.';
            }
        }
        return new String(chars);
    }

    // Dgt board protocol:
    private static Square dgt2Square(byte dgtByte) {
        int x = dgtByte % 8;
        int y = 7 - dgtByte / 8;
        return new Square(x, y);
    }

    // Dgt board protocol:
    private static int dgt2Piece(byte _dgtByte) {
        final int[] map = {Config.EMPTY, Config.WHITE_PAWN, Config.WHITE_ROOK, Config.WHITE_KNIGHT, Config.WHITE_BISHOP, Config.WHITE_KING, Config.WHITE_QUEEN,
                Config.BLACK_PAWN, Config.BLACK_ROOK, Config.BLACK_KNIGHT, Config.BLACK_BISHOP, Config.BLACK_KING, Config.BLACK_QUEEN };
        int dgtByte = _dgtByte & 0x0ff;
        if(dgtByte >= map.length) {
            return Config.EMPTY;        // strange DGT eBoard bug
        }
        return map[dgtByte];
    }

    // async usb reader
    class ReadThread extends Thread {
        private volatile boolean keepRunning;
        private final BoardMessageConsumer boardMessageConsumer;
        private final byte[] readBuffer = new byte[4096];

        ReadThread(BoardMessageConsumer boardMessageConsumer) {
            this.boardMessageConsumer = boardMessageConsumer;
            this.start();
            this.setPriority(Thread.MAX_PRIORITY);
        }

        @Override
        public void run() {
            logger.debug("ReadThread started!");
            keepRunning = true;
            int offset = 0;
            while (keepRunning) {
                int timeout = 1000;
                if(passMessages) {
                    timeout = 100;
                }
                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {
                    // ignore
                }
                int readCount;
                try {
                    String tName = Thread.currentThread().getName();
                    if(DEBUG) {
                        logger.debug(String.format("%s: hanging on read", tName));
                    }
                    readCount = dgtBoardIO.read(readBuffer, offset, readBuffer.length - offset);
                    if(readCount == -1) {
                        throw new IOException("Read length=-1");
                    }
                    if(DEBUG) {
                        logger.debug(String.format(Locale.getDefault(), "%s: read %d bytes %s", tName, readCount, bytesToHex(readBuffer, 0, offset + readCount)));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    continue;
                }
                if (readCount == 0) {
                    continue;
                }
                offset = cutMessages(readBuffer, offset + readCount, boardMessageConsumer);
            }
            keepRunning = false;
            boardMessageConsumer.consume(new BoardMessageInfo("ReadThread stopped!"));
            logger.debug("ReadThread stopped!");
            DgtBoardWatcher.this.readThread = null;
        }

        int cutMessages(byte[] readBuffer, int length, BoardMessageConsumer boardMessageConsumer) {
            int expectedLength;
            do {
                byte msgId = readBuffer[0];
                boolean lenghtExpected = false;
                if ((msgId & MSG_BIT) != 0) {
                    byte _msgId = (byte) (msgId & ~MSG_BIT);
                    lenghtExpected = COMMANDS_WITH_LENGTH[_msgId];
                }
                if (lenghtExpected) {
                    if (length < 3) {
                        return length;
                    }
                    if(msgId == (byte)0x90 && readBuffer[1] == (byte)0x81) {
                        expectedLength = 10;        // todo: find details of this undocumented message 908100080000B5000891
                    } else {
                        expectedLength = (readBuffer[1] << 7) + readBuffer[2];
                    }
                } else {
                    expectedLength = length;
                }
                if (length < expectedLength) {
                    return length;
                }
                if(expectedLength <= 0) {
                    expectedLength = length;    // another undocumented message?
                }
                if(passMessages) {
                    boardMessageConsumer.consume(BoardMessage.createMessage(readBuffer, expectedLength));
                }

                // shift readBuffer:
                length -= expectedLength;
                System.arraycopy(readBuffer, expectedLength, readBuffer, 0, length);
                expectedLength = (readBuffer[1] << 7) + readBuffer[2];
            } while (length > 0 && length >= expectedLength);
            return length;
        }

        void finish() {
            keepRunning = false;
            try {
                // trigger read finish
                dgtBoardIO.write(DgtBoardProtocol.DGT_MSG_SERIALNR);
                logger.debug("sent DgtBoardProtocol.DGT_MSG_SERIALNR");
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // ignore
            }
            logger.debug("ReadThread.finish()");
            DgtBoardWatcher.this.readThread = null;
        }
    }

    public interface BoardMessageConsumer {
        void consume(BoardMessage boardMessage);
    }

    public static class BoardMessage {
        protected final PgnLogger logger = PgnLogger.getLogger(this.getClass());
        private final byte[] buffer;

        static BoardMessage createMessage(byte[] buffer, int length) {
            switch (buffer[0]) {
                case DgtBoardProtocol.DGT_MSG_SERIALNR:
                case DgtBoardProtocol.DGT_MSG_TRADEMARK:
                    return new BoardMessageInfo(buffer, length);

                case DgtBoardProtocol.DGT_MSG_FIELD_UPDATE:
                    return new BoardMessageMoveChunk(buffer, length);

                case DgtBoardProtocol.DGT_MSG_BOARD_DUMP:
                    return new BoardMessagePosition(buffer, length);

                default:
                    String text = bytesToHex(buffer, 0, length);
                    return new BoardMessageInfo(text);
            }
        }

        private BoardMessage(byte[] buffer, int length) {
            this.buffer = new byte[length];
            System.arraycopy(buffer, 0, this.buffer, 0, length);
            if(DEBUG) {
                logger.debug(String.format("BoardMessage 0x%s", Integer.toHexString(this.buffer[0] & 0x0ff)));
            }
        }

        byte getMsgId() {
            return buffer[0];

        }

        public boolean equals(BoardMessage that) {
            if (this == that)
                return true;
            if (that == null)
                return false;
            return Arrays.equals(this.buffer, that.buffer);
        }
    }

    public static class BoardMessageInfo extends BoardMessage {
        public final String text;

        private BoardMessageInfo(String text) {
            super(new byte[] {BOARD_MESSAGE_ERROR}, 1);
            this.text = text;
        }

        private BoardMessageInfo(byte[] buffer, int length) {
            super(buffer, length);
            this.text = bytesToString(buffer, 3, length - 3);
        }
    }

    public static class BoardMessageMoveChunk extends BoardMessage {
        public final Square square;
        public final int piece;

        private BoardMessageMoveChunk(byte[] buffer, int length) {
            super(buffer, length);
            this.square = dgt2Square(buffer[3]);
            this.piece = dgt2Piece(buffer[4]);
        }

        BoardMessageMoveChunk(int x, int y, int piece) {
            super(new byte[] {GENERATED_CHUNK}, 1);
            this.square = new Square(x, y);
            this.piece = piece;
        }

        @Override
        public String toString() {
            String res = "";
            if(piece == Config.EMPTY) {
                res = "x";
            } else {
                res += Config.FEN_PIECES.charAt(piece);
            }
            return res + square.toString();
        }

        @Override
        public int hashCode() {
            return ((piece * 8) + square.getY() * 8) + square.getX();
        }

        // probably not needed because BoardMessageMoveChunk is not used in HashMap
        @Override
        public boolean equals(Object obj) {
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            return equals((BoardMessageMoveChunk)obj);
        }

        public boolean equals(BoardMessageMoveChunk that) {
            if (this == that)
                return true;
            if (that == null)
                return false;
            return this.hashCode() == that.hashCode();
        }
    }

    public static class BoardMessagePosition extends BoardMessage {
        public final Board board = new Board();

        private BoardMessagePosition(byte[] buffer, int length) {
            super(buffer, length);
            for (int i = 3; i < length; ++i) {
                int piece = dgt2Piece(buffer[i]);
                Square sq = dgt2Square((byte) (i - 3));
                board.setPiece(sq, piece);
            }
            if(DEBUG) {
                logger.debug(String.format("BoardMessagePosition %s", board.toFEN()));
            }
        }
    }

    // async usb writer
    private class WriteThread extends Thread {
        private boolean keepRunning = true;
        private final byte command;
        private final int timeout;

        WriteThread(int command, int timeout) {
            this.command = (byte)command;
            this.timeout = timeout;
            this.start();
        }

        @Override
        public void run() {
            while (keepRunning) {
                try {
                    Thread.sleep(timeout);
                } catch (InterruptedException e) {
                    // ignore
                }
                try {
                    if(DEBUG) {
                        logger.debug(String.format("write 0x%s", Integer.toHexString(command & 0xFF)));
                    }
                    if(dgtBoardIO == null) {
                        DgtBoardWatcher.this.dumpBoardLoopStop();
                        break;
                    }
                    dgtBoardIO.write(command);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;  // ?
                }
            }
        }

        void finish() {
            this.interrupt();   // interrupt read
            keepRunning = false;
        }
    }
}
