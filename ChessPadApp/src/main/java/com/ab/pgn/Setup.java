package com.ab.pgn;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Setup data
 * Created by Alexander Bootman on 11/26/16.
 */

public class Setup {
    private final PgnLogger logger = PgnLogger.getLogger(PgnGraph.class);

    private int errNum;
    private Board board;
    private List<Pair<String, String>> tags;
    private String enPass = "";

    public Setup(PgnGraph pgnGraph) {
        this.board = pgnGraph.getBoard().clone();
        this.board.setMove(null);
        this.tags = pgnGraph.getPgn().getTags();
        int index = ((CpFile.Pgn)pgnGraph.getPgn().getParent()).getTagIndex(Config.TAG_Round);
        int round = 1;
        try {
            round = Integer.valueOf(this.tags.get(index).second);
        } catch (Exception e) {
            // ignore
        }
        this.tags.set(index, new Pair<>(Config.TAG_Round, "" + round));
        setEnPass();
    }

    public void serialize(BitStream.Writer writer) throws Config.PGNException {
        serializeSetupBoard(writer);
        CpFile.serializeTagList(writer, tags);
    }

    public Setup(BitStream.Reader reader) throws Config.PGNException {
        this.board = unserializeSetupBoard(reader);
        this.tags = CpFile.unserializeTagList(reader);
        setEnPass();
    }

    private void serializeSetupBoard(BitStream.Writer writer) throws Config.PGNException {
        try {
            board.pack(writer);
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
            Board board = Board.unpack(reader);
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
        if(errNum != 0) {
            logger.error(String.format("Setup error %s\n%s", errNum, board.toString()));
            return new PgnGraph();
        }
        PgnGraph pgnGraph = new PgnGraph(board);
        pgnGraph.getPgn().setTags(tags);
        Board initBoard = pgnGraph.getInitBoard();
        if(!initBoard.equals(new Board())) {
            pgnGraph.getPgn().setFen(initBoard.toFEN());
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
        if(enPass != null && enPass.length() == 2) {
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

}
