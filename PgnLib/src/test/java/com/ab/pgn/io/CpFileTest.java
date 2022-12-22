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

 * test CpFile operations
 * Created by Alexander Bootman on 7/30/16.
 */
package com.ab.pgn.io;

import com.ab.pgn.BaseTest;
import com.ab.pgn.BitStream;
import com.ab.pgn.Config;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CpFileTest extends BaseTest {

    @Test
    public void testSort() {
        List<CpFile> list = new ArrayList<>(Arrays.asList(
            CpFile.fromPath("1.pgn"),
            CpFile.fromPath("2.pgn"),
            CpFile.fromPath("MaxLange-0.pgn"),
            CpFile.fromPath("20170523.pgn"),

            CpFile.fromPath("abc.pgn"),
            CpFile.fromPath("xyz.pgn"),
            CpFile.fromPath("3abc.pgn"),
            CpFile.fromPath("12abc.pgn"),
            CpFile.fromPath("3xyz.pgn"),
            CpFile.fromPath("12xyz.pgn"),

            CpFile.fromPath("abc-dir"),
            CpFile.fromPath(".dir"),
            CpFile.fromPath(".zip"),
            CpFile.fromPath("xyz-dir"),
            CpFile.fromPath("3abc-dir"),
            CpFile.fromPath("12abc-dir"),
            CpFile.fromPath("3xyz-dir"),
            CpFile.fromPath("12xyz-dir"),

            CpFile.fromPath("abc.zip"),
            CpFile.fromPath("xyz.zip"),
            CpFile.fromPath("3abc.zip"),
            CpFile.fromPath("12abc.zip"),
            CpFile.fromPath("3xyz.zip"),
            CpFile.fromPath("12xyz.zip")
        ));
        Collections.sort(list);
        for (CpFile item : list) {
            System.out.println(String.format("%s, %s", item.getClass().toString(), ((CpFile)item).getAbsolutePath()));
        }
        logger.debug("finish");
    }

    @Test
    public void testFromPath() {
        Object[][] allFileData = {
            // path, type, parent path, parent type
            // non-existing:
            {"1.pgn", CpFile.CpFileType.Pgn, "", CpFile.CpFileType.Dir},
            {"a/1.pgn", CpFile.CpFileType.Pgn, "a", CpFile.CpFileType.Dir},
            {"a/b/1.pgn", CpFile.CpFileType.Pgn, "a/b", CpFile.CpFileType.Dir},
            {"a/b/c", CpFile.CpFileType.Dir, "a/b", CpFile.CpFileType.Dir},
            {"a/b/c.zip", CpFile.CpFileType.Zip, "a/b", CpFile.CpFileType.Dir},
            {"a/b.zip/c", CpFile.CpFileType.Dir, "a/b.zip", CpFile.CpFileType.Zip},
            {"a/b.zip/c.pgn", CpFile.CpFileType.Pgn, "a/b.zip", CpFile.CpFileType.Zip},
            {"a/b.zip/d/c.pgn", CpFile.CpFileType.Pgn, "a/b.zip", CpFile.CpFileType.Zip},
            {"a/b.zip/d/e/c.pgn", CpFile.CpFileType.Pgn, "a/b.zip", CpFile.CpFileType.Zip},
            {"a/b.zip/d/e/c.pgn/d.pgn", CpFile.CpFileType.Pgn, "a/b.zip", CpFile.CpFileType.Zip},
            {"a/b/1.pgn/2.pgn", CpFile.CpFileType.Pgn, "a/b/1.pgn", CpFile.CpFileType.Dir},
            {"a/b/1.pgn/2.pgn/3.pgn", CpFile.CpFileType.Pgn, "a/b/1.pgn/2.pgn", CpFile.CpFileType.Dir},
            {"x/dir/z", CpFile.CpFileType.Dir, "x/dir", CpFile.CpFileType.Dir},
            {"x/dir/c.zip", CpFile.CpFileType.Zip, "x/dir", CpFile.CpFileType.Dir},
            {"x/dir/b.zip/c", CpFile.CpFileType.Dir, "x/dir/b.zip", CpFile.CpFileType.Zip},
            {"xyz/aaa.zip/abc/edf/aa.pgn", CpFile.CpFileType.Pgn, "xyz/aaa.zip", CpFile.CpFileType.Zip},
            {"x/dir/b.zip/d/c.pgn", CpFile.CpFileType.Pgn, "x/dir/b.zip", CpFile.CpFileType.Zip},
            {"x/dir/.zip/d/c.pgn", CpFile.CpFileType.Pgn, "x/dir/.zip/d", CpFile.CpFileType.Dir},
            {"x/dir/.zip", CpFile.CpFileType.Dir, "x/dir", CpFile.CpFileType.Dir},
            {"x/dir/.pgn", CpFile.CpFileType.Dir, "x/dir", CpFile.CpFileType.Dir},
            {"a/b/.pgn", CpFile.CpFileType.Dir, "a/b", CpFile.CpFileType.Dir},
            // existing:
            {"x/dir", CpFile.CpFileType.Dir, "x", CpFile.CpFileType.Dir},
            {"x/dir/books1.zip", CpFile.CpFileType.Zip, "x/dir", CpFile.CpFileType.Dir},
            {"x/dir/books1.zip/t.pgn", CpFile.CpFileType.Pgn, "x/dir/books1.zip", CpFile.CpFileType.Zip},
            {"x/dir/books1.zip/masters/list1.pgn", CpFile.CpFileType.Pgn, "x/dir/books1.zip", CpFile.CpFileType.Zip},
/*/
            {"famous_games.zip/famous_games.pgn/item", CpFile.CpFileType.Item, "famous_games.zip/famous_games.pgn", CpFile.CpFileType.Pgn},
//*/
        };

        for (Object[] fileData : allFileData) {
            String path = (String)fileData[0];
            if (!path.isEmpty()) {
                path = "/" + path;
            }
            CpFile.CpFileType type = (CpFile.CpFileType)fileData[1];
            String parentPath = (String)fileData[2];
            if (!parentPath.isEmpty()) {
                parentPath = "/" + parentPath;
            }
            CpFile.CpFileType parentType = (CpFile.CpFileType)fileData[3];

            CpFile cpFile = CpFile.fromPath(path);
            Assert.assertEquals("error in " + path, type, cpFile.getType());
            Assert.assertEquals("error in " + path, path, cpFile.getAbsolutePath());

            String resultParentPath = cpFile.getParent().getAbsolutePath();
            Assert.assertEquals("error in " + path, parentPath, resultParentPath);
            Assert.assertEquals("error in " + path, parentType, cpFile.parent.getType());
        }
    }

    @Test
    public void testRoot() {
        final String[] rootNames = {
            "/",
            "",
            null,
        };
        for (String rootName : rootNames) {
            CpFile root = CpFile.fromPath("/");
            Assert.assertTrue("error for " + rootName, root.isRoot());
        }
    }

    @Test
    public void testNewDir() {
        String path = "xyz";
        CpFile cpParent = CpFile.fromPath(path);
        Assert.assertTrue(cpParent.getParent().isRoot());
    }

    @Test
//    @Ignore("Just prints dir content")
    public void testDir() throws Exception {
        int testItemIndex = 1;
        CpFile.Dir topDir = (CpFile.Dir)CpFile.fromPath("test_subdir");
        List<CpFile> topDirChildren = topDir.getChildrenNames();
        for (CpFile topDirChild : topDirChildren) {
            CpFile topDirItem = (CpFile)topDirChild;
            List<CpFile> topDirGrandchildren = topDirItem.getChildrenNames();
            logger.debug(String.format("%s, %s", topDirItem.getClass().toString(), topDirItem.getAbsolutePath()));
            if (topDirItem instanceof CpFile.PgnFile) {
                if (testItemIndex < topDirGrandchildren.size()) {
                    CpFile.PgnItem pgnItem = ((CpFile.PgnFile)topDirItem).getPgnItem(testItemIndex);
                    logger.debug(String.format("\t%s", pgnItem.toString()));
                }
            } else if (topDirItem instanceof CpFile.Zip) {
                for (CpFile p : topDirGrandchildren) {
                    CpFile.PgnFile topDirGrandchild = (CpFile.PgnFile)p;
                    logger.debug(String.format("\t%s", topDirGrandchild.toString()));
                    // PgnFiles
                    List<CpFile> zipPgnItemNames = topDirGrandchild.getChildrenNames();
                    int index = -1;
                    for (CpFile zipPgnItemName : zipPgnItemNames) {
                        // PgnItems
                        if (DEBUG) {
                            logger.debug(String.format("\t\t%s", zipPgnItemName.toString()));
                        }
                        if (++index == testItemIndex) {
                            CpFile.PgnItem testPgnItem = topDirGrandchild.getPgnItem(testItemIndex);
                            if (DEBUG) {
                                logger.debug(String.format("\t%s", testPgnItem.getMoveText()));
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testPgnFileList() {
        final String[] fileNames = {
            "lichess_puzzles-0.pgn",
            "SicilianTaimanovMain.pgn",
        };

        for (String fileName : fileNames) {
            CpFile.PgnFile pgnFile = (CpFile.PgnFile) CpFile.fromPath(fileName);
            long start0 = System.currentTimeMillis();
            List<CpFile> pgnItems = pgnFile.getChildrenNames();
            long dur0 = System.currentTimeMillis() - start0;
            logger.debug(String.format("msec=%d", dur0));
            logger.debug(String.format("%s children=%s", fileName, pgnItems.size()));
        }
    }

    @Test
    public void testPgnItemUpdate() throws Exception {
        // remove comments from all pgn files, including within zip
        currentRootPath = TEST_TMP_ROOT;

        final String[] fileNames = {
            "x/dir/y",
            "x/dir/books1.zip",
        };

        for (String fileName : fileNames) {
            File testFile = toTempTest(fileName);
            CpFile.Dir dir = (CpFile.Dir) CpFile.fromPath(fileName);
            List<CpFile> list = dir.getChildrenNames();
            for (CpFile _pgnFile : list) {
                CpFile.PgnFile pgnFile = (CpFile.PgnFile)_pgnFile;
                // 1. get all PgnItem entries
                List<CpFile> items = pgnFile.getChildrenNames();
                for (int i = 0; i < items.size(); ++i) {
                    // 2. for each PgnItem get its text, remove comments and save
                    CpFile.PgnItem pgnItem = pgnFile.getPgnItem(i);
                    String moveText = pgnItem.getMoveText().replaceAll("(?s)\\{.*?}", "");
                    if (!moveText.equals(pgnItem.getMoveText())) {
                        final int[] pgnOffset = {0};
                        pgnItem.setMoveText(moveText);
                        pgnItem.save();
                        if (DEBUG) {
                            logger.debug(String.format("%s offset=%s", pgnItem.toString(), pgnOffset[0]));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testPgnItemDelete() throws Exception {
        // remove 1st (0-indexed) PgnItem from all pgn files, including within zip, until its deletion
        // test that all empty directories are removed up to the root
        currentRootPath = TEST_TMP_ROOT;

        final String[] fileNames = {
            "x/dir/y",
            "x/dir/books1.zip",
        };

        for (String fileName : fileNames) {
            File testFile = toTempTest(fileName);
            CpFile.Dir dir = (CpFile.Dir) CpFile.fromPath(fileName);
            FilAx realFile = CpFile.newFile(dir.getAbsolutePath());
            List<CpFile> list = dir.getChildrenNames();
            for (CpFile _pgnFile : list) {
                CpFile.PgnFile pgnFile = (CpFile.PgnFile)_pgnFile;
                List<CpFile> items = pgnFile.getChildrenNames();
                while (items.size() > 0) {
                    CpFile.PgnItem pgnItem = new CpFile.PgnItem(pgnFile);
                    pgnItem.index = 0;
                    pgnItem.setMoveText(null);      // delete it
                    pgnItem.save();
                    if (!realFile.exists()) {
                        break;
                    }
                    List<CpFile> items1 = pgnFile.getChildrenNames();
                    Assert.assertEquals(items.size() - 1, items1.size());
                    items = items1;
                }
            }
            Assert.assertFalse(realFile.getName() + " was not deleted", realFile.exists());
//            Assert.assertEquals(0, new File(CpFile.getRootPath()).list().length);
        }
    }

    @Test
    public void testPgnItemAdd() throws Exception {
        String pgnText =
            "[White \"SicilianTaimanov\"]\n" +
            "[Black \"Main\"]\n" +
            "1. e4 c5 " +
            "\n";

        final String[] fileNames = {
            "x/dir/books1.zip",
            "x/dir/y",
            "a1/b1/c1",
            "x/dir/books1.zip/t.pgn",
            "x/dir/books1.zip/masters/list1.pgn",
            "x/dir/books1.zip/new.pgn",
            "x/dir/y/sample.pgn",
            "a2/b2/1.pgn",
            "a3/b3/z.zip/1.pgn",
            "a4/b4/z.zip/a/b/1.pgn",
        };

        currentRootPath = TEST_TMP_ROOT;
        InputStream is = new ByteArrayInputStream(StandardCharsets.UTF_8.encode(pgnText).array());
        List<CpFile.PgnItem> pgnItems = parsePgnFile(null, is, true);
        Assert.assertEquals(1, pgnItems.size());
        CpFile.PgnItem pgnItem = pgnItems.get(0);

        for (String fileName : fileNames) {
            CpFile parent = CpFile.fromPath(fileName);
            File testFile = toTempTest(parent.parent.getAbsolutePath());
            // Dir or Zip
            logger.debug(parent.toString());
            if (parent instanceof CpFile.PgnFile) {
                testPgnItemAdd(pgnItem, (CpFile.PgnFile)parent);
                continue;
            }
            List<CpFile> childrenNames = parent.getChildrenNames();
            for (CpFile childName : childrenNames) {
                CpFile.PgnFile pgnFile = (CpFile.PgnFile)childName;
                logger.debug("\t" + pgnFile.toString());
                testPgnItemAdd(pgnItem, pgnFile);
            }
            testPgnItemAdd(pgnItem, new CpFile.PgnFile(parent, "added.pgn"));
        }
        logger.debug("finish");
    }

    private void testPgnItemAdd(CpFile.PgnItem pgnItem, CpFile.PgnFile pgnFile) throws Config.PGNException {
        pgnItem.parent = pgnFile;
        List<CpFile> items = pgnFile.getChildrenNames();
        // m.b. update tag names in pgnFile?
        pgnItem.setIndex(-1);
        pgnItem.save();     // append
        List<CpFile> updatedItems = pgnFile.getChildrenNames();
        Assert.assertEquals("error in " + pgnFile.getAbsolutePath(), updatedItems.size(), items.size() + 1);

        CpFile.PgnItem addedItem = pgnFile.getPgnItem(items.size());
        Assert.assertEquals("error in " + pgnFile.getAbsolutePath(), pgnItem, addedItem);
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
    public void testSerialize() throws Config.PGNException, IOException {
        String[] paths = {
            "test_subdir/x/dir",
            "test_subdir/x/dir/books1.zip",
            "test_subdir/x/dir/books1.zip/masters/list1.pgn",
        };
        for (String path : paths) {
            CpFile cpFile = CpFile.fromPath(path);
            List<CpFile> children = cpFile.getChildrenNames();  // to obtain totalChildren
            BitStream.Writer writer = new BitStream.Writer();
            cpFile.serialize(writer);
            writer.close();
            BitStream.Reader reader = new BitStream.Reader(writer.getBits());
            CpFile unserializedCpFile = (CpFile) CpFile.unserialize(reader);
            Assert.assertEquals("Error in " + path,
                    cpFile, unserializedCpFile);
            Assert.assertEquals("Error in " + path,
                    cpFile.length, unserializedCpFile.length);
            Assert.assertEquals("Error in " + path,
                    cpFile.totalChildren, unserializedCpFile.totalChildren);
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

        CpFile.PgnFile pgnFile = (CpFile.PgnFile) CpFile.fromPath("abc.pgn");
        CpFile.PgnItem pgnItem = new CpFile.PgnItem((CpFile)null);
        Runtime runtime = Runtime.getRuntime();
        long initFreeMemory = runtime.freeMemory();
        int count = 0;
        for (String tag : tags) {
            ++count;
            System.out.println(tag.trim());
            CpFile.parseTag(pgnItem, tag);
            long freeMemory = runtime.freeMemory();
            System.out.println(String.format("%s, used %s", count, (initFreeMemory - freeMemory)));
        }
        logger.debug("done");
    }
    @Test
    public void testRename() throws Exception {
        currentRootPath = TEST_TMP_ROOT;

        final String[][] fileItems = {
            {"x/dir/y", "z"},
            {"x/dir/books1.zip", "b2.zip"},
        };

        for (String[] fileItem : fileItems) {
            File testFile = toTempTest(fileItem[0]);
            FilAx filAx = CpFile.getFilAxProvider().newFilAx(fileItem[0]);
            filAx.renameTo(fileItem[1]);
// todo: assert results
        }
    }
}
