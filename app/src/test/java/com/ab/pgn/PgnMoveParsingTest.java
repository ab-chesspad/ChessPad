package com.ab.pgn;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * parsing tests
 * Created by Alexander Bootman on 8/6/16.
 */
//@Ignore
public class PgnMoveParsingTest extends BaseTest {
    final static String version = Config.version;

    private List<PgnGraph> testParsing(String pgn) throws Config.PGNException {
        List<PgnGraph> pgnGraphs = parse2PgnGraphs(pgn);
        for (PgnGraph pgnGraph : pgnGraphs) {
            logger.debug(pgnGraph.getInitBoard().toFEN());
            logger.debug(pgnGraph.getBoard().toFEN());
            String finalFen = pgnGraph.pgn.getHeader(MY_HEADER);
            if(finalFen != null) {
                Assert.assertEquals(finalFen, pgnGraph.getBoard().toFEN());
            }
        }
        return pgnGraphs;
    }

    @Test
    public void testAmbig() throws Config.PGNException {
        String pgn = "[Event \"1\"]\n" +
            "[Site \"?\"]\n" +
            "[Date \"2016.08.05\"]\n" +
            "[Round \"?\"]\n" +
            "[White \"?\"]\n" +
            "[Black \"?\"]\n" +
            "[Result \"1-0\"]\n" +
            "[FEN \"4k3/2R5/8/8/1KR3r1/8/7n/8 w - - 0 1\"]\n" +
            "[Source \"?\"]\n" +
            finalFen2Header("4k3/8/2R5/8/1KR3r1/8/7n/8 b - - 1 1") +
            "\n" +
            "1. Rc6  1-0\n\n" +
            "\n";
        testParsing(pgn);
    }

    @Test
    public void testParsingSimple() throws Config.PGNException {
        String pgn = "[Event \"?\"]\n" +
            "[Site \"Hastings\"]\n" +
            "[Date \"1951.??.??\"]\n" +
            "[Round \"?\"]\n" +
            "[White \"Barden, Leonard W\"]\n" +
            "[Black \"Adams, Michael\"]\n" +
            "[Result \"1-0\"]\n" +
            "[ECO \"C57\"]\n" +
            finalFen2Header("2rk1b1r/pp4pp/3PP3/1q6/5Q2/8/PP4PP/3R1R1K b - - 0 27") +
            "\n" +
            "1.e4 e5 2.Nf3 Nc6 3.Bc4 Nf6 4.Ng5 d5 5.exd5 Nxd5 6.d4 Bb4+ 7.c3 Be7 8.Nxf7\n" +
            "Kxf7 9.Qf3+ Ke6 10.Qe4 Bf8 11.O-O Ne7 12.f4 c6 13.fxe5 Kd7 14.Be2 Ke8 15.\n" +
            "c4 Nc7 16.Nc3 Be6 17.Bg5 Qd7 18.Rad1 Rc8 19.Bxe7 Qxe7 20.d5 Qc5+ 21.Kh1 \n" +
            "cxd5 22.cxd5 Bd7 23.e6 Bb5 24.Qf4 Kd8 25.Bxb5 Nxb5 26.Nxb5 Qxb5 27.d6 1-0 ";

        testParsing(pgn);
    }

    @Test
    public void testParsingVariants() throws Config.PGNException {
        String pgn =
            "[White \"merge\"]\n" +
            "[Black \"variations \"]\n" +
            "\n" +
            "1. e4 Nf6 (1. ... Nc6 2. Nf3 (2. Nc3 Nf6) 2. ... Nf6 3. Nc3 e6) (1. ... e5 2. Bc4) 2. Nc3 Nc6 3. Nf3 e5" +
            "";
        testParsing(pgn);
    }

    @Test
    public void testParsingAnnotated() throws Config.PGNException, IOException {
        String pgn =
            "[Event \"23rd USSR ch\"]\n" +
            "[Site \"\"]\n" +
            "[Date \"1956\"]\n" +
            "[White \"Tal,M\"]\n" +
            "[Black \"Simagin\"]\n" +
            "[Result \"1-0\"]\n" +
            "[Source \"Exeter Chess Club\"]\n" +
            "[Annotator \"DrDave\"]\n" +
            finalFen2Header("8/P7/2p4p/8/6PK/2P1Qk2/2P1r2P/8 b - - 0 45") +
            "{A real sacrifice}\n" +
            "1. e4 c6 2. d4 d6 3. Nc3 Nf6 4. f4 Qb6 5. Nf3 Bg4 6. Be2 Nbd7 7. e5 Nd5 8. O-O Nxc3 9. bxc3\n" +
            "9... e6\n" +
            "(9... Bxf3 10. Bxf3 dxe5 11. fxe5 Nxe5 12. Ba3)\n" +
            "10. Ng5 Bxe2 11. Qxe2 h6\n" +
            "12. Nxf7\n" +
            "{\n" +
            "Easy to see, hard to play! Simagin undoubtedly expected this move and was deliberately inviting it, judging that Tal was bluffing. Bravery from both players, then!\n" +
            "Spielmann calls this type of move a 'real' sacrifice as opposed to those sacrificial combinations where the hoped-for gain is clear and short- term. We have seen already a Tal sacrifice in the game against Averbakh where the omens may have been good but the precise justification was not obvious.\n" +
            "}\n" +
            "12... Kxf7 13. f5 dxe5 14. fxe6+ Kxe6 15. Rb1 Qxb1 16. Qc4+ Kd6 17. Ba3+ Kc7 18. Rxb1 Bxa3 19. Qb3 Be7 20. Qxb7+ Kd6\n" +
            "21. dxe5+ (21. Rd1 $1 $18) Nxe5 22. Rd1+ Ke6 23. Qb3+ Kf5 24. Rf1+\n" +
            "24... Ke4\n" +
            "(24... Kg6 25. Qe6+ Bf6 26. Qf5+ Kf7 27. Qxe5)\n" +
            "25. Re1+ Kf5 26. g4+ Kf6 27. Rf1+ Kg6 28. Qe6+ Kh7 29. Qxe5 Rhe8 30. Rf7 Bf8 31. Qf5+ Kg8 32. Kf2 Bc5+ 33. Kg3 Re3+ 34. Kh4 Rae8 35. Rxg7+ Kxg7 36. Qxc5 R8e6 37. Qxa7+ Kg6 38. Qa8 Kf6 39. a4 Ke5 40. a5 Kd5 41. Qd8+ Ke4 42. a6 Kf3 43. a7 Re2 44. Qd3+ R6e3 45. Qxe3+\n" +
            "1-0\n"+

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
            finalFen2Header("4nk2/1b3pp1/p2b4/1p6/2p4B/2P5/PPB2PPP/5NK1 b - - 1 14") +
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

            "\n";

        List<PgnGraph> pgnGraphs = testParsing(pgn);
        for(PgnGraph pgnGraph : pgnGraphs) {
            BitStream.Writer writer = new BitStream.Writer();
            pgnGraph.serializeGraph(writer, TEST_SERIALIZATION_VERSION);
            PgnGraph unserialized = new PgnGraph(new BitStream.Reader(writer), TEST_SERIALIZATION_VERSION, null);
            unserialized.toEnd();
            pgnGraph.toEnd();
            Assert.assertEquals(pgnGraph.getBoard().toString(), unserialized.getBoard().toString());
            Assert.assertTrue(areEqual(pgnGraph.rootMove, unserialized.rootMove));
            Assert.assertTrue(areEqual(pgnGraph, unserialized));
        }
    }

    @Test
    public void testRepetition() throws Config.PGNException {
        String pgn =  "[White \"Fischer,M\"]\n" +
            "[Black \"Petrosian\"]\n" +
            "\n"+
            "1. e4 e6 2. d4 d5 3. Nc3 Nf6 4. Bg5 de4 5. Ne4 Be7 6. Bf6 gf6 7. g3 f5 8. Nc3 Bf6 9. Nge2 Nc6 10. d5 ed5 11. Nd5 Bb2 12. Bg2 O-O 13. O-O Bh8 14. Nef4 Ne5 " +
            "15. Qh5 Ng6 16. Rad1 c6 17. Ne3 Qf6 18. Kh1 Bg7 19. Bh3 Ne7 20. Rd3 Be6 21. Rfd1 Bh6 22. Rd4 Bf4 23. Rf4 Rad8 24. Rd8 Rd8 25. Bf5 Nf5 26. Nf5 Rd5 27. g4 Bf5 28. gf5 h6 29. h3 Kh7 " +
            "30. Qe2 Qe5 31. Qh5 Qf6 32. Qe2 Re5 33. Qd3 Rd5 34. Qe2\n"+
            "";
        List<PgnGraph> pgnGraphs = parse2PgnGraphs(pgn);
        logger.debug(pgnGraphs.get(0).toPgn());
        Assert.assertTrue((pgnGraphs.get(0).moveLine.getLast().moveFlags & Config.FLAGS_REPETITION) != 0);
    }

    @Test
    public void testTal0() throws Config.PGNException {
        String pgn = "[Event \"\"]\n" +
            "[Site \"\"]\n" +
            "[Date \"1956\"]\n" +
            "[White \"Tal,M\"]\n" +
            "[Black \"Lisitsin\"]\n" +
            "[Result \"1-0\"]\n" +
            "[Source \"Exeter Chess Club\"]\n" +
            "[Annotator \"DrDave\"]\n" +
            finalFen2Header("8/1P6/2K5/6kP/1R6/3P1p2/5r2/8 b - - 0 54") +
            "\n" +
            "{Active King in the Ending}\n" +
            "1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 g6 6. f4 Nc6 7. Nxc6 bxc6 8. e5 Nd7 9. exd6 exd6 10. Be3 Be7 11. Qf3 d5 12. O-O-O Bf6 13. Bd4 O-O 14. h4 Rb8 15. Qf2 Rb4 16. Bxf6 Nxf6 17. a3 Qb6 18. Qxb6 Rxb6 19. Na4 Rb7 20. Bd3 Nh5 21. Rhf1 Re7\n" +
            "{\n" +
            "How to save the f-pawn?\n" +
            "}\n" +
            "22. f5 $1\n" +
            "{\n" +
            "Can't be finish, but Tal gives it up for a high price - scrambled pawns.\n" +
            "}\n" +
            "22... gxf5 23. Rfe1 Rfe8 24.Rxe7 Rxe7 25. Kd2\n" +
            "{\n" +
            "In Exeter we say \"KUFTE!\" (King Up For The Endgame!)\n" +
            "}\n" +
            "25... Ng3 26. Kc3 f4 27. Kd4 Bf5\n" +
            "{\n" +
            "And there it is, nicely posted in the middle of a lot of weak Black pawns.\n" +
            "}\n" +
            "28. Rd2 Re6 29. Nc5 Rh6 30. Ke5 $1 Bxd3 31. cxd3 Rxh4 32. Kd6 Rh6+ 33. Kc7 Nf5 34. Kb7 Nd4 35. Rf2 a5 36. Rxf4 Ne6 37. Rg4+ Kf8\n" +
            "{\n" +
            "The game is decided all in the position of the two Kings.\n" +
            "}\n" +
            "38. Kxc6 $1 38... Nxc5+ 39. Kxc5 Re6 40. Kxd5 Rb6 41. b4 axb4 42. axb4 Ke7\n" +
            "{\n" +
            "Too late\n" +
            "}\n" +
            "43. Kc5 Rf6 44. Rd4 Rf5+ 45. Kb6 Rf6+ 46. Kc7 Rf5 47. Re4+ Kf6 48. Kc6 Rf2 49. g4 h5 50. gxh5 Kg5 51. b5 f5 52. Rb4 f4 53. b6 f3 54. b7\n" +
            "(54. b7 Rc2+ 55. Kd5 f2 56. b8=Q f1=Q 57. Qg3+ Kf6 58. Qe5+ Kf7 59. Rb7+ Rc7 60. Rxc7+ Kf8 61. Qh8#)\n" +
            "1-0\n";

        testParsing(pgn);
    }

    @Test
    public void testTal1() throws Config.PGNException {
        String pgn = "[Event \"Cambridge Springs\"]\n" +
            "[Site \"?\"]\n" +
            "[Date \"1904.??.??\"]\n" +
            "[Round \"2\"]\n" +
            "[White \"aMarco, Georg\"]\n" +
            "[Black \"Lasker, Emanuel\"]\n" +
            "[Result \"1/2-1/2\"]\n" +
            "[ECO \"C77\"]\n" +
            "[Annotator \"JvR\"]\n" +
            "[PlyCount \"91\"]\n" +
            "[EventDate \"1904.??.??\"]\n" +
            "\n" +
            "1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. Nc3 d6 6. d3 Be7 7. Bg5 0-0 8. h3 \n" +
            "Be6 9. Bb3 Nd7 10. Bxe7 Nxe7 11. d4 exd4 12. Nxd4 Nc5 13. Bxe6 Nxe6 14. 0-0 Ng6 \n" +
            "15. g3 Qd7 16. Nf5 Rfe8 17. h4 Ne7 18. Nxe7+ Qxe7 19. Nd5 Qd8 20. Kg2 c6 21. Nc3\n" +
            " Qb6 22. Qd2 Rad8 23. Rae1 Nc5 24. b3 Qa5 25. a4 Ne6 26. Re2 Qh5 27. f4 f5 28.\n" +
            " Rfe1 fxe4 29. Nxe4 Nc7 30. Ng5 h6 31. Ne6 Nxe6 32. Rxe6 Rxe6 33. Rxe6 d5 34.\n" +
            " Re7 Re8 35. Qe3 Rxe7 36. Qxe7 Qf5 37. Qe8+ Kh7 38. Qe2 a5 39. h5 Qd7 40. g4 b5 \n" +
            "41. Qd3+ Kg8 42. Qg6 bxa4 43. bxa4 c5 44. Kg3 Qe7 45. Kf3 Qe1 46. Qf5 {An\n" +
            " uneventful draw.} \n";

        testParsing(pgn);
    }

    @Test
    public void testUpdateZip() throws Exception {
        String root = TEST_TMP_ROOT;
        PgnItem.setRoot(new File(root));
        File testFile = new File(String.format("%stest.zip", root));
        PgnItem.copy(new File(TEST_ROOT + "newyork1924.zip"), testFile);
        List<PgnItem> items = getZipItems(testFile.getAbsolutePath());
        int count = items.size();
        int testIndex = 1;
        if(testIndex >= count) {
            testIndex = 0;
        }
        PgnItem.Item item = (PgnItem.Item) items.get(testIndex);
        item.setIndex(-1);      // append
        item.save(null);            // append with no moveText
        ++count;
        List<PgnItem> items1 = getZipItems(testFile.getAbsolutePath());
        Assert.assertEquals(count, items1.size());

        item.setMoveText(null);
        item.setIndex(0);
        for(int i = 0; i < count; ++i) {
            item.save(null);    // delete #0 since moveText is null
            items1 = getZipItems(testFile.getAbsolutePath());
            if(i == count - 1) {
                Assert.assertNull(items1);
                break;
            }
            Assert.assertEquals(count, items1.size() + i + 1);
        }
    }

    private List<PgnItem> getZipItems(String path) throws Config.PGNException {
        File test = new File(path);
        if(!test.exists()) {
            return null;
        }
        PgnItem zip = new PgnItem.Zip(path);
        List<PgnItem> list = zip.getChildrenNames(null);
        Assert.assertEquals("Zip file unsuitable for this test, must contain a single pgn file", list.size(), 1);
        PgnItem pgn = list.get(0);
        return pgn.getChildrenNames(null);
    }

    @Test
    public void testUpdatePgn() throws Exception {
        String root = TEST_TMP_ROOT;
        PgnItem.setRoot(new File(root));
        File testFile = new File(String.format("%stest.pgn", root));
        PgnItem.copy(new File(TEST_ROOT + "exeter_lessons_from_tal.pgn"), testFile);
        List<PgnItem> items = getPgnItems(testFile.getAbsolutePath());
        int count = items.size();
        int testIndex = 1;
        if(testIndex >= count) {
            testIndex = 0;
        }
        PgnItem.Item item = (PgnItem.Item) items.get(testIndex);
        item.setIndex(-1);      // append
        item.save(null);        // append with no moveText
        ++count;
        List<PgnItem> items1 = getPgnItems(testFile.getAbsolutePath());
        Assert.assertEquals(count, items1.size());

        item.setMoveText(null);
        item.setIndex(0);
        for(int i = 0; i < count; ++i) {
//            logger.debug(String.format("delete %s", i));
            item.save(null);    // delete #0 since moveText is null
            items1 = getPgnItems(testFile.getAbsolutePath());
            if(i == count - 1) {
                Assert.assertNull(items1);
                break;
            }
            Assert.assertEquals(count, items1.size() + i + 1);
        }
    }

    private List<PgnItem> getPgnItems(String path) throws Config.PGNException {
        File test = new File(path);
        if(!test.exists()) {
            return null;
        }
        PgnItem pgn = new PgnItem.Pgn(path);
        return pgn.getChildrenNames(null);
    }

    @Test
//    @Ignore("Just prints file content")
    public void testZipSimple() throws Exception {
        String root = TEST_TMP_ROOT;
        PgnItem.setRoot(new File(root));
        File testFile = new File(String.format("%s/test.zip", root));
        PgnItem.copy(new File(TEST_ROOT + "adams.zip"), testFile);
        PgnItem zip = new PgnItem.Zip(testFile.getAbsolutePath());
        List<PgnItem> list = zip.getChildrenNames(null);
        for (PgnItem pgn : list) {
            logger.debug(String.format("%s, %s", pgn.getClass().toString(), pgn.getName()));
            List<PgnItem> items = pgn.getChildrenNames(null);
            for (PgnItem item : items) {
                logger.debug(item.toString());
                PgnGraph pgnGraph = new PgnGraph((PgnItem.Item) item, null);
                logger.debug(String.format("[%s \"%s\"]\n", MY_HEADER, pgnGraph.getBoard().toFEN()));
            }
        }
    }

    @Test
//    @Ignore("Just prints file content")
    public void testPgnAnnotated() throws Exception {
        String pgn = TEST_ROOT + "exeter_lessons_from_tal.pgn";
        BufferedReader br = new BufferedReader(new FileReader(pgn));
        final List<PgnItem> items = new LinkedList<>();
        PgnItem.parsePgnItems(null, br, new PgnItem.EntryHandler() {
            @Override
            public boolean handle(PgnItem entry, BufferedReader bufferedReader) throws Config.PGNException {
                items.add(entry);
                return true;
            }

            @Override
            public boolean getMoveText(PgnItem entry) {
                return true;
            }

            @Override
            public void addOffset(int length, int totalLength) {

            }
        });

        for(PgnItem item : items) {
            PgnGraph pgnGraph = new PgnGraph((PgnItem.Item)item, null);
            logger.debug(String.format("[%s \"%s\"]\n", MY_HEADER, pgnGraph.getBoard().toFEN()));
        }
    }

    @Test
//    @Ignore("Just prints file content")
    public void testZipAnnotated() throws Exception {
        String root = TEST_TMP_ROOT;
        PgnItem.setRoot(new File(root));
        File testFile = new File(String.format("%s/test.zip", root));
        PgnItem.copy(new File(TEST_ROOT + "newyork1924.zip"), testFile);
        PgnItem zip = new PgnItem.Zip(testFile.getAbsolutePath());
        List<PgnItem> list = zip.getChildrenNames(null);
        for (PgnItem pgn : list) {
            logger.debug(String.format("%s, %s", pgn.getClass().toString(), pgn.getName()));
            List<PgnItem> items = pgn.getChildrenNames(null);
            for (PgnItem item : items) {
                logger.debug(item.toString());
                PgnGraph pgnGraph = new PgnGraph((PgnItem.Item) item, null);
                logger.debug(String.format("[%s \"%s\"]\n", MY_HEADER, pgnGraph.getBoard().toFEN()));
            }
        }
    }

    @Test
    public void testPgnParser() throws Config.PGNException {
        final String comment1 = "{{Active {King in the}} Ending}";
        final String comment2 = "{How to {save} the f-pawn?}";
        String pgn =
            "\n" +
            "$15 {" + comment1 + "}\n" +
            "1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 o-o \n" +
            "{" + comment2 + "}\n" +
            "1-0\n";

        PgnParser.parseMoves(pgn, new PgnParser.MoveTextHandler() {
            @Override
            public void onComment(String value) {
                if (value.equals(comment1) || value.equals(comment2)) {
                    return;    // ok
                }
                Assert.assertTrue(String.format("Invalid comment {%s}", value), false);
                logger.debug(String.format("comment %s", value));
            }

            @Override
            public void onGlyph(String value) {
                logger.debug(String.format("glyph %s", value));
            }

            @Override
            public boolean onMove(String moveText) throws Config.PGNException {
                logger.debug(String.format("move %s", moveText));
                return true;
            }

            @Override
            public void onVariantOpen() {
                logger.debug(String.format("variant ("));
            }

            @Override
            public void onVariantClose() {
                logger.debug(String.format("variant )"));
            }
        }, null);
    }
}
