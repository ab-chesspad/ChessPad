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

 * Created by Alexander Bootman on 10/29/17.
 */
package com.ab.pgn;

import com.ab.pgn.io.CpFile;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class PgnGraphTest extends BaseTest {
    private static final String LOG_DIR_NAME = "log/";
    private static final String LOG_FILE_WRITER_NAME = TEST_TMP_ROOT + LOG_DIR_NAME + "graph-w.log";
    private static final String LOG_FILE_READER_NAME = TEST_TMP_ROOT + LOG_DIR_NAME + "graph-r.log";

    private static final int TEST_GLYPH = 79;
    private static final String TEST_COMMENT = "Test comment";

    private List<PgnGraph> testParsing(String pgn) throws Config.PGNException {
        List<PgnGraph> pgnGraphs = parse2PgnGraphs(pgn);
        for (PgnGraph pgnGraph : pgnGraphs) {
            if (DEBUG) {
                logger.debug(pgnGraph.getInitBoard().toFEN());
                logger.debug(pgnGraph.getBoard().toFEN());
            }
            String finalFen = pgnGraph.pgnItem.getTag(MY_TAG);
            if (!finalFen.equals(Config.TAG_UNKNOWN_VALUE)) {
                Assert.assertEquals(finalFen, pgnGraph.getBoard().toFEN());
            }
            String tags = new String(pgnGraph.getPgnItem().tagsToString(true, false));
            String resPgn = tags + "\n" + pgnGraph.toPgn();
            if (DEBUG) {
                System.out.println(resPgn);
            }
            Assert.assertEquals(0, pgnGraph.getNumberOfMissingVertices());

            List<PgnGraph> resPgnGraphs = parse2PgnGraphs(resPgn);
            Assert.assertEquals(1, resPgnGraphs.size());
            PgnGraph resPgnGraph = resPgnGraphs.get(0);
            Assert.assertTrue(String.format("diff:\n%s\n%s", resPgn, resPgnGraph.toPgn()), areEqual(pgnGraph, resPgnGraph));
        }

        for (PgnGraph pgnGraph: pgnGraphs) {
            testFindMove(pgnGraph);
        }
        return pgnGraphs;
    }

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
            "14. Bh6#) 14. Bxg8) 12. Rxe8+ Rxe8 13. Rxe8+ Nxe8 14. Bc2 $18 1-0\n"+
            "";
        testParsing(pgn);
    }

    private void testFindMove(PgnGraph pgnGraph) {
        ListIterator<Move> li = pgnGraph.moveLine.listIterator(pgnGraph.moveLine.size());
        Board nextBoard = null;
        Move nextMove = null;
        while (li.hasPrevious()) {
            Move move = li.previous();
            Board board = pgnGraph.getBoard();
            System.out.println(String.format("%s\n%s", move, board));
            if (nextBoard != null) {
                Move _move = board.findMove(nextBoard);
                Assert.assertTrue(nextMove.isSameAs(_move));
            }
            nextMove = move;
            nextBoard = board;
            if (move != pgnGraph.rootMove) {
                li.remove();
            }
        }
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
        List<PgnGraph> graphs = parse2PgnGraphs(pgn);
        Assert.assertEquals(3, graphs.size());
        Assert.assertTrue(areEqual(graphs.get(2), graphs.get(1)));

        PgnGraph pgnGraph = graphs.get(0);
        String s = pgnGraph.toPgn();
        Assert.assertTrue(s.indexOf("Nf6 $5 {main; v1}") > 0);
        Assert.assertTrue(s.trim().endsWith("4. c3 $21 {main; v2; v1} *"));
        System.out.println(s);
        List<String> movesText = pgnGraph.getMovesText();
        for (String part : movesText) {
            System.out.println(part);
        }
    }

    @Test
    public void testDelMainMove() throws Config.PGNException {
        String pgn =
            "[White \"merge\"]\n" +
            "[Black \"variations\"]\n" +
            "{Merge variations test}" +
            "1.e4 e5 2.Nf3 {main} (2.Bc4 {v1} Nc6 {v1} 3.Nf3 {v1} Nf6 {v1} $4 4.c3{v1} $7) (2.Bc4 {v2} Nf6 {v2} 3.Nf3 {v2} Nc6 {v2} $20 4.c3{v2} $21) 2. ... Nc6 {main} 3.Bc4{main} Nf6{main} $5 4.c3{main}\n" +
            "\n";
        List<PgnGraph> graphs = parse2PgnGraphs(pgn);
        Assert.assertEquals(1, graphs.size());
        PgnGraph graph = graphs.get(0);
        String s = graph.toPgn();
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());
        Assert.assertTrue(s.indexOf("Nf6 $5 {main; v1}") > 0);
        Assert.assertTrue(s.trim().endsWith("4. c3 $21 {main; v2; v1} *"));

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
//        graph.setModified(false);
//        Assert.assertFalse(graph.isModified());
    }

    @Test
    public void testDelVariantMove() throws Config.PGNException {
        String pgn =
            "[White \"merge\"]\n" +
            "[Black \"variations\"]\n" +
            "{Merge variations test}" +
            "1.e4 e5 2.Nf3 {main} (2.Bc4 {v1} Nc6 {v1} 3.Nf3 {v1} Nf6 {v1} $4 4.c3{v1} $7) (2.Bc4 {v2} Nf6 {v2} 3.Nf3 {v2} Nc6 {v2} $20 4.c3{v2} $21) 2. ... Nc6 {main} 3.Bc4{main} Nf6{main} $5 4.c3{main}\n" +
            "\n";
        List<PgnGraph> graphs = parse2PgnGraphs(pgn);
        Assert.assertEquals(1, graphs.size());
        PgnGraph graph = graphs.get(0);
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
        Assert.assertTrue(graph.getParsingError().startsWith("invalid move 4. c5  for:"));
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
    public void testMerge_SicilianMisc2() throws Config.PGNException {
        String pgn =
            "[White \"Staunton, Howard\"]\n" +
            "[Black \"Cochrane, John Miles\"]\n" +
            "1.e4 c5 2.c4" +
            "\n";
        List<PgnGraph> graphs = parse2PgnGraphs(pgn);
        Assert.assertEquals(1, graphs.size());
        PgnGraph graph = graphs.get(0);
        Move m = graph.getCurrentMove();
        Board b = graph.getBoard();

        CpFile.PgnFile pgnFile = (CpFile.PgnFile)CpFile.fromPath("SicilianMisc2.pgn");
        PgnGraph.MergeData md = new PgnGraph.MergeData(pgnFile);
        md.end = md.start = -1;
        md.annotate = true;
        graph.merge(md);
        logger.debug(String.format("merged %s items", md.merged));
        String s = graph.toPgn();
        logger.debug(s);
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());
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
        String fName = TEST_ROOT + "SicilianTaimanovMain-merged.pgn";
        InputStream is = new FileInputStream(fName);
        List<CpFile.PgnItem> pgnItems = parsePgnFile(null, is, true);

        Assert.assertEquals(1, pgnItems.size());
        PgnGraph graph = new PgnGraph((CpFile.PgnItem)pgnItems.get(0));
        logger.debug(graph.toPgn());
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());

        List<Move> moveLine = navigate(graph, "1.e4 c5 2. Nf3 Nc6 3.d4 e6");
        logger.debug(moveLine);
    }

    @Test
    public void testMerge_SicilianGranPrix() throws Config.PGNException {
        String pgnFileName = "SicilianGrandPrix.pgn";

        Object[][] allMergeData = {
            {"e4 c5 Nc3 Nc6 f4", new int[]{6, 620472, 10407}},
            {"Nc3 Nf6 Nb1 Ng8 Nc3 c5 f4 Nc6 e4", new int[]{10, 620476, 10407}},
        };
        PgnGraph[] mergedGraphs = new PgnGraph[allMergeData.length];

        int i = -1;
        for (Object[] data : allMergeData) {
            String moveLine = (String)data[0];
            int[] metrics = (int[])data[1];
            List<PgnGraph> graphs = parse2PgnGraphs(moveLine);
            Assert.assertEquals(1, graphs.size());
            PgnGraph graph = graphs.get(0);

            CpFile.PgnFile pgn = (CpFile.PgnFile)CpFile.fromPath(pgnFileName);
            PgnGraph.MergeData md = new PgnGraph.MergeData(pgn);
            md.end = md.start = -1;
            md.annotate = true;
            graph.merge(md);
            Assert.assertEquals(metrics[2], md.merged);
            Assert.assertEquals(metrics[1], graph.positions.size());
            Assert.assertEquals(metrics[0], graph.moveLine.size());

            // remove all moves preceding merge:
            Pack pack = null;
            for (Move m : graph.moveLine) {
                if (pack == null) {
                    pack = new Pack(m.packData);  // init board
                    continue;
                }
                Board b = graph.positions.remove(pack);
                String x = b.toString();
                logger.debug(x);
                pack = new Pack(m.packData);  // init board
            }
            graph.rootMove.packData = graph.moveLine.getLast().packData;
            mergedGraphs[++i] = graph;
        }
        for (i = 1; i < mergedGraphs.length; ++i) {
            Assert.assertTrue(areEqual(mergedGraphs[0], mergedGraphs[i]));
        }
    }

    @Test
    public void testMerge_MaxLangeAttack() throws Config.PGNException, FileNotFoundException {
        String pgn =
            "[White \"Max Lange\"]\n" +
            "[Black \"Attack\"]\n" +
            "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4.d4" +
            "\n";
        List<PgnGraph> graphs = parse2PgnGraphs(pgn);
        Assert.assertEquals(1, graphs.size());
        PgnGraph graph = graphs.get(0);
        Move m = graph.getCurrentMove();
        Board b = graph.getBoard();

        String fName = "GiuocoPiano";
        CpFile.PgnFile pgnFile = (CpFile.PgnFile)CpFile.fromPath("GiuocoPiano.zip/" + fName + ".pgn");
        PgnGraph.MergeData md = new PgnGraph.MergeData(pgnFile);
        md.end = md.start = -1;
        md.annotate = true;
        graph.merge(md);
        Assert.assertEquals(108, md.merged);
        Assert.assertEquals(6302, graph.positions.size());
        Assert.assertEquals(8, graph.moveLine.size());
        String s = graph.toPgn();
        logger.debug(s);
        Assert.assertEquals(0, graph.getNumberOfMissingVertices());
        PrintStream ps = new PrintStream(new FileOutputStream(TEST_TMP_ROOT + pgnFile + "-merged.pgn"));
        ps.print(s);
        ps.flush();
        ps.close();
    }

    @Test
    public void testMerge_MaxLangeAttackMain() throws Config.PGNException {
        Board.DEBUG = false;
        PgnGraph.DEBUG = DEBUG;
        String pgn =
            "[White \"Max Lange Attack\"]\n" +
            "[Black \"Main\"]\n" +
            "1. e4 e5 2. Nf3 Nc6 3. Bc4 Nf6 4. d4 exd4 5. O-O Bc5" +
            "\n";
        List<PgnGraph> graphs = parse2PgnGraphs(pgn);
        Assert.assertEquals(1, graphs.size());
        PgnGraph targetGraph = graphs.get(0);
        Move m = targetGraph.getCurrentMove();
        Board b = targetGraph.getBoard();

        CpFile.PgnFile pgnFile = (CpFile.PgnFile)CpFile.fromPath("MaxLange-0.pgn");
        PgnGraph.MergeData md = new PgnGraph.MergeData(pgnFile);
        md.end = md.start = -1;
        md.annotate = true;
        targetGraph.merge(md);
        Assert.assertEquals(1, md.merged);
        Assert.assertEquals(11, targetGraph.positions.size());
        Assert.assertEquals(11, targetGraph.moveLine.size());
        String s = targetGraph.toPgn();
        logger.debug(s);
        Assert.assertEquals(0, targetGraph.getNumberOfMissingVertices());
    }

    //*
    @Test
//    @Ignore("lond test")
    public void testMerge_SicilianTaimanovMain() throws Config.PGNException, IOException {
        String fName = "SicilianTaimanovMain.pgn";
        String pgn =
            "[White \"SicilianTaimanov\"]\n" +
            "[Black \"Main\"]\n" +
            "1. e4 c5 " +
            "\n";
        List<PgnGraph> graphs = parse2PgnGraphs(pgn);
        Assert.assertEquals(1, graphs.size());
        PgnGraph graph = graphs.get(0);
        Move m = graph.getCurrentMove();
        Board b = graph.getBoard();

        CpFile.PgnFile pgnFile = (CpFile.PgnFile)CpFile.fromPath(fName);
        PgnGraph.MergeData md = new PgnGraph.MergeData(pgnFile);
        md.end = md.start = -1;
        md.annotate = true;
        graph.merge(md);
        Assert.assertEquals(29942, md.merged);
        Assert.assertEquals(1729980, graph.positions.size());
        Assert.assertEquals(3, graph.moveLine.size());
        List<Move> moveLine = navigate(graph, "1.e4 c5 2. Nf3 Nc6 3.d4 e6");
        logger.debug(moveLine);

        String s = graph.toPgn();
        PrintStream ps = new PrintStream(new FileOutputStream(TEST_TMP_ROOT + "SicilianTaimanovMain-merged.pgn"));
        ps.print(s);
        ps.flush();
        ps.close();
    }

    @Test
    public void testSerializeGraph_0() throws Config.PGNException, IOException {
        String pgn =
            "[White \"merge\"]\n" +
            "[Black \"variations\"]\n" +
            "[CustomTag1 \"custom tag 1\"]\n" +
            "[CustomTag2 \"custom tag 2\"]\n" +
            "{Merge variations test}" +
            "1.e4{1.} e5{..1} 2.Nf3 {main} (2.Bc4 {v1} Nc6 {v1} 3.Nf3 {v1} Nf6 {v1} $4 4. d4 {v1} (4.c3{v11} $7) 4. ... exd4 {v1}) (2.Bc4 {v2} Nf6 {v2} 3.Nf3 {v2} Nc6 {v2} $20 4.c3{v2} $21) (2.d4 {v3} exd4 {v3}) 2. ... Nc6 {main} 3.Bc4{main} Nf6{main} $5 4.c3{main}\n" +
            "\n";

        List<PgnGraph> graphs = parse2PgnGraphs(pgn);
        Assert.assertEquals(1, graphs.size());
        for (PgnGraph graph : graphs) {
            logger.debug(graph.toPgn());
            PgnGraph unserialized;
            PgnGraph.DEBUG = DEBUG;
            BitStream.Writer writer = new BitStream.Writer();
            graph.serializeGraph(writer, TEST_SERIALIZATION_VERSION);
            writer.close();
            Assert.assertEquals(0, graph.getNumberOfMissingVertices());
            BitStream.Reader reader = new BitStream.Reader(writer);
            unserialized = new PgnGraph(reader, TEST_SERIALIZATION_VERSION);
            logger.debug(graph.toPgn());
            Assert.assertEquals(0, graph.getNumberOfMissingVertices());
            logger.debug(unserialized.toPgn());
            Assert.assertEquals(0, unserialized.getNumberOfMissingVertices());
            Assert.assertTrue(areEqual(graph, unserialized));
        }
    }

    @Test
    public void testSerializeGraph_1() throws Config.PGNException, IOException {
        File tmpLog = new File(TEST_TMP_ROOT + LOG_DIR_NAME);
        tmpLog.mkdirs();
        String[] fNames = {
            "MaxLange-0.pgn",
            "MaxLange-00.pgn",
//            "SicilianTaimanovMain-merged.pgn",    // long file, crashes with Java heap space
        };

        for (final String fName : fNames) {
            FileInputStream fis = new FileInputStream(TEST_ROOT + fName);
            final List<CpFile.PgnItem> pgnItems = new LinkedList<>();
            CpFile.parsePgnFile(null, fis, new CpFile.EntryHandler() {
                @Override
                public boolean skip(int index) {
                    return false;
                }

                @Override
                public boolean getMovesText(int index) {
                    return true;
                }

                @Override
                public boolean handle(int index, CpFile.PgnItem entry) throws Config.PGNException {
                    pgnItems.add(entry);
                    return true;
                }
            }, true);

            for (CpFile.PgnItem pgnItem : pgnItems) {
                PgnGraph graph = new PgnGraph(pgnItem);
                logger.debug(graph.toPgn());
                Assert.assertEquals(0, graph.getNumberOfMissingVertices());

                PgnLogger.setFile(LOG_FILE_WRITER_NAME);
                String serFileName = TEST_TMP_ROOT + fName + ".ser";
                PgnGraph unserialized;
                BitStream.Writer writer = new BitStream.Writer(new FileOutputStream(serFileName));
                graph.serializeGraph(writer, TEST_SERIALIZATION_VERSION);
                writer.close();
                Assert.assertEquals(0, graph.getNumberOfMissingVertices());
                PgnLogger.setFile(LOG_FILE_READER_NAME);

                BitStream.Reader reader = new BitStream.Reader(new FileInputStream(serFileName));
                unserialized = new PgnGraph(reader, TEST_SERIALIZATION_VERSION);
                Assert.assertEquals(writer.bitCount, reader.bitCount);
                PgnLogger.setFile(null);
                logger.debug(unserialized.toPgn());
                Assert.assertEquals(0, unserialized.getNumberOfMissingVertices());
                Assert.assertTrue(areEqual(graph, unserialized));
            }
        }
        PgnLogger.setFile(null);
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
    @Ignore("can run only after runBookBuilder()")
    public void testBook() throws Config.PGNException, IOException {
        String fileName = BOOK_PATH;
        File f = new File(fileName);
        long length = f.length();
        InputStream is = new FileInputStream(f);
        Book book = new Book(is, length);

        String[] moveLines = {
                "e4 c5 Nc3 Nc6 f4",
                "Nc3 Nf6 Nb1 Ng8 Nc3 c5 f4 Nc6 e4",
        };

        for (int i = 0; i < moveLines.length; ++i) {
            String moveLine = moveLines[i];
            PgnGraph graph = new PgnGraph();
            graph.parseMoves(moveLine);
            Board board = graph.getBoard();
            List<Move> moves = book.getMoves(board);
            System.out.print(board.toString());
            if (moves == null) {
                System.out.println("no book moves");
            } else {
                for (Move m : moves) {
                    System.out.printf("\t%s\n", m.toCommentedString());
                }
            }
            System.out.println();
        }
    }
}
