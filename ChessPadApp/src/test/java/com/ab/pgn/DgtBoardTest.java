package com.ab.pgn;

import org.junit.BeforeClass;

/**
 *
 * Created by Alexander Bootman on 3/11/18.
 */
public class DgtBoardTest {

    public static boolean skipDgtBoardTests = false;
//    public static final String DGT_BOARD_PORT = System.getenv(DgtBoardConnector.DGT_BOARD_LIB_HOME);
//    public static final String dgtBoardHome = System.getenv(DgtBoardConnector.DGT_BOARD_LIB_HOME);


     @BeforeClass
    public static void beforeClass() {
//         if(dgtBoardHome == null) {
//             System.out.println(String.format("%s env variable is not set, all DgtBoardConnector tests skipped", DgtBoardConnector.DGT_BOARD_LIB_HOME));
//         }
    }

/*
    @Test
    public void testPosition() throws Config.PGNException {
        if(dgtBoardHome == null) {
            return;
        }
        System.out.println("testPosition");
        DgtBoardConnector dgtBoard = new DgtBoardConnector();
        dgtBoard.open();
        dgtBoard.write(DgtBoardConnector.DGT_SEND_BRD);
        byte[] buffer = new byte[256];
        int len = dgtBoard.read(buffer, 5000);
        System.out.println(String.format("read %s bytes", len));
        System.out.println(String.format("%s", DgtBoardConnector.bytesToHex(buffer, len)));
    }

    @Test
    @Ignore
    public void testPosition1() throws Config.PGNException {
        if(dgtBoardHome == null) {
            return;
        }
        System.out.println("testPosition");
        DgtBoardConnector dgtBoard = new DgtBoardConnector();
        dgtBoard.open();
    }
*/
}
