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

 * Setup data
 * Created by Alexander Bootman on 11/26/16.
 */
package com.ab.pgn;

import com.ab.pgn.io.CpFile;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class Setup {
    private final PgnLogger logger = PgnLogger.getLogger(PgnGraph.class);

    public enum PredefinedPosition {
        User,
        Empty,
        Init,
    }

    private int errNum;
    private Board board, savedBoard;
    private List<Pair<String, String>> tags;
    private String enPass = "";
    private boolean flipped = false;
    private PredefinedPosition currentPredefinedPosition = PredefinedPosition.User;

    public Setup(PgnGraph pgnGraph) {
        this.board = pgnGraph.getBoard().clone();
        this.board.setMove(null);
        this.tags = pgnGraph.getPgnItem().getTags();
        savedBoard = board;
        int index = ((CpFile.PgnFile)pgnGraph.getPgnItem().getParent()).getTagIndex(Config.TAG_Round);
        int round = 1;
        try {
            round = Integer.valueOf(this.tags.get(index).second);
        } catch (Exception e) {
            // ignore
        }
        this.tags.set(index, new Pair<>(Config.TAG_Round, "" + round));
        setEnPass();
        currentPredefinedPosition = PredefinedPosition.User;
    }

    public void serialize(BitStream.Writer writer) throws Config.PGNException {
        serializeSetupBoard(writer);
        CpFile.serializeTagList(writer, tags);
    }

    public Setup(BitStream.Reader reader) throws Config.PGNException {
        unserializeSetupBoard(reader);
        this.tags = CpFile.unserializeTagList(reader);
        setEnPass();
    }

    private void serializeSetupBoard(BitStream.Writer writer) throws Config.PGNException {
        try {
            board.serializeAnyBoard(writer);
            writer.write(flipped ? 1 : 0, 1);
            writer.write(currentPredefinedPosition.ordinal(), 2);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    private void unserializeSetupBoard(BitStream.Reader reader) throws Config.PGNException {
        try {
            board = Board.unserializeAnyBoard(reader);
            flipped = reader.read(1) == 1;
            int index = reader.read(2);
            currentPredefinedPosition = PredefinedPosition.values()[index];
            savedBoard = board;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void setFlipped(boolean  flipped) {
        this.flipped = flipped;
    }

    public Board getBoard() {
        return board;
    }

    public void setBoard(Board board) {
        this.board = board;
    }

    public void setBoardPosition(Board board) {
        this.board.copyPosition(board);
        validate();
    }

    public List<Pair<String, String>> getTags() {
        return tags;
    }

    public String getTitleText() {
        return CpFile.getTitle(tags);
    }

    public PgnGraph toPgnGraph() throws Config.PGNException {
        validate();
        if (errNum != 0) {
            logger.error(String.format("Setup error %s\n%s", errNum, board.toString()));
            return new PgnGraph();
        }
        PgnGraph pgnGraph = new PgnGraph(board);
        pgnGraph.getPgnItem().setTags(tags);
        Board initBoard = pgnGraph.getInitBoard();
        if (!initBoard.equals(new Board())) {
            pgnGraph.getPgnItem().setFen(initBoard.toFEN());
        }
        return pgnGraph;
    }

    public void setTags(List<Pair<String, String>> tags) {
        this.tags = tags;
    }

    public int getFlag(int flag) {
        return getBoard().getFlags() & flag;
    }

    public void setFlag(int flag, boolean set) {
        if (set) {
            getBoard().raiseFlags(flag);
        } else {
            getBoard().clearFlags(flag);
        }
    }

    // setup error number
    public void validate() {
        errNum = board.validateSetup(true);   // debug?
    }

    public int getErrNum() {
        return errNum;
    }

    private void setEnPass() {
        Square sq = this.board.getEnpassant();
        if (sq.getX() == -1) {
            this.enPass = "";
        } else {
            this.enPass = sq.toString();
        }
    }

    public void setEnPass(String enPass) {
        this.enPass = enPass;
        Square sq = new Square();
        if (enPass != null && enPass.length() == 2) {
            sq = new Square(enPass);
        }
        if (sq.getX() == -1) {
            board.clearFlags(Config.FLAGS_ENPASSANT_OK);
        } else {
            board.setEnpassant(sq);
            board.raiseFlags(Config.FLAGS_ENPASSANT_OK);
        }
    }

    public String getEnPass() {
        return enPass;
    }

    public PredefinedPosition getCurrentPredefinedPosition() {
        return currentPredefinedPosition;
    }

    public void setNextPredefinedPosition() {
        PredefinedPosition[] predefinedPositions = PredefinedPosition.values();
        int nextIndex = (currentPredefinedPosition.ordinal() + 1) % predefinedPositions.length;
        currentPredefinedPosition = predefinedPositions[nextIndex];
        switch (currentPredefinedPosition) {
            case Init:
                board = new Board();
                break;

            case User:
                board = savedBoard;
                break;

            case Empty:
                savedBoard = board;
                board = new Board();
                board.toEmpty();
                break;
        }
    }

}
