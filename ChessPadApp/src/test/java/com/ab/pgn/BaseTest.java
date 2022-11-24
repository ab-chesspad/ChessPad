/*
     Copyright (C) 2021-2022	Alexander Bootman, alexbootman@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

 * base class for all test classes
 * Created by Alexander Bootman on 8/7/16.
 */
package com.ab.pgn;

import com.ab.pgn.io.CpFile;
import com.ab.pgn.io.FilAx;
import com.ab.pgn.io.FilAxImp;

import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaseTest {
    public static final boolean DEBUG = false;
    static final int FLAGS_PROMOTION = 0x010000;
    static final int FLAGS_ENPASSANT = 0x020000;

    static String prefix = "";
    static String bookPath = "etc/test/../../src/main/book/combined.book";
    static {
        // in IntelliJ working dir is <project>, in AndroidStudio it is <project>/app
        File testFile = new File("xyz");
        if (testFile.getAbsoluteFile().getParent().endsWith("/ChessPadApp")) {
            prefix = "../";
            bookPath = "etc/test/../../ChessPadApp/src/main/assets/book/combined.book";
        }
    }
    protected static final String TEST_ROOT = prefix + "etc/test/";
    protected static final String TEST_TMP_ROOT = prefix + "etc/test_tmp/";
    protected static final String BOOK_PATH = bookPath;

    /*
    public static String LOG_FILE_NAME = null;
    /*/
    public static String LOG_FILE_NAME = prefix + "log/cp.log";
    //*/

    static final String MY_TAG = "Final";
    static final int ERR = -1;
    static final int TEST_SERIALIZATION_VERSION = 1;
    protected static String currentRootPath = TEST_ROOT;

    public final PgnLogger logger = PgnLogger.getLogger(this.getClass(), true);

    @Rule
    public final ExpectedException expectedEx = ExpectedException.none();

    @BeforeClass
    public static void init() {
        CpFile.setFilAxProvider(new FilAx.FilAxProvider() {
            @Override
            public FilAx newFilAx(String path) {
                return new FilAxImp(path);
            }

            @Override
            public FilAx newFilAx(FilAx parent, String name) {
                return new FilAxImp(parent, name);
            }

//            @Override
//            public FilAx newFilAx(CpFile parent, String name) {
//                return new FilAxImp(parent, name);
//            }

            @Override
            public String getRootPath() {
                File f = new File(currentRootPath);
                return f.getAbsolutePath();
            }
        });
        FilAxImp.setFilAxProvider(CpFile.getFilAxProvider());
        File tmpTest = new File(TEST_TMP_ROOT);
        deleteDirectory(tmpTest);
        tmpTest.mkdirs();
        Board.DEBUG = DEBUG;
        PgnGraph.DEBUG = DEBUG;
    }

    @After
    public void restore() {
        currentRootPath = TEST_ROOT;
    }

    private static void deleteDirectory(File directory) {
        boolean res;
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (null != files) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        res = file.delete();
                    }
                }
            }
        }
        res = directory.delete();
    }

    void fullCopy(File sourceLocation, File targetLocation) throws IOException {
        if (sourceLocation.isDirectory()) {
            if (!targetLocation.exists()) {
                if (!targetLocation.mkdirs()) {
                    throw new IOException("cannot create " + targetLocation.getAbsolutePath());
                }
            }
            String[] children = sourceLocation.list();
            for (int i = 0; i < sourceLocation.listFiles().length; i++) {
                fullCopy(new File(sourceLocation, children[i]),
                        new File(targetLocation, children[i]));
            }
        } else {
            try (
                    InputStream in = new FileInputStream(sourceLocation);
                    OutputStream out = new FileOutputStream(targetLocation)) {
                // Copy the bits from instream to outstream
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    protected File toTempTest(String origFileName) throws IOException {
        File origFile = new File(TEST_ROOT + origFileName);
        if (!origFile.exists()) {
            return null;
        }
        File testFile = new File(TEST_TMP_ROOT + origFileName);
        File parent = testFile.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            return null;
        }
        fullCopy(origFile, testFile);
        return testFile;
    }

    boolean areEqual(Move m1, Move m2) {
        if (m1 == null && m2 == null) {
            return true;
        } else if (m1 == null || m2 == null) {
            return false;
        }

        if (m1.getGlyph() != m2.getGlyph()) {
            return false;
        }
        if (m1.comment == null || m2.comment == null) {
            if (m1.comment != null || m2.comment != null) {
                return false;
            }
        } else if (!m1.comment.equals(m2.comment)) {
            return false;
        }
        if ((m1.moveFlags & Config.FLAGS_NULL_MOVE) != 0 || (m2.moveFlags & Config.FLAGS_NULL_MOVE) != 0) {
            return (m1.moveFlags & Config.FLAGS_NULL_MOVE) == (m2.moveFlags & Config.FLAGS_NULL_MOVE);
        }
        if ((m1.moveFlags & Config.MOVE_FLAGS) != (m2.moveFlags & Config.MOVE_FLAGS)) {
            return false;
        }
        if (m1.getPiece() != m2.getPiece()) {
            return false;
        }
        return areEqual(m1.variation, m2.variation);
    }

    String finalFen2Tag(String fen) {
        return String.format("[%s \"%s\"]\n", MY_TAG, fen);
    }

    boolean areEqual(PgnGraph g1, PgnGraph g2) {
        // ignore tags
        if (g2.isModified() != g1.isModified()) {
            return false;
        }
        if (g2.positions.size() != g1.positions.size()) {
            return false;
        }
        if (!areEqual(g2.rootMove, g1.rootMove)) {
            return false;
        }
        if (!g2.getInitBoard().equals(g1.getInitBoard())) {
            return false;
        }

        for (Map.Entry<Pack, Board> entry : g2.positions.entrySet()) {
            entry.getValue().setVisited(false);
        }
        for (Map.Entry<Pack, Board> entry : g1.positions.entrySet()) {
            entry.getValue().setVisited(false);
        }
        return areEqual(g1, g1.rootMove, g2, g2.rootMove);
    }

    private boolean areEqual(PgnGraph g1, Move _m1, PgnGraph g2, Move _m2) {
        Move m1 = _m1, m2 = _m2;
        while (m1 != null && m2 != null) {
            if (!areEqual(m1, m2)) {
                return false;
            }
            Board b1 = g1.getBoard(m1);
            Board b2 = g2.getBoard(m2);
            if (!b2.equals(b1)) {
                return false;
            }
            boolean f2 = b2.getVisited();
            boolean f1 = b1.getVisited();
            if (f2 != f1) {
                return false;
            }
            if (f2) {
                return true;
            }

            b2.setVisited(true);
            b1.setVisited(true);
            if (!areEqual(g1, m1.getVariation(), g2, m2.getVariation())) {
                return false;
            }
            m2 = b2.getMove();
            m1 = b1.getMove();
        }
        return m1 == m2;    // both nulls
    }

    String invert(String move) {
        if (move.startsWith(Config.PGN_K_CASTLE_ALT) || move.startsWith(Config.PGN_Q_CASTLE_ALT)) {
            return move;
        }
        Pattern pattern = Pattern.compile("[0-9]");
        Matcher matcher = pattern.matcher(move);
        int start = 0;
        String inverted = "";
        while (matcher.find()) {
            int s = matcher.start();
            inverted += move.substring(start, s);
            int y = 9 - Integer.valueOf(matcher.group());
            inverted += y;
            start = matcher.end();
        }
        if (start == 0) {
            return move;
        } else if (start < move.length()){
            inverted += move.substring(start);
        }
        return inverted;
    }

    public Move invert(Move src) {
        Move trg = src.clone();
        trg.moveFlags ^= Config.FLAGS_BLACK_MOVE;
        trg.setToY(Config.BOARD_SIZE - 1 - trg.getToY());
        if (trg.isFromSet()) {
            trg.setFromY(Config.BOARD_SIZE - 1 - trg.getFromY());
        }
        trg.setPiece(trg.getPiece());
        if (trg.getPiecePromoted() != Config.EMPTY) {
            trg.setPiecePromoted(trg.getPiecePromoted() ^ Config.PIECE_COLOR);
        }
        return trg;
    }

    Board invert(Board src) {
        Board trg = new Board();
        trg.toEmpty();
        for (int y = 0; y < Config.BOARD_SIZE; ++y) {
            for (int x = 0; x < Config.BOARD_SIZE; ++x) {
                int piece = src.getPiece(x, y);
                if (piece != Config.EMPTY) {
                    piece ^= Config.PIECE_COLOR;
                }
                trg.setPiece(x, Config.BOARD_SIZE -1 - y, piece);
            }
        }
        trg.setEnpassant(src.getEnpassant());
        trg.setBKing(src.getWKingX(), Config.BOARD_SIZE -1 - src.getWKingY());
        trg.setWKing(src.getBKingX(), Config.BOARD_SIZE -1 - src.getBKingY());
        trg.setFlags(invertBoardFlags(src.getFlags()));
        trg.setReversiblePlyNum(src.getReversiblePlyNum());
        trg.setPlyNum(src.getPlyNum());
        return trg;
    }

    private int invertBoardFlags(int flags) {
        int res = flags;
        res ^= Config.FLAGS_BLACK_MOVE;
        res &= ~Config.INIT_POSITION_FLAGS;
        if ((flags & Config.FLAGS_W_KING_OK) != 0) {
            res |= Config.FLAGS_B_KING_OK;
        }
        if ((flags & Config.FLAGS_B_KING_OK) != 0) {
            res |= Config.FLAGS_W_KING_OK;
        }
        if ((flags & Config.FLAGS_W_QUEEN_OK) != 0) {
            res |= Config.FLAGS_B_QUEEN_OK;
        }
        if ((flags & Config.FLAGS_B_QUEEN_OK) != 0) {
            res |= Config.FLAGS_W_QUEEN_OK;
        }
        return res;
    }

    // when parseItems == false return raw pgn item text in item.moveText
    protected synchronized List<CpFile.PgnItem> parsePgnFile(CpFile.PgnFile parent, InputStream is, boolean parseItems) throws Config.PGNException {
        final List<CpFile.PgnItem> pgnItems = new LinkedList<>();
        if (is == null) {
            return pgnItems; // crashes otherwise
        }

        CpFile.parsePgnFile(parent, is, new CpFile.EntryHandler() {
            @Override
            public boolean handle(int index, CpFile.PgnItem entry) {
                CpFile.PgnItem copy = new CpFile.PgnItem(null);
                entry.copy(copy);
                pgnItems.add(copy);
                return true;
            }

            @Override
            public boolean getMovesText(int index) {
                return true;
            }
        }, parseItems);

        return pgnItems;
    }

    protected List<PgnGraph> parse2PgnGraphs(String pgn) throws Config.PGNException {
        List<PgnGraph> res = new LinkedList<>();
        InputStream is = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(pgn).array());
        final List<CpFile.PgnItem> items = parsePgnFile(null, is, true);

        int i = -1;
        for (CpFile.PgnItem item : items) {
            if (DEBUG) {
                logger.debug(item.toString());
            }
            item.setIndex(++i);
            PgnGraph pgnGraph = new PgnGraph(item);
            res.add(pgnGraph);
        }
        return res;
    }

/*
    private CpFile.PgnItem pgnFromFen(String fen) throws Config.PGNException {
        String pgn = String.format("[FEN \"%s\"]", fen);
        BufferedReader br = new BufferedReader(new StringReader(pgn));
        final List<CpFile> items = new LinkedList<>();
        CpFile.x_parsePgnFile(null, br, new CpFile.x_EntryHandler() {
            @Override
            public boolean handle(CpFile entry, BufferedReader bufferedReader) {
                items.add(entry);
                return true;
            }

            @Override
            public boolean getMoveText(CpFile entry) {
                return true;
            }

            @Override
            public boolean addOffset(int length, int totalLength) {
                return false;
            }
        });
        Assert.assertEquals(items.size(), 1);
        return (CpFile.PgnItem) items.get(0);
    }
*/

    private void testMove(CpFile.PgnItem pgnItem, String moveText, int resultFlags) throws Config.PGNException {
        pgnItem.setMoveText("");
        PgnGraph pgnGraph = new PgnGraph(pgnItem);
        Board initBoard = pgnGraph.getInitBoard();
        Move move = new Move(initBoard.getFlags() & Config.FLAGS_BLACK_MOVE);
        Util.parseMove(move, moveText);
        boolean res = pgnGraph.validateUserMove(move);
        if (resultFlags == ERR) {
            Assert.assertFalse(String.format("%s must be error\n%s", moveText, initBoard.toString()), res);
        } else {
            Assert.assertTrue(String.format("%s must be ok\n%s", moveText, initBoard.toString()), res);
            pgnGraph.addMove(move);

            int moveFlags = move.moveFlags & Config.MOVE_FLAGS;
            int expectedMoveFlags = resultFlags & Config.MOVE_FLAGS;
            if ((expectedMoveFlags & Config.FLAGS_Y_AMBIG) == 0) {
                moveFlags &= ~Config.FLAGS_Y_AMBIG; // kludgy hack for Util.parseMove
            }
            if ((expectedMoveFlags & Config.FLAGS_X_AMBIG) == 0) {
                moveFlags &= ~Config.FLAGS_X_AMBIG; // kludgy hack for Util.parseMove
            }

            Assert.assertEquals(String.format("%s\n%s\nmove flags 0x%04x != 0x%04x", moveText, initBoard.toString(), moveFlags, expectedMoveFlags),
                    moveFlags, expectedMoveFlags);
            Board board = pgnGraph.getBoard();
            int positionFlags = board.getFlags() & Config.POSITION_FLAGS;
            int expectedPositionFlags = (resultFlags ^ Config.FLAGS_BLACK_MOVE) & Config.POSITION_FLAGS;
            Assert.assertEquals(String.format("%s\n%s\nposition flags 0x%04x != 0x%04x", moveText, board.toString(), positionFlags, expectedPositionFlags),
                    positionFlags, expectedPositionFlags);
        }
    }

    /**
     * For the supplied position validate each move as received via UI and as read from pgn file
     * revert position and check the same move made by opponent
     * @param fen position
     * @param moves array of moves with expected result
     * @throws Config.PGNException
     */
    void testUserMoves(String fen, Pair<String, Integer>[] moves) throws Config.PGNException {
        Board initBoard = new Board(fen);
        Assert.assertEquals(String.format("%s != %s", fen, initBoard.toFEN()), fen, initBoard.toFEN());

        Board invertedInitBoard = invert(initBoard);
        String invertedFen = invertedInitBoard.toFEN();
        Assert.assertEquals(String.format("%s != %s", invertedFen, invertedInitBoard.toFEN()), invertedFen, invertedInitBoard.toFEN());

        for (Pair<String, Integer> entry : moves) {
            testUserMove(initBoard, entry.first, entry.second);

            // test inverted board:
            String invertedMove = invert(entry.first);
            int invertedExpectedFlags = entry.second;
            if (entry.second != ERR) {
                invertedExpectedFlags = invertBoardFlags(entry.second);
            }
            testUserMove(invertedInitBoard, invertedMove, invertedExpectedFlags);
        }
    }

    void testUserMove(Board initBoard, String moveText, int resultFlags) throws Config.PGNException {
        Move move = new Move(initBoard.getFlags() & Config.FLAGS_BLACK_MOVE);
        try {
            Util.parseMove(move, moveText);
        } catch (Config.PGNException e) {
            if (resultFlags == ERR) {
                return;
            }
            Assert.fail(String.format("%s must be ok, %s", moveText, e.getMessage()));
        }
        PgnGraph pgnGraph = new PgnGraph(initBoard);
        boolean res = pgnGraph.validateUserMove(move);
        if (resultFlags == ERR) {
            Assert.assertFalse(String.format("%s must be error\n%s", moveText, initBoard.toString()), res);
        } else {
            Assert.assertTrue(String.format("%s must be ok\n%s", moveText, initBoard.toString()), res);
            pgnGraph.addUserMove(move);
            logger.debug(pgnGraph.toPgn());

            Board board = pgnGraph.getBoard();
            int moveFlags = move.moveFlags & Config.MOVE_FLAGS;
            int expectedMoveFlags = resultFlags & Config.MOVE_FLAGS;
            if ((expectedMoveFlags & Config.FLAGS_Y_AMBIG) == 0) {
                moveFlags &= ~Config.FLAGS_Y_AMBIG; // kludgy hack for Util.parseMove
            }
            if ((expectedMoveFlags & Config.FLAGS_X_AMBIG) == 0) {
                moveFlags &= ~Config.FLAGS_X_AMBIG; // kludgy hack for Util.parseMove
            }

            Assert.assertEquals(String.format("%s\n%s\nmove flags 0x%04x != 0x%04x", moveText, board.toString(), moveFlags, expectedMoveFlags),
                    moveFlags, expectedMoveFlags);
            int positionFlags = board.getFlags() & Config.POSITION_FLAGS;
            int expectedPositionFlags = (resultFlags ^ Config.FLAGS_BLACK_MOVE) & Config.POSITION_FLAGS;
            Assert.assertEquals(String.format("%s\n%s\nposition flags 0x%04x != 0x%04x", moveText, board.toString(), positionFlags, expectedPositionFlags),
                    positionFlags, expectedPositionFlags);
            if ((resultFlags & FLAGS_PROMOTION) != 0) {
                Assert.assertTrue(String.format("%s must be promotion", moveText), move.isPromotion());
            }
            if ((resultFlags & FLAGS_ENPASSANT) != 0) {
                Assert.assertTrue(String.format("%s must be en passant", moveText), initBoard.isEnPassant(move));
            }

            Move _move = initBoard.findMove(board);
            Assert.assertTrue(String.format("expected %s, got %s", move, _move), move.isSameAs(_move));
            pgnGraph.delCurrentMove();
        }
    }

    /**
     * For the supplied position validate each move as read from pgn file
     * revert position and check the same move made by the opponent
     * @param fen position
     * @param moves array of moves with expected result
     * @throws IOException
     */
    void testPgnMoves(String fen, Pair<String, Integer>[] moves) throws Config.PGNException {
        Board initBoard = new Board(fen);
        Assert.assertEquals(String.format("%s != %s", fen, initBoard.toFEN()), fen, initBoard.toFEN());

        Board invertedInitBoard = invert(initBoard);
        String invertedFen = invertedInitBoard.toFEN();
        Assert.assertEquals(String.format("%s != %s", invertedFen, invertedInitBoard.toFEN()), invertedFen, invertedInitBoard.toFEN());

        for (Pair<String, Integer> entry : moves) {
            testPgnMove(initBoard, entry.first, entry.second);

            // test inverted board:
            String invertedMove = invert(entry.first);
            int invertedExpectedFlags = entry.second;
            if (entry.second != ERR) {
                invertedExpectedFlags = invertBoardFlags(entry.second);
            }
            testPgnMove(invertedInitBoard, invertedMove, invertedExpectedFlags);
        }
    }

    private void testPgnMove(Board initBoard, String moveText, int resultFlags) throws Config.PGNException {
        Move move = new Move(initBoard.getFlags() & Config.FLAGS_BLACK_MOVE);
        PgnGraph pgnGraph = new PgnGraph(initBoard);

        try {
            Util.parseMove(move, moveText);
        } catch (Config.PGNException e) {
            if (resultFlags == ERR) {
                return;
            }
            Assert.fail(String.format("%s must be ok, %s", moveText, e.getMessage()));
        }

        boolean res = pgnGraph.validatePgnMove(move);
        if (resultFlags == ERR) {
            Assert.assertFalse(String.format("%s validatePgnMove must be error\n%s", moveText, initBoard.toString()), res);
        } else {
            Assert.assertTrue(String.format("%s validatePgnMove must be ok\n%s", moveText, initBoard.toString()), res);
            pgnGraph.addMove(move);

            Board board = pgnGraph.getBoard();

            int moveFlags = move.moveFlags & Config.MOVE_FLAGS;
            int expectedMoveFlags = resultFlags & Config.MOVE_FLAGS;
            Assert.assertEquals(String.format("%s\n%s\nmove flags 0x%04x != 0x%04x", moveText, initBoard.toString(), moveFlags, expectedMoveFlags),
                    moveFlags, expectedMoveFlags);
            int positionFlags = board.getFlags() & Config.POSITION_FLAGS;
            int expectedPositionFlags = (resultFlags ^ Config.FLAGS_BLACK_MOVE) & Config.POSITION_FLAGS;
            Assert.assertEquals(String.format("%s\n%s\nposition flags 0x%04x != 0x%04x", moveText, initBoard.toString(), positionFlags, expectedPositionFlags),
                    positionFlags, expectedPositionFlags);
            if ((resultFlags & FLAGS_PROMOTION) != 0) {
                Assert.assertTrue(String.format("%s must be promotion", moveText), move.isPromotion());
            }
            if ((resultFlags & FLAGS_ENPASSANT) != 0) {
                Assert.assertTrue(String.format("%s must be en passant", moveText), initBoard.isEnPassant(move));
            }
        }
    }
}
