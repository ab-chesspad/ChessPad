package com.ab.pgn;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * Record Dgt Board moves, notify DgtBoard.UpdateListener about changes on the board
 *
 * Created by Alexander Bootman on 3/18/18.
 */
public class DgtBoardWatcher implements DgtBoard.UpdateListener {
    public static boolean DEBUG = false;
    private DgtBoard dgtBoard;
    private UpdateListener updateListener;
    private PgnGraph pgnGraph;
    private List<DgtBoard.BoardDataMoveChunk> chunks = new LinkedList<>();
    private Move incompleteMove = null;
    private HashSet<DgtBoard.BoardDataMoveChunk> expectedChunks = new HashSet<>();
    private Square expectedPromotion;
    private boolean skipFirstMessage = false;
    private DgtBoard.BoardData lastBoardData;
    private boolean definePositionFlags = true;

    public enum UpdateState {
        None,
        NewPosition,
        NextMove,
        Checkmate,
    }

    public DgtBoardWatcher(String dgtBoardPort, int timeout, UpdateListener updateListener) throws Config.PGNException {
        if(DgtBoard.REPEAT_COMMAND_AFTER_MSEC > 0) {
            skipFirstMessage = true;
        }
        this.updateListener = updateListener;
        dgtBoard = new DgtBoard(dgtBoardPort, timeout, this);
        dgtBoard.sendCommand(DgtBoardProtocol.DGT_SEND_RESET);
        dgtBoard.sendCommand(DgtBoardProtocol.DGT_SEND_TRADEMARK);
        dgtBoard.sendCommand(DgtBoardProtocol.DGT_SEND_BRD);
        dgtBoard.sendCommand(DgtBoardProtocol.DGT_SEND_UPDATE_BRD);
        System.out.println("DgtBoardWatcher started");
    }

    public PgnGraph getPgnGraph() {
        return pgnGraph;
    }

    public void stop() {
        dgtBoard.closeBoard();
    }

    @Override
    public void update(DgtBoard.BoardData boardData) throws Config.PGNException {
        if(DEBUG) {
            System.out.println(String.format("DgtBoardWatcher.update, %s", boardData.toString()));
        }

        if( boardData instanceof DgtBoard.BoardDataPosition) {
            DgtBoard.BoardDataPosition position = (DgtBoard.BoardDataPosition)boardData;
            if(skipFirstMessage) {
                if(lastBoardData == null) {
                    lastBoardData = boardData;
                    return;
                }
            }
            lastBoardData = null;
            if(pgnGraph == null) {
                pgnGraph = new PgnGraph(position.board);
                definePositionFlags = !position.board.equals(new Board());
                if(updateListener != null) {
                    updateListener.update(UpdateState.NewPosition, pgnGraph);
                }
            } else {
                // todo: unexpected move(s), position refreshed
            }
        } else if( boardData instanceof DgtBoard.BoardDataMoveChunk) {
            DgtBoard.BoardDataMoveChunk chunk = (DgtBoard.BoardDataMoveChunk)boardData;
            if(expectedPromotion != null && expectedChunks.size() == 0) {
                int color;
                if(expectedPromotion.getY() == 7) {
                    color = Config.WHITE;
                } else {
                    color = Config.BLACK;
                }
                if ((chunk.piece & Config.PIECE_COLOR) == color) {
                    incompleteMove.piecePromoted = chunk.piece;
                    if(validateMove(incompleteMove, definePositionFlags)) {
                        addMove(incompleteMove);
                        expectedPromotion = null;
                    }
                    return;
                }
            }
            if(expectedChunks.remove(chunk)) {
                if(expectedChunks.size() == 0) {
                    if(expectedPromotion == null) {
                        addMove(incompleteMove);
                    }
                }
                return;
            }
            System.out.println(pgnGraph.getBoard().toString());
            chunks.add(chunk);
            if(chunks.size() >= 2) {
                createMove();
            }
        } else if( boardData instanceof DgtBoard.BoardDataTrademark) {
            DgtBoard.BoardDataTrademark trademark = (DgtBoard.BoardDataTrademark)boardData;
            if(skipFirstMessage) {
                if(lastBoardData == null) {
                    lastBoardData = boardData;
                    return;
                }
            }
            lastBoardData = null;
            System.out.println(trademark.toString());
        }
    }

    private void createMove() throws Config.PGNException {
        for(int i = 0; i < chunks.size(); ++i) {
            DgtBoard.BoardDataMoveChunk chunkTo = chunks.get(i);
            if(chunkTo.piece != Config.EMPTY) {
                for(int j = 0; j < chunks.size(); ++j) {
                    if(j == i) {
                        continue;   // skip
                    }
                    DgtBoard.BoardDataMoveChunk chunkFrom = chunks.get(j);
                    if(chunkFrom.piece == Config.EMPTY) {
                        Board board = pgnGraph.getBoard();
                        int oldPiece = board.getPiece(chunkFrom.square);
                        if(oldPiece == chunkTo.piece && !chunkFrom.square.equals(chunkTo.square)) {
                            Move move = new Move(board, chunkFrom.square, chunkTo.square);
                            if(validateMove(move, definePositionFlags)) {
                                if((move.moveFlags & Config.FLAGS_CASTLE) != 0) {
                                    incompleteMove = move;
                                    setChunksForCastle(move);
                                } else if((move.moveFlags & Config.FLAGS_ENPASSANT) != 0) {
                                    incompleteMove = move;
                                    setChunksForEnpassant(move);
                                } else if((move.moveFlags & Config.FLAGS_PROMOTION) != 0) {
                                    incompleteMove = move;
                                    setChunksForPromotion(move);
                                } else {
                                    addMove(move);
                                }
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private void addMove(Move move) throws Config.PGNException {
        pgnGraph.addUserMove(move);
        chunks.clear();
        if(updateListener != null) {
            if((move.moveFlags & Config.FLAGS_CHECKMATE) != 0) {
                updateListener.update(UpdateState.Checkmate, pgnGraph);
            } else {
                updateListener.update(UpdateState.NextMove, pgnGraph);
            }
        }
        definePositionFlags = false;
    }

    private boolean validateMove(Move move, boolean definePositionFlags) {
        if(definePositionFlags) {
            defineInitPositionFlags(move);
        }
        return pgnGraph.validateUserMove(move);
    }

    // based on the board position we cannot tell whether castling or enpassant is allowed
    // so let's assume that the first move is legit and try to figure out the flags
    private void defineInitPositionFlags(Move move) {
        Board board = pgnGraph.getBoard();
        int flags = board.getFlags();
        if (board.getPiece(4, 0) != Config.WHITE_KING) {
            board.clearFlags(Config.FLAGS_W_QUEEN_OK | Config.FLAGS_W_KING_OK);
        } else {
            if (board.getPiece(0, 0) != Config.WHITE_ROOK) {
                board.clearFlags(Config.FLAGS_W_QUEEN_OK);
            }
            if (board.getPiece(7, 0) != Config.WHITE_ROOK) {
                board.clearFlags(Config.FLAGS_W_KING_OK);
            }
        }
        if (board.getPiece(4, 7) != Config.BLACK_KING) {
            board.clearFlags(Config.FLAGS_B_QUEEN_OK | Config.FLAGS_B_KING_OK);
        } else {
            if (board.getPiece(0, 7) != Config.BLACK_ROOK) {
                board.clearFlags(Config.FLAGS_B_QUEEN_OK);
            }
            if (board.getPiece(7, 7) != Config.BLACK_ROOK) {
                board.clearFlags(Config.FLAGS_B_KING_OK);
            }
        }
        int piece = board.getPiece(move.from);
        if((piece & Config.BLACK) != 0) {
            board.raiseFlags(Config.FLAGS_BLACK_MOVE);
            board.setPlyNum(1);
        }

        // enpassant:
        if ((piece & ~Config.BLACK) == Config.PAWN && move.from.x != move.from.y) {
            int hisPawn = piece ^ Config.BLACK;
            if (board.getPiece(move.to) == Config.EMPTY && board.getPiece(move.to.x, move.from.y) == hisPawn) {
                board.raiseFlags(Config.FLAGS_ENPASSANT_OK);
                board.setEnpassant(new Square(move.to.x, move.from.y));
            }
        }
    }

    private void setChunksForCastle(Move move) {
        int x0, x1;
        int piece;
        if(move.piece == Config.WHITE_KING) {
            piece = Config.WHITE_ROOK;
        } else {
            piece = Config.BLACK_ROOK;
        }
        if(move.to.x == 6) {
            // 0-0
            x0 = 7;
            x1 = 5;
        } else {
            // 0-0-0
            x0 = 0;
            x1 = 3;
        }
        expectedChunks.add(dgtBoard.new BoardDataMoveChunk(x0, move.from.y, Config.EMPTY));
        expectedChunks.add(dgtBoard.new BoardDataMoveChunk(x1, move.from.y, piece));
    }

    private void setChunksForEnpassant(Move move) {
        expectedChunks.add(dgtBoard.new BoardDataMoveChunk(move.to.x, move.from.y, Config.EMPTY));
    }

    private void setChunksForPromotion(Move move) {
        expectedChunks.add(dgtBoard.new BoardDataMoveChunk(move.to.x, move.to.y, Config.EMPTY));
        expectedPromotion = move.to;
    }

    public static void main(String[] args) throws Config.PGNException, IOException {
        String ttyPort = args[0];
        int timeout = Integer.valueOf(args[1]);
        System.out.println(String.format("ttyPort=%s, timeout=%s\n", ttyPort, timeout));
        System.out.println("*\n* Dgt Board initialization takes time, don't move pieces yet...\n*");
        final boolean[] done = {false};

        DgtBoardWatcher dgtBoardWatcher = new DgtBoardWatcher(ttyPort, timeout, new UpdateListener() {
            @Override
            public boolean update(UpdateState updateState, PgnGraph pgnGraph) throws Config.PGNException {
                switch (updateState) {
                    case NewPosition:
                        System.out.println(pgnGraph.getBoard().toString());
                        break;

                    case NextMove:
                        System.out.println(pgnGraph.getCurrentMove().toString());
                        System.out.println(pgnGraph.getBoard().toString());
                        break;

                    case Checkmate:
                        System.out.println(pgnGraph.getInitBoard().toString());
                        System.out.println(pgnGraph.toPgn());
                        done[0] = true;
                        return false;

                }
                return true;
            }
        });

        System.out.print("Move pieces or click Enter to finish: ");
        while (!done[0]) {
            if (System.in.available() > 0) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
        dgtBoardWatcher.stop();
        if (!done[0]) {
            System.out.println(dgtBoardWatcher.getPgnGraph().getInitBoard().toString());
            System.out.println(dgtBoardWatcher.getPgnGraph().toPgn());
        }
    }
    public interface UpdateListener {
        // return false to stop
        boolean update(UpdateState updateState, PgnGraph pgnGraph) throws Config.PGNException;
    }
}
