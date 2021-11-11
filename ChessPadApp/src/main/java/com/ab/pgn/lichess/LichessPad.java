package com.ab.pgn.lichess;

import com.ab.pgn.BitStream;
import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Move;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.PgnLogger;
import com.ab.pgn.Square;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Created by Alexander Bootman on 4/4/2020.
 */
public class LichessPad {
    public static final String
        PUZZLE_TAG_ROUND_PREFIX = "id ",
        PUZZLE_TAG_RATING = "Rating",
        PUZZLE_TAG_VOTE = "Vote",
        pub_str_dummy = null;

    private static final String
        // Lichess Json keys:
        LJK_data = "data",

        LJK_game = "game",
        LJK_game_moves = "treeParts",
        LJK_game_players = "players",
        LJK_game_players_name = "name",
        LJK_game_players_color = "color",

        LJK_puzzle = "puzzle",
        LJK_puzzle_fen = "fen",
        LJK_puzzle_gameId = "gameId",
        LJK_puzzle_rating = "rating",
        LJK_puzzle_id = "id",
        LJK_puzzle_vote = "vote",
        LJK_puzzle_moves = "lines",

        LJK_user = "user",
        LJK_user_rating = "rating",
        LJK_user_history = "recent",

//        PUZZLE_TAG_Event = "link ",
        str_dummy = null;


    private static final PgnLogger logger = PgnLogger.getLogger(LichessPad.class);

    private LichessMessageConsumer lichessMessageConsumer;
    private LichessClient lichessClient;
    private User user;
    private volatile List<Pair<Integer, PgnGraph>> queue = new LinkedList<>();
    private transient boolean notifyConsumer = false;

    public LichessPad(LichessMessageConsumer lichessMessageConsumer) {
        this.lichessMessageConsumer = lichessMessageConsumer;
        lichessClient = new LichessClient();
    }

//    public LichessPad(String user, String password) throws Config.PGNException {
//        lichessClient = new LichessClient(this, user, password);
//    }

    public void serialize(BitStream.Writer writer) throws Config.PGNException {
        try {
            lichessClient.serialize(writer);
            if (user == null) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                user.serialize(writer);
            }
            writer.write(queue.size(), 5);
            for (Pair<Integer, PgnGraph> pair : queue) {
                writer.write(pair.first, 32);
                pair.second.serializeGraph(writer, 0);
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public void unserialize(BitStream.Reader reader) throws Config.PGNException {
        try {
            lichessClient = new LichessClient(reader);
            if (reader.read(1) == 1) {
                user = new User(reader);
            }
            queue.clear();     // sanity check
            int size = reader.read(5);
            for (int i = 0; i < size; ++i) {
                int puzzleId = reader.read(32);
                PgnGraph pgnGraph = new PgnGraph(reader, 0, null);
                queue.add(new Pair<>(puzzleId, pgnGraph));
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public void login(LichessSettings lichessSettings) {
        bgCall(() -> {
            try {
                int res = lichessClient.login(lichessSettings.username, lichessSettings.password);
                if (lichessMessageConsumer != null) {
                    if (res == LichessClient.LICHESS_RESULT_OK) {
                        lichessMessageConsumer.consume(new LichessMessageLoginOk());
                    } else {
                        lichessMessageConsumer.error(new LichessMessageLoginError());
                    }
                }
            } catch (Config.PGNException e) {
                logger.error(e);
                lichessMessageConsumer.error(new LichessMessageNetworkError());
            }
        });
    }

    public void logout() {
        user = null;
        bgCall(() -> lichessClient.logout());
    }

    public PgnGraph getPuzzle() {
        if (queue.size() > 0) {
            Pair<Integer, PgnGraph> pair = queue.remove(0);
            return pair.second;
        }
        notifyConsumer = true;
        fetchPuzzle();
        return null;
    }

    private void fetchPuzzle() {
        bgCall(() -> {
            String json = null;
            try {
                json = lichessClient.getPuzzle();
                logger.debug(String.format(Locale.US, "json: %s", json));
                parse(json);
                if (notifyConsumer) {
                    lichessMessageConsumer.consume(new LichessMessagePuzzle());
                }
                notifyConsumer = false;
            } catch (IOException e) {
                logger.error(e);
                lichessMessageConsumer.error(new LichessMessageNetworkError());
            } catch (Config.PGNException e) {
                logger.error(String.format(Locale.US, "json: %s", json));
                lichessMessageConsumer.error(new LichessMessagePuzzleError());
            }
        });
    }

    public String fetchPuzzleBatch() {
        try {
            return lichessClient.fetchPuzzleBatch();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    void parse(String json) throws Config.PGNException {
        JSONObject jsonObject = null;
        try {
            jsonObject = new JSONObject(json);
            if (jsonObject.has(LJK_data)) {
                jsonObject = jsonObject.getJSONObject(LJK_data);
            }
            JSONObject _user = null;
            if (jsonObject.has(LJK_user)) {
                _user = jsonObject.getJSONObject(LJK_user);
                LichessPad.User user = new LichessPad.User();
                user.rating = _user.getInt(LJK_user_rating);

                JSONArray history = _user.getJSONArray(LJK_user_history);
                int length = history.length();
                user.history = new LichessPad.Attempt[length];
                for (int i = 0; i < length; ++i) {
                    JSONArray _attempt = history.getJSONArray(i);
                    LichessPad.Attempt attempt = new LichessPad.Attempt();
                    attempt.puzzleId = _attempt.getInt(0);
                    attempt.change = _attempt.getInt(1);
                    attempt.priorRating = _attempt.getInt(2);
                    user.history[i] = attempt;
                }
                setUser(user);
            }
            JSONObject game = jsonObject.getJSONObject(LJK_game);
            JSONArray moves = game.getJSONArray(LJK_game_moves);
            int lastMoveNum = ((JSONArray) moves).length() - 1;
            JSONObject lastMove = ((JSONArray) moves).getJSONObject(lastMoveNum);
            String fen = lastMove.getString(LJK_puzzle_fen);
            PgnGraph puzzle = new PgnGraph(new Board(fen));
            JSONArray players = game.getJSONArray(LJK_game_players);
            for (int i = 0; i < players.length(); ++i) {
                JSONObject player = players.getJSONObject(i);
                String color = player.getString(LJK_game_players_color);
                color = color.substring(0, 1).toUpperCase() + color.substring(1);
                String name = player.getString(LJK_game_players_name);
                puzzle.getPgn().setTag(color, name);
            }

            JSONObject jsonPuzzle = jsonObject.getJSONObject(LJK_puzzle);
            Object moveObj = jsonPuzzle.get(LJK_puzzle_moves);
            while (moveObj instanceof JSONObject) {
                Object nextMoveObj = null;
                boolean variation = false;
                for (Iterator<String> it = ((JSONObject) moveObj).keys(); it.hasNext(); ) {
                    String move = it.next();
                    nextMoveObj = ((JSONObject) moveObj).get(move);
                    if (nextMoveObj.equals("retry")) {
                        continue;       // skip
                    }
                    if (variation) {
                        puzzle.toPrev();
                    }
                    parseMove(puzzle, move);
                    variation = true;
                }
                moveObj = nextMoveObj;
            }
//          logger.debug(moveObj.toString()); // win?
            int firstMoveFlags = puzzle.moveLine.get(1).moveFlags;
            if ((puzzle.getCurrentMove().moveFlags & Config.FLAGS_BLACK_MOVE) != (firstMoveFlags & Config.FLAGS_BLACK_MOVE)) {
                puzzle.delCurrentMove();
            }
            // store puzzle data in tags
            puzzle.getPgn().setTag(Config.TAG_Site, LichessClient.DOMAIN);
            String gameId = jsonPuzzle.getString(LJK_puzzle_gameId);
            puzzle.getPgn().setTag(Config.TAG_Event, LichessClient.DOMAIN_URL + "/" + gameId);
            int id = jsonPuzzle.getInt(LJK_puzzle_id);
            puzzle.getPgn().setTag(Config.TAG_Round, PUZZLE_TAG_ROUND_PREFIX + id);
            int rating = jsonPuzzle.getInt(LJK_puzzle_rating);
            puzzle.getPgn().setTag(PUZZLE_TAG_RATING, "" + rating);
            int vote = jsonPuzzle.getInt(LJK_puzzle_vote);
            puzzle.getPgn().setTag(PUZZLE_TAG_VOTE, "" + vote);
            addToQueue(id, puzzle);
        } catch (JSONException e) {
            throw new Config.PGNException(e);
        }
    }

    private void addToQueue(int puzzleId, PgnGraph puzzle) {
        for (Pair<Integer, PgnGraph> pair : queue) {
            if(pair.first == puzzleId) {
                return;
            }
        }
        queue.add(new Pair<>(puzzleId, puzzle));
    }

    // parse lichess moves e.g. b6c5 e2g4 h3g4 d1g4 f2f1q
    public static void parseMove(PgnGraph puzzle, String move) throws Config.PGNException {
        Board board = puzzle.getBoard();
        Move puzzleMove = board.newMove();
        puzzleMove.setFrom(new Square(move.substring(0, 2)));
        puzzleMove.setTo(new Square(move.substring(2, 4)));
        puzzleMove.setPiece(board.getPiece(puzzleMove.getFrom()));
        if (move.length() > 4) {
            String promotion = move.substring(4, 5);
            int p = Config.FEN_PIECES.indexOf(promotion);
            puzzleMove.setPiecePromoted(p);
        }
        if (puzzle.validateUserMove(puzzleMove)) {
            puzzle.addUserMove(puzzleMove);
        }
    }

    public void recordResult(PgnGraph puzzle, int result) {
        int _puzzleId = -1;
        String idTag = puzzle.getPgn().getTag(Config.TAG_Round);
        boolean tagOk = true;
        // protect puzzleId from user update?
        if (idTag == null) {
            logger.error("Cannot record result, no 'Round' tag for puzzle");
            tagOk = false;
        } else {
            try {
                _puzzleId = Integer.valueOf(idTag.substring(PUZZLE_TAG_ROUND_PREFIX.length()));
            } catch (Exception e) {
                logger.error(String.format(Locale.US, "Cannot record result, 'Round' tag for puzzle corrupted, %s", idTag));
                tagOk = false;
            }
        }

        if (tagOk) {
            final int puzzleId = _puzzleId;
            bgCall(() -> {
                try {
                    lichessClient.recordResult(puzzleId, result);
                    fetchPuzzle();
                } catch (IOException e) {
                    logger.error(e);
                }
            });
        } else {
            fetchPuzzle();
        }
    }

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

    public boolean isUserLoggedIn() {
        return lichessClient.isUserLoggedIn();
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
        public Attempt[] history = new Attempt[0];

        public User() {}

        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            try {
                writer.write(rating, 12);
                if (history == null) {
                    writer.write(0, 5);
                } else {
                    writer.write(history.length, 5);
                    for (Attempt a : history) {
                        a.serialize(writer);
                    }
                }
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        public User(BitStream.Reader reader) throws Config.PGNException {
            try {
                rating = reader.read(12);
                int len = reader.read(5);
                if (len > 0) {
                    history = new Attempt[len];
                    for (int i = 0; i < len; ++i) {
                        history[i] = new Attempt(reader);
                    }
                }
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }
    }

    public static class Attempt {
        public int puzzleId, priorRating, change;

        public Attempt() {}

        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            try {
                writer.write(puzzleId, 32);
                writer.write(priorRating, 12);
                writer.write(change, 9);
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        public Attempt(BitStream.Reader reader) throws Config.PGNException {
            try {
                puzzleId = reader.read(32);
                priorRating = reader.read(12);
                change = reader.read(9);
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }
    }

    public static class LichessSettings {
        private  String username;
        private  String password;

        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            try {
                writer.writeString(username);
                writer.writeString(password);
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        public void unserialize(BitStream.Reader reader) throws Config.PGNException {
            try {
                username = reader.readString();
                password = reader.readString();

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

        public String getPassword() {
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

    public static class LichessMessageLoginOk extends LichessMessage {}

    public static class LichessMessagePuzzle extends LichessMessage {}

    public static class LichessMessageNetworkError extends LichessMessage {}

    public static class LichessMessageLoginError extends LichessMessage {}

    public static class LichessMessagePuzzleError extends LichessMessage {}

    public interface LichessMessageConsumer {
        void consume(LichessMessage message);
        void error(LichessMessage message);
    }

}
