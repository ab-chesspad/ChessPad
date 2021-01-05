package com.ab.pgn.lichess;

import com.ab.pgn.BitStream;
import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.CpFile;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Alexander Bootman on 4/4/2020.
 */
public class LichessPad {
    private static final boolean DEBUG = true;
    private static final boolean TEST_HTML = true;

    private static final String
        //    PUZZLE_MARK = "lichess.puzzle = ",
        PUZZLE_MARK = "{LichessPuzzle(",    // updated 2020/11/23

        // Lichess JSON keys:
        LJK_data = "data",

// old tags, obsolete on 2020/12/25
//        LJK_game_moves = "treeParts",
//        LJK_puzzle_fen = "fen",
//        LJK_puzzle_gameId = "gameId",
//        LJK_puzzle_vote = "vote",
//        LJK_puzzle_moves = "lines",

        LJK_game = "game",
        LJK_game_id = "id",
        LJK_game_pgn = "pgn",
        LJK_game_players = "players",
        LJK_game_players_name = "name",
        LJK_game_players_color = "color",

        LJK_puzzle = "puzzle",
        LJK_puzzle_id = "id",
        LJK_puzzle_rating = "rating",
        LJK_puzzle_plays = "plays",
        LJK_puzzle_initial_ply = "initialPly",
        LJK_puzzle_solution = "solution",

        LJK_user = "user",
        LJK_user_rating = "rating",
        LJK_user_history = "recent",

        PUZZLE_TAG_ROUND_PREFIX = "id ",
        PUZZLE_TAG_RATING = "Rating",
        PUZZLE_TAG_VOTE = "Vote",
//        PUZZLE_TAG_Event = "link ",
        str_dummy = null;


    private static final PgnLogger logger = PgnLogger.getLogger(LichessPad.class);

    private LichessMessageConsumer lichessMessageConsumer;
    LichessClient lichessClient;
    private User user;
    private volatile List<Pair<String, PgnGraph>> queue = new LinkedList<>();
    private transient boolean notifyConsumer = false;

    public LichessPad(LichessMessageConsumer lichessMessageConsumer) {
        this.lichessMessageConsumer = lichessMessageConsumer;
        lichessClient = new LichessClient();
    }

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
            for (Pair<String, PgnGraph> pair : queue) {
                writer.writeString(pair.first);
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
                String puzzleId = reader.readString();
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
            Pair<String, PgnGraph> pair = queue.remove(0);
            return pair.second;
        }
        notifyConsumer = true;
        fetchPuzzle();
        return null;
    }

    void fetchPuzzle() {
//        logger.error("fetchPuzzle()", new Error("fetchPuzzle()"));
        bgCall(() -> {
            String json = null;
            try {
                json = lichessClient.getPuzzle();
                if (DEBUG) {
                    logger.debug(String.format(Locale.US, "json: %s", json));
                }
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
        int j = json.indexOf(PUZZLE_MARK);
        if (j > 0) {
            json = json.substring(j + PUZZLE_MARK.length());
        }

        JSONObject jsonObject;
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
            String pgn = game.getString(LJK_game_pgn);
            CpFile.Item item = new CpFile.Item(new CpFile.Pgn("dummy"));
            item.setMoveText(pgn);
            PgnGraph _puzzle = new PgnGraph(item, null);    // game from the beginning
            PgnGraph puzzle = new PgnGraph(_puzzle.getBoard());
//            int lastMoveNum = ((JSONArray) moves).length() - 1;
//            JSONObject lastMove = ((JSONArray) moves).getJSONObject(lastMoveNum);
//            String fen = lastMove.getString(LJK_puzzle_fen);
//            PgnGraph puzzle = new PgnGraph(new Board(fen));
            JSONArray players = game.getJSONArray(LJK_game_players);
            for (int i = 0; i < players.length(); ++i) {
                JSONObject player = players.getJSONObject(i);
                String color = player.getString(LJK_game_players_color);
                color = color.substring(0, 1).toUpperCase() + color.substring(1);
                String name = player.getString(LJK_game_players_name);
                puzzle.getPgn().setTag(color, name);
            }
            // store puzzle data in tags
            String gameId = game.getString(LJK_game_id);
            puzzle.getPgn().setTag(Config.TAG_Event, LichessClient.DOMAIN_URL + "/" + gameId);

            JSONObject jsonPuzzle = jsonObject.getJSONObject(LJK_puzzle);
//            JSONObject moveObj = jsonPuzzle.getJSONObject(LJK_puzzle_moves);
//            parseMoves(puzzle, moveObj);
            if (DEBUG) {
                logger.debug(puzzle.toPgn());
            }
            // store puzzle data in tags
            puzzle.getPgn().setTag(Config.TAG_Site, LichessClient.DOMAIN);

            String id = jsonPuzzle.getString(LJK_puzzle_id);
            puzzle.getPgn().setTag(Config.TAG_Round, PUZZLE_TAG_ROUND_PREFIX + id);

            if (TEST_HTML) {
                Pattern p = Pattern.compile("\\{\"game\":\\{\"id\":\"(.*?)\",.*?,\"solution\":\\[\"(.*?)\"],");
                Matcher m = p.matcher(json);
                if (m.find()) {
//                    String g1 = m.group(1);
                    String g2 = m.group(2);
                    logger.debug(String.format("%s --> \"%s\"", id, g2));
                }
            }

            int rating = jsonPuzzle.getInt(LJK_puzzle_rating);
            puzzle.getPgn().setTag(PUZZLE_TAG_RATING, "" + rating);

            int initialPly = jsonPuzzle.getInt(LJK_puzzle_initial_ply);     // ignored

            JSONArray solutionArray = jsonPuzzle.getJSONArray(LJK_puzzle_solution);
            parseMoves(puzzle, solutionArray);

//            int vote = jsonPuzzle.getInt(LJK_puzzle_vote);
//            puzzle.getPgn().setTag(PUZZLE_TAG_VOTE, "" + vote);

            addToQueue(id, puzzle);
            if (DEBUG) {
                logger.debug(puzzle.toString());
            }
        } catch (JSONException e) {
            throw new Config.PGNException(e);
        }
    }

    private void parseMoves(PgnGraph puzzle, JSONArray solutionArray) throws Config.PGNException, JSONException {
        for (int i = 0; i < solutionArray.length(); ++i) {
            String move = solutionArray.getString(i);
            if (DEBUG) {
                logger.debug(String.format("move %s", move));
            }
            parseMove(puzzle, move);
        }
    }

    // old format
    private void parseMoves(PgnGraph puzzle, JSONObject moveObj) throws Config.PGNException, JSONException {
        Object nextMoveObj = null;
        boolean variation = false;
        for (Iterator<String> it = moveObj.keys(); it.hasNext(); ) {
            String move = it.next();
            nextMoveObj = moveObj.get(move);
            if (nextMoveObj.equals("retry")) {
                continue;       // skip, why has lichess these moves anyway?
            }
            if (variation) {
                puzzle.toPrev();
            }
            if (nextMoveObj.equals("win")) {
                if (puzzle.moveLine.size() > 1) {
                    if ((puzzle.getCurrentMove().moveFlags & Config.FLAGS_BLACK_MOVE) == (puzzle.getInitBoard().getFlags() & Config.FLAGS_BLACK_MOVE)) {
                        break;      // ignore unnecessary move
                    }
                }
            }
            parseMove(puzzle, move);
            variation = true;
            if (nextMoveObj instanceof JSONObject) {
                int moveLen = puzzle.moveLine.size();
                parseMoves(puzzle, (JSONObject)nextMoveObj);
                while (puzzle.moveLine.size() > moveLen) {
                    puzzle.toPrev();
                }
            }
        }
    }

    private void parseMove(PgnGraph puzzle, String move) throws Config.PGNException {
        Board board = puzzle.getBoard();
//        logger.debug(String.format("\n%s%s", board.toString(), move));
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

    private void addToQueue(String puzzleId, PgnGraph puzzle) {
        for (Pair<String, PgnGraph> pair : queue) {
            if(pair.first == puzzleId) {
                return;
            }
        }
        queue.add(new Pair<>(puzzleId, puzzle));
    }

    public void recordResult(PgnGraph puzzle, int result) {
        String _puzzleId = "";
        String idTag = puzzle.getPgn().getTag(Config.TAG_Round);
        boolean tagOk = true;
        // protect puzzleId from user update?
        if (idTag == null) {
            logger.error("Cannot record result, no 'Round' tag for puzzle");
            tagOk = false;
        } else {
//            try {
//                _puzzleId = Integer.valueOf(idTag.substring(PUZZLE_TAG_ROUND_PREFIX.length()));
//            } catch (Exception e) {
//                logger.error(String.format(Locale.US, "Cannot record result, 'Round' tag for puzzle corrupted, %s", idTag));
//                tagOk = false;
//            }
            _puzzleId = idTag.substring(PUZZLE_TAG_ROUND_PREFIX.length());
        }

        if (tagOk) {
            final String puzzleId = _puzzleId;
            bgCall(() -> {
                try {
                    lichessClient.recordResult(puzzleId, result);
                    synchronized (this) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } catch (IOException e) {
                    logger.error(e);
                    lichessMessageConsumer.error(new LichessMessageRecordPuzzleError());
                }
                fetchPuzzle();
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
        private String username;
        private String password;

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

    public static class LichessMessageRecordPuzzleError extends LichessMessage {}

    public interface LichessMessageConsumer {
        void consume(LichessMessage message);
        void error(LichessMessage message);
    }

}
