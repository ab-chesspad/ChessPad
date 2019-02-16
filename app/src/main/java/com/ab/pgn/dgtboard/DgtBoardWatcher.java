package com.ab.pgn.dgtboard;

import java.io.IOException;
import java.util.Arrays;

import com.ab.pgn.*;


/**
 *
 * Created by Alexander Bootman on 3/18/18.
 */
public class DgtBoardWatcher {
    public static boolean DEBUG = true;
    public static final byte BOARD_MESSAGE_ERROR = 0x0;
    public static final byte GENERATED_CHUNK = 0x0;
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

    private static final boolean[] COMMANDS_WITH_LENGTH = new boolean[128];
    static {
        for (byte b : WITH_LENGTH) {
            COMMANDS_WITH_LENGTH[b & DgtBoardProtocol.MESSAGE_MASK] = true;
        }
    }

    private final PgnLogger logger = PgnLogger.getLogger(this.getClass());
    private DgtBoardInterface dgtBoardInterface;
    private BoardMessageConsumer boardMessageConsumer;
    private ReadThread readThread;
    private WriteThread writeThread;
    private boolean passMessages;

    public DgtBoardWatcher(DgtBoardInterface dgtBoardInterface, BoardMessageConsumer boardMessageConsumer) {
        this.dgtBoardInterface = dgtBoardInterface;
        this.boardMessageConsumer = boardMessageConsumer;
        logger.debug(String.format("DgtBoardWatcher: dgtBoardInterface %s", dgtBoardInterface));
    }

    public void start() throws IOException {
        logger.debug("start");
        if(readThread == null) {
            readThread = new ReadThread(boardMessageConsumer);
        }
        passMessages = true;
    }

    public void stop() {
        logger.debug("stop");
        passMessages = false;
    }

    public void dumpBoardLoopStart(int timeout) {
        if(readThread != null && writeThread == null) {
            writeThread = new WriteThread(DgtBoardProtocol.DGT_SEND_BRD, timeout);
        }
    }

    public void dumpBoardLoopStop() {
        if(writeThread != null) {
            writeThread.finish();
            writeThread = null;
        }
    }

    public void requestBoardDump() throws IOException {
        dgtBoardInterface.write(DgtBoardProtocol.DGT_SEND_BRD);
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes, int offset, int length) {
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
        String res = new String(hexChars);
        return res;
    }

    public static String bytesToString(byte[] bytes, int offset, int length) {
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
//        if(boardIsInverted) {
//            x = 7 - x;
//            y = 7 - y;
//        }
        return new Square(x, y);
    }

    // Dgt board protocol:
    private static int dgt2Piece(byte dgtByte) {
        final int[] map = {Config.EMPTY, Config.WHITE_PAWN, Config.WHITE_ROOK, Config.WHITE_KNIGHT, Config.WHITE_BISHOP, Config.WHITE_KING, Config.WHITE_QUEEN,
                Config.BLACK_PAWN, Config.BLACK_ROOK, Config.BLACK_KNIGHT, Config.BLACK_BISHOP, Config.BLACK_KING, Config.BLACK_QUEEN };
        return map[dgtByte];
    }

    // async usb reader
    class ReadThread extends Thread {
        private final byte MSG_BIT = DgtBoardProtocol.MESSAGE_BIT;
        private volatile boolean keepRunning;
        private BoardMessageConsumer boardMessageConsumer;
        private byte[] readBuffer = new byte[4096];

        ReadThread(BoardMessageConsumer boardMessageConsumer) {
            this.boardMessageConsumer = boardMessageConsumer;
            this.start();
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
                }
                int readCount = 0;
                try {
                    String tName = Thread.currentThread().getName();
                    logger.debug(String.format("%s: hanging on read", tName));
                    readCount = dgtBoardInterface.read(readBuffer, offset, readBuffer.length - offset);
                    logger.debug(String.format("%s: read %d bytes %s", tName, readCount, bytesToHex(readBuffer, 0, offset + readCount)));
                    if(readCount == -1) {
                        throw new IOException("Read length=-1");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
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
                if(expectedLength <= 0 || expectedLength > length) {
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

        public void finish() {
            keepRunning = false;
            try {
                // trigger read finish
                dgtBoardInterface.write(DgtBoardProtocol.DGT_MSG_SERIALNR);
                logger.debug("sent DgtBoardProtocol.DGT_MSG_SERIALNR");
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
            logger.debug(String.format("ReadThread.finish()"));
            DgtBoardWatcher.this.readThread = null;
        }
    }

    public interface BoardMessageConsumer {
        void consume(BoardMessage boardMessage);
    }

    public static class BoardMessage {
        private final PgnLogger logger = PgnLogger.getLogger(this.getClass());
        private final byte[] buffer;

        public static BoardMessage createMessage(byte[] buffer, int length) {
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
            logger.debug(String.format("BoardMessage 0x%s", Integer.toHexString(this.buffer[0] & 0x0ff)));
        }

        public byte getMsgId() {
            return buffer[0];
        }

        public boolean equals(BoardMessage that) {
            if(that == null) {
                return false;
            }
            return Arrays.equals(this.buffer, that.buffer);
        }
    }

    public static class BoardMessageInfo extends BoardMessage {
        public String text;

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
        public Square square;
        public int piece;

        private BoardMessageMoveChunk(byte[] buffer, int length) {
            super(buffer, length);
            this.square = dgt2Square(buffer[3]);
            this.piece = dgt2Piece(buffer[4]);
        }

        public BoardMessageMoveChunk(int x, int y, int piece) {
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

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            return this.hashCode() == ((BoardMessageMoveChunk)obj).hashCode();
        }
    }

    public static class BoardMessagePosition extends BoardMessage {
        public Board board = new Board();

        private BoardMessagePosition(byte[] buffer, int length) {
            super(buffer, length);
            for (int i = 3; i < length; ++i) {
                int piece = dgt2Piece(buffer[i]);
                Square sq = dgt2Square((byte) (i - 3));
                board.setPiece(sq, piece);
            }
        }
    }

    // async usb writer
    private class WriteThread extends Thread {
        private boolean keepRunning = true;
        private byte command;
        private int timeout;

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
                }
                try {
                    logger.debug(String.format("write 0x%s", Integer.toHexString(command & 0xFF)));
                    if(dgtBoardInterface == null) {
                        DgtBoardWatcher.this.dumpBoardLoopStop();
                        break;
                    }
                    dgtBoardInterface.write(command);
                } catch (IOException e) {
                    e.printStackTrace();
                    break;  // ?
                }
            }
        }

        public void finish() {
            this.interrupt();   // interrupt read
            keepRunning = false;
        }
    }
}
