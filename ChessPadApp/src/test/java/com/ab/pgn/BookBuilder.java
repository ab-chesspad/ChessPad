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

 * build opening book, run as a test
 * Created by Alexander Bootman on 9/11/2022.
 */
package com.ab.pgn;

import com.ab.pgn.io.CpFile;
import com.ab.pgn.BaseTest;

import org.junit.Ignore;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BookBuilder extends BaseTest {
    @Test
    @Ignore("todo: move this code to buildSrc to use with com.ab.pgn package")
    public void runBookBuilder() throws Config.PGNException {
        final String DATA_DIR = "../../ChessPadApp/src/main/book/";
        final String ecoFileName = DATA_DIR + "eco.pgn";
        final String internalBookFileName = TEST_ROOT + DATA_DIR + "internal_openings.txt";
        final String outputFileName = TEST_ROOT + "../../ChessPadApp/src/main/assets/book/combined.book";

        build(ecoFileName, internalBookFileName, outputFileName);
    }

    private void build(String ecoPgnFile, String additionalTextFile, String resultFile) throws Config.PGNException {
        CpFile.PgnFile pgnFile = (CpFile.PgnFile) CpFile.fromPath(ecoPgnFile);
        MergeData mergeData = new MergeData(pgnFile);

        Book book = new Book();
        book.merge(mergeData);
        int size = mergeData.commentStrings.size();
        book.commentStrings = new String[size];
        for (Map.Entry<String, Integer> entry : mergeData.commentStrings.entrySet()) {
            book.commentStrings[entry.getValue()] = entry.getKey();
        }

        String title = "";
        String line = "";
        int totalNewMoves = 0;
        try (FileReader fr = new FileReader(additionalTextFile);
             BufferedReader br = new BufferedReader(fr)) {
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("#")) {
                    title = line;
                    continue;
                }
                PgnGraph graph = new PgnGraph();
                graph.parseMoves(line);
                int newMoves;
                if ((newMoves = merge(book, graph)) > 0) {
                    totalNewMoves += newMoves;
                }
            }
//            logger.debug(String.format(Locale.US, "added total %s moves", totalNewMoves));
            save(book, resultFile);
        } catch (Config.PGNException e) {
            e.printStackTrace();
            System.out.printf("error for %s on %s", title, line);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int merge(Book book, PgnGraph graph) {
        int newMoves = 0;
        Board prevBoard = null;
        String commonComment = null;
        for (Move move : graph.moveLine) {
            Board nextBoard = graph.getBoard(move);
            Board ecoBoard = book.getBoard(move);
            if (ecoBoard == null) {
                ++newMoves;
                move.comment = commonComment;
                ecoBoard = nextBoard.clone();
                book.positions.put(new Pack(move.packData), ecoBoard);
                Move prevMove = prevBoard.getMove();
                if (prevMove == null) {
                    prevBoard.setMove(move);
                } else {
                    move.setVariation(prevMove.getVariation());     // add new move as variation
                    prevMove.setVariation(move);
                }
            } else {
                Move m;
                if (prevBoard != null && (m = prevBoard.getMove()) != null) {
                    while (m != null) {
                        if (move.isSameAs(m)) {
                            break;
                        }
                        m = m.getVariation();
                    }
                    // m == null when the position results with moves transposition
                    if (m != null) {
                        commonComment = m.comment;
                    }
                }
            }
            prevBoard = ecoBoard;
        }
        return newMoves;
    }

    private void save(Book book, String fileName) throws IOException {
        File f = new File(fileName);
        f.getParentFile().mkdirs();
        try (OutputStream outputStream = new FileOutputStream(f);
             PrintStream ps = new PrintStream(outputStream)) {
            int size;
            if (book.commentStrings == null) {
                ps.println(0);
            } else {
                size = book.commentStrings.length;
                ps.println(size);
                for (int indx = 0; indx < size; ++indx) {
                    ps.println(book.commentStrings[indx]);
                }
            }
            ps.println(book.toPgn());    // todo: make shorter
        }
    }

    private static class MergeData extends PgnGraph.MergeData {
        final Map<String, Integer> commentStrings;  // all comment strings, moves contain references on them
        String commonComment;

        MergeData(CpFile.PgnFile pgnFile) {
            super(pgnFile);
            commentStrings = new HashMap<>();
        }

        @Override
        public PgnGraph.MergeState onNewItem(CpFile.PgnItem pgnItem) {
            StringBuilder sb = new StringBuilder();
            List<Pair<String, String>> tags = pgnItem.getTags();
            for (Pair<String, String> tag : tags) {
                if (Config.TAG_UNKNOWN_VALUE.equals(tag.second)) {
                    continue;
                }
                int index = commentStrings.size();
                Integer ind = commentStrings.get(tag.second);
                if (ind == null) {
                    commentStrings.put(tag.second, index);
                } else {
                    index = ind;
                }
                sb.append(Config.BOOK_COMMENT_STRING_TAG).append(index);
            }
            if (sb.length() > 0) {
                commonComment = new String(sb);
            } else {
                commonComment = null;
            }

            return PgnGraph.MergeState.Merge;
        }

        @Override
        public String getCommonComment() {
            return commonComment;
        }
    }
}