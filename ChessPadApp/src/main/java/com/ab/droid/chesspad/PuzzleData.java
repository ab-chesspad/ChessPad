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

 * keep puzzle statistics
 * Created by Alexander Bootman on 1/18/19.
*/
package com.ab.droid.chesspad;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.ab.pgn.BitStream;
import com.ab.pgn.Config;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.io.CpFile;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;

public class PuzzleData {
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    private static final String
        PUZZLE_INFO_EXT = ".info",
        str_dummy = null;

    transient private final Random random = new Random(System.currentTimeMillis());
    //        transient private final Random random = new Random(1);    // debug
    private final ChessPad chessPad;
    private CpFile.PgnFile pgnFile;
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
        reset(null);
    }

    public void reset(CpFile.PgnFile pgnFile) {
        save();
        load(pgnFile);
        this.pgnFile = pgnFile;
    }

    private void clear() {
        totalPuzzles = Integer.MAX_VALUE;
        currentIndex = Integer.MAX_VALUE;
        totalOpened = 0;
        totalSolved =
        totalFailed = 0;
        solvedMoves = 0;
    }

    public void newPuzzle() {
        newPuzzle(true);
    }

    private void newPuzzle(boolean increment) {
        if (increment) {
            ++totalOpened;
        }
        solved = failed = started = false;
        solvedMoves = 0;
        PgnGraph pgnGraph = chessPad.getPgnGraph();
        if (pgnGraph != null) {
            solvedMoves = pgnGraph.moveLine.size() - 1;
        }
    }

    public void setFailed() {
        if (chessPad.isPuzzleMode() && !failed) {
            int msg = 0;
            if (chessPad.mode == ChessPad.Mode.Puzzle) {
                ++totalFailed;
                msg = R.string.msg_puzzle_failure_with_statistics;
            } // else?
            Toast.makeText(MainActivity.getContext(), statisticsToString(msg), Toast.LENGTH_LONG).show();
            failed = true;
        }
    }

    public boolean isUnsolved() {
        return !solved;
    }

    public boolean isDone() {
        return solved || failed;
    }

    public void setSuccess() {
        if (chessPad.isPuzzleMode()) {
            if (failed) {
                return;
            }
            if (!solved) {
                int msg = 0;
                if (chessPad.mode == ChessPad.Mode.Puzzle) {
                    ++totalSolved;
                    msg = R.string.msg_puzzle_success_with_statistics;
                }
                Toast.makeText(MainActivity.getContext(), statisticsToString(msg), Toast.LENGTH_LONG).show();
                solved = true;
            }
        }
    }

    public void setTotalPuzzles(int totalPuzzles) {
        this.totalPuzzles = totalPuzzles;
    }

    public  int getNextIndex() {
        if (this.totalPuzzles <= 0 || this.totalPuzzles == Integer.MAX_VALUE) {
            currentIndex = Integer.MAX_VALUE;
            return currentIndex;
        }

        int totalPuzzles = this.totalPuzzles;
        int newIndex = random.nextInt(totalPuzzles);
        if (totalPuzzles > 1) {
            while (newIndex == currentIndex) {
                newIndex = random.nextInt(totalPuzzles);
            }
        }
        currentIndex = newIndex;
        return currentIndex;
    }

    private void load(CpFile.PgnFile pgnFile) {
        totalPuzzles = Integer.MAX_VALUE;
        if (pgnFile != null) {
            try (DataInputStream dis = new DataInputStream(MainActivity.getContext().openFileInput(puzzleInfoName(pgnFile)))) {
                long timeStamp = dis.readLong();
                long pgnFileTimestamp = pgnFile.lastModified();
                if (timeStamp == pgnFileTimestamp) {
                    totalPuzzles = dis.readInt();
                    Log.d(DEBUG_TAG, String.format("from file totalPuzzles=%s", totalPuzzles));
                    totalOpened = dis.readInt();
                    totalSolved = dis.readInt();
                    totalFailed = dis.readInt();
                    currentIndex = dis.readInt();
                }
            } catch (IOException e) {
                Log.w(DEBUG_TAG, e.getLocalizedMessage());
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
        final String form = MainActivity.getContext().getResources().getString(format);
        return String.format(Locale.getDefault(), form, totalOpened, totalSolved, (float)totalSolved * 100 / totalOpened);
    }

    private void save() {
        if (pgnFile == null) {
            return;
        }
        try (DataOutputStream dos = new DataOutputStream(MainActivity.getContext().openFileOutput(puzzleInfoName(pgnFile), Context.MODE_PRIVATE))) {
            long pgnFileTimestamp = pgnFile.lastModified();
            dos.writeLong(pgnFileTimestamp);
            dos.writeInt(totalPuzzles);
            dos.writeInt(totalOpened);
            dos.writeInt(totalSolved);
            dos.writeInt(totalFailed);
            dos.writeInt(currentIndex);
        } catch (IOException e) {
            Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
        }
        clear();
    }

    private String puzzleInfoName(CpFile.PgnFile pgnFile) {
        String path = pgnFile.getAbsolutePath();
        path = path.replaceAll("/", "_");
        int i = path.lastIndexOf(".");
        return (path.substring(0, i) + PUZZLE_INFO_EXT);
    }

    void unserialize(BitStream.Reader reader) throws Config.PGNException {
        try {
            if (reader.read(1) == 1) {
                String path = reader.readString();
                pgnFile = (CpFile.PgnFile)CpFile.fromPath(path);
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
            if (pgnFile == null) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                writer.writeString(pgnFile.getAbsolutePath());
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
