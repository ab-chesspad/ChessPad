package com.ab.pgn.dgtboard;

import java.io.*;

/**
 * todo: check on Ubuntu
 * Created by Alexander Bootman on 1/7/19.
 */
public class DgtBoardInterface {
    private static int REPEAT_COMMAND_AFTER_MSEC = 500;
    protected FileInputStream inputStream;
    protected FileOutputStream outputStream;

    public void open(String ttyPort) throws IOException {
        outputStream = new FileOutputStream(new File(ttyPort));
        inputStream = new FileInputStream(new File(ttyPort));
    }

    public void close() {
        try {
            Thread.sleep(10);
        } catch(InterruptedException e){ /* ignore */ }

        try {
            inputStream.close();
        } catch(Exception e){ /* ignore */ }

        try {
            outputStream.close();
        } catch(Exception e){ /* ignore */ }

        inputStream = null;
        outputStream = null;
    }

    public void write(byte command) throws IOException {
        if (outputStream == null) {
            return;   // writing before opening DgtBoardInterface
        }
        byte[] data = new byte[1];
        data[0] = command;
        try{
            Thread.sleep(100);
        } catch(InterruptedException e){ /* ignore */ }
        if (outputStream == null) {
            return;   // writing after closing DgtBoardInterface
        }
        outputStream.write(data);
    }

    public int read(byte[] buffer, int offset, int length) throws IOException {
        if(inputStream == null) {
            return 0;   // reading before opening DgtBoardInterface
        }
        if(buffer.length < 3) {
            return 0;   // buffer too short
        }

        int readCount = 0;
        while(readCount == 0) {
            try {
                Thread.sleep(50);
            }
            catch (InterruptedException e) {
                // ignore
            }
            readCount = inputStream.read(buffer, offset, length);
        }
        return readCount;
    }

}
