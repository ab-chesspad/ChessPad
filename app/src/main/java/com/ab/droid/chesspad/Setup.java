package com.ab.droid.chesspad;

import android.util.Log;

import com.ab.pgn.BitStream;
import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnItem;
import com.ab.pgn.PgnTree;
import com.ab.pgn.Square;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Setup data
 * Created by alex on 11/26/16.
 */

public class Setup implements ChessPadView.ChangeObserver {
    transient protected final String DEBUG_TAG = this.getClass().getName();

    private ChessPadView chessPadView;
    private Board board;
    private List<Pair<String, String>> headers = new LinkedList<>();

    ChessPadView.StringWrapper enPass, hmClock, moveNum;

    public Setup(PgnTree pgnTree, ChessPadView chessPadView) {
        this.chessPadView = chessPadView;
        this.board = pgnTree.getBoard().clone();
        this.headers = pgnTree.getPgn().cloneHeaders(Config.HEADER_FEN);
        this.headers.add(new Pair<>(Popups.ADD_HEADER_LABEL, ""));
        enPass = new ChessPadView.StringWrapper(_getEnpass(), this);
        hmClock = new ChessPadView.StringWrapper("" + getBoard().reversiblePlyNum, this);
        moveNum = new ChessPadView.StringWrapper("" + (getBoard().plyNum / 2 + 1), this);
        onValueChanged(null);       // update status
    }

    public void serialize(BitStream.Writer writer) throws IOException {
        board.serialize(writer);
        PgnItem.serialize(writer, headers);
        enPass.serialize(writer);
        hmClock.serialize(writer);
        moveNum.serialize(writer);
    }

    public Setup(BitStream.Reader reader) throws IOException {
        this.board = new Board(reader);
        this.headers = PgnItem.unserializeHeaders(reader);
        this.enPass = new ChessPadView.StringWrapper(reader, this);
        this.hmClock = new ChessPadView.StringWrapper(reader, this);
        this.moveNum = new ChessPadView.StringWrapper(reader, this);
    }

    public void setChessPadView(ChessPadView chessPadView) {
        this.chessPadView = chessPadView;
    }

    private String _getEnpass() {
        Square sq = getBoard().getEnpass();
        if(sq.getY() == -1) {
            return "";
        }
        return sq.toString();
    }

    public Board getBoard() {
        return board;
    }

    public void setBoard(Board board) {
        this.board = board;
    }

    public List<Pair<String, String>> getHeaders() {
        return headers;
    }

    public String getTitleText() {
        return PgnItem.getTitle(headers);
    }

    public PgnTree toPgnTree() throws IOException {
        PgnTree pgnTree = new PgnTree(board);
        headers.remove(headers.size() - 1);     // remove 'add new' row
        pgnTree.getPgn().setHeaders(headers);
        return pgnTree;
    }

    public void setHeaders(List<Pair<String, String>> headers) {
/*
        int fenIndex = this.headers.indexOf(Config.HEADER_FEN);
        if(fenIndex != -1) {
            headers.add(headers.get(fenIndex));
        }
*/
        this.headers = headers;
    }

    public int getFlags() {
        return board.flags;
    }

    public int getFlag(int flag) {
        return getBoard().flags & flag;
    }

    public void setFlag(boolean set, int flag) {
        if (set) {
            getBoard().flags |= flag;
        } else {
            getBoard().flags &= ~flag;
        }
        onAnyValueChanged();
    }

    public boolean onSquareClick(Square clicked, int newPiece) {
        Log.d(DEBUG_TAG, String.format("onSquareClick %s, new piece %s", clicked.toString(), newPiece));
        board.setPiece(clicked, newPiece);
        onAnyValueChanged();
        return true;
    }

    public boolean onFling(Square clicked) {
        Log.d(DEBUG_TAG, String.format("onFling %s", clicked.toString()));
        board.setPiece(clicked, Config.EMPTY);
        onAnyValueChanged();
        return true;
    }

    // return error number
    public int validate() {
        return board.validateSetup();
    }

    public void onAnyValueChanged() {
        int errNum = 0;
        if(chessPadView != null) {
            String enPass = this.enPass.getValue();
            if(enPass != null && !enPass.isEmpty()) {
                Square sq = new Square(enPass);
                if (sq.getX() < 0 || sq.getY() < 0) {
                    errNum = 1;
                } else if((board.flags & Config.FLAGS_BLACK_MOVE) == 0) {
                    if(sq.getY() != 5) {
                        errNum = 3;
                    }
                } else {
                    if(sq.getY() != 2) {
                        errNum = 4;
                    }
                }
                if(errNum == 0) {
                    board.enpass = sq.getX();
                    board.flags |= Config.FLAGS_ENPASSANT_OK;
                } else {
                    board.flags &= ~Config.FLAGS_ENPASSANT_OK;
                }
            }
            if(errNum == 0) {
                errNum = validate();
            }
            chessPadView.setStatus(errNum);
            chessPadView.invalidate();
        }
    }

    @Override
    public void onValueChanged(Object value) {
        onAnyValueChanged();
    }

}
