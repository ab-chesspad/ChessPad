package com.ab.pgn.lichess;

import com.ab.pgn.BitStream;
import com.ab.pgn.Config;
import com.ab.pgn.CpFile;
import com.ab.pgn.PgnLogger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Created by Alexander Bootman on 4/4/2020.
 */
public class LichessPad {

    private static int i = -1;

    enum Message {
        ButYouCanKeepTrying(++i),
        PlayedXTimes(++i),
        ThankYou(++i),
        PlayWithAFriend(++i),
        UsingServerAnalysis(++i),
        YourTurn(++i),
        YourPuzzleRatingX(++i),
        ToTrackYourProgress(++i),
        LoadingEngine(++i),
        BoardEditor(++i),
        SignUp(++i),
        ContinueFromHere(++i),
        ShowThreat(++i),
        GoodMove(++i),
        ThisPuzzleIsWrong(++i),
        GoDeeper(++i),
        ButYouCanDoBetter(++i),
        Casual(++i),
        BestMove(++i),
        PlayedXTimes_few(++i),
        DepthX(++i),
        PlayedXTimes_many(++i),
        RetryThisPuzzle(++i),
        ToggleLocalEvaluation(++i),
        PleaseVotePuzzle(++i),
        FindTheBestMoveForBlack(++i),
        FromGameLink(++i),
        PlayedXTimes_one(++i),
        FindTheBestMoveForWhite(++i),
        PuzzleId(++i),
        Analysis(++i),
        CloudAnalysis(++i),
        GameOver(++i),
        ViewTheSolution(++i),
        Rated(++i),
        PuzzleFailed(++i),
        PlayWithTheMachine(++i),
        KeepGoing(++i),
        InLocalBrowser(++i),
        ContinueTraining(++i),
        Success(++i),
        RatingX(++i),
        ThisPuzzleIsCorrect(++i),
        WasThisPuzzleAnyGood(++i),

        LoginFailed(++i);

        private final int value;
        private static final Message[] values = Message.values();

        Message(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Message command(int v) {
            return values[v];
        }

//        public static int total() {
//            return values.length;
//        }
    }

    private static final PgnLogger logger = PgnLogger.getLogger(LichessPad.class);

//    private  String username;
//    private  String password;
    private LichessMessageConsumer lichessMessageConsumer;
    private LichessClient lichessClient;
    private Puzzle puzzle;
    private Map<Message, String> dictionary = new HashMap<>();
    private User user;

    public LichessPad(LichessMessageConsumer lichessMessageConsumer) throws Config.PGNException {
        this.lichessMessageConsumer = lichessMessageConsumer;
        lichessClient = new LichessClient();
    }

//    public LichessPad(String user, String password) throws Config.PGNException {
//        lichessClient = new LichessClient(this, user, password);
//    }

    public void serialize(BitStream.Writer writer) {
        // todo
    }

    public void unserialize(BitStream.Reader reader) {
        // todo
    }

    public void login(LichessSettings lichessSettings) throws Config.PGNException {
        bgCall(() -> {
            try {
                int res = lichessClient.login(lichessSettings.username, lichessSettings.password);
                if (lichessMessageConsumer != null) {
                    if (res == LichessClient.LICHESS_RESULT_OK) {
                        lichessMessageConsumer.consume(new LichessMessageLoginOk());
                    } else {
                        lichessMessageConsumer.error(new Config.PGNException(getLocalizedMessage(Message.LoginFailed)));
                    }
                }
            } catch (Config.PGNException e) {
                logger.error(e);
            }
        });
    }

//    public void login(String user, String password) throws Config.PGNException {
//        lichessClient.login(user, password);
//    }

    public void logout() {
        bgCall(() -> lichessClient.logout());
    }

    public boolean isUserLoggedIn() {
        return lichessClient != null && lichessClient.isUserLoggedIn();
    }

    public void loadPuzzle() throws Config.PGNException {
        bgCall(() -> {
            try {
                String json = lichessClient.getPuzzle();
                logger.debug(String.format(Locale.US, "json: %s", json));
                puzzle = new Puzzle(this, json);
                lichessMessageConsumer.consume(new LichessMessagePuzzle());
            } catch (IOException | Config.PGNException e) {
                e.printStackTrace();
                lichessMessageConsumer.error(e);
            }
        });
    }

    public void loadPuzzle(int puzzleId) throws Config.PGNException {
//        String url = "training/" + puzzleId;
//        String puzzleHtml = lichessClient.doGet(url);
//        puzzle = new Puzzle(this, puzzleHtml);
    }

    public CpFile.Item getPuzzle() {
        return puzzle.getPgn();
    }

    void putLocalizedMessage(String key, String msg) {
        key = Character.toUpperCase(key.charAt(0)) + key.substring(1);
        try {
            Message m = Message.valueOf(key);
            dictionary.put(m, msg);
        } catch (java.lang.IllegalArgumentException e) {
            logger.error(String.format("Unknown I18N message %s", key));
        }
    }

    public String getLocalizedMessage(Message msg) {
        String res = dictionary.get(msg);
        if (res == null) {
            // cannot guarantee that the current dictionary is full
            res = splitCamelCase(msg.toString());
        }
        return res;
    }

//    @Override
//    public void consume(String message) {
//        logger.debug(message);
//        // todo
//    }
//
//    @Override
//    public void error(Exception e) {
//        logger.error(e);
//        // todo
//    }

    private String splitCamelCase(String s) {
        return s.replaceAll(
                String.format("%s|%s|%s",
                        "(?<=[A-Z])(?=[A-Z][a-z])",
                        "(?<=[^A-Z])(?=[A-Z])",
                        "(?<=[A-Za-z])(?=[^A-Za-z])"
                ),
                " "
        );
    }

    public User getUser() {
        return user;
    }

    void setUser(User user) {
        this.user = user;
    }

    // on Android all network operations must be in BG
    private void bgCall(BgCall caller) {
        new Thread(caller::send).start();
    }

    private interface BgCall {
        void send();
    }

    public static class User {
        public int rating;
        public Attempt[] history;
    }

    public static class Attempt {
        public int puzzleId, priorRating, change;
    }

    public static class LichessSettings {
        private  String username;
        private  String password;

        public void unserialize(BitStream.Reader reader) throws Config.PGNException {
            try {
                username = reader.readString();
                password = reader.readString();
            } catch (Exception e) {
                throw new Config.PGNException(e);
            }
        }

        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            try {
                writer.writeString(username);
                writer.writeString(password);
            } catch (Exception e) {
                throw new Config.PGNException(e);
            }
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

    public static abstract class LichessMessage {
//        String message;
//        public LichessMessage(String message) {
//            this.message = message;
//        }
    }

    public static class LichessMessageLoginOk extends LichessMessage {
        private LichessMessageLoginOk() {}
    }

    public static class LichessMessagePuzzle extends LichessMessage {
        private LichessMessagePuzzle() {}
    }


    public interface LichessMessageConsumer {
        void consume(LichessMessage message);
        void error(Exception e);
    }

}
