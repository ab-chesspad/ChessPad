package com.ab.pgn;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

/**
 * unit tests
 * Created by Alexander Bootman on 8/14/16.
 */
public class PackTest {

    @Test
    public void testInit() throws Config.PGNException {
        Board board = new Board();
        Pack pack = new Pack(board);
        Assert.assertEquals(pack.getNumberOfPieces(), 30);
        Board clone = pack.unpack();
        Assert.assertEquals(board.toFEN(), clone.toFEN());
    }

    @Test
    public void testEmpty() throws Config.PGNException {
        Board board = new Board();
        board.toEmpty();
        // Pack requires both kings to be on the board
        board.setPiece(0, 0, Config.WHITE_KING);
        board.setPiece(7, 0, Config.BLACK_KING);
        Pack pack = new Pack(board);
        Assert.assertEquals(pack.getNumberOfPieces(), 0);
        Board clone = pack.unpack();
        Assert.assertEquals(board.toFEN(), clone.toFEN());
    }

    @Test
    public void testFEN() throws Config.PGNException {
        String[] fens = {"r1bq1rk1/4bppp/p1n2n2/1pppp3/4P3/2PP1N2/PPB2PPP/R1BQRNK1 w - - 0 1",
                "4k3/2R5/8/8/1KR3r1/8/7n/8 w - - 0 1",
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 0 1",
                "r1b2rk1/pp1p1pp1/1b1p2B1/n1qQ2p1/8/5N2/P3RPPP/4R1K1 w - - 0 1",
                "2r1nr1k/pp1q1p1p/3bpp2/5P2/1P1Q4/P3P3/1B3P1P/R3K1R1 w Q - 0 1",
                "rnbqkbn1/ppppp3/7r/6pp/3P1p2/3BP1B1/PPP2PPP/RN1QK1NR w KQq - 0 1",
                "r2q1r2/pp2np2/1bp4p/3p2pk/1P1N2b1/2PB2B1/P5PP/R2QK2R w KQ - 0 1",
                "6R1/p4p2/1p2q2p/8/6Pk/8/PP2r1PK/3Q4 w - - 0 1",
        };
        for (String fen : fens) {
            int i = fen.indexOf(" ");
            String onlyLetters = fen.substring(0, i).replaceAll("[^\\p{L}]", "");
            int pieces = onlyLetters.length() - 2;  // without kings
            Board board = new Board(fen);
            Pack pack = new Pack(board);
            Assert.assertEquals(pieces, pack.getNumberOfPieces());
            Board clone = pack.unpack();
            Assert.assertEquals(fen, clone.toFEN());
            Assert.assertTrue(new Pack(board).equals(new Pack(clone)));
        }
    }

    @Test
    public void testPack() throws Config.PGNException, IOException {
        String fen = "r1bq1rk1/4bppp/p1n2n2/1pppp3/4P3/2PP1N2/PPB2PPP/R1BQRNK1 w - - 0 1";
        Board board = new Board(fen);

        BitStream.Writer writer = new BitStream.Writer();
        int i = 25;
        writer.write(i, 5);
        Pack.pack(board, writer);

        byte[] buf = writer.getBits();
        BitStream.Reader reader = new BitStream.Reader(buf);
        int j = reader.read(5);
        Assert.assertEquals(i, j);

        Board copy = Pack.unpack(reader);
        String fenCopy = copy.toFEN();
        Assert.assertEquals(fen, fenCopy);
    }
}
