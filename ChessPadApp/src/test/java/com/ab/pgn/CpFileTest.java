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

 * test pgn operations
 * Created by Alexander Bootman on 7/30/16.
 */
package com.ab.pgn;

import static org.mockito.ArgumentMatchers.startsWith;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

//@Ignore
public class CpFileTest extends BaseTest {
    private final int BOOKS1_ZIP_LENGTH = 7286;

    @Test
    public void testRoot() {
        String name = "xyz";
        File root = new File(name);
        CpFile.setRoot(root);
        CpFile cpFile = new CpFile.Dir(name);
        Assert.assertTrue(cpFile.getParent().isRoot());
    }

    @Test
//    @Ignore("Just prints dir content")
    public void testDir() throws Exception {
        int testItemIndex = 1;
        CpFile dir = new CpFile.Dir(".");
        List<CpFile> list = dir.getChildrenNames(null);
        for (CpFile item : list) {
            if(DEBUG) {
                logger.debug(String.format("%s, %s", item.getClass().toString(), item.getName()));
            }
            if (item instanceof CpFile.Pgn || item instanceof CpFile.Zip) {
                List<CpFile> items = item.getChildrenNames(null);
                for (CpFile p : items) {
                    if(DEBUG) {
                        logger.debug(String.format("\t%s", p.toString()));
                    }
                    if (p instanceof CpFile.Item) {
                        if (p.index == testItemIndex) {
                            // pgn
                            CpFile.getPgnFile((CpFile.Item)p, null);
                            if(DEBUG) {
                                logger.debug(String.format("\t%s", ((CpFile.Item) p).getMoveText()));
                            }
                        }
                    } else if (p instanceof CpFile.Pgn) {
                        // zip
                        List<CpFile> children = p.getChildrenNames(null);
                        for (CpFile c : children) {
                            if(DEBUG) {
                                logger.debug(String.format("\t\t%s", c.toString()));
                            }
                            if (c.index == testItemIndex) {
                                CpFile.getPgnFile((CpFile.Item)c, null);
                                if(DEBUG) {
                                    logger.debug(String.format("\t%s", ((CpFile.Item) c).getMoveText()));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testZip() throws Exception {
        int testItemIndex = 1;
        CpFile zip = new CpFile.Zip("books1.zip");
        Assert.assertEquals(BOOKS1_ZIP_LENGTH, zip.getLength());
        List<CpFile> list = zip.getChildrenNames(null);
        for (CpFile pgn : list) {
            logger.debug(String.format("%s, %s", pgn.getClass().toString(), pgn.getName()));
            List<CpFile> items = pgn.getChildrenNames(null);
            for (CpFile item : items) {
                if (item.index == testItemIndex) {
                    CpFile.getPgnFile((CpFile.Item)item, null);
                }
                logger.debug(item.toString());
            }
        }
    }

    @Test
    public void testParsePgnFiles() throws Config.PGNException {
        String pgn =
            "{No tags pgn} 1. e4 e5 2. Nf3 Nc6 3. Bc4 Nf6 4. d4 exd4\n" +
            "\n" +
            "[\"]\n" +
            "{Empty tag pgn} 1. e4 e5 2. Nf3 Nc6 3. Bc4 Nf6 4. d4 exd4\n" +
            "\n" +
            "[Event \"\\\"Lloyds Bank\\\" op\"]\n" +
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

            "[Event \"Lloyds Bank op\"]\n" +
            "[Site \"London\"]\n" +
            "[Date \"1984.??.??\"]\n" +
            "[Round \"3\"]\n" +
            "[White \"Adams, Michael\"]\n" +
            "[Black \"Dickenson, Neil F\"]\n" +
            "[Result \"1-0\"]\n" +
            "[WhiteElo \"\"]\n" +
            "[BlackElo \"2230\"]\n" +
            "[ECO \"C07\"]\n" +
            "\n" +
            "1.e4 e6 2.d4 d5 3.Nd2 c5 4.exd5 Qxd5 5.Ngf3 cxd4 6.Bc4 Qd6 7.O-O Nf6 8.Nb3 Nc6\n" +
            "9.Nbxd4 Nxd4 10.Nxd4 a6 11.Nf3 b5 12.Bd3 Bb7 13.a4 Ng4 14.Re1 Qb6 15.Qe2 Bc5\n" +
            "16.Rf1 b4 17.h3 Nf6 18.Bg5 Nh5 19.Be3 Bxe3 20.Qxe3 Qxe3 21.fxe3 Ng3 22.Rfe1 Ne4\n" +
            "23.Ne5 Nc5 24.Bc4 Ke7 25.a5 Rhd8 26.Red1 Rac8 27.b3 Rc7 28.Rxd8 Kxd8 29.Nd3 Nxd3\n" +
            "30.Bxd3 Rc5 31.Ra4 Kc7 32.Kf2 g6 33.g4 Bc6 34.Rxb4 Rxa5 35.Rf4 f5 36.g5 Rd5\n" +
            "37.Rh4 Rd7 38.Bxa6 Rd2+ 39.Ke1 Rxc2 40.Rxh7+ Kd6 41.Bc4 Bd5 42.Rg7 Rh2 43.Rxg6 Rxh3\n" +
            "44.Kd2 Rg3 45.Rg8 Bxc4 46.bxc4 Kc5 47.g6 Kd6 48.c5+ Kc7 49.g7 Kb7 50.c6+  1-0\n" +

            "[Event \"Lloyds Bank op\"]\n" +
            "[Site \"London\"]\n" +
            "[Date \"1984.??.??\"]\n" +
            "[Round \"4\"]\n" +
            "[White \"Hebden, Mark\"]\n" +
            "[Black \"Adams, Michael\"]\n" +
            "[Result \"1-0\"]\n" +
            "[WhiteElo \"2480\"]\n" +
            "[BlackElo \"\"]\n" +
            "[ECO \"B10\"]\n" +
            "\n" +
            "1.e4 c6 2.c4 d5 3.exd5 cxd5 4.cxd5 Nf6 5.Nc3 g6 6.Bc4 Bg7 7.Nf3 O-O 8.O-O Nbd7\n" +
            "9.d3 Nb6 10.Qb3 Bf5 11.Re1 h6 12.a4 Nfd7 13.Be3 a5 14.Nd4 Nxc4 15.dxc4 Nc5\n" +
            "16.Qa3 Nd3 17.Nxf5 gxf5 18.Red1 Ne5 19.b3 Ng4 20.Qc1 f4 21.Bd4 Bxd4 22.Rxd4 e5\n" +
            "23.Rd2 Qh4 24.h3 Nf6 25.Qe1 Qg5 26.Ne4 Nxe4 27.Qxe4 f5 28.Qxe5 Rae8 29.h4 Qxh4\n" +
            "30.Qc3 Re4 31.d6 Qg5 32.f3 Re3 33.Qxa5 Rfe8 34.Rf2 Qf6 35.Rd1 R3e5 36.d7  1-0";

        BufferedReader br = new BufferedReader(new StringReader(pgn));
        final List<CpFile> items = new LinkedList<>();
        CpFile.parsePgnFiles(null, br, new CpFile.EntryHandler() {
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

            @Override
            public boolean skip(CpFile entry) {
                return false;
            }
        });
        Assert.assertEquals(5, items.size());

        for (CpFile p : items) {
            logger.debug(p.toString());
        }
    }

    @Test
    public void testZipRemoveComments() throws Exception {
        File origFile = new File(TEST_ROOT + "books1.zip");
        String root = TEST_TMP_ROOT;
        CpFile.setRoot(new File(root));
        File testFile = new File(String.format("%s/test.zip", root));
        CpFile.copy(origFile, testFile);

        CpFile zip = new CpFile.Zip(testFile.getAbsolutePath());
        Assert.assertEquals(BOOKS1_ZIP_LENGTH, zip.getLength());
        List<CpFile> list = zip.getChildrenNames(null);
        for (CpFile pgn : list) {
//            logger.debug(String.format("%s, %s", pgn.getClass().toString(), pgn.getName()));
            List<CpFile> items = pgn.getChildrenNames(null);
            for (CpFile src : items) {
                CpFile.Item item = (CpFile.Item)src;
                CpFile.getPgnFile(item, null);
                String moveText = item.getMoveText().replaceAll("(?s)\\{.*?}", "");
                if(!moveText.equals(item.getMoveText())) {
                    final int[] pgnOffset = {0};
                    item.setMoveText(moveText);
                    item.save(progress -> {
                        pgnOffset[0] = progress;
                        return false;
                    });
//                logger.debug(String.format("\t%s", item.getMoveText()));
                    if(DEBUG) {
                        logger.debug(String.format("%s offset=%s", pgn.getName(), pgnOffset[0]));
                    }
                    Assert.assertTrue(pgnOffset[0] > 95);
                }
            }
        }
    }

    @Test
    public void testDirRemoveComments() throws Exception {
        File root = new File(TEST_TMP_ROOT);
        CpFile.setRoot(root);
        copyDirectory(new File(TEST_ROOT), root);

        int testItemIndex = 1;
        CpFile dir = new CpFile.Dir(root.getAbsolutePath());
        List<CpFile> list = dir.getChildrenNames(null);
        for (CpFile item : list) {
            logger.debug(String.format("%s, %s", item.getClass().toString(), item.getName()));
            if (item instanceof CpFile.Pgn || item instanceof CpFile.Zip) {
                List<CpFile> items = item.getChildrenNames(null);
                for (CpFile p : items) {
                    if(DEBUG) {
                        logger.debug(String.format("\t%s", p.toString()));
                    }
                    if (p instanceof CpFile.Pgn) {
                        // zip
                        final int[] pgnOffset = {0};
                        List<CpFile> children = p.getChildrenNames(null);
                        for (CpFile c : children) {
                            if(DEBUG) {
                                logger.debug(String.format("\t\t%s", c.toString()));
                            }
                            if (c.index == testItemIndex) {
                                CpFile.getPgnFile((CpFile.Item)c, null);
                                String moveText = ((CpFile.Item)c).getMoveText().replaceAll("(?s)\\{.*?}", "");
                                ((CpFile.Item) c).setMoveText(moveText);
                                if(DEBUG) {
                                    logger.debug(String.format("\t%s", ((CpFile.Item) c).getMoveText()));
                                }
                                ((CpFile.Item) c).save(progress -> {
                                    pgnOffset[0] = progress;
                                    return false;
                                });
                                logger.debug(String.format("\t offset=%s", pgnOffset[0]));
                                Assert.assertTrue(pgnOffset[0] > 95);
                            }
                        }
                    }
                }
            }
        }
        logger.debug("finish");
    }

    @Test
    public void testParentIndex() throws Config.PGNException {
        testParentIndex(new CpFile.Dir(new File(TEST_ROOT).getAbsolutePath() + "/x/"));

        // test non-readable files
        File testDir = new File(new File(TEST_ROOT).getAbsolutePath() + "/test.pgn/");
        File[] list = testDir.listFiles((pathname) -> {
            boolean res = pathname.setReadable(false);
            return false;    // drop it, save space
        });
        testParentIndex(new CpFile.Dir(testDir.getAbsolutePath()));
        // restore
        list = testDir.listFiles((pathname) -> {
            boolean res = pathname.setReadable(true);
            return false;    // drop it, save space
        });
    }

    private void testParentIndex(CpFile parent) throws Config.PGNException {
        List<CpFile> list;
        try {
            list = parent.getChildrenNames(null);
        } catch (RuntimeException e) {
            return;     // no children
        }
        int index = -1;
        for (CpFile item : list) {
            int realIndex = item.parentIndex(parent);
            Assert.assertEquals(String.format("Invalid index of %s in %s", item.getAbsolutePath(), parent.getAbsolutePath()), ++index, realIndex);
            testParentIndex(item);
        }
    }

    @Test
    public void testRegex1() {
        String s = "1. d4 d5\n" +
            "2. c4 e6\n" +
            "3. Nc3 Nf6\n" +
            "4. Bg5 {It was Pillsbury who first demonstrated the\n" +
            "strength of the this move, which today is routine}\n" +
            "4..Be7\n" +
            "5. Nf3 Nd7\n" +
            "6. Rc1 O-O";
        String noComments = s.replaceAll("(?s)\\{.*?}", "");
        Assert.assertFalse(noComments.contains("{"));
        logger.debug("finish");
    }

    @Test
    public void testRegex2() {
        String s = "\n" +
            "{An active bishop obtained at the cost of a backward pawn  With his last move .\n" +
            ".. Pd5, Black has taken the initiative in the centre, and now threatens either\n" +
            "to gain space and freedom by ... Pd4 or else release tension by ... Pdxe4.\n" +
            "Although after exchanges on e4 Black will have a weak point at d5, which\n" +
            "White may exploit by Ne3-d5, but under the circumstances this is less\n" +
            "important} 1. exd5 (1. -- dxe4 2. dxe4 -- 3. Bb3 3... c4 {\n" +
            "White's bishop isn't active}) 1... Qxd5 2. Qe2 2... Bb7 3. Bg5 $1 (3. Nxe5 Nxe5\n" +
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
            "14. Bh6#) 14. Bxg8) 12. Rxe8+ Rxe8 13. Rxe8+ Nxe8 14. Bc2 $18 1-0\n";
        String noCr = s.replaceAll("(?s)\\s+", " ");
        Assert.assertFalse(noCr.contains("\n"));
        logger.debug("finish");
    }

    @Test
    public void testUnescapeTag() {
        String s =
            "\\\"Lloyds Bank\\\" op\"]" +
            "";
        String pat = "([^\\])\\\"";
        String s3 = s.replaceAll("(^|[^\\\\])\\\\([\"\\\\])", "$1$2");
        String s31 = CpFile.unescapeTag(s);
        logger.debug(String.format("regex: %s: \n%s \n%s", s, s3, s31));
        logger.debug("finish");
    }

    @Test
//    @Ignore("Just prints results")
    public void testSort() {
        List<CpFile> list = new ArrayList<>(Arrays.asList(
            new CpFile.Pgn("1.pgn"),
            new CpFile.Pgn("2.pgn"),
            new CpFile.Pgn("MaxLange-0.pgn"),
            new CpFile.Pgn("20170523.pgn"),

            new CpFile.Pgn("abc.pgn"),
            new CpFile.Pgn("xyz.pgn"),
            new CpFile.Pgn("3abc.pgn"),
            new CpFile.Pgn("12abc.pgn"),
            new CpFile.Pgn("3xyz.pgn"),
            new CpFile.Pgn("12xyz.pgn"),

            new CpFile.Dir("abc-dir"),
            new CpFile.Dir("xyz-dir"),
            new CpFile.Dir("3abc-dir"),
            new CpFile.Dir("12abc-dir"),
            new CpFile.Dir("3xyz-dir"),
            new CpFile.Dir("12xyz-dir"),

            new CpFile.Zip("abc.zip"),
            new CpFile.Zip("xyz.zip"),
            new CpFile.Zip("3abc.zip"),
            new CpFile.Zip("12abc.zip"),
            new CpFile.Zip("3xyz.zip"),
            new CpFile.Zip("12xyz.zip")
        ));
        Collections.sort(list);
        for(CpFile item : list) {
            System.out.println(String.format("%s, %s", item.getClass().toString(), item.getName()));
        }
        logger.debug("finish");
    }

    @Test
    public void testDataStream() throws IOException {
        if(!USE_BIT_STREAMS) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            int val = 0xfedcba98;
            dos.writeInt(val);
            String str = "this is a test string";
            Util.writeString(dos, str);
            dos.flush();
            dos.close();
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
            int v = dis.readInt();
            Assert.assertEquals(val, v);
            String s = Util.readString(dis);
            Assert.assertEquals(str, s);
        }
    }

    @Test
    public void testEscapeTag() {
        String s = "\"bl\\a-\"bla\"";
        String s3 = s.replaceAll("([\\\\\"])", "\\\\$1");
        String s31 = CpFile.escapeTag(s);
        logger.debug(String.format("regex: %s: \n%s \n%s", s, s3, s31));
        logger.debug("finish");
    }

    @Test
    public void testTagParsing() {
        String[] tags = {
            "[Black \"black \\\"black\\\"\"]\n",
            "[Bla\\\"bla \"\\\"bla\\\\bla\\\"bla\\\"\"]\n",
            "[Event \"Lloyds Bank op\"]\n",
            "[White \"white\\\\spec chars, checkmate\"]\n",
        };

        File file = new File(CpFile.getRootDir().absPath, "test.pgn");
        CpFile.Pgn parent = (CpFile.Pgn) CpFile.fromFile(file);
        CpFile.Item cpFile = new CpFile.Item(parent);
        Runtime runtime = Runtime.getRuntime();
        long initFreeMemory = runtime.freeMemory();
        int count = 0;
        for(String tag : tags) {
            ++count;
            System.out.println(String.format("'%s'", tag.trim()));
            CpFile.parseTag(cpFile, tag);
            long freeMemory = runtime.freeMemory();
            System.out.println(String.format("%s, used %s", count, (initFreeMemory - freeMemory)));
        }
        logger.debug("done");
    }

    @Test
    public void testFromRoot() throws RuntimeException {
        File file = new File("/");
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage(startsWith("Invalid file "));
        CpFile cpFile = CpFile.fromFile(file);
        Assert.assertEquals(file.getAbsolutePath(), cpFile.getAbsolutePath());
        Assert.assertTrue(cpFile instanceof CpFile.Dir);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFromPath() throws IOException {
        // prepare root dir:
        File tmpRoot = new File(TEST_TMP_ROOT);
        CpFile.setRoot(tmpRoot);
        File path = new File(tmpRoot, "dir1/dir2");
        Assert.assertTrue(path.mkdirs());

        copyTestFile(path.getAbsolutePath(), "books1.zip");
        copyTestFile(path.getAbsolutePath(), "exeter_lessons_from_tal.pgn");
        copyTestFile(TEST_TMP_ROOT, "MaxLange-0.pgn");

        Pair<Class<CpFile>, String>[] pairs = new Pair[] {
            new Pair(CpFile.Dir.class, "."),
            new Pair(CpFile.Dir.class, "dir1"),
            new Pair(CpFile.Dir.class, "dir1/dir2"),
            new Pair(CpFile.Dir.class, "dummy"),            // non-existent

            new Pair(CpFile.Pgn.class, "MaxLange-0.pgn"),
            new Pair(CpFile.Pgn.class, "dir1/dir2/exeter_lessons_from_tal.pgn"),
            new Pair(CpFile.Pgn.class, "x/dummy.pgn"),      // non-existent


            new Pair(CpFile.Zip.class, "dir1/dir2/dummy.zip"),  // non-existent
            new Pair(CpFile.Zip.class, "dummy/books.zip"),          // non-existent

            new Pair(CpFile.Pgn.class, "dir1/dir2/books1.zip/t2.pgn"),
            new Pair(CpFile.Pgn.class, "dir1/dir2/books1.zip/masters/list1.pgn"),
            new Pair(CpFile.Pgn.class, "dir1/dir2/dummy.zip/t2.pgn"),               // non-existent
            new Pair(CpFile.Pgn.class, "dir1/dir2/dummy.zip/masters/list1.pgn"),    // non-existent
            new Pair(CpFile.Pgn.class, "dir1/dir2/dummy.pgn/t2.pgn"),               // non-existent
            new Pair(CpFile.Pgn.class, "dir1/dir2/dummy.pgn/masters/list1.pgn"),    // non-existent

            new Pair(CpFile.Item.class, "MaxLange-0.pgn/item"),
            new Pair(CpFile.Item.class, "dir1/dir2/exeter_lessons_from_tal.pgn/item"),

            new Pair(CpFile.Item.class, "dir1/dir2/books1.zip/t2.pgn/item"),
            new Pair(CpFile.Item.class, "dir1/dir2/books1.zip/masters/list1.pgn/item"),
            new Pair(CpFile.Item.class, "dir1/dir2/dummy.zip/t2.pgn/item"),               // non-existent
            new Pair(CpFile.Item.class, "dir1/dir2/dummy.zip/masters/list1.pgn/item"),    // non-existent

// to do?
//                new Pair(CpFile.Item.class, "dir1/dir2/books1.zip/t2.pgn/item/subitem"),              // syntax error
//                new Pair(CpFile.Item.class, "dir1/dir2/books1.zip/masters/list1.pgn/item/subitem"),   // syntax error
        };

        for (Pair<Class<CpFile>, String> entry : pairs) {
            Class<CpFile> claz = entry.first;
            CpFile cpFile = CpFile.fromFile(new File(TEST_TMP_ROOT + entry.second));
            CpFile parent = cpFile.getParent();

            long lastModified = cpFile.lastModified();
            Date lastModifiedDate = new Date(lastModified);
            if(cpFile.getAbsolutePath().contains("dummy")) {
                Assert.assertEquals(String.format("error for %s, modified on %s", cpFile.getAbsolutePath(), lastModifiedDate), 0, lastModified);
            } else {
                Assert.assertTrue(String.format("error for %s, modified on %s", cpFile.getAbsolutePath(), lastModifiedDate), lastModified > 0);
            }
            System.out.println(String.format("parent %s, '%s', class %s, '%s', modified on %s",
                    parent.getClass().getSimpleName(), getRelativePath(parent),
                    claz.getSimpleName(), cpFile.getName(), lastModifiedDate.toString()
            ));
            Assert.assertEquals(String.format("Error for %s, %s", claz.toString(), entry.second), claz, cpFile.getClass());
            String expectedPath = tmpRoot.getAbsolutePath() + File.separator + entry.second;
            String actualPath = parent.getAbsolutePath() + File.separator + cpFile.getName();
            Assert.assertEquals("Paths do not match", expectedPath, actualPath);
            if(cpFile instanceof CpFile.Item) {
                Assert.assertEquals(String.format("Error for Item %s, %s", claz.toString(), entry.second), CpFile.Pgn.class, parent.getClass());
            }

            CpFile pgn = CpFile.fromFile(new File(cpFile.getAbsolutePath()));
            Assert.assertEquals("Classes do no match", claz, pgn.getClass());
        }
    }

    private void copyTestFile(String parent, String name) throws IOException {
        File origFile, testFile;

        origFile = new File(TEST_ROOT, name);
        testFile = new File(parent, name);
        CpFile.copy(origFile, testFile);
    }

    private String getRelativePath(CpFile cpFile) {
        File tmpRoot = new File(TEST_TMP_ROOT);
        String rootPath = tmpRoot.getAbsolutePath();
        String absPath = cpFile.getAbsolutePath();
        Assert.assertTrue("Invalid path " + absPath, absPath.startsWith(rootPath));
        return absPath.substring(rootPath.length());
    }
}
