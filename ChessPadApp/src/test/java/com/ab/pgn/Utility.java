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

 * Created by Alexander Bootman on 8/6/16.
 */
package com.ab.pgn;

import com.ab.pgn.lichess.LichessPad;
import com.ab.pgn.uci.UCI;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Ignore("Total mess")
//@Ignore("needed ones to cleanup the downloaded puzzle file")
public class Utility extends BaseTest implements UCI.EngineWatcher {
    private static final boolean DEBUG = false;
    private static final boolean USE_STOCKFISH = false;     // Stockfish use testing unfinished
    private static final int DEBUG_CLEAR_LICHESS_PUZZLES = 0;
    private static final int LICHESS_PUZZLES_COUNT = 400000;
    private static final int LICHESS_PUZZLES_MIN_RATING = 1500;

    private static final String stockFishPath = "src/main/cpp/stockfish/stockfish";

    private final Object lock = new Object();
    private int inputPuzzleCount = 0;
    private int outputPuzzleCount = 0;
    private int puzzlesSkipped = 0;

    private UCI uciEngine;
    private PgnGraph pgnGraph;
    private Board initBoard;
    private Move firstMove;
    private int totalMoves;
    private UCI.IncomingInfoMessage lastInfoMessage;

    @Test
    public void testCleanupCVS() throws IOException, Config.PGNException {
        final String srcPgn = prefix + "etc/lichess_db_puzzle.csv";
        final String trgPgn = prefix + "etc/lichess_puzzles";
        cleanupCVS(srcPgn, trgPgn);
    }

    /**
     * remove:
     * corrupted
     * with no moves (comment only)
     * Too simple - single move or 3 plies or less with first check/checkmate
     *
     * make sure that whoever starts the game wins it and makes the last move
     */
    public void cleanupCVS(String srcPgn, String trgPgn) throws IOException, Config.PGNException {
        final int
            id_index = 0,
            fen_index = 1,
            moves_index = 2,
            rating_index = 3,
            vote_index = 6,
            theme_index = 7,
            link_index = 8,
            int_dummy = 0;
        long start = System.currentTimeMillis();
        System.out.println(String.format("testCleanup() start: %d", start));
        int cvsCount = 0;
        int count = 0;
        PrintStream out = null;
        try (BufferedReader br = new BufferedReader(new FileReader(srcPgn))) {
            out = new PrintStream(new FileOutputStream(String.format("%s-%d.pgn", trgPgn, cvsCount)));
            String line;
            try {
                while ((line = br.readLine()) != null) {
                    // 00WT8,r4rk1/ppp3pp/1b1p4/3P2q1/4B3/2P3P1/PP2QPK1/R4R2 w - - 0 20,f1h1 f8f2 e2f2 b6f2,1227,81,100,79,advantage fork middlegame short,https://lichess.org/nOoSj8Dd#39
                    ++inputPuzzleCount;
                    try {
                        String[] parts = line.split(",");
//                        System.out.println(String.format("%d parts", parts.length));
                        int rating = Integer.valueOf(parts[rating_index]);
                        if (rating < LICHESS_PUZZLES_MIN_RATING) {
                            continue;
                        }
                        String strMoves = parts[moves_index];
                        String[] moves = strMoves.split(" ");
                        if (moves.length <= 2) {
                            // too simple
                            ++puzzlesSkipped;
                            continue;
                        }

                        Board board = new Board((parts[fen_index]));
                        PgnGraph puzzle = new PgnGraph(board);
                        LichessPad.parseMove(puzzle, moves[0]);
                        Move firstMove = board.getMove();
                        Board initBoard = puzzle.getBoard(firstMove);
                        puzzle = new PgnGraph(initBoard);
                        for (int j = 1; j < moves.length; ++j) {
                            LichessPad.parseMove(puzzle, moves[j]);
                        }
                        CpFile.Item item = puzzle.getPgn();
                        item.setTag(Config.TAG_Round, parts[id_index]);
//                        item.setTag(Config.TAG_Site, LichessClient.DOMAIN);
                        String[] link_parts = parts[link_index].split("/|#");
                        item.setTag(Config.TAG_Site, link_parts[3]);
                        item.setTag(LichessPad.PUZZLE_TAG_RATING, "" + rating);
//                        item.setTag(LichessPad.PUZZLE_TAG_VOTE, parts[vote_index]);
                        puzzle.prepareToPgn();
                        out.println(item.tagsToString(true, true, true));
                        out.println();
                        out.println(item.getMoveText());
                        out.println();
                        ++count;
                        if (DEBUG_CLEAR_LICHESS_PUZZLES > 0 && count > DEBUG_CLEAR_LICHESS_PUZZLES) {
                            break;
                        }
                        if (count >= LICHESS_PUZZLES_COUNT) {
                            out.flush();
                            out.close();
                            out = new PrintStream(new FileOutputStream(String.format("%s-%d.pgn", trgPgn, ++cvsCount)));
                            outputPuzzleCount += count;
                            count = 0;
                        }
                    } catch (Throwable t) {
                        logger.error(String.format("line #%d ignored, %s", inputPuzzleCount, t.getMessage()));
                        ++puzzlesSkipped;
                    }
                }
                out.flush();
                out.close();
                outputPuzzleCount += count;
            } catch (Throwable e) {
                logger.error(e);
                throw new Config.PGNException(e);
            }
        };
        long end = System.currentTimeMillis();
        System.out.println(String.format("testCleanup() end: %d, duration %d sec", end, (end - start) / 1000));
        System.out.println(String.format("   input: %d puzzles\n  output: %d puzzles\n skipped: %d", inputPuzzleCount, outputPuzzleCount, puzzlesSkipped));
    }

    @Test
    public void testCleanup() throws IOException, Config.PGNException {
        // source file downloaded from https://github.com/xinyangz/chess-tactics-pgn
        //  zip -9 tactics-clean.zip tactics.pgn
        final String srcPgn = "../etc/tactics-original.pgn";
        final String trgPgn = "../etc/tactics.pgn";
        cleanup(srcPgn, trgPgn);
    }

    /**
         * remove:
         * corrupted
         * with no moves (comment only)
         * Too simple - single move or 3 plies or less with first check/checkmate
         *
         * make sure that whoever starts the game wins it and makes the last move
         */
    public void cleanup(String srcPgn, String trgPgn) throws IOException, Config.PGNException {
        File libPath = new File(stockFishPath);
        System.out.println(libPath.getAbsolutePath());
        Assert.assertTrue(libPath.exists());

        long start = System.currentTimeMillis();
        System.out.println(String.format("testCleanup() start: %d", start));
        if (USE_STOCKFISH) {
//            uciEngine = new StockFish();
            System.out.println(String.format("engine launched: %d", System.currentTimeMillis()));
        }

        try (BufferedReader br = new BufferedReader(new FileReader(srcPgn));
                PrintStream out = new PrintStream(new FileOutputStream(trgPgn))) {
            CpFile.parsePgnFiles(null, br, new CpFile.EntryHandler() {
                @Override
                public boolean handle(CpFile entry, BufferedReader bufferedReader) throws Config.PGNException {
                    doCleanup(entry, out);
                    return true;    // continue
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
            long end = System.currentTimeMillis();
            System.out.println(String.format("testCleanup() end: %d, duration %d sec", end, (end - start) / 1000));
            System.out.println(String.format("   input: %d puzzles\n  output: %d puzzles\n skipped: %d", inputPuzzleCount, outputPuzzleCount, puzzlesSkipped));
        }
    }

    private synchronized void doCleanup(CpFile entry, PrintStream out) throws Config.PGNException {
        ++inputPuzzleCount;
        CpFile.Item puzzle = (CpFile.Item)entry;
        if (DEBUG) {
            System.out.println(String.format("puzzle %d, %s", inputPuzzleCount, puzzle.getFen()));
        }
        initBoard = new Board(puzzle.getFen());
        int err = initBoard.validateSetup(true);
        if(err != 0) {
            // unrecovearble error
            System.out.println(String.format("puzzle %d, %s error %d, skipped", inputPuzzleCount, puzzle.getFen(), err));
            ++puzzlesSkipped;
            return;
        }
        pgnGraph = new PgnGraph(initBoard);
        try {
            pgnGraph.parseMoves(puzzle.getMoveText(), null);
        } catch (Config.PGNException e) {
            System.out.println(String.format("puzzle %d, %s error %s, skipped", inputPuzzleCount, puzzle.getFen(), e.getMessage()));
            ++puzzlesSkipped;
            return;
        }

        firstMove = initBoard.getMove();
        if (firstMove == null) {
            System.out.println(String.format("puzzle %d, %s no moves, skipped", inputPuzzleCount, puzzle.getFen()));
            ++puzzlesSkipped;
            return;
        }
        Move lastMove = pgnGraph.getCurrentMove();
        totalMoves = pgnGraph.moveLine.size() - 1;

        int color;
        String result = puzzle.getTag(Config.TAG_Result);
        if("1-0".equals(result)) {
            color = 0;      // white
        } else if("0-1".equals(result)) {
            color = Config.BLACK;
        } else {
            color = lastMove.moveFlags & Config.FLAGS_BLACK_MOVE;
            puzzle.setTag(Config.TAG_Result, Config.TAG_UNKNOWN_VALUE);
        }

        if((lastMove.moveFlags & Config.FLAGS_BLACK_MOVE) != color) {
            pgnGraph.delCurrentMove();
        }
        if ((firstMove.moveFlags & Config.FLAGS_BLACK_MOVE) != color) {
            // puzzle must start with the same color as end
            initBoard = pgnGraph.getBoard(firstMove);
            firstMove = initBoard.getMove();
            --totalMoves;
        }
        if (DEBUG) {
            System.out.println(String.format("Engine ready, start analysis: %d \n%s", System.currentTimeMillis(), initBoard.toString()));
        }
        boolean save = true;
        if (USE_STOCKFISH) {
            save = verifyWithStockfish();
        }
        if(save) {
            save(puzzle, out);
        } else {
            System.out.println(String.format("%s skipped", initBoard.toString()));
        }
    }

    private void save(CpFile.Item puzzle, PrintStream out) throws Config.PGNException {
        Pattern tagValuePattern = Pattern.compile("[^?^.]");

        PgnGraph resPgnGraph = new PgnGraph(initBoard.clone());
        Move move = initBoard.getMove();
        int check = move.moveFlags & (Config.FLAGS_CHECK | Config.FLAGS_CHECKMATE);
        int count = 0;
        while(move != null) {
            ++count;
            resPgnGraph.addMove(move);
            move = pgnGraph.getBoard(move).getMove();
        }
        if(count <= 1 || count <= 3 && check != 0) {
            System.out.println(String.format("puzzle %d, %s skipped, too simple: %s", inputPuzzleCount, pgnGraph.getInitBoard().toFEN(), beautifyMoveLine(resPgnGraph.toPgn())));
            ++puzzlesSkipped;
            return;
        }

        StringBuilder sb = new StringBuilder();
        String sep = "";
        List<Pair<String, String>> tags = puzzle.getTags();
        for (Pair<String, String> tag : tags) {
            String value = tag.second;
            Matcher m = tagValuePattern.matcher(value);
            if (m.find()) {
                sb.append(sep).append("[").append(tag.first).append(" \"").append(value).append("\"]");
                sep = "\n";
            }
        }
        sb.append(sep).append("[").append(Config.TAG_FEN).append(" \"").append(resPgnGraph.getInitBoard().toFEN()).append("\"]\n\n");

        String moves = beautifyMoveLine(resPgnGraph.toPgn());
//        moves = moves.substring(0, moves.length() - 2).trim();      // unstring " *" and \n
        if ((resPgnGraph.getCurrentMove().moveFlags & Config.FLAGS_CHECKMATE) == 0) {
            moves += " *";
        }
        moves += "\n\n";
        out.println(String.format("%s%s", new String((sb)), moves));
        ++outputPuzzleCount;
        System.out.println(String.format("puzzle %d, %s saved: %s", inputPuzzleCount, pgnGraph.getInitBoard().toFEN(), beautifyMoveLine(resPgnGraph.toPgn())));
    }

    private String beautifyMoveLine(String moves) {
        // quick and dirty
        return moves.substring(0, moves.length() - 2).trim();      // unstring " *" and \n
    }

    private boolean verifyWithStockfish() {
        if (DEBUG) {
            System.out.println(String.format("Analyze: \n%s", initBoard.toString()));
        }
        lastInfoMessage = null;
//        uciEngine.waitUntilReady();
//        uciEngine.doAnalysis(true);
        synchronized (lock) {
            try {
                lock.wait(10003);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (DEBUG) {
            System.out.println(String.format("Wait done, end analysis: %d \n%s", System.currentTimeMillis(), initBoard.toString()));
            if (lastInfoMessage == null) {
                System.out.println(String.format("%s unsolved", initBoard.toString()));
            } else {
                System.out.println(String.format("%s solved: %s", initBoard.toString(), lastInfoMessage.moves));
            }
        }

        if (lastInfoMessage == null) {
            System.out.println(String.format("%s unsolved", initBoard.toString()));
            return false;
        }

        pgnGraph.toInit();
        String[] moves = lastInfoMessage.moves.split("\\s+");
        Move puzzleMove = firstMove;
        boolean same = true;
        int i = -1;
        while (puzzleMove != null) {
            System.out.println(String.format(Locale.US, "%s %s", pgnGraph.getBoard().toString(), puzzleMove.toString()));
            String move = moves[++i];
            String m = puzzleMove.getFrom().toString() + puzzleMove.getTo().toString();
            if (!move.substring(0, 4).equals(m)) {
                same = false;
                break;
            }
            if (move.length() > 4) {
                if (!puzzleMove.isPromotion()) {
                    same = false;
                    break;
                }
                int p = puzzleMove.getPiecePromoted();
                String puzzleProm = Config.FEN_PIECES.substring(p, p + 1).toLowerCase();
                String promotion = move.substring(4, 5);
                if (!promotion.equals(puzzleProm)) {
                    same = false;
                    break;
                }
            }
            pgnGraph.toNext();
            Board board = pgnGraph.getBoard();
            puzzleMove = board.getMove();
        }
        if (!same) {
            System.out.println(String.format(Locale.US, "%s found different moves: %s", initBoard.toString(), lastInfoMessage.moves));
            return false;
        }
        while (++i < moves.length) {
            String move = moves[i];
            Board board = pgnGraph.getBoard();
            puzzleMove = board.newMove();
            puzzleMove.setFrom(new Square(move.substring(0, 2)));
            puzzleMove.setTo(new Square(move.substring(2, 4)));
            puzzleMove.setPiece(board.getPiece(puzzleMove.getFrom()));
            if (move.length() > 4) {
                String promotion = move.substring(4, 5);
                int p = Config.FEN_PIECES.indexOf(promotion);
                puzzleMove.setPiecePromoted(p);
            }
            if (pgnGraph.validateUserMove(puzzleMove)) {
                try {
                    pgnGraph.addUserMove(puzzleMove);
                } catch (Config.PGNException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String getCurrentFen() {
//        return pgnGraph.getBoard().toFEN();
        return initBoard.toFEN();
    }

    @Override
    public void engineOk() {
        uciEngine.setOption("Hash", 64);
        // do we need this?
//        uciEngine.setOption("SyzygyPath", "/storage/emulated/0/DroidFish/rtb");
//        uciEngine.setOption("SyzygyPath", "/storage/emulated/0/DroidFish/rtb");
    }

    @Override
    public void acceptAnalysis(UCI.IncomingInfoMessage incomingInfoMessage) {
        lastInfoMessage = incomingInfoMessage;

//        if(moves.length >= totalMoves) {
//            System.out.println(String.format("%s stop analysis, %d moves", pgnGraph.getInitBoard().toFEN(), moves.length));
//            uciEngine.doAnalysis(false);
//            lastInfoMessage = incomingInfoMessage;
//            synchronized (lock) {
//                lock.notify();
//            }
//        }
    }

    @Override
    public void reportError(String message) {

    }

/*
    class StockFishEngine extends UCIEngine {
//        private final Object lock = new Object();
        boolean positionSent, analysisComplete;

        public StockFishEngine(EngineWatcher engineWatcher) throws IOException {
            super(engineWatcher);
            launch();
        }

        @Override
        protected synchronized void sendPosition() {
            if (positionSent) {
                if(!analysisComplete) {
                    analysisComplete = true;
                    uciEngine.doAnalysis(false);
                    if (DEBUG) {
                        System.out.println(String.format("lock.notify() start, analysis: %d \n%s", System.currentTimeMillis(), initBoard.toString()));
                    }
                    synchronized (Utility.this.lock) {
                        Utility.this.lock.notify();
                    }
                    if (DEBUG) {
                        System.out.println(String.format("lock.notify() end, analysis: %d \n%s", System.currentTimeMillis(), initBoard.toString()));
                    }
                }
                return;
            }
            String fen = pgnGraph.getBoard().toFEN();
            setOption(OPTION_SKILL_LEVEL, ANALYSIS_SKILL_LEVEL);
//            isBlackMove = fen.contains(" b ");      // quick and dirty
            writeCommand(UCIEngine.COMMAND_POSITION + fen);  // todo: send moves?
            setOption(UCIEngine.OPTION_ANALYSIS, true);
//            writeCommand(UCIEngine.COMMAND_GO_INFINITE);
            writeCommand("go depth " + (totalMoves + 2));
            setState(State.ANALYZE);
            positionSent = true;
        }

        @Override
        protected String getExecutablePath() throws IOException {
            return stockFishPath;
        }

        public void waitUntilReady() {
            positionSent = false;
            analysisComplete = false;
            while (state != State.IDLE) {
                synchronized (lock) {
                    try {
                        lock.wait(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }
*/

    @Test
    public void testJsonParsing() throws IOException, JSONException {
        final String srcHtml = "/home/alex/java/lichess-all/etc/r-0.htm";
        final String START = "lichess.puzzle = ";
        final String END = "</script>";
        String json = "";

        File file = new File(srcHtml);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            String src = new String(data, "UTF-8");
            int start = src.indexOf(START) + START.length();
            int end = src.indexOf(END, start);
            json = src.substring(start, end);
        }
//        System.out.println(json);
        JSONObject jsonObject = new JSONObject(json);

        System.out.println(jsonObject);

    }
}
