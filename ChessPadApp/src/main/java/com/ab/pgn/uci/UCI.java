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

 * UCI implementation
 * implements http://wbec-ridderkerk.nl/html/UCIProtocol.html
 * Created by Alexander Bootman on 10/20/2021.
*/

package com.ab.pgn.uci;

import java.io.IOException;
import java.util.Locale;

public class UCI {
    private static final boolean DEBUG = true;

    public static final String
        // to UCI engine:
        COMMAND_INIT = "uci",
        COMMAND_QUIT = "quit",
        COMMAND_STOP = "stop",
        COMMAND_UCINEWGAME = "ucinewgame",
        COMMAND_ISREADY = "isready",
        COMMAND_POSITION = "position fen ",
        COMMAND_GO_INFINITE = "go infinite",

        // from UCI engine:
        MSG_UCIOK = "uciok",
        MSG_READYOK = "readyok",
        MSG_BESTMOVE = "bestmove ",
        MSG_INFO = "info",
        MSG_INFO_SCORE_DEPTH = "depth",
        MSG_INFO_SCORE = "score",
        MSG_INFO_SCORE_MATE = "mate",
        MSG_INFO_SCORE_LOWERBOUND = "lowerbound",
        MSG_INFO_SCORE_UPPERBOUND = "upperbound",
        MSG_INFO_MOVES = "pv",
        MSG_INFO_CHECKMATE = "info depth 0 score mate 0",

        OPTION_ANALYSIS = "UCI_AnalyseMode",
        OPTION_SKILL_LEVEL = "Skill Level",
        dummy_string = null;

    protected static final int ANALYSIS_SKILL_LEVEL = 20;

    private UCIImpl uciImpl;

    /**
     * Engine state.
     */
    protected enum State {
        INIT,           // initial state
        IDLE,          // engine not searching.
        ANALYZE,       // "go" sent, waiting for analysis
        DEAD,          // engine process has terminated or not initiated
    }

    protected State state = State.INIT;
    private EngineWatcher engineWatcher;
    private Thread stdInThread;
    private Thread stdErrThread;
    private boolean isBlackMove;

    public UCI(EngineWatcher engineWatcher, UCIImpl uciImpl) throws IOException {
        this.engineWatcher = engineWatcher;
        this.uciImpl = uciImpl;
        uciImpl.loadLibrary();
        uciImpl.launch();

        stdErrThread = bgCall(() -> {
            String fromSF;
            while ((fromSF = uciImpl.read_err()) != null) {
                if (fromSF.trim().isEmpty()) {
                    continue;
                }
                System.out.printf("Engine -> GUI: error %s\n", fromSF);
                engineWatcher.reportError(fromSF);
            }
            System.out.printf("stdErrThread %s loop ended\n", stdErrThread.toString());
        });

        stdInThread = bgCall(() -> {
            String fromSF;
            while ((fromSF = uciImpl.read()) != null) {
                if (fromSF.trim().isEmpty()) {
                    continue;
                }
                if (DEBUG) {
                    System.out.printf("Engine -> GUI, state=%s: %s\n", state.toString(), fromSF);
                }
                consume(fromSF);
            }
            System.out.printf("stdInThread %s loop ended\n", stdInThread.toString());
        });
        uciImpl.setOptions(this);
    }

    public void doAnalysis(boolean doAnalysis) {
        abortCurrentAnalisys();
        System.out.println(String.format("doAnalysis, state %s, new %b", state.toString(), doAnalysis));
        if (!doAnalysis) {
            return;
        }
        execute(UCI.COMMAND_UCINEWGAME);
        execute(COMMAND_ISREADY);
        sendPosition();
    }

    // stop current analysis, after UCIEndine.state == IDLE, it picks up the new position and resumes analysis
    public void abortCurrentAnalisys() {
        System.out.println(String.format("abortCurrentAnalisys, state %s", state.toString()));
        if (state == State.ANALYZE) {
            execute(UCI.COMMAND_STOP);
            setState(State.IDLE);
        }
    }

    private void setState(State state) {
        this.state = state;
        System.out.println(String.format("setState %s", state.toString()));
    }

    /**
     * Shut down engine.
     */
    public void shutDown() {
        execute(COMMAND_QUIT);
        if (stdInThread != null) {
            stdInThread.interrupt();
        }
        if (stdErrThread != null) {
            stdErrThread.interrupt();
        }
    }

    /**
     * Set an engine integer option.
     */
    public void setOption(String name, int value) {
        setOption(name, String.format(Locale.US, "%d", value));
    }

    /**
     * Set an engine boolean option.
     */
    public void setOption(String name, boolean value) {
        setOption(name, value ? "true" : "false");
    }

    /**
     * Set an engine String option.
     */
    public void setOption(String name, String value) {
        if (value.length() == 0) {
            value = "<empty>";
        }
        execute(String.format(Locale.US, "setoption name %s value %s", name, value));
    }

    public void execute(String command) {
        if (DEBUG) {
            System.out.println(String.format("UI -> Engine: %s, state=%s", command, state.toString()));
        }
        uciImpl.execute(command);
        if (DEBUG) {
            System.out.println(String.format("UI -> Engine: %s done, state=%s", command, state.toString()));
        }
    }

    public void sendPosition() {
        String fen = engineWatcher.getCurrentFen();
        if (fen == null) {
            return;
        }
        setOption(OPTION_SKILL_LEVEL, ANALYSIS_SKILL_LEVEL);
        isBlackMove = fen.contains(" b ");      // quick and dirty
        execute(UCI.COMMAND_POSITION + fen);  // todo: send moves?
        setOption(UCI.OPTION_ANALYSIS, true);
        execute(UCI.COMMAND_GO_INFINITE);
        setState(State.ANALYZE);
    }

    protected void consume(String s) {
        String[] lines = s.split("\n");
        for (String line : lines) {
            switch (state) {
                case INIT:
                    System.out.printf("Engine: %s\n", line);   // log engine options
                    if (MSG_UCIOK.equals(line)) {
                        setState(State.IDLE);
                        engineWatcher.engineOk();
                    }
                    break;

                case IDLE:
                    System.out.printf("Engine idle: %s\n", line);   // log engine options
                    break;

                case ANALYZE: {
                    if (line.startsWith(MSG_INFO)) {
                        if (MSG_INFO_CHECKMATE.equals(line)) {
                            engineWatcher.acceptAnalysis(new IncomingInfoMessage(true));
                        } else if (line.contains(" pv ")) {
                            parseInfoMsg(line);
                        }
                    }
                    break;
                }

                default:
            }
        }
    }

    private void parseInfoMsg(String s) {
        System.out.println(s);
        String[] tokens = s.split("\\s+");
        IncomingInfoMessage incomingInfoMessage = new IncomingInfoMessage();
        incomingInfoMessage.isBlackMove = isBlackMove;
        int i = 0;
        while (++i < tokens.length) {
            String token = tokens[i];
            switch (token) {
                case MSG_INFO_SCORE_DEPTH:
                    incomingInfoMessage.depth = Integer.valueOf(tokens[++i]);
                    break;

                case MSG_INFO_SCORE:
                    incomingInfoMessage.toMate = tokens[++i].equals(MSG_INFO_SCORE_MATE);
                    incomingInfoMessage.score = Integer.valueOf(tokens[++i]);
                    incomingInfoMessage.upperBound = tokens[++i].equals(MSG_INFO_SCORE_UPPERBOUND);
                    incomingInfoMessage.lowerBound = tokens[i].equals(MSG_INFO_SCORE_LOWERBOUND);
                    break;

                case MSG_INFO_MOVES:
                    StringBuilder sb = new StringBuilder();
                    String sep = "";
                    ++i;
                    do {
                        sb.append(sep).append(tokens[i++]);
                        sep = " ";
                    } while (i < tokens.length);
                    incomingInfoMessage.moves = new String(sb);
                    break;
            }
        }
        engineWatcher.acceptAnalysis(incomingInfoMessage);
    }

    public static class IncomingInfoMessage {
        int depth;
        boolean toMate;
        public int score;
        boolean isBlackMove, upperBound, lowerBound;
        public String info;     // for moves from book
        public String moves;

        public IncomingInfoMessage() {
        }

        IncomingInfoMessage(boolean isMate) {
            toMate = isMate;
        }

        public String getMoves() {
            return moves;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (info == null) {
                sb.append("[").append(depth).append("] ");
                if (this.upperBound || this.lowerBound) {
                    if (this.upperBound ^ isBlackMove) {
                        sb.append("<=");
                    } else {
                        sb.append(">=");
                    }
                }
                int score = this.score;
                if (isBlackMove) {
                    score = -score;
                }

                if (toMate) {
                    sb.append("#").append(score);
                } else {
                    sb.append(String.format(Locale.US, "%.2f", (float) score / 100));
                }
            } else {
                sb.append(info);
            }
            if (moves != null) {
                sb.append(" ").append(moves);
            }
            return new String(sb);
        }
    }

    public interface EngineWatcher {
        String getCurrentFen();     // return null not to trigger analysis
        void engineOk();            // engine ready to get initial options
        void acceptAnalysis(IncomingInfoMessage incomingInfoMessage);
        void reportError(String message);
    }

    public static Thread bgCall(BgCall caller) {
        Thread t = new Thread(caller::exec);
        t.start();
        return t;
    }

    public interface BgCall {
        void exec();
    }

    public interface UCIImpl {
        void loadLibrary();
        void launch() throws IOException;
        void setOptions(UCI uci);
        void execute(String command);
        String read();
        String read_err();
        void quit();
    }
}
