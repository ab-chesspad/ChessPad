/*
    DroidFish - An Android chess program.
    Copyright (C) 2011-2014  Peter Österlund, peterosterlund2@gmail.com

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

    Alexander Bootman: modified for ChessPad
    implements http://wbec-ridderkerk.nl/html/UCIProtocol.html
*/

package org.petero.droidfish.engine;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Locale;

public abstract class UCIEngine {
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
        ERROR_START_ENGINE = "Failed to start engine",
        dummy_string = null;

    private static final int ANALYSIS_SKILL_LEVEL = 20;

    /** Engine state. */
    private enum State {
        READ_OPTIONS,  // "uci" command sent, waiting for "option" and "uciok" response.
        WAIT_READY,    // "isready" sent, waiting for "readyok".
        IDLE,          // engine not searching.
        ANALYZE,       // "go" sent, waiting for "bestmove" (which will be ignored)
        STOP_SEARCH,   // "stop" sent, waiting for "bestmove"
        DEAD,          // engine process has terminated or not initiated
    }

    private final Object lock = new Object();

    private EngineWatcher engineWatcher;
    private Process engineProc;
    private Thread stdInThread;
    private Thread stdErrThread;
    private boolean processAlive;
    private boolean startedOk;
    private State state = State.DEAD;
    private boolean isBlackMove;
    private boolean doAnalysis;

    static {
        System.loadLibrary("nativeutil");
    }

    /** Change the priority of a process. */
    public static native void reNice(int pid, int prio);

    /** Executes chmod on exePath. */
    public static native boolean chmod(String exePath, int mod);

    public UCIEngine(EngineWatcher engineWatcher) {
        this.engineWatcher = engineWatcher;
        processAlive = false;
    }

    public void launch() throws IOException {
        if (!processAlive) {
            String exePath = getExecutablePath();
            String workDir = engineWatcher.getEngineWorkDirectory();
            startProcess(exePath, workDir);
            processAlive = true;
            writeCommand(COMMAND_INIT);
            setState(State.READ_OPTIONS);
        }
    }

    protected abstract String getExecutablePath() throws IOException;

    /** Start engine. */
    private void startProcess(String exePath, String engineWorkDir) throws IOException {
        File workDir = new File(engineWorkDir);
        ProcessBuilder pb = new ProcessBuilder(exePath);
        if (workDir.canRead() && workDir.isDirectory()) {
            pb.directory(workDir);
        }
        synchronized (lock) {
            engineProc = pb.start();
        }
        _reNice();

        // Start a thread to read stdin
        stdInThread = new Thread(() -> {
            Process ep = engineProc;
            if (ep == null)
                return;
            InputStream is = ep.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr, 8192);
            String line;
            try {
                boolean first = true;
                while ((line = br.readLine()) != null) {
                    if (first) {
                        startedOk = true;
                        first = false;
                        synchronized (lock) {
                            lock.notify();
                        }
                    }
                    if(line.isEmpty()) {
                        continue;
                    }
                    if(DEBUG) {
                        System.out.printf("Engine -> GUI, state=%s: %s\n", state.toString(), line);
                    }
                    consume(line);
                }
                System.out.printf("stdInThread %s loop ended\n", stdInThread.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        stdInThread.start();

        // Start a thread to read stderr
        stdErrThread = new Thread(() -> {
            byte[] buffer = new byte[128];
            while (true) {
                Process ep = engineProc;
                if ((ep == null) || Thread.currentThread().isInterrupted())
                    return;
                try {
                    int len = ep.getErrorStream().read(buffer, 0, buffer.length);
                    if (len < 0) {
                        break;
                    }
                    String errMsg = new String(buffer);
                    System.out.printf("Engine -> GUI: error %s\n", errMsg);
                    engineWatcher.reportError(errMsg);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            System.out.printf("stdErrThread %s loop ended\n", stdErrThread.toString());
        });
        stdErrThread.start();

        synchronized (lock) {
            try {
                lock.wait(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (!startedOk) {
            throw  new IOException(ERROR_START_ENGINE);
        }
    }

    public void doAnalysis(boolean doAnalysis) {
        if (this.doAnalysis) {
            abortCurrentAnalisys();
        }
        System.out.println(String.format("doAnalysis, state %s, old %b, new %b", state.toString(), this.doAnalysis, doAnalysis));
        this.doAnalysis = doAnalysis;
        if (doAnalysis) {
            startNewGame();
            sendPosition();
        }
    }

    // stop current analysis, after UCIEndine.state == IDLE, it picks up the new position and resumes analysis
    public void abortCurrentAnalisys() {
        System.out.println(String.format("abortCurrentAnalisys, state %s, %b", state.toString(), this.doAnalysis));
        if (state == State.ANALYZE) {
            writeCommand(UCIEngine.COMMAND_STOP);
            setState(State.STOP_SEARCH);
        }
    }

    private void setState(State state) {
        this.state = state;
        System.out.println(String.format("setState %s", state.toString()));
    }

    /** Shut down engine. */
    public void shutDown() {
        if (processAlive) {
            writeCommand(COMMAND_QUIT);
            processAlive = false;
        }
        if (engineProc != null) {
            for (int i = 0; i < 25; i++) {
                try {
                    engineProc.exitValue();
                    break;
                } catch (IllegalThreadStateException e) {
                    try { Thread.sleep(10); } catch (InterruptedException ignore) { }
                }
            }
            engineProc.destroy();
        }
        engineProc = null;
        if (stdInThread != null) {
            stdInThread.interrupt();
        }
        if (stdErrThread != null) {
            stdErrThread.interrupt();
        }
    }

    /** Set an engine integer option. */
    public void setOption(String name, int value) {
        setOption(name, String.format(Locale.US, "%d", value));
    }

    /** Set an engine boolean option. */
    public void setOption(String name, boolean value) {
        setOption(name, value ? "true" : "false");
    }

    /** Set an engine String option. */
    public void setOption(String name, String value) {
        if (value.length() == 0) {
            value = "<empty>";
        }
        writeCommand(String.format(Locale.US, "setoption name %s value %s", name, value));
    }

    /** Try to lower the engine process priority. */
    private void _reNice() {
        try {
            java.lang.reflect.Field f = engineProc.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            int pid = f.getInt(engineProc);
            reNice(pid, 10);
        } catch (Throwable ignore) {
        }
    }

    /** Write a command to the engine. \n is added automatically. */
    private void writeCommand(String command) {
        if(DEBUG) {
            System.out.println(String.format("UI -> Engine: %s, state=%s", command, state.toString()));
        }

        if (!command.endsWith("\n")) {
            command += "\n";
        }
        OutputStream engineOutputStream = engineProc.getOutputStream();
        try {
            engineOutputStream.write(command.getBytes());
            engineOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startNewGame() {
        writeCommand(UCIEngine.COMMAND_UCINEWGAME);
        writeCommand(COMMAND_ISREADY);
    }

    public void resumeAnalisys() {
        System.out.printf("resumeAnalisys(), state=%s\n", state.toString());
        if(state == State.IDLE) {
            handleIdleState();
        }
    }

    private void sendPosition() {
        String fen = engineWatcher.getCurrentFen();
        if (fen == null) {
            return;
        }
        setOption(OPTION_SKILL_LEVEL, ANALYSIS_SKILL_LEVEL);
        isBlackMove = fen.contains(" b ");      // quick and dirty
        writeCommand(UCIEngine.COMMAND_POSITION + fen);  // todo: send moves?
        setOption(UCIEngine.OPTION_ANALYSIS, true);
        writeCommand(UCIEngine.COMMAND_GO_INFINITE);
        setState(State.ANALYZE);
    }

    private void handleIdleState() {
        if(doAnalysis) {
            sendPosition();
        }
    }

    private void consume(String s) {
        switch (state) {
            case READ_OPTIONS: {
                System.out.printf("Engine: %s\n", s);   // log engine options
                if (MSG_UCIOK.equals(s)) {
                    engineWatcher.engineOk();
                    writeCommand(COMMAND_UCINEWGAME);
                    writeCommand(COMMAND_ISREADY);
                    setState(State.WAIT_READY);
                }
            }
            break;

            case WAIT_READY: {
                if (MSG_READYOK.equals(s)) {
                    setState(State.IDLE);
                    handleIdleState();
                }
                break;
            }

            case ANALYZE: {
                if(s.startsWith(MSG_INFO)) {
                    if(MSG_INFO_CHECKMATE.equals(s)) {
                        engineWatcher.acceptAnalysis(new IncomingInfoMessage(true));
                    } else if(s.contains(" pv ")) {
                        parseInfoMsg(s);
                    }
                } else if(s.startsWith(MSG_BESTMOVE)) {
                    setState(State.IDLE);
                    handleIdleState();
                }
                break;
            }
            case STOP_SEARCH: {
                if(s.startsWith(MSG_BESTMOVE)) {
                    writeCommand(COMMAND_ISREADY);
                    setState(State.WAIT_READY);
                }
                break;
            }
            default:
        }
    }

    private void parseInfoMsg(String s) {
        System.out.println(s);
        String[] tokens = s.split("\\s+");
        IncomingInfoMessage incomingInfoMessage = new IncomingInfoMessage();
        incomingInfoMessage.isBlackMove = isBlackMove;
        int i = 0;
        while(++i < tokens.length) {
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
                    } while(i < tokens.length);
                    incomingInfoMessage.moves = new String(sb);
                    break;
            }
        }
        engineWatcher.acceptAnalysis(incomingInfoMessage);
    }

    public static class IncomingInfoMessage {
        int depth;
        boolean toMate;
        int score;
        boolean isBlackMove, upperBound, lowerBound;
        public String info;     // for moves from book
        public String moves;

        public IncomingInfoMessage() {}

        public IncomingInfoMessage(boolean isMate) {
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
            if(moves != null) {
                sb.append(" ").append(moves);
            }
            return new String(sb);
        }
    }

    public interface EngineWatcher {
        String getEngineWorkDirectory();
        String getCurrentFen();     // return null not to trigger analysis
        void engineOk();            // engine ready to get initial options
        void acceptAnalysis(IncomingInfoMessage incomingInfoMessage);
        void reportError(String message);
    }

}
