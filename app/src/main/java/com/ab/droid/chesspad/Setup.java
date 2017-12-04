package com.ab.droid.chesspad;

import android.util.Log;

import com.ab.pgn.BitStream;
import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Pack;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnItem;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.Square;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Setup data
 * Created by Alexander Bootman on 11/26/16.
 */

public class Setup implements ChessPadView.ChangeObserver {
    transient protected final String DEBUG_TAG = this.getClass().getName();

    private ChessPadView chessPadView;
    private Board board;
    private List<Pair<String, String>> headers = new LinkedList<>();

    ChessPadView.StringWrapper enPass, hmClock, moveNum;

    public Setup(PgnGraph pgnGraph, ChessPadView chessPadView) {
        this.chessPadView = chessPadView;
        this.board = pgnGraph.getBoard().clone();
        this.headers = pgnGraph.getPgn().cloneHeaders(Config.HEADER_FEN);
        this.headers.add(new Pair<>(Popups.ADD_HEADER_LABEL, ""));
        enPass = new ChessPadView.StringWrapper(_getEnpass(), this);
        hmClock = new ChessPadView.StringWrapper("" + getBoard().getReversiblePlyNum(), this);
        moveNum = new ChessPadView.StringWrapper("" + (getBoard().getPlyNum() / 2 + 1), this);
        onValueChanged(null);       // update status
    }

    public void serialize(BitStream.Writer writer) throws Config.PGNException {
        try {
            serializeSetupBoard(writer);
            PgnItem.serialize(writer, headers);
            enPass.serialize(writer);
            hmClock.serialize(writer);
            moveNum.serialize(writer);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public Setup(BitStream.Reader reader) throws Config.PGNException {
        try {
            this.board = unserializeSetupBoard(reader);
            this.headers = PgnItem.unserializeHeaders(reader);
            this.enPass = new ChessPadView.StringWrapper(reader, this);
            this.hmClock = new ChessPadView.StringWrapper(reader, this);
            this.moveNum = new ChessPadView.StringWrapper(reader, this);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    private void serializeSetupBoard(BitStream.Writer writer) throws Config.PGNException {
        try {
            Pack.packBoard(board, 0, writer);
            List<Square> wKings = new LinkedList<>();
            List<Square> bKings = new LinkedList<>();
            for (int x = 0; x < Config.BOARD_SIZE; x++) {
                for (int y = 0; y < Config.BOARD_SIZE; y++) {
                    int piece = board.getPiece(x, y);
                    if (piece == Config.WHITE_KING) {
                        wKings.add(new Square(x, y));
                    }
                    if (piece == Config.BLACK_KING) {
                        bKings.add(new Square(x, y));
                    }
                }
            }
            writer.write(wKings.size(), 6);
            for (Square sq : wKings) {
                sq.serialize(writer);
            }
            writer.write(bKings.size(), 6);
            for (Square sq : bKings) {
                sq.serialize(writer);
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    private Board unserializeSetupBoard(BitStream.Reader reader) throws Config.PGNException {
        try {
            Board board = Pack.unpackBoard(reader);
            int n = reader.read(6);
            for (int i = 0; i < n; ++i) {
                Square sq = new Square(reader);
                board.setPiece(sq, Config.WHITE_KING);
            }
            n = reader.read(6);
            for (int i = 0; i < n; ++i) {
                Square sq = new Square(reader);
                board.setPiece(sq, Config.BLACK_KING);
            }
            return board;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public void setChessPadView(ChessPadView chessPadView) {
        this.chessPadView = chessPadView;
    }

    private String _getEnpass() {
        Square sq = getBoard().getEnpassant();
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
        return PgnItem.getTitle(headers, -1);
    }

    public PgnGraph toPgnGraph() throws Config.PGNException {
        int err;
        if((err = validate()) != 0) {
            Log.e(DEBUG_TAG, String.format("Setup error %s\n%s", err, board.toString()));
            return new PgnGraph();
        }
        PgnGraph pgnGraph = new PgnGraph(board);
        headers.remove(headers.size() - 1);     // remove 'add new' row
        pgnGraph.getPgn().setHeaders(headers);
        return pgnGraph;
    }

    public void setHeaders(List<Pair<String, String>> headers) {
        this.headers = headers;
    }

    public int getFlag(int flag) {
        return getBoard().getFlags() & flag;
    }

    public void setFlag(boolean set, int flag) {
        if (set) {
            getBoard().setFlags(flag);
        } else {
            getBoard().clearFlags(flag);
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
                } else if((board.getFlags() & Config.FLAGS_BLACK_MOVE) == 0) {
                    if(sq.getY() != 5) {
                        errNum = 3;
                    }
                } else {
                    if(sq.getY() != 2) {
                        errNum = 4;
                    }
                }
                if(errNum == 0) {
                    board.setEnpassant(sq);
                    board.setFlags(Config.FLAGS_ENPASSANT_OK);
                } else {
                    board.clearFlags(Config.FLAGS_ENPASSANT_OK);
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
