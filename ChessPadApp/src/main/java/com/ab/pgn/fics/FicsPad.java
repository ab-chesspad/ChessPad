package com.ab.pgn.fics;

import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Move;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.PgnLogger;
import com.ab.pgn.Util;
import com.ab.pgn.fics.chat.InboundMessage;

import java.io.IOException;

/**
 *
 * Created by Alexander Bootman on 5/6/19.
 */
public class FicsPad {
    private static final String
        COMMAND_GET_MOVES = "moves",
        dummy_string = null;

    enum FicsStatus {
        None,
        Play,
        Observe,
        Exam,
    }

    final PgnLogger logger = PgnLogger.getLogger(this.getClass(), true);

//    private InboundMessageConsumer inboundMessageConsumer;
    private FicsClient ficsClient;
    private InboundMessage.G1Game currentGame;
    private PgnGraph pgnGraph;

    public FicsPad(FicsSettings ficsSettings, final InboundMessageConsumer inboundMessageConsumer) throws IOException {
        String user = "guest";
        String password = "";
        if(!ficsSettings.isLoginAsGuest()) {
            user = ficsSettings.getPassword();
            password = ficsSettings.getPassword();
        }
        connect(user, password, inboundMessageConsumer);
    }

    public FicsPad(String user, String password, final InboundMessageConsumer inboundMessageConsumer) throws IOException {
        connect(user, password, inboundMessageConsumer);
//        this.inboundMessageConsumer = inboundMessageConsumer;
/*
        init(new InboundMessage.G1Game());

        ficsClient = new FicsClient(user, password, new FicsClient.GameHolder() {
            @Override
            public InboundMessage.G1Game getGame() {
                return currentGame;
            }
        }, new FicsClient.InboundMessageConsumer() {
            @Override
            public void consume(InboundMessage.Info inboundMessage) {
                if(inboundMessage.getClass().equals(InboundMessage.G1Game.class)) {
                    if (handleG1((InboundMessage.G1Game) inboundMessage)) {
                        inboundMessage = currentGame;
                    }
//                } else if(inboundMessage.getClass().equals(InboundMessage.Info.class)) {
//                    if (doPrint) {
//                        logger.debug(inboundMessage.toString());
//                    }
//                } else if(inboundMessage.getClass().equals(InboundMessage.InboundList.class)) {
//                    System.out.println(inboundMessage.toString());
                }
                if (inboundMessageConsumer != null) {
                    inboundMessageConsumer.consume(inboundMessage);
                }
//                ++count[0];
            }
        });
        ficsClient.connect();
*/
    }

    private void connect(String user, String password, final InboundMessageConsumer inboundMessageConsumer) throws IOException {
        init(new InboundMessage.G1Game());

        ficsClient = new FicsClient(user, password, new FicsClient.GameHolder() {
            @Override
            public InboundMessage.G1Game getGame() {
                return currentGame;
            }
        }, new FicsClient.InboundMessageConsumer() {
            @Override
            public void consume(InboundMessage.Info inboundMessage) {
                if(inboundMessage.getClass().equals(InboundMessage.G1Game.class)) {
                    if (handleG1((InboundMessage.G1Game) inboundMessage)) {
                        inboundMessage = currentGame;
                    }
//                } else if(inboundMessage.getClass().equals(InboundMessage.Info.class)) {
//                    if (doPrint) {
//                        logger.debug(inboundMessage.toString());
//                    }
//                } else if(inboundMessage.getClass().equals(InboundMessage.InboundList.class)) {
//                    System.out.println(inboundMessage.toString());
                }
                if (inboundMessageConsumer != null) {
                    inboundMessageConsumer.consume(inboundMessage);
                }
//                ++count[0];
            }
        });
        ficsClient.connect();
    }

    public void close() throws IOException {
        ficsClient.close();
    }

    public boolean isConnected() {
        return ficsClient.isAlive();
    }

    public void write(String command) throws IOException {
        ficsClient.write(command);
    }

    private void init(InboundMessage.G1Game gameInfo) {
        currentGame = gameInfo;
        try {
            pgnGraph = new PgnGraph();
        } catch (Config.PGNException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public InboundMessage.G1Game getCurrentGame() {
        return currentGame;
    }

    public PgnGraph getPgnGraph() {
        return pgnGraph;
    }

    private boolean handleG1(InboundMessage.G1Game gameInfo) {
        if(!gameInfo.isUpdated()) {
            return false;
        }

        boolean ok = false;
        if(!gameInfo.getId().equals(currentGame.getId())) {
            // forget old G1
            init(gameInfo);
        }

        for(InboundMessage.InboundMove inboundMove : gameInfo.getInboundMoves()) {
            try {
                if(inboundMove.moveText.equals(Config.FICS_NO_MOVE)) {
                    pgnGraph = new PgnGraph(inboundMove.board);
                    logger.debug(String.format("New game\n%s", inboundMove.board.toString()));
                    continue;
                }
                Board board = pgnGraph.getBoard();
                Move move = new Move(board.getFlags() & Config.FLAGS_BLACK_MOVE);
                Util.parseMove(move, inboundMove.moveText);

                ok = pgnGraph.validatePgnMove(move);
                if(ok) {
                    pgnGraph.addUserMove(move);
                    if(inboundMove.board != null) {
                        // on 'moves' we get them without board
                        if (pgnGraph.getBoard().equals(inboundMove.board)) {
                            logger.debug(String.format("Move %s\n%s", inboundMove.moveText, inboundMove.board.toString()));
                        } else {
                            if(gameInfo.getWhiteRemainingTime() < 0 || gameInfo.getWhiteRemainingTime() < 0) {
                                gameInfo.getInboundMoves().clear();
                                return ok;
                            }
                            logger.error(String.format("New position after %s \n%s\n different from old\n%s",
                                    inboundMove.moveText, inboundMove.board.toString(), pgnGraph.getBoard().toString()));
                            ok = false;
                        }
                    }
                }
                if(!ok){
                    // Game 104: puzzlebot backs up 1 move.
                    //<12> b---r--- --rN-ppk --n-p--- p------- P----P-- -R------ q-P--QPP ---R--K- W -1 0 0 0 0 0 104 abfics puzzlebot 2 0 0 27 29 0 0 2 K/g8-h7 (0:00.000) Kxh7 0 0 0
                    // for illegal move fics ignores it, sends the same position
                    // validate?
                    if(!pgnGraph.getBoard().equals(inboundMove.board)) {
                        while (pgnGraph.moveLine.size() > 1) {
                            pgnGraph.toPrev();
                            if (pgnGraph.getBoard().equals(inboundMove.board)) {
                                break;
                            }
                        }
                    }
                    logger.debug(String.format("backed up to %s\n%s", inboundMove.moveText, inboundMove.board.toString()));
                    if(pgnGraph.moveLine.size() == 1) {
                        // This happens when observing game. fics sends only the latest move without move line
                        logger.error(String.format("cannot find position\n%s for move %s", inboundMove.board.toString(), inboundMove.moveText));
                        // should we check that we are observing?
                        ficsClient.write(COMMAND_GET_MOVES);
                    }
                }
            } catch (Config.PGNException e) {
                logger.error(e.getMessage(), e);
            }
        }
        gameInfo.getInboundMoves().clear();
        return ok;
    }

    public static class FicsSettings {
        private boolean loginAsGuest = true;
        private  String username;
        private  String password;

        public boolean isLoginAsGuest() {
            return loginAsGuest;
        }

        public void setLoginAsGuest(boolean loginAsGuest) {
            this.loginAsGuest = loginAsGuest;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public interface InboundMessageConsumer {
        void consume(InboundMessage.Info inboundMessage);
    }
}
