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
}
