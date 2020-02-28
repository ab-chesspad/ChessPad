package com.ab.pgn;

import org.junit.Assert;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.startsWith;

/**
 * unit tests
 * Created by Alexander Bootman on 8/14/16.
 */
public class PackTest extends BaseTest {

    @Test
    public void testInit() throws Config.PGNException {
        Board board = new Board();
        Pack pack = new Pack(board.pack());
        Assert.assertEquals(30, pack.getNumberOfPieces());
        Board clone = Board.unpack(pack.getPackData());
        Assert.assertEquals(board.toFEN(), clone.toFEN());
    }

    @Test
    public void testEmpty() throws Config.PGNException {
        Board board = new Board();
        board.toEmpty();
        // Pack requires both kings to be on the board
        board.setPiece(0, 0, Config.WHITE_KING);
        board.setPiece(7, 0, Config.BLACK_KING);
        Pack pack = new Pack(board.pack());
        Assert.assertEquals(pack.getNumberOfPieces(), 0);
        Board clone = Board.unpack(pack.getPackData());
        Assert.assertEquals(board.toFEN(), clone.toFEN());
    }

    @Test
    public void testFEN() throws Config.PGNException {
        String[] fens = {
            "r1bq1rk1/4bppp/p1n2n2/1pppp3/4P3/2PP1N2/PPB2PPP/R1BQRNK1 w - - 0 1",
            "4k3/2R5/8/8/1KR3r1/8/7n/8 w - - 0 1",
            "rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
            "rnbqkbnr/pp1ppppp/8/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 0 1",
            "r1b2rk1/pp1p1pp1/1b1p2B1/n1qQ2p1/8/5N2/P3RPPP/4R1K1 w - - 0 1",
            "2r1nr1k/pp1q1p1p/3bpp2/5P2/1P1Q4/P3P3/1B3P1P/R3K1R1 w Q - 0 1",
            "rnbqkbn1/ppppp3/7r/6pp/3P1p2/3BP1B1/PPP2PPP/RN1QK1NR w KQq - 0 1",
            "r2q1r2/pp2np2/1bp4p/3p2pk/1P1N2b1/2PB2B1/P5PP/R2QK2R w KQ - 0 1",
            "6R1/p4p2/1p2q2p/8/6Pk/8/PP2r1PK/3Q4 w - - 0 1",
        };
        Map<Pack, Board> positions = new HashMap<>();
        for (String fen : fens) {
            int i = fen.indexOf(" ");
            String onlyLetters = fen.substring(0, i).replaceAll("[^\\p{L}]", "");
            int pieces = onlyLetters.length() - 2;  // without kings
            Board board = new Board(fen);
            Pack pack = new Pack(board.pack());
            positions.put(pack, board);
            Board clone = Board.unpack(pack.getPackData());
            Pack clonePack = new Pack(clone.pack());
            Assert.assertEquals(fen, clone.toFEN());
            Assert.assertEquals(pack, clonePack);
            Board fromMap = positions.get(clonePack);
            Assert.assertEquals(board, fromMap);
        }
    }

    @Test
    public void testEquality() {
        Pack p1 = new Pack(new int[] {0x20CD8F, 0xE1A71018, 0x6F040F0C, 0x73DCD70F, 0x7BF9BE6F, 0x12D});
        Pack p2 = new Pack(new int[] {0x20CD8F, 0xE1A71018, 0x6F040F04, 0x73DCD70F, 0x7BF9BE6F, 0x12D});
        Pack p3 = new Pack(new int[] {0x20CD8F, 0xE1A71018, 0x6F040F08, 0x73DCD70F, 0x7BF9BE6F, 0x12D});
        Assert.assertTrue(p1.equalPosition(p2));
        Assert.assertTrue(p1.equalPosition(p3));
    }

    @Test
    public void testPositionEquality() throws Config.PGNException {
        String[] fens = {
            "8/pp3p1k/2p2q1p/3r1P2/5R2/7P/P1P1QP2/7K b - - 2 30",
            "8/pp3p1k/2p2q1p/3r1P2/5R2/7P/P1P1QP2/7K b - - 6 32",
        };
        Board b0 = new Board(fens[0]);
        Pack p0 = new Pack(b0.pack());
        Board b1 = new Board(fens[1]);
        Pack p1 = new Pack(b1.pack());
        Assert.assertTrue(String.format("\"%s\" != \"%s\"", fens[0], fens[1]), p1.equalPosition(p0));
    }

    @Test
    public void testPack() throws Config.PGNException, IOException {
        String fen = "r1bq1rk1/4bppp/p1n2n2/1pppp3/4P3/2PP1N2/PPB2PPP/R1BQRNK1 w - - 0 1";
        Board board = new Board(fen);

        BitStream.Writer writer = new BitStream.Writer();
        int i = 25;
        writer.write(i, 5);
        board.pack(writer);

        byte[] buf = writer.getBits();
        BitStream.Reader reader = new BitStream.Reader(buf);
        int j = reader.read(5);
        Assert.assertEquals(i, j);

        Board copy = Board.unpack(reader);
        String fenCopy = copy.toFEN();
        Assert.assertEquals(fen, fenCopy);
        int[] pack = board.pack();
        Board b = Board.unpack(pack);
        fenCopy = b.toFEN();
        Assert.assertEquals(fen, fenCopy);
    }

    @Test
    public void testInvalidPack() throws Config.PGNException {
        String fen = "rnbqkbnr/pppppppp/8/q/8/8/PPPPPPPP/RNBQKBNR w - - 0 1";
        Board invalid = new Board(fen);
        expectedEx.expect(Config.PGNException.class);
        expectedEx.expectMessage(startsWith("Invalid position to pack:"));
        expectedEx.expectMessage("Invalid position to pack:");
        int[] pack = invalid.pack();
    }

    @Test
    public void testUtil() throws IOException {
        final String TEST_FILE_NAME = "xx.cpbmp";
        int[] values = {1, 0x0ff, 0x07ee, 0xab0000, 0xabcdef, 0xffffff00};
        for(int v : values) {
            FileOutputStream fos = new FileOutputStream(TEST_TMP_ROOT + TEST_FILE_NAME);
            Util.writeInt(fos, v);
            fos.close();
            FileInputStream fis = new FileInputStream(TEST_TMP_ROOT + TEST_FILE_NAME);
            int val = Util.readInt(fis);
            Assert.assertEquals(String.format("written 0x%04x != read 0x%04x", v, val), v, val);
        }
    }

}
