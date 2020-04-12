package com.ab.pgn.lichess;

import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Move;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.PgnLogger;
import com.ab.pgn.Square;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class Puzzle extends PgnGraph {
    private static final String
        START_MARK = "lichess.puzzle = ",   // on html page

        // Lichess Json keys:
        LJK_data = "data",

        LJK_data_game = "game",
        LJK_data_game_moves = "treeParts",
        LJK_data_game_gameId = "gameId",
        LJK_data_game_rating = "rating",
        LJK_data_game_id = "id",
        LJK_data_game_vote = "vote",

        LJK_data_puzzle = "puzzle",
//        LJK_puzzle_color = "color",
        LJK_puzzle_fen = "fen",
        LJK_puzzle_moves = "lines",

        LJK_data_user = "user",
        LJK_user_rating = "rating",
        LJK_user_history = "recent",

        LJK_dictionary = "i18n",
        str_dummy = null;

/*
    public static final String
        MSG_butYouCanKeepTrying = "butYouCanKeepTrying",
        MSG_playedXTimes = "playedXTimes",
        MSG_thankYou = "thankYou",
        MSG_playWithAFriend = "playWithAFriend",
        MSG_usingServerAnalysis = "usingServerAnalysis",
        MSG_yourTurn = "yourTurn",
        MSG_yourPuzzleRatingX = "yourPuzzleRatingX",
        MSG_toTrackYourProgress = "toTrackYourProgress",
        MSG_loadingEngine = "loadingEngine",
        MSG_boardEditor = "boardEditor",
        MSG_signUp = "signUp",
        MSG_continueFromHere = "continueFromHere",
        MSG_showThreat = "showThreat",
        MSG_goodMove = "goodMove",
        MSG_thisPuzzleIsWrong = "thisPuzzleIsWrong",
        MSG_goDeeper = "goDeeper",
        MSG_butYouCanDoBetter = "butYouCanDoBetter",
        MSG_casual = "casual",
        MSG_bestMove = "bestMove",
        MSG_playedXTimes_few = "playedXTimes_few",
        MSG_depthX = "depthX",
        MSG_playedXTimes_many = "playedXTimes_many",
        MSG_retryThisPuzzle = "retryThisPuzzle",
        MSG_toggleLocalEvaluation = "toggleLocalEvaluation",
        MSG_pleaseVotePuzzle = "pleaseVotePuzzle",
        MSG_findTheBestMoveForBlack = "findTheBestMoveForBlack",
        MSG_fromGameLink = "fromGameLink",
        MSG_playedXTimes_one = "playedXTimes:one",
        MSG_findTheBestMoveForWhite = "findTheBestMoveForWhite",
        MSG_puzzleId = "puzzleId",
        MSG_analysis = "analysis",
        MSG_cloudAnalysis = "cloudAnalysis",
        MSG_gameOver = "gameOver",
        MSG_viewTheSolution = "viewTheSolution",
        MSG_rated = "rated",
        MSG_puzzleFailed = "puzzleFailed",
        MSG_playWithTheMachine = "playWithTheMachine",
        MSG_keepGoing = "keepGoing",
        MSG_inLocalBrowser = "inLocalBrowser",
        MSG_continueTraining = "continueTraining",
        MSG_success = "success",
        MSG_ratingX = "ratingX",
        MSG_thisPuzzleIsCorrect = "thisPuzzleIsCorrect",
        MSG_wasThisPuzzleAnyGood = "wasThisPuzzleAnyGood";
*/

    private static final PgnLogger logger = PgnLogger.getLogger(Puzzle.class);

    private String gameId;
    private int rating, id, vote;

//    private User user;
//    private Map<String, String> dictionary = new HashMap<>();

    public Puzzle(LichessPad lichessPad, String htmlPage) throws Config.PGNException {
        super();
        // should we check that the page is correct?
        int start = htmlPage.indexOf(START_MARK);
        if (start > 0) {
            start += START_MARK.length();     // regex?
            htmlPage = htmlPage.substring(start);
        }
        try {
            parse(lichessPad, htmlPage);
        } catch (JSONException e) {
            throw new Config.PGNException(e);
        }
    }

    private void parse(LichessPad lichessPad, String json) throws Config.PGNException, JSONException {
        JSONObject jsonObject = new JSONObject(json);
        if (jsonObject.has((LJK_data))) {
            jsonObject = jsonObject.getJSONObject(LJK_data);
        }
        JSONObject _user = null;
        if (jsonObject.has(LJK_data_user)) {
            _user = jsonObject.getJSONObject(LJK_data_user);
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
            if (lichessPad != null) {
                lichessPad.setUser(user);
            }
        }
//        if (user == null) {
//            logger.debug("no user, log in!");
//        }
        JSONObject game = jsonObject.getJSONObject(LJK_data_game);
        JSONArray moves = game.getJSONArray(LJK_data_game_moves);
        int lastMoveNum = ((JSONArray)moves).length() - 1;
        JSONObject lastMove = ((JSONArray)moves).getJSONObject(lastMoveNum);
        String fen = lastMove.getString(LJK_puzzle_fen);
        init(new Board(fen), null);
//        logger.debug(getBoard().toString());

        JSONObject puzzle = jsonObject.getJSONObject(LJK_data_puzzle);
        Object moveObj = puzzle.get(LJK_puzzle_moves);
        while(moveObj instanceof JSONObject) {
            Object nextMoveObj = null;
            for (Iterator<String> it = ((JSONObject) moveObj).keys(); it.hasNext(); ) {
                String move = it.next();
                handleMove(move);
                nextMoveObj = ((JSONObject) moveObj).get(move);
            }
            moveObj = nextMoveObj;
        }
//        logger.debug(moveObj.toString()); // win?
        int firstMoveFlags = moveLine.get(1).moveFlags;
        if ( (getCurrentMove().moveFlags & Config.FLAGS_BLACK_MOVE) != (firstMoveFlags & Config.FLAGS_BLACK_MOVE)) {
            delCurrentMove();
        }
        gameId = puzzle.getString(LJK_data_game_gameId);
        rating = puzzle.getInt(LJK_data_game_rating);
        id = puzzle.getInt(LJK_data_game_id);
        vote = puzzle.getInt(LJK_data_game_vote);

        logger.debug(String.format("%s\n%s", getInitBoard().toString(), toPgn()));

        if (jsonObject.has(LJK_dictionary)) {
            JSONObject dict = jsonObject.getJSONObject(LJK_dictionary);
            for (Iterator<String> it = dict.keys(); it.hasNext(); ) {
                String key = it.next();
                String value = dict.getString(key);
                if (lichessPad != null) {
                    lichessPad.putLocalizedMessage(key, value);
                }
//            dictionary.put(key, value);
            }
        }
    }

    private void handleMove(String move) throws Config.PGNException {
        Board board = getBoard();
        Move puzzleMove = board.newMove();
        puzzleMove.setFrom(new Square(move.substring(0, 2)));
        puzzleMove.setTo(new Square(move.substring(2, 4)));
        puzzleMove.setPiece(board.getPiece(puzzleMove.getFrom()));
        if (move.length() > 4) {
            String promotion = move.substring(4, 5);
            int p = Config.FEN_PIECES.indexOf(promotion);
            puzzleMove.setPiecePromoted(p);
        }
        if (validateUserMove(puzzleMove)) {
            addUserMove(puzzleMove);
        }
    }

    public String getGameId() {
        return gameId;
    }

    public int getRating() {
        return rating;
    }

    public int getId() {
        return id;
    }

    public int getVote() {
        return vote;
    }

/*
    public String getLocalizedMessage(String key) {
        String res = dictionary.get(key);
        if (res == null) {
            return splitCamelCase(key);
        }
        return res;
    }

    String splitCamelCase(String s) {
        return s.replaceAll(
            String.format("%s|%s|%s",
                "(?<=[A-Z])(?=[A-Z][a-z])",
                "(?<=[^A-Z])(?=[A-Z])",
                "(?<=[A-Za-z])(?=[^A-Za-z])"
            ),
            " "
        );
    }
*/

/*
    public User getUser() {
        return user;
    }
*/

/*
    public static class User {
        public int rating;
        public Attempt[] history;
    }

    public static class Attempt {
        public int puzzleId, priorRating, change;
    }
*/

}
