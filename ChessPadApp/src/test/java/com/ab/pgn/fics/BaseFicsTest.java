package com.ab.pgn.fics;

import org.junit.After;

import java.util.Date;

/**
 * Created by Alexander Bootman on 10/6/19.
 */
class BaseFicsTest {
    private static final String
        USER = "guest",
        PASSWORD = "",
    dummy_str = null;

    FicsPad ficsPad;
    volatile boolean testDone = false;
    volatile boolean allSend = false;

    void openFicsPad(FicsPad.InboundMessageConsumer inboundMessageConsumer) {
        ficsPad = new FicsPad(USER, PASSWORD, inboundMessageConsumer);
        while (!ficsPad.isConnected()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    @After
    public void closeFicsPad() {
        if(ficsPad != null) {
            ficsPad.close();
        }
    }

    void send(String command, String ... params) {
        ficsPad.send(command, params);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    void waitUntilDone(int maxWaitMsec) {
        Date end = new Date(new Date().getTime() + maxWaitMsec);
        while(!testDone) {
            if(end.compareTo(new Date()) < 0) {
                throw new RuntimeException("Wait ended unsuccessfully");
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        System.out.println("Done");
    }
}
