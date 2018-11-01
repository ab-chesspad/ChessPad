package com.ab.pgn;

/*
Dgt Board API wrapper

command-line utility:

0. build com.ab.pgn package with IntelliJ.
1. export JAVA_HOME=/usr/lib/jvm/java-8-oracle (or something like this).
2. cd ..../pgn
3. javah -cp target/classes -d dgtboard com.ab.pgn.DgtBoard
4. cd dgtboard
5. g++ -fPIC -shared -I$JAVA_HOME/include -I$JAVA_HOME/include/linux -I. -o dgtlib.so dgtlib.cpp
6. sudo adduser alex dialout
7. cd ..
8. java -cp target/classes/ -Ddgt_board_lib_home="./dgtboard/" com.ab.pgn.DgtBoard /dev/ttyUSB0 300

check ports:
dmesg | grep tty

*/

import java.util.Scanner;

public class DgtBoard {
    public static boolean DEBUG = false;
    public static int REPEAT_COMMAND_AFTER_MSEC = 500;   // somehow commands are missing

    public static final String DGT_BOARD_LIB_HOME = "dgt_board_lib_home";
    public static final int READER_BUF_SIZE = 1024;

    // can we define if non-initial position is inverted?
    private static final byte[] INVERTED_INIT_BOARD = {
        2,3,4,5,6,4,3,2,
        1,1,1,1,1,1,1,1,
        0,0,0,0,0,0,0,0,
        0,0,0,0,0,0,0,0,
        0,0,0,0,0,0,0,0,
        0,0,0,0,0,0,0,0,
        7,7,7,7,7,7,7,7,
        8,9,10,11,12,10,9,8};

    private DgtReader dgtReader;
    private int timeout;
    private UpdateListener updateListener;

    private native int _open(String ttyName);
    private native void _close();
    private native void _write(byte command);
    private native int _read(byte[] buffer, int timeout_msec);

    private int tty = -1;               // to use in JNI
    private boolean isInverted = false;

    // android version
    public DgtBoard(int timeout, UpdateListener updateListener) throws Config.PGNException {
        this.timeout = timeout;
        this.updateListener = updateListener;
        // todo: open android port

        dgtReader = new DgtReader();
    }

    // non-android version
    public DgtBoard(String dgtBoardPort, int timeout, UpdateListener updateListener) throws Config.PGNException {
        this.timeout = timeout;
        this.updateListener = updateListener;
        String dgtBoardHome = System.getenv(DgtBoard.DGT_BOARD_LIB_HOME);
        if (dgtBoardHome == null) {
            String msg = String.format("%s env variable is not set", DGT_BOARD_LIB_HOME);
            System.out.println(msg);
            throw new Config.PGNException(msg);
        }
        System.load(String.format("%s/dgtlib.so", dgtBoardHome));
        int tty = _open(dgtBoardPort);
        if (tty == -1) {
            String msg = String.format("%s port failed to open", dgtBoardPort);
            System.out.println(msg);
            throw new Config.PGNException(msg);
        }
        dgtReader = new DgtReader();
    }

    public void closeBoard() {
        dgtReader.stop();
        _close();
    }

    public void sendCommand(int command) {
        _write((byte) command);
        if(REPEAT_COMMAND_AFTER_MSEC > 0) {
            try {
                Thread.sleep(REPEAT_COMMAND_AFTER_MSEC);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            _write((byte) command);
        }
    }

    private Square dgt2Square(byte dgtByte) {
        int x;
        int y;
        if(isInverted) {
            x = 7 - dgtByte % 8;
            y = dgtByte / 8;
        } else {
            x = dgtByte % 8;
            y = 7 - dgtByte / 8;
        }
        return new Square(x, y);
    }

    private static int dgt2Piece(byte dgtByte) {
        final int[] map = {Config.EMPTY, Config.WHITE_PAWN, Config.WHITE_ROOK, Config.WHITE_KNIGHT, Config.WHITE_BISHOP, Config.WHITE_KING, Config.WHITE_QUEEN,
                Config.BLACK_PAWN, Config.BLACK_ROOK, Config.BLACK_KNIGHT, Config.BLACK_BISHOP, Config.BLACK_KING, Config.BLACK_QUEEN };
        return map[dgtByte];
    }

//    private static String dgtPiece2String(byte dgtByte) {
//        final String[] map = {"x", "WHITE_PAWN", "WHITE_ROOK", "WHITE_KNIGHT", "WHITE_BISHOP", "WHITE_KING", "WHITE_QUEEN",
//                "BLACK_PAWN", "BLACK_ROOK", "BLACK_KNIGHT", "BLACK_BISHOP", "BLACK_KING", "BLACK_QUEEN" };
//        return map[dgtByte];
//    }

    public static String piece2String(int piece) {
        final String[] map = {"x", "", "WHITE_KING", "BLACK_KING", "WHITE_QUEEN", "BLACK_QUEEN", "WHITE_BISHOP", "BLACK_BISHOP",
                "WHITE_KNIGHT", "BLACK_KNIGHT", "WHITE_ROOK", "BLACK_ROOK", "WHITE_PAWN", "BLACK_PAWN"};
        return map[piece];
    }

    public void update(byte[] buf, int length) throws Config.PGNException {
        if(updateListener == null) {
            System.err.println(String.format("No listener, lost %s", bytesToHex(buf, length)));
            return;
        }
        if(DEBUG) {
            System.err.print(String.format("DgtBoard.update, %s\n%s\n", new String(buf, 0, length), bytesToHex(buf, length)));
        }
        int msgByte = (int)buf[0] & 0x0ff;
        if((msgByte & DgtBoardProtocol.MESSAGE_BIT) == 0) {
            return;     // ignore, throw exception?
        }
        msgByte &= ~DgtBoardProtocol.MESSAGE_BIT;
        BoardData boardData = null;
        switch (msgByte) {
            case DgtBoardProtocol.DGT_TRADEMARK:
                boardData = new BoardDataTrademark(buf, length);
                break;

            case DgtBoardProtocol.DGT_FIELD_UPDATE:
                boardData = new BoardDataMoveChunk(buf, length);
                break;

            case DgtBoardProtocol.DGT_BOARD_DUMP:
                BoardDataPosition position = new BoardDataPosition(buf, length);
                boardData = position;
                break;
        }

        if (boardData != null) {
            if(DEBUG) {
                System.err.print("DgtBoard.update, calling updateListener");
            }
            updateListener.update(boardData);
        }
    }

/*
    public static void main(String[] args) throws Config.PGNException {
        String ttyPort = args[0];
        int timeout = Integer.valueOf(args[1]);
        System.out.println(String.format("ttyPort=%s, timeout=%s\n", ttyPort, timeout));
        DgtBoardWatcher dgtBoardWatcher = new DgtBoardWatcher(ttyPort, timeout, null);
        System.out.println("*\n* Dgt Board initialization takes time, bear with me...\n*");
        Scanner scanner = new Scanner(System.in);

        while(true) {
            System.out.print(String.format("hex command or 'done': "));
            String _command = scanner.next();
            if ("done".equals(_command)) {
                break;
            }
            int command = Integer.valueOf(_command, 16);
            dgtBoardWatcher.dgtBoard.sendCommand(command);
        }
        dgtBoardWatcher.stop();
    }
*/

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes, int length) {
        if (length == -1) {
	        length = bytes.length;
        }
        if(length == 0) {
            return "";
        }
        char[] hexChars = new char[length * 2];
        for ( int j = 0; j < length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    class DgtReader implements Runnable {
        public volatile boolean stop = false;
        Thread readThread = new Thread(this);
        public int count = 0;

        DgtReader() {
            readThread.start();
        }

        void stop() {
            this.stop = true;
            readThread.interrupt();
        }

        @Override
        public void run() {
            byte[] buf = new byte[READER_BUF_SIZE];

            while(!stop) {
    	        int len = DgtBoard.this._read(buf, DgtBoard.this.timeout);
    	        ++count;
    	        if(DEBUG) {
                    System.err.print(String.format("%s %s bytes", count, len));
                    if(len > 0) {
                        System.err.print(String.format(", %s\n%s\n", new String(buf, 0, len), bytesToHex(buf, len)));
                    }
                    System.err.println();
                }
        	    if(len > 0) {
                    try {
                        DgtBoard.this.update(buf, len);
                    } catch (Config.PGNException e) {
                        e.printStackTrace();    // todo
                    }
                }
                // Sleep for a while
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
            if(DEBUG) {
                System.err.println("dgtReader stopped");
            }
        }

    }

    public abstract class BoardData {
    }

    public class BoardDataPosition extends DgtBoard.BoardData {
        Board board = new Board();

        public BoardDataPosition(byte[] buf, int length) {
            DgtBoard.this.isInverted = true;

            for(int i = 3; i < length; ++i) {
                if(buf[i] != DgtBoard.INVERTED_INIT_BOARD[i - 3]) {
                    DgtBoard.this.isInverted = false;
                }
                int piece = dgt2Piece(buf[i]);
                Square sq = dgt2Square((byte)(i - 3));
                board.setPiece(sq, piece);
            }

            if(DgtBoard.this.isInverted) {
                board = new Board();
            }
        }

        @Override
        public String toString() {
            return board.toString();
        }
    }

    public class BoardDataMoveChunk extends BoardData {
        Square square;
        int piece;

        public BoardDataMoveChunk(byte[] buf, int length) {
            this.square = dgt2Square(buf[3]);
            this.piece = dgt2Piece(buf[4]);
        }

        public BoardDataMoveChunk(int x, int y, int piece) {
            this(new Square(x, y), piece);
        }

        public BoardDataMoveChunk(Square square, int piece) {
            this.square = square;
            this.piece = piece;
        }

        @Override
        public boolean equals(Object that) {
            if (!(that instanceof BoardDataMoveChunk)) {
                return false;
            }
            return square.equals(((BoardDataMoveChunk)that).square) && piece == ((BoardDataMoveChunk)that).piece;
        }

        @Override
        public int hashCode() {
            return square.hashCode() ^ piece;
        }

        @Override
        public String toString() {
            return square.toString() + "-" + piece2String(piece);
        }
    }

    public class BoardDataTrademark extends BoardData {
        String trademark;

        public BoardDataTrademark(byte[] buf, int length) {
            this.trademark = new String(buf, 3, length - 3);
        }

        @Override
        public String toString() {
            return trademark;
        }
    }

    interface UpdateListener {
        void update(BoardData boardData) throws Config.PGNException;
    }
}

