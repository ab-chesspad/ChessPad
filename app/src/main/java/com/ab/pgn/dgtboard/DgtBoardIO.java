package com.ab.pgn.dgtboard;

import java.io.IOException;

/**
 *
 * Created by Alexander Bootman on 2/21/19.
 */
public abstract class DgtBoardIO {
    public abstract void write(byte command) throws IOException;
    public abstract int read(byte[] buffer, int offset, int length) throws IOException;

    protected void init() throws IOException {
        write(DgtBoardProtocol.DGT_SEND_TRADEMARK);
        write(DgtBoardProtocol.DGT_SEND_UPDATE_BRD);
        write(DgtBoardProtocol.DGT_SEND_BRD);
    }

}
