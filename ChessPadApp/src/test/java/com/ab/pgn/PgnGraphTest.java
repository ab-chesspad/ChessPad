/*
     Copyright (C) 2021	Alexander Bootman, alexbootman@gmail.com

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

 * unit tests
 * Created by Alexander Bootman on 10/29/17.
 */
package com.ab.pgn;

import com.ab.pgn.io.CpFile;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

//@Ignore
public class PgnGraphTest extends BaseTest {
    private static final String LOG_DIR_NAME = "log/";
    private static final String LOG_FILE_WRITER_NAME = TEST_TMP_ROOT + LOG_DIR_NAME + "graph-w.log";
    private static final String LOG_FILE_READER_NAME = TEST_TMP_ROOT + LOG_DIR_NAME + "graph-r.log";

    private static final int TEST_GLYPH = 79;
    private static final String TEST_COMMENT = "Test comment";
    static final int TEST_MOVELINE_SERIALIZATION_VERSION = 3;

    @Test
    public void testParsingSimple() throws Config.PGNException {
        String pgn = "[Event \"\\\"Lloyds Bank\\\" op\"]\n" +
            "[Site \"London\"]\n" +
            "[Date \"1984.??.??\"]\n" +
            "[Round \"1\"]\n" +
            "[White \"Adams\\\\, Michael\"]\n" +
            "[Black \"Sedgwick, David\"]\n" +
            "[Result \"1-0\"]\n" +
            "[WhiteElo \"\"]\n" +
            "[BlackElo \"\"]\n" +
            "[ECO \"C05\"]\n" +
            "\n" +
            "1.e4 e6 2.d4 d5 3.Nd2 Nf6 4.e5 Nfd7 5.f4 c5 6.c3 Nc6 7.Ndf3 cxd4 8.cxd4 f6\n" +
            "9.Bd3 Bb4+ 10.Bd2 Qb6 11.Ne2 fxe5 12.fxe5 O-O 13.a3 Be7 14.Qc2 Rxf3 15.gxf3 Nxd4\n" +
            "16.Nxd4 Qxd4 17.O-O-O Nxe5 18.Bxh7+ Kh8 19.Kb1 Qh4 20.Bc3 Bf6 21.f4 Nc4 22.Bxf6 Qxf6\n" +
            "23.Bd3 b5 24.Qe2 Bd7 25.Rhg1 Be8 26.Rde1 Bf7 27.Rg3 Rc8 28.Reg1 Nd6 29.Rxg7 Nf5\n" +
            "30.R7g5 Rc7 31.Bxf5 exf5 32.Rh5+  1-0\n" +
            "";
        testParsing(pgn);
    }

    @Test
    public void testParsingAnnotated() throws Config.PGNException {
        String pgn =
            "[Event \"Amsterdam\"]\n" +
            "[Site \"?\"]\n" +
            "[Date \"1950.??.??\"]\n" +
            "[Round \"?\"]\n" +
            "[White \"Pilnik\"]\n" +
            "[Black \"Kramer\"]\n" +
            "[Result \"1-0\"]\n" +
            "[Annotator \"01: Active Bishop\"]\n" +
            "[SetUp \"1\"]\n" +
            "[FEN \"r1bq1rk1/4bppp/p1n2n2/1pppp3/4P3/2PP1N2/PPB2PPP/R1BQRNK1 w - - 0 1\"]\n" +
            "[PlyCount \"27\"]\n" +
            "[Source \"Hays Publishing\"]\n" +
            "[SourceDate \"1964.01.01\"]\n" +
            finalFen2Tag("4nk2/1b3pp1/p2b4/1p6/2p4B/2P5/PPB2PPP/5NK1 b - - 1 14") +
            "\n" +
            "{An active bishop obtained at the cost of a backward pawn  With his last move .\n" +
            ".. Pd5, Black has taken the initiative in the centre, and now threatens either\n" +
            "to gain space and freedom by ... Pd4 or else release tension by ... Pdxe4.  \n" +
            " Although after exchanges on e4 Black will have a weak point at d5, which\n" +
            "White may exploit by Ne3-d5, but under the circumstances this is less\n" +
            "important}" +
            " 1. exd5 (1. -- dxe4 2. dxe4 -- 3. Bb3 3... c4 {\n" +
            "White's bishop isn't active}) 1... Qxd5 " +
            "2. Qe2 2... Bb7 3. Bg5 $1 (3. Nxe5 Nxe5\n" +
            "4. Qxe5 Qxg2#) (3. Bb3 {threat to win the pawn} 3... Qd7 4. Nxe5 Nxe5 5. Qxe5\n" +
            "5... Bd6 $44 {a strong attack}) 3... Rfe8 4. Bh4 {\n" +
            "threatening to win the e-pawn with Bg3 as well as making room for Ng5 with Bb3}\n" +
            "4... Rad8 {Apparently Black still stands very well. His pressure on the d-pawn\n" +
            "seems to condemn the Bishop to passivity} 5. Bb3 $3 {\n" +
            "This deep moves demonstrates otherwise} (5. Red1 {\n" +
            "relieves Black's chief worry of protecting the e-pawn}) (5. Rad1 Qxa2) 5...\n" +
            "Qxd3 6. Qxd3 Rxd3 7. Nxe5 Nxe5 8. Rxe5 8... c4 $2 {The Black pieces are\n" +
            "awkardly ties up, while the White ones have developed great activity. With the\n" +
            "text move Black hopes to persuade the dangerous bishop to be more modest, but\n" +
            "he doesn't succeed} (8... Bd6 $2 {the point of Bb3} 9. Rxe8+ Nxe8 10. Bc2 Rd5\n" +
            "11. Be4 11... Rh5 12. Bxb7 Rxh4 13. Bxa6 $16 {winning ending}) (8... Rdd8 $1 {\n" +
            "best defence} 9. Rae1 Kf8) 9. Bc2 Rdd8 (9... Rd7 10. Rae1 Nd5 11. Bf5 Rc7 12.\n" +
            "Bg3 $18 {Rippis\n" +
            "}) 10. Rae1 10... Kf8 11. Bxh7 $1 11... Bd6 (11... Nxh7 12.\n" +
            "Bxe7+ $16) (11... g6 12. Bg5 12... Ng8 13. Bc1 (13. Bxg8 Bxg5 14. Rxe8+ Rxe8\n" +
            "15. Rxe8+ 15... Kxe8 $19) 13... -- {there is no avoiding Bxg8.\n" +
            "} (13... Nf6\n" +
            "14. Bh6#) 14. Bxg8) 12. Rxe8+ Rxe8 13. Rxe8+ Nxe8 14. Bc2 $18 1-0\n" +
            "";
        testParsing(pgn);
    }

    @Test
    public void testParsingVariants() throws Config.PGNException {
        String pgn =
            "[White \"merge\"]\n" +
            "[Black \"variations\"]\n" +
            "{Merge variations test}" +
            "1.e4 e5 2.Nf3 {main} (2.Bc4 {v1} Nc6 {v1} 3.Nf3 {v1} Nf6 {v1} $4 4.c3{v1} $7) (2.Bc4 {v2} Nf6 {v2} 3.Nf3 {v2} Nc6 {v2} $20 4.c3{v2} $21) 2. ... Nc6 {main} 3.Bc4{main} Nf6{main} $5 4.c3{main}\n" +
            "\n" +
            "[White \"merge\"]\n" +
            "[Black \"variations 1\"]\n" +
            "\n" +
            "1. e4 Nf6 (1. ... Nc6 2. Nf3 (2. Nc3 Nf6) 2. ... Nf6 3. Nc3 e6) (1. ... e5 2. Bc4) 2. Nc3 Nc6 3. Nf3 e5" +
            "\n" +
            "[White \"merge\"]\n" +
            "[Black \"variations 2\"]\n" +
            "\n" +
            "1. e4 Nf6 (1. ... Nc6 (1. ... e5 2. Bc4) 2. Nf3 (2. Nc3 Nf6) 2. ... Nf6 3. Nc3 e6) 2. Nc3 Nc6 3. Nf3 e5" +
            "\n";
        List<PgnGraph> graphs = testParsing(pgn);
        Assert.assertEquals(3, graphs.size());

        PgnGraph pgnGraph = graphs.get(0);
        String s = pgnGraph.toPgn();
        Assert.assertTrue(s.indexOf("Nf6 $5 {main; v1}") > 0);
        Assert.assertTrue(s.trim().endsWith("4. c3 $21 {main; v2; v1} *"));
    }

    @Test
    public void testDelMainMove() throws Config.PGNException {
        String pgn =
            "[White \"merge\"]\n" +
            "[Black \"variations\"]\n" +
            "{Merge variations test}" +
            "1.e4 e5 2.Nf3 {main} (2.Bc4 {v1} Nc6 {v1} 3.Nf3 {v1} Nf6 {v1} $4 4.c3{v1} $7) (2.Bc4 {v2} Nf6 {v2} 3.Nf3 {v2} Nc6 {v2} $20 4.c3{v2} $21) 2. ... Nc6 {main} 3.Bc4{main} Nf6{main} $5 4.c3{main}\n" +
            "\n";
        List<PgnGraph> graphs = testParsing(pgn);
        Assert.assertEquals(1, graphs.size());
        PgnGraph graph = graphs.get(0);
        String s = graph.toPgn();
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());
        Assert.assertTrue(s.indexOf("Nf6 $5 {main; v1}") > 0);
        Assert.assertTrue(s.trim().endsWith("4. c3 $21 {main; v2; v1} *"));

        graph.toEnd();
        graph.delCurrentMove();
        s = graph.toPgn();
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());
        s = graph.getNumberedMove();
        Assert.assertEquals("3. ... Nf6 ", s);
        graph.delCurrentMove();
        s = graph.toPgn();
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());
        Assert.assertTrue(s.trim().endsWith("3. Bc4 {main} *"));

        graph.delCurrentMove();
        s = graph.toPgn();
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());
        Assert.assertTrue(s.trim().endsWith("2. ... Nc6 {main} *"));

        // test navigation through graph
        graph.toInit();
        Assert.assertTrue(graph.isInit());
        Assert.assertEquals("", graph.getNumberedMove());
        Assert.assertEquals(1, graph.moveLine.size());
        graph.toEnd();
        Assert.assertTrue(graph.isEnd());
        List<Move> variations = graph.getVariations();
        Assert.assertNull(variations);

        graph.toInit();
        // set glyph for root move is ignored
        Assert.assertFalse(graph.okToSetGlyph());
        graph.setGlyph(TEST_GLYPH);
        Assert.assertEquals(0, graph.getGlyph());
        graph.setComment(TEST_COMMENT);
        Assert.assertEquals(TEST_COMMENT, graph.getComment());

        // del root move is ignored
        graph.delCurrentMove();
        Assert.assertEquals(1, graph.moveLine.size());
        s = graph.toPgn();
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());

        Move m = graph.getNextMove(graph.getCurrentMove());
        graph.toNext();
        Assert.assertEquals(2, graph.moveLine.size());
        Move m1 = graph.getCurrentMove();
        Assert.assertEquals(m, m1);
        graph.setComment(TEST_COMMENT);
        Assert.assertEquals(TEST_COMMENT, graph.getComment());
        graph.setComment("");
        Assert.assertNull(graph.getComment());

        // delete the whole graph
        graph.delCurrentMove();
        Assert.assertEquals(1, graph.moveLine.size());
        s = graph.toPgn().trim();
        Assert.assertEquals(String.format("{%s} *", TEST_COMMENT), s);
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());

        Assert.assertTrue(graph.isModified());
        graph.setModified(false);
        Assert.assertFalse(graph.isModified());
    }

    @Test
    public void testDelVariantMove() throws Config.PGNException {
        String pgn =
            "[White \"merge\"]\n" +
            "[Black \"variations\"]\n" +
            "{Merge variations test}" +
            "1.e4 e5 2.Nf3 {main} (2.Bc4 {v1} Nc6 {v1} 3.Nf3 {v1} Nf6 {v1} $4 4.c3{v1} $7) (2.Bc4 {v2} Nf6 {v2} 3.Nf3 {v2} Nc6 {v2} $20 4.c3{v2} $21) 2. ... Nc6 {main} 3.Bc4{main} Nf6{main} $5 4.c3{main}\n" +
            "\n";
        List<PgnGraph> graphs = testParsing(pgn);
        Assert.assertEquals(1, graphs.size());
        PgnGraph graph = graphs.get(0);
        graph.toEnd();
        Assert.assertTrue(graph.isEnd());
        Assert.assertTrue(graph.okToSetGlyph());
        graph.setGlyph(TEST_GLYPH);
        Assert.assertEquals(TEST_GLYPH, graph.getGlyph());
        Assert.assertEquals(Config.INIT_POSITION_FLAGS | Config.FLAGS_BLACK_MOVE, graph.getFlags());
        String s = graph.toPgn();
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());
        Assert.assertTrue(s.indexOf("Nf6 $5 {main; v1}") > 0);
        Assert.assertTrue(s.trim().endsWith(String.format("4. c3 $%s {main; v2; v1} *", TEST_GLYPH)));

        graph.toPrevVar();                      // 1. ... e5
        List<Move> variations = graph.getVariations();
        Assert.assertEquals(2, variations.size());
        graph.toVariation(variations.get(1));   // 2. Bc4
        Assert.assertEquals("2. Bc4 ", graph.getNumberedMove());

        graph.delCurrentMove();
        s = graph.toPgn();
        Assert.assertTrue(s.trim().endsWith(String.format("4. c3 $%s {main; v2; v1} *", TEST_GLYPH)));
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());

        Move m = new Move(0);
        Move m1 = graph.getNextMove(m);
        Assert.assertNull(m1);
    }

    @Test
    public void testParsingError() throws Config.PGNException {
        String pgn =
            "[White \"merge\"]\n" +
            "[Black \"variations\"]\n" +
            "{Merge variations test}" +
            "1.e4 e5 2.Nf3 {main} (2.Bc4 {v1} Nc6 {v1} 3.Nf3 {v1} Nf6 {v1} $4 4.c3{v1} $7) (2.Bc4 {v2} Nf6 {v2} 3.Nf3 {v2} Nc6 {v2} $20 4.c3{v2} $21) 2. ... Nc6 {main} 3.Bc4{main} Nf6{main} $5 4.c5{main}\n" +
            "\n";
        List<PgnGraph> graphs = parse2PgnGraphs(pgn);
        Assert.assertEquals(1, graphs.size());
        PgnGraph graph = graphs.get(0);
        // 2/12 ??
        Assert.assertTrue(graph.getParsingError().startsWith("com.ab.pgn.Config$PGNException: invalid move 4. c5 "));
        Assert.assertEquals(0, graph.getParsingErrorNum());
    }

    @Test
    public void testRepetition() throws Config.PGNException {
        String pgn =
                "[White \"white\\\\repetition, promotion\"]\n" +
                        "[Black \"black \\\"black\\\"\"]\n" +
                        "[FEN \"r3kbnr/pPp2p1p/4p3/3pP3/8/5P2/P1PP2pP/RNBQK2R w KQkq - 92 3\"]\n" +
                        "3. Nc3 Bd6 4. Nb1 Bf8 5. Nc3 Bd6 6. Nb1 Bf8 {creates 3-fold repetition}" +
                        "\n";
        List<PgnGraph> graphs = parse2PgnGraphs(pgn);
        Assert.assertEquals(1, graphs.size());
        PgnGraph graph = graphs.get(0);
        Move m = graph.getCurrentMove();
        Assert.assertTrue((m.moveFlags & Config.FLAGS_REPETITION) != 0);
        String s = graph.getNumberedMove();
        System.out.println(s);
        graph.toPrev();

        Move move = new Move(graph.getBoard().getFlags() & Config.FLAGS_BLACK_MOVE);
        Util.parseMove(move, "Be7");
        boolean res = graph.validatePgnMove(move);
        Assert.assertTrue(res);
        graph.addUserMove(move);
        Assert.assertEquals(100, graph.getBoard().getReversiblePlyNum());
    }

    @Test
//    @Ignore("Android Studio 2020.3.1 Patch 2 hangs on this test")
    public void testMerge_SicilianMisc2() throws Config.PGNException {
        if (ANDROID_TESTING) {
            logger.debug("Android Studio 2020.3.1 Patch 2 hangs on testMerge_SicilianMisc2()");
            return;
        }
        String pgnText =
                "[White \"Staunton, Howard\"]\n" +
                        "[Black \"Cochrane, John Miles\"]\n" +
                        "1.e4 c5 2.c4" +
                        "\n";
        List<PgnGraph> graphs = parse2PgnGraphs(pgnText);
        Assert.assertEquals(1, graphs.size());
        PgnGraph graph = graphs.get(0);
        Move m = graph.getCurrentMove();
        Board b = graph.getBoard();

        CpFile.PgnFile pgn = (CpFile.PgnFile) CpFile.CpParent.fromPath("SicilianMisc2.pgn");
        PgnGraph.MergeData md = new PgnGraph.MergeData(pgn);
        md.end = md.start = -1;
        md.annotate = true;
        graph.merge(md, null);
//        logger.debug(String.format("merged %s items", md.merged));
        Assert.assertEquals(3404, md.merged);
        Assert.assertTrue(graph.isModified());
        Assert.assertEquals(4, graph.moveLine.size());
        Assert.assertEquals(218623, graph.positions.size());
        Assert.assertEquals(0, graph.getParsingErrorNum());
        Assert.assertNull(graph.getParsingError());

        String s = graph.toPgn();
        Assert.assertEquals(1551609, s.length());
//        logger.debug(s);
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());
    }

    @Test
//    @Ignore("Android Studio 2020.3.1 Patch 2 hangs on this test")
    public void testMerge_SicilianTaimanovMain() throws Config.PGNException, IOException {
        if (ANDROID_TESTING) {
            logger.debug("Android Studio 2020.3.1 Patch 2 hangs on testMerge_SicilianTaimanovMain()");
            return;
        }
        String _pgnFileName = "SicilianTaimanovMain";
        String pgnFileName = _pgnFileName + ".pgn";
        String pgnText =
                "[White \"SicilianTaimanov\"]\n" +
                        "[Black \"Main\"]\n" +
                        "1. e4 c5 " +
                        "\n";
        List<PgnGraph> graphs = parse2PgnGraphs(pgnText);
        Assert.assertEquals(1, graphs.size());
        PgnGraph graph = graphs.get(0);
//        Move m = graph.getCurrentMove();
//        Board b = graph.getBoard();

        File origFile = new File(TEST_ROOT + pgnFileName);
        String root = TEST_TMP_ROOT;
        File testFile = new File(root + pgnFileName);
        fullCopy(origFile, testFile);
        CpFile.setRoot(root);

        CpFile.PgnFile pgnFile = (CpFile.PgnFile) CpFile.CpParent.fromPath(pgnFileName);
        PgnGraph.MergeData md = new PgnGraph.MergeData(pgnFile);
        md.end = md.start = -1;
        md.annotate = true;
        graph.merge(md, (progress) -> {
            if (DEBUG) {
                logger.debug(String.format("\t offset=%s", progress));
            }
            return false;
        });
        Assert.assertEquals(29942, md.merged);
        Assert.assertTrue(graph.isModified());
        Assert.assertEquals(3, graph.moveLine.size());
        Assert.assertEquals(1729980, graph.positions.size());
        Assert.assertEquals(0, graph.getParsingErrorNum());
        Assert.assertNull(graph.getParsingError());

        String s = graph.toPgn();
        Assert.assertEquals(12251856, s.length());
        PrintStream ps = new PrintStream(new FileOutputStream(root + pgnFile + "-merged.pgnText"));
        ps.print(s);
        ps.flush();
        ps.close();
        CpFile.setRoot(TEST_ROOT);
    }

    // e.g. " 1.e4 c5 2. Nf3 Nc6 3.d4 e6"
    private List<Move> navigate(PgnGraph graph, String sMoveLine) {
        List<Move> moveLine = new LinkedList<>();
        Board board = graph.getInitBoard();
        String[] sMoves = sMoveLine.split("\\d+\\.|\\s+");
        for (String sMove : sMoves) {
            if (sMove.isEmpty()) {
                continue;
            }
            logger.debug(sMove);
            Move m = board.getMove();
            while (m != null && !sMove.equals(m.toString().trim())) {
                m = m.getVariation();
            }
            Assert.assertNotNull(String.format("%s not found\n%s", sMove, board.toString()), m);
            moveLine.add(m);
            board = graph.getBoard(m);
        }
        return moveLine;
    }

    @Test
    public void test_SicilianTaimanovMain_merged() throws Config.PGNException, IOException {
        List<PgnGraph> graphs = testParsingFile("SicilianTaimanovMain-merged.pgn");
        Assert.assertEquals(1, graphs.size());
        PgnGraph graph = graphs.get(0);
        logger.debug(graph.toPgn());
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());

        List<Move> moveLine = navigate(graph, "1.e4 c5 2. Nf3 Nc6 3.d4 e6");
        logger.debug(moveLine);
    }

    private PgnGraph testMerge(String pgnText, String mergeFileName) throws Config.PGNException, FileNotFoundException {
        List<PgnGraph> graphs = parse2PgnGraphs(pgnText);
        Assert.assertEquals(1, graphs.size());
        PgnGraph graph = graphs.get(0);
        Move m = graph.getCurrentMove();
        Board b = graph.getBoard();

        CpFile.PgnFile pgn = (CpFile.PgnFile) CpFile.CpParent.fromPath(mergeFileName);
        PgnGraph.MergeData md = new PgnGraph.MergeData(pgn);
        md.end = md.start = -1;
        md.annotate = true;
        graph.merge(md, (progress) -> {
            if (DEBUG) {
                logger.debug(String.format("\t offset=%s", progress));
            }
            return false;
        });
        logger.debug(String.format("merged %s items", md.merged));
        String s = graph.toPgn();   // moves only
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());
        String resPath = mergeFileName.replaceAll("/", "~");
        try (PrintStream ps = new PrintStream(new FileOutputStream(TEST_TMP_ROOT + resPath + "-merged.pgnText"))) {
            ps.print(s);
            ps.flush();
        }
        return graph;
    }

    @Test
    public void testMerge_0() throws Config.PGNException, FileNotFoundException {
        if (ANDROID_TESTING) {
            logger.debug("Android Studio 2020.3.1 Patch 2 hangs on testMerge_0()");
            return;
        }
        String[][] pgnTexts = {
                {"[White \"Max Lange\"]\n" +
                        "[Black \"Attack\"]\n" +
                        "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4.d4" +
                        "\n", "GiuocoPiano.zip/GiuocoPiano.pgn"
                },
        };

        for (String[] pgnData : pgnTexts) {
            PgnGraph graph = testMerge(pgnData[0], pgnData[1]);
            String s = graph.toPgn();
            Assert.assertEquals(43438, s.length());

//            Assert.assertEquals(29942, md.merged);
            Assert.assertTrue(graph.isModified());
            Assert.assertEquals(8, graph.moveLine.size());
            Assert.assertEquals(6302, graph.positions.size());
            Assert.assertEquals(0, graph.getParsingErrorNum());
            Assert.assertNull(graph.getParsingError());

            break;
        }
    }

    @Test
    public void testMerge_1() throws Config.PGNException, FileNotFoundException {
        if (ANDROID_TESTING) {
            logger.debug("Android Studio 2020.3.1 Patch 2 hangs on testMerge_1()");
            return;
        }
        String[][] pgnTexts = {
                {"[White \"Max Lange Attack\"]\n" +
                        "[Black \"Main\"]\n" +
                        "1.e4 e5 2.Nf3 Nc6 3.Bc4 Nf6 4.d4 exd4 5.O-O Bc5" +
                        "\n", "TwoKnights.pgn"
                },
        };

        for (String[] pgnData : pgnTexts) {
            PgnGraph graph = testMerge(pgnData[0], pgnData[1]);

            String s = graph.toPgn();
            Assert.assertEquals(93113, s.length());

//            Assert.assertEquals(29942, md.merged);
            Assert.assertTrue(graph.isModified());
            Assert.assertEquals(11, graph.moveLine.size());
            Assert.assertEquals(12531, graph.positions.size());
            Assert.assertEquals(0, graph.getParsingErrorNum());
            Assert.assertNull(graph.getParsingError());

            break;
        }
    }

    @Test
    public void testMerge_SicilianGranPrix() throws Config.PGNException, FileNotFoundException {
        String pgnFile = "SicilianGrandPrix.zip/SicilianGrandPrix.pgn";
        Object[][] moveLines = {
                {"e4 c5 Nc3 Nc6 f4", 4351124, 6, 620472},
                {"Nc3 Nf6 Nb1 Ng8 Nc3 c5 f4 Nc6 e4", 4362249, 10, 620476},
        };

        for (Object[] objects : moveLines) {
            String moveLine = (String) objects[0];
            int len = (int) objects[1];
            int mlLength = (int) objects[2];
            int totalPositions = (int) objects[3];
            PgnGraph graph = testMerge(moveLine, pgnFile);
            String s = graph.toPgn();
            Assert.assertEquals(len, s.length());
            Assert.assertTrue(graph.isModified());
            Assert.assertEquals(mlLength, graph.moveLine.size());
            Assert.assertEquals(totalPositions, graph.positions.size());
            Assert.assertEquals(0, graph.getParsingErrorNum());
            Assert.assertNull(graph.getParsingError());
        }
    }

    @Test
    @Ignore("Android Studio 2020.3.1 Patch 2 hangs on this test, so it is split on two, testMerge_0 and testMerge_1")
    public void testMerge() throws Config.PGNException, FileNotFoundException {
        String[][] pgnTexts = {
                {"[White \"Max Lange\"]\n" +
                        "[Black \"Attack\"]\n" +
                        "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4.d4" +
                        "\n", "GiuocoPiano.zip/GiuocoPiano.pgn"
                },
                {"[White \"Max Lange Attack\"]\n" +
                        "[Black \"Main\"]\n" +
                        "1.e4 e5 2.Nf3 Nc6 3.Bc4 Nf6 4.d4 exd4 5.O-O Bc5" +
                        "\n", "TwoKnights.pgn"
                },
        };
        for (String[] pgnData : pgnTexts) {
            testMerge(pgnData[0], pgnData[1]);
            String pgnText = pgnData[0];
            String pgnFileName = pgnData[1];
            List<PgnGraph> graphs = parse2PgnGraphs(pgnText);
            Assert.assertEquals(1, graphs.size());
            PgnGraph graph = graphs.get(0);
            Move m = graph.getCurrentMove();
            Board b = graph.getBoard();

            CpFile.PgnFile pgn = (CpFile.PgnFile) CpFile.CpParent.fromPath(pgnFileName);
            PgnGraph.MergeData md = new PgnGraph.MergeData(pgn);
            md.end = md.start = -1;
            md.annotate = true;
            graph.merge(md, (progress) -> {
                if (DEBUG) {
                    logger.debug(String.format("\t offset=%s", progress));
                }
                return false;
            });
            logger.debug(String.format("merged %s items", md.merged));
            String s = graph.toPgn();   // moves only
            logger.debug(s);
            Assert.assertEquals(0, graph.getNumberOfMissingVertices());
            String resPath = pgnFileName.replaceAll("/", "~");
            try (PrintStream ps = new PrintStream(new FileOutputStream(TEST_TMP_ROOT + resPath + "-merged.pgnText"))) {
                ps.print(s);
                ps.flush();
            }
        }
    }


    @Test
    public void testSerializeGraph_0() throws Config.PGNException, IOException {
        String pgnText =
                "[White \"merge\"]\n" +
                        "[Black \"variations\"]\n" +
                        "[CustomTag1 \"custom tag 1\"]\n" +
                        "[CustomTag2 \"custom tag 2\"]\n" +
                        "{Merge variations test}" +
                        "1.e4{1.} e5{..1} 2.Nf3 {main} (2.Bc4 {v1} Nc6 {v1} 3.Nf3 {v1} Nf6 {v1} $4 4. d4 {v1} (4.c3{v11} $7) 4. ... exd4 {v1}) (2.Bc4 {v2} Nf6 {v2} 3.Nf3 {v2} Nc6 {v2} $20 4.c3{v2} $21) (2.d4 {v3} exd4 {v3}) 2. ... Nc6 {main} 3.Bc4{main} Nf6{main} $5 4.c3{main}\n" +
                        "\n";

        List<PgnGraph> graphs = parse2PgnGraphs(pgnText);
        Assert.assertEquals(1, graphs.size());
        for (PgnGraph graph : graphs) {
            graph.toInit();
            graph.toEnd();
            logger.debug(graph.toPgn());
            PgnGraph.DEBUG = DEBUG;
            BitStream.Writer writer = new BitStream.Writer();
            graph.serializeGraph(writer, TEST_SERIALIZATION_VERSION);
            graph.serializeMoveLine(writer, TEST_MOVELINE_SERIALIZATION_VERSION);
            writer.close();
            Assert.assertEquals(0, graph.getNumberOfMissingVertices());
            BitStream.Reader reader = new BitStream.Reader(writer.getBits());
            PgnGraph unserialized = new PgnGraph(reader, TEST_SERIALIZATION_VERSION, null);
            unserialized.unserializeMoveLine(reader, TEST_MOVELINE_SERIALIZATION_VERSION);
            unserialized.toInit();
            unserialized.toEnd();
            logger.debug(graph.toPgn());
            Assert.assertEquals(0, graph.getNumberOfMissingVertices());
            logger.debug(unserialized.toPgn());
            Assert.assertEquals(0, unserialized.getNumberOfMissingVertices());
            Assert.assertTrue(areEqual(graph, unserialized));
            Assert.assertEquals(0, verifyMoveLinesEqual(graph.moveLine, unserialized.moveLine));
        }
    }

    @Test
    public void testSerializeGraph_1() throws Config.PGNException, IOException {
        File tmpLog = new File(TEST_TMP_ROOT + LOG_DIR_NAME);
        tmpLog.mkdirs();
        String[] fNames = {
                "MaxLange-0.pgn",
                "MaxLange-00.pgn",
        };

        for (final String fName : fNames) {
            List<PgnGraph> pgnGraphs = testParsingFile(fName);
            for (PgnGraph graph : pgnGraphs) {
                PgnLogger.setFile(LOG_FILE_WRITER_NAME);
                graph.toInit();
                graph.toEnd();
                String serFileName = TEST_TMP_ROOT + fName + ".ser";
                BitStream.Writer writer = new BitStream.Writer(new FileOutputStream(serFileName));
                graph.serializeGraph(writer, TEST_SERIALIZATION_VERSION);
                graph.serializeMoveLine(writer, TEST_MOVELINE_SERIALIZATION_VERSION);
                writer.close();
                Assert.assertEquals(0, graph.getNumberOfMissingVertices());
                PgnLogger.setFile(LOG_FILE_READER_NAME);

                BitStream.Reader reader = new BitStream.Reader(new FileInputStream(serFileName));
                PgnGraph unserialized = new PgnGraph(reader, TEST_SERIALIZATION_VERSION, (progress) -> {
                    if (DEBUG) {
                        System.out.println(String.format("%s, unserialized %d%%", fName, progress));
                    }
                    return false;
                });
                unserialized.unserializeMoveLine(reader, TEST_MOVELINE_SERIALIZATION_VERSION);
                Assert.assertEquals(writer.bitCount, reader.bitCount);
                unserialized.toInit();
                unserialized.toEnd();
                PgnLogger.setFile(null);
                logger.debug(unserialized.toPgn());
                Assert.assertEquals(0, unserialized.getNumberOfMissingVertices());
                Assert.assertTrue(areEqual(graph, unserialized));
                Assert.assertEquals(0, verifyMoveLinesEqual(graph.moveLine, unserialized.moveLine));
            }
        }
    }

    @Test
    public void testSerializeEmptyGraph() throws Config.PGNException, IOException {
        PgnGraph graph = new PgnGraph();
        PgnGraph.DEBUG = DEBUG;
        Board board = graph.getInitBoard();
        for (Board b : graph.positions.values()) {
            b.setVisited(false);
        }
        Assert.assertFalse(board.getVisited());

        BitStream.Writer writer = new BitStream.Writer();
        graph.serializeGraph(writer, TEST_SERIALIZATION_VERSION);
        writer.close();
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());
        BitStream.Reader reader = new BitStream.Reader(writer.getBits());
        PgnGraph unserialized = new PgnGraph(reader, TEST_SERIALIZATION_VERSION, null);
        logger.debug(unserialized.toPgn());
        Assert.assertEquals(0, unserialized.getNumberOfMissingVertices());
        Assert.assertTrue(areEqual(graph, unserialized));
    }

    @Test
    public void test_withIndent() {
        System.out.println("0         1         2         3         4");
        System.out.println("01234567890123456789012345678901234567890");
        int n = 0;
        Assert.assertEquals("testWithIndent", PgnGraph.withIndent("testWithIndent", n));
        n = 3;
        Assert.assertEquals("   testWithIndent", PgnGraph.withIndent("testWithIndent", n));
    }

    @Test
    public void test_modifyStatisticsComment() {
        Move move = new Move(0);
        logger.debug(String.format("src, %s", move.comment));
        PgnGraph.modifyStatisticsComment(move, "0-1");
        logger.debug(String.format("black, %s", move.comment));
        PgnGraph.modifyStatisticsComment(move, "1-0");
        logger.debug(String.format("white, %s", move.comment));
        PgnGraph.modifyStatisticsComment(move, "1/2-1/2");
        logger.debug(String.format("draw, %s", move.comment));

        move.comment = "sadcbiwubd; w=123; b=5; d=3; abc; qeaidbaidc";
        logger.debug(String.format("src, %s", move.comment));
        PgnGraph.modifyStatisticsComment(move, "0-1");
        logger.debug(String.format("black, %s", move.comment));
        PgnGraph.modifyStatisticsComment(move, "1-0");
        logger.debug(String.format("white, %s", move.comment));
        PgnGraph.modifyStatisticsComment(move, "1/2-1/2");
        logger.debug(String.format("draw, %s", move.comment));
    }

    @Test
//    @Ignore("todo: move this code to buildSrc to use with com.ab.pgn package")
    public void runBookBuilder() throws Config.PGNException, IOException {
        final String ecoFileName = bookPath + "eco.pgn";
        final String internalBookFileName = CpFile.getRootPath() + bookPath + "internal_openings.txt";
        final String outputFileName = CpFile.getRootPath() + bookPath + "combined.book";
        Book.Builder.build(ecoFileName, internalBookFileName, outputFileName);
        testBook();
    }

    // can run only after runBookBuilder()
//    @Test
    public void testBook() throws Config.PGNException, IOException {
        String fileName = CpFile.getRootPath() + bookPath + "combined.book";
        File f = new File(fileName);
        long length = f.length();
        InputStream is = new FileInputStream(f);
        Book book = new Book(is, length);

        String[][] moveLineData = {
                {"e4 c5 Nc3 Nc6 f4",
                        "g6 {~559~543~564}", "B23; Sicilian; Grand Prix attack, Schofman variation",
                        "d6 {~559~543~552}", "B23; Sicilian; Grand Prix attack",
                },
                {"Nc3 Nf6 Nb1 Ng8 Nc3 c5 f4 Nc6 e4",
                        "", ""  // todo: search positions regardless of plyNum
                },
        };

        for (String[] mlData : moveLineData) {
            Map<String, String> mlDataMap = new HashMap<>();
            for (int j = 1; j < mlData.length; j += 2) {
                mlDataMap.put(mlData[j], mlData[j + 1]);
            }
            String moveLine = mlData[0];
            PgnGraph graph = new PgnGraph();
            graph.parseMoves(moveLine, null);
            Board board = graph.getBoard();
            List<Move> moves = book.getMoves(board);
            System.out.print(board.toString());
            if (moves == null) {
                System.out.printf("%s, no book moves", moveLine);
            } else {
                for (Move m : moves) {
                    String s = m.toCommentedString();
                    String comment = mlDataMap.get(s);
                    Assert.assertNotNull(comment);
                    System.out.printf("\t%s, %s\n", s, comment);
                }
            }
            System.out.println();
        }
    }

    @Test
    public void testSomething() throws Config.PGNException, IOException {
        int i = 1 << 24;
        System.out.printf("%d, %08x\n", i, i);
    }

}
