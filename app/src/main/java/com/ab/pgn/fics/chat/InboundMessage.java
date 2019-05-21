/**
 *
 * 04/22/2019 Alexander Bootman - simplified and optimized
 *
 */

package com.ab.pgn.fics.chat;

import com.ab.pgn.Board;
import com.ab.pgn.Config;

import java.util.LinkedList;
import java.util.List;

/**
 *
 * Created by Alexander Bootman on 4/30/19.
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
        private static GameType[] values = GameType.values();

        GameType(char abbr, int value) {
            this.abbr = abbr;
            this.value = value;
        }

        public int getValue() {
            return value;
        }
        public char getAbbreviation() {
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

        public static int total() {
            return values.length;
        }

//        @Override
//        public String toString() {
//            return super.toString().toLowerCase();
//        }
    }

    /*
    * my relation to this game:
            -3 isolated position, such as for "ref 3" or the "sposition" command
    -2 I am observing game being examined
     2 I am the examiner of this game
    -1 I am playing, it is my opponent's move
            1 I am playing and it is my move
     0 I am observing a game being played
*/
    public enum MyRelationToGame {
        Unknown("-999"),
        IsolatedPosition("-3"),
        MeObservingExamined("-2"),
        MePlayingHisMove("-1"),
        MeObserving("0"),
        MePlayingMyMove("1"),
        MeExamining("2"),
        ;

        private final int value;
        private static MyRelationToGame[] values = MyRelationToGame.values();

        MyRelationToGame(int value) {
            this.value = value;
        }

        MyRelationToGame(String value) {
            this.value = Integer.valueOf(value);
        }

        public int getValue() {
            return value;
        }

        public static MyRelationToGame myRelationToGame(int v)
        {
            for(MyRelationToGame value : values) {
                if(v == value.value) {
                    return value;
                }
            }
            return null;
        }
    }


    public final static String
        WHITE_ABBR = "w",
        BLACK_ABBR = "b",
        dummy_string = null;

    public final static int DEFAULT_INT = -1;

//    Message msg = bgMessageHandler.obtainMessage();
//    msg.arg1 = msgId & 0x0ff;
//                bgMessageHandler.sendMessage(msg);

    private static int j = Config.FICS_FIRST_MSG_TYPE;
    public enum MessageType {
        Info(++j),
        Challenge(++j),
        PlayedGame(++j),
        Ad(++j),
        G1Game(++j),
        InboundList(++j),
        ;

        private final int value;
        private static MessageType[] values = MessageType.values();

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
        private String message;

        public Info() {
            setMessageType(MessageType.Info);
        }

        public Info(String message) {
            setMessageType(MessageType.Info);
            this.message = message;
        }

        public void setMessageType(MessageType messageType) {
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
            return message;
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

    public static abstract class GameInfo extends Info {
        protected String id;
        protected boolean isRated;
        protected boolean isWhitesMove;
        protected boolean isPrivate;
        protected GameType gameType;
        protected int time;
        protected int inc;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public boolean isRated() {
            return isRated;
        }

        public void setRated(boolean rated) {
            isRated = rated;
        }

        public boolean isWhitesMove() {
            return isWhitesMove;
        }

        public void setWhitesMove(boolean whitesMove) {
            isWhitesMove = whitesMove;
        }

        public boolean isPrivate() {
            return isPrivate;
        }

        public void setPrivate(boolean aPrivate) {
            isPrivate = aPrivate;
        }

        public GameType getGameType() {
            return gameType;
        }

        public void setGameType(GameType gameType) {
            this.gameType = gameType;
        }

        public int getTime() {
            return time;
        }

        public int getTimeMin() {
            return time / 60;
        }

        public void setTime(int time) {
            this.time = time;
        }

        public void setTimeMin(int time) {
            this.time = time * 60;
        }

        public int getInc() {
            return inc;
        }

        public void setInc(int inc) {
            this.inc = inc;
        }

        public void clear() {
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
        private boolean isBeingExamined;
        private int moveNumber;
        private MyRelationToGame myRelationToGame;
        private int whiteRemainingTime;
        private int blackRemainingTime;

        public PlayedGame() {
            setMessageType(MessageType.PlayedGame);
        }

        public String getWhiteName() {
            return whiteName;
        }

        public void setWhiteName(String whiteName) {
            this.whiteName = whiteName;
        }

        public String getBlackName() {
            return blackName;
        }

        public void setBlackName(String blackName) {
            this.blackName = blackName;
        }

        public String getWhiteElo() {
            return whiteElo;
        }

        public void setWhiteElo(String whiteElo) {
            this.whiteElo = whiteElo;
        }

        public String getBlackElo() {
            return blackElo;
        }

        public void setBlackElo(String blackElo) {
            this.blackElo = blackElo;
        }

        public boolean isBeingExamined() {
            return isBeingExamined;
        }

        public void setBeingExamined(boolean beingExamined) {
            isBeingExamined = beingExamined;
        }

        public int getMoveNumber() {
            return moveNumber;
        }

        public void setMoveNumber(int moveNumber) {
            this.moveNumber = moveNumber;
        }

        public MyRelationToGame getMyRelationToGame() {
            return myRelationToGame;
        }

        public void setMyRelationToGame(MyRelationToGame myRelationToGame) {
            this.myRelationToGame = myRelationToGame;
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
            isBeingExamined = false;
            moveNumber = DEFAULT_INT;
            myRelationToGame = MyRelationToGame.Unknown;
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

    public static class Ad extends GameInfo {
        private String soughtColor;
        private String soughtName;
        private String soughtElo;
        private boolean manual;
        private boolean useFormula;
        private int minElo;
        private int maxElo;

        public Ad() {
            setMessageType(MessageType.Ad);
        }

        public String getSoughtColor() {
            return soughtColor;
        }

        public void setSoughtColor(String soughtColor) {
            this.soughtColor = soughtColor;
        }

        public String getSoughtName() {
            return soughtName;
        }

        public void setSoughtName(String soughtName) {
            this.soughtName = soughtName;
        }

        public String getSoughtElo() {
            return soughtElo;
        }

        public void setSoughtElo(String soughtElo) {
            this.soughtElo = soughtElo;
        }

        public boolean isManual() {
            return manual;
        }

        public void setManual(boolean manual) {
            this.manual = manual;
        }

        public boolean isUseFormula() {
            return useFormula;
        }

        public void setUseFormula(boolean useFormula) {
            this.useFormula = useFormula;
        }

        public int getMinElo() {
            return minElo;
        }

        public void setMinElo(int minElo) {
            this.minElo = minElo;
        }

        public int getMaxElo() {
            return maxElo;
        }

        public void setMaxElo(int maxElo) {
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
        private List<Info> objects = new LinkedList<>();

        public InboundList() {
            setMessageType(MessageType.InboundList);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("List of %d\n", objects.size()));
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
        public String moveText;
        public Board board;

        public InboundMove(String moveText, Board board) {
            this.moveText = moveText;
            this.board = board;
        }
    }

}
