package com.ab.droid.chesspad;

import android.util.Log;
import android.widget.Toast;

import com.ab.pgn.BitStream;
import com.ab.pgn.Config;
import com.ab.pgn.CpFile;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.Util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;

public class PuzzleData {
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    private static final String
        PUZZLE_INFO_EXT = ".info",
        str_dummy = null;

    private static final int
        MIN_PUZZLE_TEXT_LENGTH = 40,    // or slightly less? [FEN "8/8/8/8/K7/8/8/7k w - - 0 1"]
        int_dummy = 0;

    transient private final Random random = new Random(System.currentTimeMillis());
    //        transient private final Random random = new Random(1);    // debug
    private final ChessPad chessPad;
    private CpFile.Pgn pgn;
    private int totalPuzzles = Integer.MAX_VALUE;
    private int totalOpened;
    private int totalSolved;
    private int totalFailed;
    private int currentIndex = Integer.MAX_VALUE;
    int solvedMoves;
    private boolean solved;
    private boolean failed;
    boolean started;

    public PuzzleData(ChessPad chessPad) {
        this.chessPad = chessPad;
    }

    public void setPgn(CpFile.Pgn pgn) {
        init(pgn);
    }

    private void init(CpFile.Pgn pgn) {
        boolean doSave = pgn != null && !pgn.equals(this.pgn) || pgn == null && this.pgn != null;
//        if (!doSave || started) {
//            updateTotals();
//        }
        if (doSave) {
            save();
            this.pgn = pgn;
            load();
        }
        if (pgn != null && pgn.getTotalChildren() == -1 && totalPuzzles != Integer.MAX_VALUE) {
            pgn.setTotalChildren(totalPuzzles);
        }
        newPuzzle();
    }

    private void clear() {
        totalPuzzles = Integer.MAX_VALUE;
        currentIndex = Integer.MAX_VALUE;
        totalOpened = 1;
        totalSolved =
        totalFailed = 0;
        solvedMoves = 0;
    }

    public void newPuzzle() {
        ++totalOpened;
        solved = failed = started = false;
        solvedMoves = 0;
        PgnGraph pgnGraph = chessPad.getPgnGraph();
        if (pgnGraph != null) {
            solvedMoves = pgnGraph.moveLine.size() - 1;
        }
    }

//    public boolean isFailed() {
//        return failed;
//    }

    public void setFailed() {
        if (chessPad.puzzleMode() && !failed) {
            int msg = 0;
            if (chessPad.mode == ChessPad.Mode.Puzzle) {
                ++totalFailed;
                msg = R.string.msg_puzzle_failure_with_statistics;
            } else if (chessPad.mode == ChessPad.Mode.LichessPuzzle) {
                msg = R.string.msg_puzzle_failure;
                chessPad.lichessPad.recordResult(chessPad.getPgnGraph(), 0);
            } // else?
            Toast.makeText(chessPad, statisticsToString(msg), Toast.LENGTH_LONG).show();
            failed = true;
        }
    }

    public boolean isSolved() {
        return solved;
    }

    public boolean isDone() {
        return solved || failed;
    }

    public void setSuccess() {
        if (chessPad.puzzleMode()) {
            if (failed) {
                return;
            }
            if (!solved) {
                int msg = 0;
                if (chessPad.mode == ChessPad.Mode.Puzzle) {
                    ++totalSolved;
                    msg = R.string.msg_puzzle_success_with_statistics;
                } else if (chessPad.mode == ChessPad.Mode.LichessPuzzle) {
                    msg = R.string.msg_puzzle_success;
                    chessPad.lichessPad.recordResult(chessPad.getPgnGraph(), 1);
                }
                Toast.makeText(chessPad, statisticsToString(msg), Toast.LENGTH_LONG).show();
                solved = true;
            }
        }
    }

//    public void updateTotals() {
//        if (started) {
//            if (solved) {
//                ++totalSolved;
//            } else {
//                ++totalFailed;
//            }
//        }
//    }

    public void setTotalPuzzles() {
        totalPuzzles = pgn.getTotalChildren();
    }

    public  int getNextIndex() {
        int totalPuzzles = this.totalPuzzles;
        if (totalPuzzles == Integer.MAX_VALUE) {
            totalPuzzles = pgn.getLength() / MIN_PUZZLE_TEXT_LENGTH;
        }
        if( totalPuzzles <= 0) {
            totalPuzzles = 1;
        }

        int newIndex = random.nextInt(totalPuzzles);
        if(totalPuzzles > 1) {
            while (newIndex == currentIndex) {
                newIndex = random.nextInt(totalPuzzles);
            }
        }
        currentIndex = newIndex;
        return currentIndex;
    }

    private void load() {
        totalPuzzles = Integer.MAX_VALUE;
        if (pgn != null) {
            File puzzleInfoFile = getPuzzleInfoFile(pgn);
            long puzzleInfoTimestamp = puzzleInfoFile.lastModified();
            long pgnTimestamp = pgn.lastModified();
            if (puzzleInfoTimestamp > pgnTimestamp) {
                try (FileInputStream fis = new FileInputStream(puzzleInfoFile)) {
                    totalPuzzles = Util.readInt(fis);
                    Log.d(DEBUG_TAG, String.format("from file totalPuzzles=%s", totalPuzzles));
                    totalOpened = Util.readInt(fis);
                    totalSolved = Util.readInt(fis);
                    totalFailed = Util.readInt(fis);
                    currentIndex = Util.readInt(fis);
                    pgn.setTotalChildren(totalPuzzles);     // check on -1?
                } catch (IOException e) {
                    Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
                }
            }
        }

        if (totalPuzzles == Integer.MAX_VALUE) {
            clear();
        }
    }

    public String statisticsToString(int format) {
        if (totalOpened <= 0) {
            return "";
        }
        final String form = chessPad.getResources().getString(format);
        return String.format(Locale.getDefault(), form, totalOpened, totalSolved, (float)totalSolved * 100 / totalOpened);
    }

    public void save() {
        if (pgn == null) {
            return;
        }
//        Toast.makeText(chessPad, statisticsToString(), Toast.LENGTH_LONG).show();
        File puzzleInfoFile = getPuzzleInfoFile(pgn);
        try (FileOutputStream fos = new FileOutputStream(puzzleInfoFile)) {
            Util.writeInt(fos, totalPuzzles);
            Util.writeInt(fos, totalOpened);
            Util.writeInt(fos, totalSolved);
            Util.writeInt(fos, totalFailed);
            Util.writeInt(fos, currentIndex);
        } catch (IOException e) {
            Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
        }
        clear();
    }

    private File getPuzzleInfoFile(CpFile pgn) {
        String path = pgn.getAbsolutePath().substring(CpFile.getRootPath().length() + 1);
        path = path.replaceAll("/", "_");
        int i = path.lastIndexOf(".");
        String puzzleInfoPath = (path.substring(0, i) + PUZZLE_INFO_EXT);
        return new File(CpFile.getRootPath() + File.separator + chessPad.getDefaultDirectory(), puzzleInfoPath);
    }

//    public File getPuzzleUpdateFile() {
//        String path = pgn.getAbsolutePath();
//        int i = path.lastIndexOf(".");
//        String puzzleUpdPath = (path.substring(0, i) + PUZZLE_UPDATE_FILENAMEPUZZLE_UPDATE_FILENAME);
//        return new File(puzzleUpdPath);
//    }

    void unserialize(BitStream.Reader reader) throws Config.PGNException {
        try {
            if (reader.read(1) == 1) {
                String path = reader.readString();
                pgn = (CpFile.Pgn)CpFile.fromFile(new File(path));
            }
            totalPuzzles = reader.read(32);
            totalOpened = reader.read(32);
            totalSolved = reader.read(32);
            totalFailed = reader.read(32);
            currentIndex = reader.read(32);
            solved = reader.read(1) == 1;
            failed = reader.read(1) == 1;
            started = reader.read(1) == 1;
            solvedMoves = reader.read(8);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    void serialize(BitStream.Writer writer) throws Config.PGNException {
        try {
            if(pgn == null) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                writer.writeString(pgn.getAbsolutePath());
            }
            writer.write(totalPuzzles, 32);
            writer.write(totalOpened, 32);
            writer.write(totalSolved, 32);
            writer.write(totalFailed, 32);
            writer.write(currentIndex, 32);
            writer.write(solved ? 1 : 0, 1);
            writer.write(failed ? 1 : 0, 1);
            writer.write(started ? 1 : 0, 1);
            writer.write(solvedMoves, 8);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

}
