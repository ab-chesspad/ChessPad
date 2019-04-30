package com.ab.pgn;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * unit tests
 * Created by Alexander Bootman on 8/7/16.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({Board.class, BitStream.Reader.class, ByteArrayInputStream.class})
public class BoardTest extends BaseTest {
    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Test
    public void testBoard() {
        int[][] pieces = Board.init;
        Board board = new Board(pieces);
        for(int i = 0; i < pieces.length; ++i) {
            for(int j = 0; j < pieces[i].length; ++j) {
                Assert.assertEquals(String.format("(%s,%s): 0x%02x != 0x%02x", i, j, pieces[j][i], board.getPiece(i, j)),
                        pieces[j][i], board.getPiece(i, j));
            }
        }
    }

    @Test
    public void testEqual() throws Config.PGNException {
        Board board = new Board();
        int[] p = board.pack();
        Assert.assertEquals(0, board.validateSetup());
        board.toEmpty();
        for(int i = 0; i < 8; ++i) {
            for(int j=0; j <8; ++j) {
                Assert.assertEquals(Config.EMPTY, board.getPiece(i, j));
            }
        }
        Assert.assertFalse(board.equals(new Board()));
        String fen = "rnbqkbnr/pppppppp/8/q/8/8/PPPPPPPP/RNBQKBNR w - - 0 1";
        Board invalid = new Board(fen);
        Assert.assertFalse(board.equals(invalid));
        Assert.assertFalse(invalid.equals(board));
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

    @Test
    public void testFEN_invalid_enpassant() throws Config.PGNException{
        String fen = "r1bq1rk1/4bppp/p1n2n2/1pppp3/4P3/2PP1N2/PPB2PPP/R1BQRNK1 w - - 0 1";
        Board board = new Board(fen);
        Assert.assertEquals(0, board.validateSetup());
        Board clone = board.clone();
        String resFen = clone.toFEN();
        Assert.assertEquals(fen, resFen);
        Assert.assertEquals(-1, board.getEnpassantX());
    }

    @Test
    public void testFEN_ex() throws Config.PGNException {
        String fen = "r1bq1rk1/4bppp/p1n2n2/1pppp3/4P3/2PP1N2/PPB2PPP";
        expectedEx.expect(Config.PGNException.class);
        expectedEx.expectMessage(String.format("invalid FEN %s", fen));
        Board board = new Board(fen);
    }

    private List<Board> testFEN(Pair<String, Integer>[] fens, boolean updateResult) throws Config.PGNException {
        List<Board> boards = new LinkedList<>();
        for(Pair<String, Integer> pair : fens) {
            String fen = pair.first;
            Board board = new Board(fen);
            boards.add(board);
            int res = pair.second;
            Assert.assertEquals(String.format("%s\n%s", fen, board.toString()), res, board.validateSetup());
            Board inv = invert(board);
            String invFen = inv.toFEN();
            int invRes = res;
            if(updateResult && res != 0) {
                invRes = res + 1;   // error always for White!
            }
            Assert.assertEquals(String.format("Inverted %s\n%s", invFen, inv.toString()), invRes, inv.validateSetup());
            if (res == 0) {
                Board clone = board.clone();
                String resFen = clone.toFEN();
                Assert.assertEquals(fen, resFen);
            }
        }
        return boards;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFEN_inv() throws Config.PGNException{
        final Pair<String, Integer>[] fens = new Pair[] {
            // error always for White!
            new Pair<>("r1bq1rk1/4bppp/p1n2n2/1pppp3/4P3/2PP1N2/PPB2PPP/R1BQRNK1 w - - 0 1", 0),
            new Pair<>("r1bq1rk1/4bppp/p1n2n2/1pppp3/4P3/2PP1N2/PPB2PPP/R1BQRN2 w - - 0 1", 7),
            new Pair<>("r1bq1rk1/4bppp/p1n2n2/1pppp3/4P3/2PP1N2/PPB2PP1/P1BQRNK1 w - - 0 1", 9),
            new Pair<>("r1bq1rk1/4bppp/p1n2n2/1pppp3/4P3/2PP1N2/PPB1BPPP/R1BQRNK1 w - - 0 1", 15),
            new Pair<>("r1bq1rk1/4bppp/p1n2n2/1pppp3/4P3/2PP1N2/PPB2PPP/R2QRNKK w - - 0 1", 17),
            new Pair<>("r1bq1rk1/4bppp/p1n2n2/1pppp3/8/8/BBBBBBBB/RBBQRNKB w - - 0 1", 17),
            new Pair<>("r1bq1rk1/4bppp/p1n2n2/1pppp3/8/4P3/BBBBBBBB/RBBQRNK1 w - - 0 1", 17),
            new Pair<>("r1bq1rk1/4bppp/p1n2n2/1pppp3/8/8/BBBBBBBB/RBBQRNKR w - - 0 1", 17),
        };
        testFEN(fens, true);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFEN() throws Config.PGNException {
        final Pair<String, Integer>[] fens = new Pair[]{
            new Pair<>("r1bqkbnr/pPp4p/4pp2/3pP3/6p1/5P2/P1PP2PP/RNBQKBNR w KQkq d6 2 1", 11),
            new Pair<>("r1bq1rk1/4bppp/p1n2n2/1pppp3/4P3/2PP1N2/PPB2PPP/R1BQRNK1 w KQkq - 0 1", 13),
            new Pair<>("r1bqkbnr/pPp4p/4pp2/3pP3/6p1/5P2/P1PP2PP/RNBQKBNR w KQkq d6 0 1", 0),
        };
        List<Board> boards = testFEN(fens, false);
        Board board = boards.get(0);
        board.setReversiblePlyNum(0);
        board.setEnpassant(new Square());
        Assert.assertEquals(String.format("Invalid enpass %s\n%s", board.toFEN(), board.toString()), 14, board.validateSetup());
    }

    @Test(expected = Config.PGNException.class)
    public void testPackException() throws IOException, Config.PGNException {
        BitStream.Writer writer = spy(new BitStream.Writer());
        doThrow(IOException.class).when(writer).write(anyInt(), anyInt());
        Board board = new Board();
        board.pack(writer);
        System.out.println("finish");
    }

    @Test(expected = Config.PGNException.class)
    public void testSerializeException() throws IOException, Config.PGNException {
        BitStream.Writer writer = spy(new BitStream.Writer());
        doThrow(IOException.class).when(writer).write(anyInt(), eq(Board.BOARD_COUNTS_PACK_LENGTH));
        Board board = new Board();
        board.serialize(writer);
        System.out.println("finish");
    }

    @Test(expected = Config.PGNException.class)
    public void testConstructorException() throws IOException, Config.PGNException {
        BitStream.Writer writer = new BitStream.Writer();
        Board board = new Board();
        board.pack(writer);
        byte[] buf = writer.getBits();
        BitStream.Reader reader = spy(new BitStream.Reader(buf));
        doThrow(IOException.class).when(reader).read(eq(Board.BOARD_COUNTS_PACK_LENGTH));
        board = new Board(reader);
        System.out.println("finish");
    }

    @Test(expected = Config.PGNException.class)
    public void testUnpackException() throws IOException, Config.PGNException {
        BitStream.Writer writer = new BitStream.Writer();
        Board board = new Board();
        board.pack(writer);
        byte[] buf = writer.getBits();
        BitStream.Reader reader = spy(new BitStream.Reader(buf));
        doThrow(IOException.class).when(reader).read(eq(Board.BOARD_DATA_PACK_LENGTH));
        board = Board.unpack(reader);
        System.out.println("finish");
    }

//* comment for  for Studio 3
    @Test(expected = Config.PGNException.class)
    public void testBoardPackException() throws Exception {
        BitStream.Writer writer = mock(BitStream.Writer.class);
        doThrow(IOException.class).when(writer).getBits();
        PowerMockito.whenNew(BitStream.Writer.class)
                .withAnyArguments().thenReturn(writer);
        Board board = new Board();
        int[] p = board.pack();
        System.out.println("finish");
    }

    @Test(expected = Config.PGNException.class)
    public void testUnpackArrayException() throws Exception {
        BitStream.Writer writer = new BitStream.Writer();
        Board board = new Board();
        board.pack(writer);
        byte[] buf = writer.getBits();
        int[] p = board.pack();
        PowerMockito.whenNew(ByteArrayInputStream.class)
                .withAnyArguments().thenThrow(new IOException(){});
        board = Board.unpack(p);
        System.out.println("finish");
    }
//*/
}
