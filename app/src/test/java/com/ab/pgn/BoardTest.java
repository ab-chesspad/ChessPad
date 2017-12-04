package com.ab.pgn;

import org.junit.Assert;
import org.junit.Test;

/**
 * uint tests
 * Created by Alexander Bootman on 8/7/16.
 */
public class BoardTest extends BaseTest {

    @Test
    public void testInit() {
        Board board = new Board();
        logger.debug(board.toString());
    }

    @Test
    public void testEmpty() {
        Board board = new Board();
        board.toEmpty();
        logger.debug(board.toString());
    }

    @Test
    public void testFEN() throws Config.PGNException{
        String fen = "r1bq1rk1/4bppp/p1n2n2/1pppp3/4P3/2PP1N2/PPB2PPP/R1BQRNK1 w - - 0 1";
        Board board = new Board(fen);
        logger.debug(board.toString());
        Board clone = board.clone();
        logger.debug(clone.toString());
        String resFen = board.toFEN();
        Assert.assertEquals(fen, resFen);
    }

    @Test
    public void testFlags() {
        Board board = new Board();
        int flags = Config.INIT_POSITION_FLAGS;
        board.setFlags(flags);
        Assert.assertEquals(flags, board.getFlags());
        int flags1 = flags;
        flags &= ~(Config.FLAGS_W_QUEEN_OK | Config.FLAGS_W_KING_OK);
        flags1 &= ~flags;
        board.clearFlags(flags);
        Assert.assertEquals(flags1, board.getFlags());

        flags = board.getFlags();
        board.invertFlags(Config.FLAGS_BLACK_MOVE);
        Assert.assertEquals(flags ^ Config.FLAGS_BLACK_MOVE, board.getFlags());

        board.raiseFlags(Config.FLAGS_ENPASSANT_OK);
        Square enpass = new Square(2, 2);
        board.setEnpassant(enpass);
        Assert.assertEquals(enpass.getX(), board.getEnpassantX());
        enpass = new Square(3, 2);
        board.setEnpassant(enpass);
        Assert.assertEquals(enpass.getX(), board.getEnpassantX());
    }

}
