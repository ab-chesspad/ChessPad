package com.ab.pgn.fics;

import com.ab.pgn.BitStream;
import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Move;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.PgnLogger;
import com.ab.pgn.Util;
import com.ab.pgn.fics.chat.InboundMessage;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 *
 * Created by Alexander Bootman on 5/6/19.
 */
public class FicsPad {
/*
    private static String DEBUG_MOVE = "Kxh8";
/*/
    private static final String DEBUG_MOVE = null;
//*/

    private static final String[] DEFAULT_KIBITTZ_MSGS = {
            "tell endgamebot back 2",
            "tell endgamebot move",
            "tell puzzlebot gm 1968",
            "back 2",
            "tell puzzlebot move",
            "unexamine", ""};

    public static final String
        COMMAND_GAMES = "games",
        COMMAND_GET_MOVES = "moves",
        COMMAND_GET_MATE = "tell puzzlebot gm",
        COMMAND_TACTICS = "tell puzzlebot gt",
        COMMAND_STUDY = "tell puzzlebot gs",
        COMMAND_ENDGAME = "tell endgamebot play -f ",
        COMMAND_UNEXAMINE = "unexamine",
        KIBITZ_IGNORE = "type ",

        PUZZLEBOT_RESPONSE_SUCCESS = "You solved problem number ",
        ENDGAMEBOT_RESPONSE_SUCCESS = "To play the same endgame again type ",
        dummy_string = null;

    private final String[] mainCommands = {COMMAND_GET_MATE, COMMAND_TACTICS, COMMAND_STUDY, COMMAND_ENDGAME};

    enum FicsStatus {
        None,
        Play,
        Observe,
        Exam,
    }

    private final PgnLogger logger = PgnLogger.getLogger(this.getClass(), true);

    transient private FicsClient ficsClient;
    transient private InboundMessage.G1Game currentGame;
    transient private InboundMessageConsumer inboundMessageConsumer;

    transient private volatile boolean runTimer;
    transient private long lastDate;

    private PgnGraph pgnGraph;
    private List<String> kibitzHistory = new ArrayList<>();
    private volatile int oldKibitzMsgNumber;

    private ArrayList<String> userCommandHistory = new ArrayList<>();
    private int userCommandHistoryIndex = 0;
    private String mainCommand2Fics = "";
    private StringBuilder sessionLog = new StringBuilder();
    private int round = 0;

    public FicsPad(FicsSettings ficsSettings, InboundMessageConsumer inboundMessageConsumer) {
        String user = "guest";
        String password = "";
        if(!ficsSettings.isLoginAsGuest()) {
            user = ficsSettings.getUsername();
            password = ficsSettings.getPassword();
        }
        connect(user, password, inboundMessageConsumer);
    }

    public FicsPad(String user, String password, InboundMessageConsumer inboundMessageConsumer) {
        connect(user, password, inboundMessageConsumer);
    }

    private void connect(String user, String password, final InboundMessageConsumer inboundMessageConsumer) {
        if(DEFAULT_KIBITTZ_MSGS != null) {
            for(String msg : DEFAULT_KIBITTZ_MSGS) {
                userCommandHistory.add(msg);
            }
            userCommandHistoryIndex = userCommandHistory.size() - 1;
        }
        this.inboundMessageConsumer = inboundMessageConsumer;
        init(new InboundMessage.G1Game());

        ficsClient = new FicsClient(user, password, () -> currentGame, (inboundMessage) -> {
            if(inboundMessage == null) {
                logger.error("inboundMessage == null");
                return;
            }
            if(inboundMessage.getClass().equals(InboundMessage.G1Game.class)) {
                if (handleG1((InboundMessage.G1Game) inboundMessage)) {
                    inboundMessage = currentGame;
                }
            } else if(inboundMessage.getClass().equals(InboundMessage.KibitzMsg.class)) {
                String kibitzer = ((InboundMessage.KibitzMsg)inboundMessage).getKibitzer();
                if(!hePlaying(kibitzer)) {
                    return;         // ignore other kibitzers
                }
                String msg = inboundMessage.getMessage();
                if(msg.startsWith(KIBITZ_IGNORE)) {
                    return;         // ignore
                }
                kibitzHistory.add(msg);
//                    if(pgnGraph.moveLine.size() <= 1)
                {
                    Move lastMove = pgnGraph.getCurrentMove();
                    Move backedMove = pgnGraph.getNextMove(lastMove);
                    while(backedMove != null) {
                        lastMove = backedMove;
                        backedMove = backedMove.variation;
                    }
                    String comment = lastMove.comment;
                    String sep = "; ";
                    if (comment == null) {
                        comment = "";
                        sep = "";
                    }
                    comment += sep + msg;
                    lastMove.comment = comment;
                }
                if(msg.startsWith(PUZZLEBOT_RESPONSE_SUCCESS) || msg.contains(ENDGAMEBOT_RESPONSE_SUCCESS)) {
                    appendGame();
                    pgnGraph = new PgnGraph();
                    sendNext();
                }
            } else {
                if(inboundMessage.getMessageType() == InboundMessage.MessageType.Closed) {
                    close();
                }
            }

            if (inboundMessageConsumer != null) {
                inboundMessageConsumer.consume(inboundMessage);
            }
        });
        ficsClient.connect();

        new Thread(() -> {
                runTimer = true;
                lastDate = new Date().getTime();
                while(runTimer) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // ignore
                    }
                    updateTimer();
                }
        }).start();
    }

    public void close() {
        runTimer = false;
        ficsClient.close();
        // todo: save session
    }

    public FicsPad(BitStream.Reader reader, int versionCode) throws Config.PGNException {
        try {
            pgnGraph = new PgnGraph(reader, versionCode, null);
            kibitzHistory = new ArrayList<>();
            reader.readList(kibitzHistory);
            oldKibitzMsgNumber = reader.read(8);

            userCommandHistory = new ArrayList<>();
            reader.readList(userCommandHistory);
            userCommandHistoryIndex = reader.read(8);

            mainCommand2Fics = reader.readString();
            if(mainCommand2Fics == null) {
                mainCommand2Fics = "";
            }
            String str = reader.readString();
            if(str == null) {
                str = "";
            }
            sessionLog = new StringBuilder(str);
        } catch (Exception e) {
            throw new Config.PGNException(e);
        }
    }

    public void serialize(BitStream.Writer writer, int versionCode) throws Config.PGNException {
        try {
            pgnGraph.serializeGraph(writer, versionCode);
            writer.writeList(kibitzHistory);
            writer.write(oldKibitzMsgNumber, 8);        // last 255
            writer.writeList(userCommandHistory);
            writer.write(userCommandHistoryIndex, 8);   // < 256
            writer.writeString(mainCommand2Fics);
            writer.writeString(sessionLog.toString());
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public boolean isConnected() {
        return ficsClient.isReady();
    }

    public void toNextCommand() {
        if(userCommandHistoryIndex < userCommandHistory.size()) {
            ++userCommandHistoryIndex;
        }
    }

    public void toPrevCommand() {
        if(userCommandHistoryIndex > 0) {
            --userCommandHistoryIndex;
        }
    }

    public void setUserCommand2Fics(String userCommand2Fics) {
        userCommandHistory.set(userCommandHistoryIndex,userCommand2Fics);
    }

    public void send() {
        String userCommand2Fics = userCommandHistory.get(userCommandHistoryIndex);
        if(userCommand2Fics.isEmpty()) {
            return;
        }
        send(userCommand2Fics);

        for(int i = userCommandHistory.size() - 1; i >= 0; --i) {
            String command = userCommandHistory.get(i);
            if(command.isEmpty() || command.equals(userCommand2Fics)) {
                userCommandHistory.remove(i);
            }
        }
        userCommandHistory.add(userCommand2Fics);
        userCommandHistoryIndex = userCommandHistory.size();
        userCommandHistory.add("");
    }

    public String getUserCommand2Fics() {
        try {
            return userCommandHistory.get(userCommandHistoryIndex);
        } catch (IndexOutOfBoundsException e) {
            System.out.println(e.getLocalizedMessage());
        }
        return userCommandHistory.get(userCommandHistory.size() - 1);
    }

    private void sendNext() {
        send(FicsPad.COMMAND_UNEXAMINE);    // in case we are examining a game
        send(mainCommand2Fics);
    }

    public void send(String command, String ... params) {
        switch (command) {
            case COMMAND_GET_MATE:
            case COMMAND_TACTICS:
            case COMMAND_STUDY:
                command += params[0];
                mainCommand2Fics = command;
                break;

            case COMMAND_ENDGAME:
                command += params[0];
                mainCommand2Fics = command;
                logger.debug("end of game");
                break;

            default:
                // pass through
                break;
        }

        ficsClient.write(command);
        // todo: guarantee that commands are executed in the order they are sent
        // quick and dirty:
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    private void init(InboundMessage.G1Game gameInfo) {
        currentGame = gameInfo;
        pgnGraph = new PgnGraph();
        if(isNewOpponent(gameInfo)) {
            clearKibitzHistory();
        }
    }

    private boolean isNewOpponent(InboundMessage.G1Game gameInfo) {
        String me = getLoggedIn();
        if(me == null) {
            return true;
        }
        String he = gameInfo.getBlackName();
        if(me.equals(he)) {
            he = gameInfo.getWhiteName();
        }
        return !hePlaying(he);
    }

    private void clearKibitzHistory() {
        oldKibitzMsgNumber = 0;
        kibitzHistory.clear();
    }

    public void resetKibitzHistory() {
        oldKibitzMsgNumber = 0;
    }

    public List<String> getNewKibitzMsgs() {
        if(kibitzHistory.isEmpty()) {
            return null;
        }
        List<String> sublist = new LinkedList<>();
        if(oldKibitzMsgNumber >= kibitzHistory.size()) {
            return sublist;     // empty
        }

        for (Iterator<String> it = kibitzHistory.listIterator(oldKibitzMsgNumber); it.hasNext();) {
            String msg = it.next().trim();
            if(!msg.isEmpty()) {
                sublist.add(msg);
            }
        }
        oldKibitzMsgNumber = kibitzHistory.size();
        return sublist;
    }

    private String getLoggedIn() {
        if(ficsClient == null) {
            return null;
        }
        return ficsClient.getLoggedIn();
    }

    private boolean mePlaying() {
        return hePlaying(getLoggedIn());
    }

    boolean hePlaying(String he) {
        if(he == null) {
            return false;
        }
        if(he.equals(currentGame.getWhiteName())) {
            return true;
        }
        return he.equals(currentGame.getBlackName());
    }

    public InboundMessage.G1Game getCurrentGame() {
        return currentGame;
    }

    public PgnGraph getPgnGraph() {
        return pgnGraph;
    }

    public Board getBoard() {
        // setup?
        return pgnGraph.getBoard();
    }

    private void appendGame() {
        if(pgnGraph.moveLine.size() == 1) {
            return;
        }
        List<Pair<String, String>> newTags = new LinkedList<>();
        for(Pair<String, String> tag : pgnGraph.getPgn().getTags()) {
            Pair<String, String> newTag;
            String value = tag.second;
            switch (tag.first) {
                case Config.TAG_Black:
                    value = currentGame.getBlackName();
                    break;

                case Config.TAG_White:
                    value = currentGame.getWhiteName();
                    break;

                case Config.TAG_Round:
                    value = "" + ++round;
                    break;

                case Config.TAG_Date:
                    value = new SimpleDateFormat(Config.CP_DATE_FORMAT, Locale.getDefault()).format(new Date());
                    break;

            }
            newTag = new Pair<>(tag.first, value);
            newTags.add(newTag);
        }
        pgnGraph.getPgn().setTags(newTags);
        String fullPgn = pgnGraph.toString(true);
        sessionLog.append(fullPgn);
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
                Board board;
                boolean moveTakenBack = false;
                if(gameInfo.getInboundMoves().size() == 1) {
                    Board incomingBoard = gameInfo.getInboundMoves().get(0).board;
                    int incomingPlyNum = incomingBoard.getPlyNum();
                    while(pgnGraph.moveLine.size() > 1 && getBoard().getPlyNum() > incomingPlyNum) {
                        pgnGraph.toPrev();
                        moveTakenBack = true;
                        logger.debug(String.format("took back to:\n%s\n%s", getBoard().toString(), pgnGraph.toPgn()));
                    }
                    if(moveTakenBack) {
                        return false;
                    }
                }

                if(inboundMove.moveText.equals(Config.FICS_NO_MOVE)) {
                    appendGame();
                    pgnGraph = new PgnGraph(inboundMove.board);
                    logger.debug(String.format("New game state=0x%04x\n%s", gameInfo.getState(), inboundMove.board.toString()));
                    if(isNewOpponent(gameInfo)) {
                        clearKibitzHistory();
                    }
                    currentGame = gameInfo;
                    continue;
                }
                board = getBoard();
                Move move = new Move(board.getFlags() & Config.FLAGS_BLACK_MOVE);
                Util.parseMove(move, inboundMove.moveText);

                ok = pgnGraph.validatePgnMove(move);
                if(ok) {
                    pgnGraph.addUserMove(move);
                    if(DEBUG_MOVE != null && DEBUG_MOVE.equals(move.toCommentedString())) {
                        logger.debug(String.format("DEBUG after move %s: %s", move.toString(), pgnGraph.toPgn()));
                    }
                    logger.debug(String.format("after move %s: %s", move.toString(), pgnGraph.toPgn()));
                    if(inboundMove.board != null) {
                        // on 'moves' we get them without board
                        if (getBoard().equals(inboundMove.board)) {
                            logger.debug(String.format("Move %s\n%s", inboundMove.moveText, inboundMove.board.toString()));
                        } else {
                            if(gameInfo.getWhiteRemainingTime() < 0 || gameInfo.getBlackRemainingTime() < 0) {
                                gameInfo.getInboundMoves().clear();
                                return ok;
                            }
                            logger.debug(String.format("New position after %s \n%s\n different from old\n%s",
                                    inboundMove.moveText, inboundMove.board.toString(), getBoard().toString()));
                            ok = false;
                        }
                    }
                }
                if(!ok){
                    // Game 104: puzzlebot backs up 1 move.
                    //<12> b---r--- --rN-ppk --n-p--- p------- P----P-- -R------ q-P--QPP ---R--K- W -1 0 0 0 0 0 104 abfics puzzlebot 2 0 0 27 29 0 0 2 K/g8-h7 (0:00.000) Kxh7 0 0 0
                    // for illegal move fics ignores it, sends the same position
                    // validate?
                    if(!getBoard().equals(inboundMove.board)) {
                        while (pgnGraph.moveLine.size() > 1) {
                            pgnGraph.toPrev();
                            if (getBoard().equals(inboundMove.board)) {
                                break;
                            }
                        }
                        logger.debug(String.format("after taking back: %s\n%s", pgnGraph.toPgn(), inboundMove.moveText));
                    }
                    logger.debug(String.format("backed up to %s\n%s", inboundMove.moveText, inboundMove.board.toString()));
                    if(pgnGraph.moveLine.size() == 1) {
                        // This happens when observing game. fics sends only the latest move without move line
                        logger.error(String.format("cannot find position\n%s for move %s", inboundMove.board.toString(), inboundMove.moveText));
                        // should we check that we are observing?
                        send(COMMAND_GET_MOVES);
                    }
                }
            } catch (Config.PGNException e) {
                logger.error(e.getMessage(), e);
            }
        }
        gameInfo.getInboundMoves().clear();
        return ok;
    }

    public boolean heMoved() {
        if(pgnGraph.moveLine.size() <= 1) {
            return false;
        }
        // puzzlebot and endgamebot
        if(mePlaying()) {
            int flags = (pgnGraph.getCurrentMove().moveFlags ^ pgnGraph.getInitBoard().getFlags()) & Config.FLAGS_BLACK_MOVE;
            return flags == 0;
        }
        return false;
    }

    private void updateTimer() {
        if(currentGame.isClockTicking()) {
            long currentDate = new Date().getTime();
            int diff = (int) (currentDate - lastDate);
            if ((pgnGraph.getFlags() & Config.FLAGS_BLACK_MOVE) == 0) {
                int remainingTime = currentGame.getWhiteRemainingTime();
                currentGame.setWhiteRemainingTime(remainingTime - diff);
            } else {
                int remainingTime = currentGame.getBlackRemainingTime();
                currentGame.setBlackRemainingTime(remainingTime - diff);
            }
            lastDate = currentDate;
            if (inboundMessageConsumer != null) {
                inboundMessageConsumer.consume(new InboundMessage.Info(InboundMessage.MessageType.Timer));
            }
        }
    }

    public static class FicsSettings {
        private boolean loginAsGuest = true;
        private  String username;
        private  String password;

        public void unserialize(BitStream.Reader reader) throws Config.PGNException {
            try {
                username = reader.readString();
                password = reader.readString();
                loginAsGuest = reader.read(1) == 1;
            } catch (Exception e) {
                throw new Config.PGNException(e);
            }
        }

        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            try {
                writer.writeString(username);
                writer.writeString(password);
                if(loginAsGuest) {
                    writer.write(1, 1);
                } else {
                    writer.write(0, 1);
                }
            } catch (Exception e) {
                throw new Config.PGNException(e);
            }
        }

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

        String getPassword() {
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
