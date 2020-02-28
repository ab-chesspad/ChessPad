package com.ab.pgn.fics.chat;

import com.ab.pgn.Board;
import com.ab.pgn.Config;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 *
 * Created by Alexander Bootman on 4/30/19.
 * simplified and optimized
 *
 */

public class InboundMessage {

    private static int i = -1;

    public enum GameType {
        unknown('~', ++i),
        atomic('a', ++i),
        blitz('b', ++i),
        bughouse('B', ++i),
        crazyhouse('z', ++i),
        examined('e', ++i),
        lightning('l', ++i),
        losers('L', ++i),
        standard('s', ++i),
        suicide('S', ++i),
        untimed('u', ++i),
        wild('w', ++i),
        nonstandard('n', ++i),  // must be the last one
        ;

        private final char abbr;
        private final int value;
        private static final GameType[] values = GameType.values();

        GameType(char abbr, int value) {
            this.abbr = abbr;
            this.value = value;
        }

        public int getValue() {
            return value;
        }
        char getAbbreviation() {
            return abbr;
        }

        public static GameType gameType(int v) {
            return values[v];
        }

        public static GameType byAbbreviation(char abbr) {
            for(GameType gameType : GameType.values())
                if(gameType.getAbbreviation() == abbr) {
                return gameType;
            }
            return values[total() - 1];
        }

        static int total() {
            return values.length;
        }

//        @Override
//        public String toString() {
//            return super.toString().toLowerCase();
//        }
    }

    /* https://www.freechess.org/Help/HelpFiles/style12.html
    * my relation to this game:
    -3 isolated position, such as for "ref 3" or the "sposition" command
    -2 I am observing game being examined
     2 I am the examiner of this game
    -1 I am playing, it is my opponent's move
     1 I am playing and it is my move
     0 I am observing a game being played
//*/

    public static final int NO_STATE = 0;   // to complete set of states

    // game being played
    public static final int PLAYED_STATE = 0x0001;

    // -3 isolated position, such as for "ref 3" or the "sposition" command
    public static final int ISOLATED_STATE = 0x0002;

    // I am observing a game
    public static final int OBSERVING_STATE = 0x0004;

    // game being examined
    public static final int EXAMINED_STATE = 0x0008;

    // 2 I am the examiner of this game
    public static final int EXAMINER_STATE = 0x0010;

    // my move
    public static final int MY_MOVE_STATE = 0x0020;


    public final static String
        WHITE_ABBR = "w",
        BLACK_ABBR = "b",
        dummy_string = null;

    private final static int DEFAULT_INT = -1;

//    Message msg = bgMessageHandler.obtainMessage();
//    msg.arg1 = msgId & 0x0ff;
//    bgMessageHandler.sendMessage(msg);

    private static int j = Config.MSG_FICS_FIRST - 1;
    public enum MessageType {
        Closed(++j),    // 0x14,
        Ready(++j),     // 0x15, sent after login and setup
        InboundList(++j), // 0x16
        Challenge(++j), // 0x17
        KibitzMsg(++j), // 0x18
        PlayedGame(++j), // 0x19
        Ad(++j),        // 0x1a
        G1Game(++j),    // 0x1b
        Timer(++j),     // 0x1c, timer updated locally
        Info(++j),      // 0x1d     // must be the last one
        ;

        private final int value;
        private static final MessageType[] values = MessageType.values();

        MessageType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static MessageType command(int v) {
            return values[v];
        }

        public static int total() {
            return values.length;
        }
    }

    // base minimal class
    public static class Info {
        private MessageType messageType;
        private String message = "";

        Info() {
            setMessageType(MessageType.Info);
        }

        Info(String message) {
            setMessageType(MessageType.Info);
            this.message = message;
        }

        public Info(MessageType messageType) {
            setMessageType(messageType);
        }

        void setMessageType(MessageType messageType) {
            this.messageType = messageType;
        }

        public MessageType getMessageType() {
            return messageType;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        @Override
        public String toString() {
            return String.format("%s: %s", messageType.name(), message);
        }
    }

    public static class Challenge extends Info {
        public Challenge(String message) {
            super(message);
            setMessageType(MessageType.Challenge);
        }

        @Override
        public String toString() {
            return "challenge: " + super.toString();
        }
    }

    public static class KibitzMsg extends Info {
        private final String kibitzer;
        public KibitzMsg(String kibitzer, String message) {
            super(message);
            setMessageType(MessageType.KibitzMsg);
            this.kibitzer = kibitzer;
        }

        public String getKibitzer() {
            return kibitzer;
        }

        @Override
        public String toString() {
            return String.format("%s kibitzes: %s", kibitzer, super.toString());
        }
    }

    public static abstract class GameInfo extends Info {
        String id;
        boolean isRated;
        boolean isWhitesMove;
        boolean isPrivate;
        GameType gameType;
        int time;
        int inc;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public boolean isRated() {
            return isRated;
        }

        void setRated(boolean rated) {
            isRated = rated;
        }

        boolean isWhitesMove() {
            return isWhitesMove;
        }

        void setWhitesMove(boolean whitesMove) {
            isWhitesMove = whitesMove;
        }

        public boolean isPrivate() {
            return isPrivate;
        }

        public void setPrivate(boolean aPrivate) {
            isPrivate = aPrivate;
        }

        GameType getGameType() {
            return gameType;
        }

        void setGameType(GameType gameType) {
            this.gameType = gameType;
        }

        public int getTime() {
            return time;
        }

        int getTimeMin() {
            return time / 60;
        }

        void setTime(int time) {
            this.time = time;
        }

        void setTimeMin(int time) {
            this.time = time * 60;
        }

        int getInc() {
            return inc;
        }

        void setInc(int inc) {
            this.inc = inc;
        }

        void clear() {
            id = null;
            isRated = false;
            isWhitesMove = true;
            isPrivate = true;
            gameType = GameType.unknown;
            time =
            inc = DEFAULT_INT;
        }
    }

    public static class PlayedGame extends GameInfo {
        private String whiteName;
        private String blackName;
        private String whiteElo;
        private String blackElo;
        private int moveNumber;
        private int state;
        private int whiteRemainingTime;
        private int blackRemainingTime;

        PlayedGame() {
            setMessageType(MessageType.PlayedGame);
        }

        public String getWhiteName() {
            return whiteName;
        }

        void setWhiteName(String whiteName) {
            this.whiteName = whiteName;
        }

        public String getBlackName() {
            return blackName;
        }

        void setBlackName(String blackName) {
            this.blackName = blackName;
        }

        String getWhiteElo() {
            return whiteElo;
        }

        void setWhiteElo(String whiteElo) {
            this.whiteElo = whiteElo;
        }

        String getBlackElo() {
            return blackElo;
        }

        void setBlackElo(String blackElo) {
            this.blackElo = blackElo;
        }

        int getMoveNumber() {
            return moveNumber;
        }

        void setMoveNumber(int moveNumber) {
            this.moveNumber = moveNumber;
        }

        void setState(int state) {
            this.state = state;
        }

        /**
         * Adds the state flag to the games state.
         */
        void addState(int state) {
            setState(this.state | state);
        }

        /**
         * Clears the specified state constant from the games state.
         */
        void clearState(int state) {
            setState(this.state & ~state);
        }

        /**
         * Returns true if one of the state flags is in the specified state.
         */
        public boolean isInState(int state) {
            return (this.state & state) != 0;
        }

        public int getState() {
            return this.state;
        }

        boolean isBeingExamined() {
            return isInState(EXAMINED_STATE);
        }

        void setBeingExamined(boolean beingExamined) {
            if(beingExamined) {
                addState(EXAMINED_STATE);
            } else {
                clearState(EXAMINED_STATE);
            }
        }

        public int getWhiteRemainingTime() {
            return whiteRemainingTime;
        }

        public void setWhiteRemainingTime(int whiteRemainingTime) {
            this.whiteRemainingTime = whiteRemainingTime;
        }

        public int getBlackRemainingTime() {
            return blackRemainingTime;
        }

        public void setBlackRemainingTime(int blackRemainingTime) {
            this.blackRemainingTime = blackRemainingTime;
        }

        @Override
        public void clear() {
            super.clear();
            whiteName = blackName = whiteElo = blackElo = null;
            moveNumber = DEFAULT_INT;
            state = NO_STATE;
            whiteRemainingTime = blackRemainingTime = -1;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getId()).append(" : ");
            if(isBeingExamined()) {
                sb.append("exam ");
            }
            if(isPrivate) {
                sb.append("private ");
            }
            sb.append(getGameType().toString()).append(" ");
            if(this.isRated) {
                sb.append("rated ");
            } else {
                sb.append("unrated ");
            }
            sb.append("(").append(getTimeMin()).append(",").append(getInc()).append(") ");
            sb.append(": ");
            sb.append(getWhiteElo());
            sb.append(' ');
            sb.append(getWhiteName());
            sb.append(" - ");
            sb.append(getBlackElo());
            sb.append(' ');
            sb.append(getBlackName());
            sb.append(' ');
            sb.append(isWhitesMove()? 'W':'B');
            sb.append(' ');
            sb.append(getMoveNumber());
            sb.append(' ');
            return sb.toString();
        }
    }

    // item on InboundList
    public static class Ad extends GameInfo {
        private String soughtColor;
        private String soughtName;
        private String soughtElo;
        private boolean manual;
        private boolean useFormula;
        private int minElo;
        private int maxElo;

        Ad() {
            setMessageType(MessageType.Ad);
        }

        public String getSoughtColor() {
            return soughtColor;
        }

        void setSoughtColor(String soughtColor) {
            this.soughtColor = soughtColor;
        }

        String getSoughtName() {
            return soughtName;
        }

        void setSoughtName(String soughtName) {
            this.soughtName = soughtName;
        }

        String getSoughtElo() {
            return soughtElo;
        }

        void setSoughtElo(String soughtElo) {
            this.soughtElo = soughtElo;
        }

        boolean isManual() {
            return manual;
        }

        void setManual(boolean manual) {
            this.manual = manual;
        }

        public boolean isUseFormula() {
            return useFormula;
        }

        void setUseFormula(boolean useFormula) {
            this.useFormula = useFormula;
        }

        int getMinElo() {
            return minElo;
        }

        void setMinElo(int minElo) {
            this.minElo = minElo;
        }

        int getMaxElo() {
            return maxElo;
        }

        void setMaxElo(int maxElo) {
            this.maxElo = maxElo;
        }

        @Override
        public void clear() {
            super.clear();
            soughtColor = soughtName = soughtElo = null;
            manual = true;
            useFormula = false;
            minElo = maxElo = DEFAULT_INT;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(getId()).append(" : ");
            sb.append(getSoughtName()).append(" - ").append(getSoughtElo());
            sb.append(' ');
            sb.append(getTimeMin());
            sb.append(' ');
            sb.append(getInc());
            if(this.isRated) {
                sb.append(" rated ");
            } else {
                sb.append(" unrated ");
            }
            sb.append(getGameType().toString()).append(" ");
            sb.append(this.getMinElo()).append("-").append(this.getMaxElo()).append(" ");
            if(this.isManual()) {
                sb.append("m");
            }
            if(this.useFormula) {
                sb.append("f");
            }
            return sb.toString();
        }
    }

    public static class G1Game extends PlayedGame {
        private int blackTime;
        private int blackInc;

        private boolean isBlackRegistered;
        private boolean isBlackUsingTimeseal;
        private boolean isWhiteUsingTimeseal;
        private boolean isWhtieRegistered;

        private String partnerGameId;
        private boolean isClockTicking;
        private int lagInMillis;

        private boolean updated;
        private final List<InboundMove> inboundMoves = new LinkedList<>();

        public G1Game() {
            setMessageType(MessageType.G1Game);
        }

        public G1Game(int gameId) {
            setMessageType(MessageType.G1Game);
            this.id = "" + gameId;
        }

        public int getBlackTime() {
            return blackTime;
        }

        public void setBlackTime(int blackTime) {
            this.blackTime = blackTime;
        }

        public int getBlackInc() {
            return blackInc;
        }

        public void setBlackInc(int blackInc) {
            this.blackInc = blackInc;
        }

        public boolean isBlackRegistered() {
            return isBlackRegistered;
        }

        public void setBlackRegistered(boolean blackRegistered) {
            isBlackRegistered = blackRegistered;
        }

        public boolean isBlackUsingTimeseal() {
            return isBlackUsingTimeseal;
        }

        public void setBlackUsingTimeseal(boolean blackUsingTimeseal) {
            isBlackUsingTimeseal = blackUsingTimeseal;
        }

        public boolean isWhiteUsingTimeseal() {
            return isWhiteUsingTimeseal;
        }

        public void setWhiteUsingTimeseal(boolean whiteUsingTimeseal) {
            isWhiteUsingTimeseal = whiteUsingTimeseal;
        }

        public boolean isWhtieRegistered() {
            return isWhtieRegistered;
        }

        public void setWhtieRegistered(boolean whtieRegistered) {
            isWhtieRegistered = whtieRegistered;
        }

        public String getPartnerGameId() {
            return partnerGameId;
        }

        public void setPartnerGameId(String partnerGameId) {
            this.partnerGameId = partnerGameId;
        }

        public boolean isUpdated() {
            return updated;
        }

        public void setUpdated(boolean updated) {
            this.updated = updated;
        }

        public boolean isClockTicking() {
            return isClockTicking;
        }

        public void setClockTicking(boolean clockTicking) {
            isClockTicking = clockTicking;
        }

        public int getLagInMillis() {
            return lagInMillis;
        }

        public void setLagInMillis(int lagInMillis) {
            this.lagInMillis = lagInMillis;
        }

        public List<InboundMove> getInboundMoves() {
            return inboundMoves;
        }

        @Override
        public void clear() {
            super.clear();
            blackTime = blackInc = DEFAULT_INT;
            isBlackRegistered = isBlackUsingTimeseal = isWhiteUsingTimeseal = isWhtieRegistered = false;
            partnerGameId = null;
            updated = true;
            isClockTicking = false;
            lagInMillis = 0;
            inboundMoves.clear();
        }

        @Override
        public String toString() {
            return "G1Message: gameId=" + id;
        }
    }

    public static class InboundList extends Info {
        private final List<Info> objects = new LinkedList<>();

        public InboundList() {
            setMessageType(MessageType.InboundList);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.getDefault(), "List of %d\n", objects.size()));
            for(Info info : objects) {
                sb.append(info.toString()).append("\n");
            }
            return sb.toString();
        }

        public void add(Info info) {
            objects.add(info);
        }
    }

    public static class InboundMove {
        public final String moveText;
        public final Board board;

        public InboundMove(String moveText, Board board) {
            this.moveText = moveText;
            this.board = board;
        }
    }

}
