package com.ab.pgn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Book extends PgnGraph {
    private String[] commentStrings;            // all comment strings, moves contain references on them

    Book() {
        super();
    }

    private void save(String fileName) throws IOException {
        File f = new File(fileName);
        f.getParentFile().mkdirs();
        try (OutputStream outputStream = new FileOutputStream(f);
             PrintStream ps = new PrintStream(outputStream)) {
            int size = 0;
            if(commentStrings == null) {
                ps.println(0);
            } else {
                size = commentStrings.length;
                ps.println(size);
                for(int indx = 0; indx < size; ++indx) {
                    ps.println(commentStrings[indx]);
                }
            }
            ps.println(toPgn());    // todo: make shorter
        }
    }

    public Book(InputStream is, long totalLength) throws Config.PGNException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is)) ) {
            String line = br.readLine();
            int length = line.length();
            int size = Integer.valueOf(line);
            commentStrings = new String[size];
            for(int i = 0; i < size; ++i) {
                line = br.readLine();
                length += line.length();
                commentStrings[i] = line;
            }
            StringBuilder sb = new StringBuilder((int)totalLength - length);
            while((line = br.readLine()) != null) {
                sb.append(line);
            }
            String moves = new String(sb);
            parseMoves(moves, null);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public String getComment(String bookComment) {
        if (bookComment == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String[] parts = bookComment.split(Config.BOOK_COMMENT_STRING_TAG);
        String sep = "";
        for (String part : parts) {
            try {
                int indx = Integer.valueOf(part);
                sb.append(sep).append(commentStrings[indx]);
                sep = "; ";
            } catch (Exception e) {
                // ignore
            }
        }
        return new String(sb);
    }

    public List<Move> getMoves(Board board) {
        Pack key = null;
        try {
            key = new Pack(board.pack());
        } catch (Config.PGNException e) {
            e.printStackTrace();
            return null;
        }
        board = positions.get(key);
        Move move = null;
        if(board == null || (move = board.getMove()) == null) {
            return null;
        }
        List<Move> moves = new LinkedList<>();
        do {
            moves.add(move);
            move = move.getVariation();
        } while(move != null);
        return moves;
    }

    public static class Builder {
        public static void build(String ecoPgnFile, String additionalTextFile, String resultFile) throws Config.PGNException {
            CpFile.Pgn pgn = new CpFile.Pgn(ecoPgnFile);
            MergeData mergeData = new MergeData(pgn);

            Book book = new Book();
            book.merge(mergeData, null);
            int size = mergeData.commentStrings.size();
            book.commentStrings = new String[size];
            for(Map.Entry<String, Integer> entry : mergeData.commentStrings.entrySet()) {
                book.commentStrings[entry.getValue()] = entry.getKey();
            }

            String title = "";
            String line = "";
            int totalNewMoves = 0;
            try (FileReader fr = new FileReader(additionalTextFile);
                 BufferedReader br = new BufferedReader(fr)) {
                while((line = br.readLine()) != null) {
                    line = line.trim();
                    if(line.isEmpty()) {
                        continue;
                    }
                    if(line.startsWith("#")) {
                        title = line;
                        continue;
                    }
                    PgnGraph graph = new PgnGraph();
                    graph.parseMoves(line, null);
                    int newMoves;
                    if ((newMoves = merge(book, graph)) > 0) {
                        totalNewMoves += newMoves;
                    }
                }
//            logger.debug(String.format(Locale.US, "added total %s moves", totalNewMoves));
                book.save(resultFile);
            } catch (Config.PGNException e) {
                e.printStackTrace();
                System.out.printf("error for %s on %s", title, line);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private static int merge(Book book, PgnGraph graph) {
            int newMoves = 0;
            Board prevBoard = null;
            String commonComment = null;
            for(Move move : graph.moveLine) {
                Board nextBoard = graph.getBoard(move);
                Board ecoBoard = book.getBoard(move);
                if (ecoBoard == null) {
                    ++newMoves;
                    move.comment = commonComment;
                    ecoBoard = nextBoard.clone();
                    book.positions.put(new Pack(move.packData), ecoBoard);
                    Move prevMove = prevBoard.getMove();
                    if(prevMove == null) {
                        prevBoard.setMove(move);
                    } else {
                        move.setVariation(prevMove.getVariation());     // add new move as variation
                        prevMove.setVariation(move);
                    }
                } else {
                    Move m;
                    if (prevBoard != null && (m = prevBoard.getMove()) != null) {
                        while(m != null) {
                            if (move.isSameAs(m)) {
                                break;
                            }
                            m = m.getVariation();
                        }
                        // m == null when the position results with moves transposition
                        if(m != null) {
                            commonComment = m.comment;
                        }
                    }
                }
                prevBoard = ecoBoard;
            }
            return newMoves;
        }

        private static class MergeData extends PgnGraph.MergeData {
            final Map<String, Integer> commentStrings;  // all comment strings, moves contain references on them
            String commonComment;

            MergeData(CpFile target) {
                super((target));
                commentStrings = new HashMap<>();
            }

            @Override
            public PgnGraph.MergeState onNewItem(CpFile.Item item) {
                StringBuilder sb = new StringBuilder();
                List<Pair<String, String>> tags = item.getTags();
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
                if(sb.length() > 0) {
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
}
