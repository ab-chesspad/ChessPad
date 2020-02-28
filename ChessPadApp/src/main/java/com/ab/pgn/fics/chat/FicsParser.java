/**
 * New BSD License
 * http://www.opensource.org/licenses/bsd-license.php
 * Copyright 2009-2016 RaptorProject (https://github.com/Raptor-Fics-Interface/Raptor)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 * Neither the name of the RaptorProject nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * 04/15/2019 Alexander Bootman - simplified and optimized
 *
 */
package com.ab.pgn.fics.chat;

import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnLogger;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FicsParser {
    private static final boolean DEBUG = true;

    private static final String
        CHALLENGE_MESSAGE = "Challenge: ",
        GAMES_END_MESSAGE = "games displayed.",
        SOUGHT_END_MESSAGE = "ads displayed.",
        G1_MESSAGE = "<g1> ",
        MOVELIST_MESSAGE = "Movelist for game ",
        MOVES_MESSAGE = "Move ",
        OBSERVING_MESSAGE = " observing game ",
        EXAMINER_MESSAGE = "has made you an examiner of game ",
        KIBITZ_MESSAGE = " kibitzes: ",
        S12_MESSAGE = "<12>",

        SPECIAL_GAME_START = "(",
        SPECIAL_GAME_END = ")",
        GAME_FORMULA_START = "[",
        GAME_FORMULA_END = "]",
        EXAM_GAME = "(Exam.",
        WHITE_MOVE = "W:",
        CR_LF_SEPARATOR = "[\\r\\n]+",
        dummy_string = null;

    private static final Pattern KIBITZ_PATTERN = Pattern.compile("([a-zA-Z0-9]+).*? kibitzes: (.*?)$");


    private final PgnLogger logger = PgnLogger.getLogger(this.getClass());

    public InboundMessage.Info parse(String msg, InboundMessage.G1Game currentGame) {
        currentGame.setUpdated(false);
        currentGame.setMessage(msg);
        String message = msg.trim();
        if(DEBUG) {
            logger.debug(String.format("Raw inbound \"%s\"", message));
        }
        if(message.startsWith("Game ")) {
            logger.debug(String.format("Raw inbound \"%s\"", message));
        }
        if (message.endsWith(GAMES_END_MESSAGE)) {
            return parseGames(message);
        } else if (message.endsWith(SOUGHT_END_MESSAGE)) {
            return parseSought(message);
        } else if (message.contains(KIBITZ_MESSAGE)) {
            return parseKibitzMsg(message);
        } else {
            String[] lines = message.split(CR_LF_SEPARATOR);
            InboundMessage.G1Game newGame = currentGame;
            if (message.startsWith(MOVELIST_MESSAGE)) {
                newGame = parseMoveList(lines, currentGame);
            } else {
/*
                if (message.contains(CHALLENGE_MESSAGE)) {
                    InboundMessage.Challenge newMsg = parseChallenge(lines);
                    if(newMsg != null) {
                        return newMsg;
                    }
                }
*/

                for( String _line : lines) {
//                for(int i = 0; i < lines.length; ++i) {
//                    String line = lines[i].trim();
                    String line = _line.trim();
                    if(line.isEmpty()) {
                        continue;
                    }
                    if(line.startsWith(CHALLENGE_MESSAGE)) {
                        return new InboundMessage.Challenge(line.substring(CHALLENGE_MESSAGE.length()));
                    }
                    int gameId;
                    if((gameId = getGameId(line, OBSERVING_MESSAGE)) >= 0) {
                        newGame = new InboundMessage.G1Game(gameId);
//                        newGame.setState(InboundMessage.OBSERVING_STATE);
                        continue;
                    }
                    if((gameId = getGameId(line, EXAMINER_MESSAGE)) >= 0) {
                        newGame = new InboundMessage.G1Game(gameId);
//                        newGame.setState(InboundMessage.EXAMINING_STATE);
                        continue;
                    }
                    if (line.startsWith(G1_MESSAGE)) {
                        parseG1(line, newGame);
                        continue;
                    }
                    if (line.startsWith(S12_MESSAGE)) {
//                        newGame = parseG1(line);
                        parseS12(line, newGame);
//                        continue;
                    }
                }
//                if (message.contains(S12_MESSAGE)) {
//                    parseS12(lines, newGame);
//                }
            }
            if (newGame.isUpdated()) {
                return newGame;
            }
        }
        return new InboundMessage.Info(msg);
    }

    // on games
    // https://www.freechess.org/Help/HelpFiles/games.ludens.html
    private InboundMessage.Info parseGames(String message) {
        /* examples:
        1 (Exam.    0 Plummer        0 McCartney ) [ uu  0   0] B:  1
        34 (Exam.    0 LectureNova    0 LectureNov) [ uu  0   0] W:  1
         15 ++++ GuestYBHH   ++++ saresu     [ bu  5   0]   5:00 -  5:00 (39-39) B:  1
         118 1462 notsofool   15
        */

        String[] lines = message.split(CR_LF_SEPARATOR);
        InboundMessage.InboundList gameInfos = new InboundMessage.InboundList();
        for(int i = 0; i < lines.length - 2; ++i) {     // ignore last two lines "\n NNN games displayed."
            String line = lines[i].trim();
            int j = 0;
            String[] parts = null;
            try {
                InboundMessage.PlayedGame gameInfo = new InboundMessage.PlayedGame();
                parts = line.split("\\s+");
                gameInfo.setId(parts[j++]);
                if (parts[j].startsWith(SPECIAL_GAME_START)) {
                    if (EXAM_GAME.equals(parts[j])) {
                        ++j;
                        gameInfo.setBeingExamined(true);
                    } else {
                        continue;   // skip Setup game
                    }
                }
                gameInfo.setWhiteElo(parts[j++]);
                gameInfo.setWhiteName(parts[j++]);
                gameInfo.setBlackElo(parts[j++]);
                String blackName = parts[j++];
                if(blackName.endsWith(SPECIAL_GAME_END)) {
                    blackName = blackName.substring(0, blackName.length() - SPECIAL_GAME_END.length());
                }
                gameInfo.setBlackName(blackName);

                if(SPECIAL_GAME_END.equals(parts[j])) {
                    ++j;    // skip SPECIAL_GAME_END
                }
                if(GAME_FORMULA_START.equals(parts[j])) {
                    ++j;
                }
                String category = parts[j++];
                if(category.startsWith(GAME_FORMULA_START)) {
                    category = category.substring(GAME_FORMULA_START.length());
                }
                int k = 0;
                if(category.startsWith("p")) {
                    gameInfo.setPrivate(true);
                    ++k;
                }
                gameInfo.setGameType(InboundMessage.GameType.byAbbreviation(category.charAt(k)));
                ++k;
                gameInfo.setRated(category.charAt(k) == 'r');
                String time;
                if(++k >= category.length()) {
                    time = parts[j++];
                } else {
                    time = category.substring(k);
                }
                gameInfo.setTimeMin(Integer.valueOf(time));
                String increment = parts[j];
                if(increment.endsWith(GAME_FORMULA_END)) {
                    increment = increment.substring(0, increment.length() - GAME_FORMULA_END.length());
                }
                gameInfo.setInc(Integer.valueOf(increment));

                // now parse from the end:
                j = parts.length - 1;
                gameInfo.setMoveNumber(Integer.valueOf(parts[j--]));
                gameInfo.setWhitesMove(WHITE_MOVE.equals(parts[j]));

                gameInfos.add(gameInfo);
            } catch(RuntimeException e) {
                logger.error(String.format(Locale.getDefault(), "Error parsing %d item %s for game: \"%s\"", j, parts[j], line), e);
            }
        }
        return gameInfos;
    }

    // on sought
    private InboundMessage.Info parseSought(String message) {
        /* examples:
         24 2311 MrsLurKing(C)      15   0 rated   standard               0-9999 mf
         30 ++++ Dimmsdale          15  20 unrated standard               0-9999 m
         84 2368 Knightsmasher(C)   15   0 rated   standard               0-9999 f
        113 ++++ GuestWRCY           1   5 unrated blitz      [white]     0-9999
        */

        String[] lines = message.split(CR_LF_SEPARATOR);
        InboundMessage.InboundList gameInfos = new InboundMessage.InboundList();
        for(int i = 0; i < lines.length - 1; ++i) {     // ignore last line "\n NNN ads displayed."
            String line = lines[i].trim();
            int j = 0;
            String[] parts = null;
            try {
                InboundMessage.Ad gameInfo = new InboundMessage.Ad();
                parts = line.split("\\s+");
                gameInfo.setId(parts[j++]);
                gameInfo.setSoughtElo(parts[j++]);
                gameInfo.setSoughtName(parts[j++]);

                gameInfo.setTimeMin(Integer.valueOf(parts[j++]));
                gameInfo.setInc(Integer.valueOf(parts[j++]));
                gameInfo.setRated(parts[j++].equals("rated"));
                gameInfo.setGameType(InboundMessage.GameType.valueOf(parts[j++]));
                switch (parts[j++]) {
                    case "[white]":
                    case "[w]":
                        gameInfo.setSoughtColor("w");
                        break;

                    case "[black]":
                    case "[b]":
                        gameInfo.setSoughtColor("b");
                        break;

                    default:
                        --j;    // reread
                        break;
                }
                String[] eloRange = parts[j++].split("-");
                gameInfo.setMinElo(Integer.valueOf(eloRange[0]));
                gameInfo.setMaxElo(Integer.valueOf(eloRange[1]));

                // now parse from the end:
                String lastToken = parts[parts.length - 1];
                gameInfo.setManual(lastToken.contains("m"));
                gameInfo.setUseFormula(lastToken.contains("f"));

                gameInfos.add(gameInfo);
            } catch (RuntimeException e) {
                logger.error(String.format(Locale.getDefault(), "Error parsing %d item %s for ad: \"%s\"", j, parts[j], line), e);
            }
        }
        return gameInfos;
    }

    private InboundMessage.KibitzMsg parseKibitzMsg(String message) {
        // endgamebot(*)(TD)(----)[58] kibitzes: Hello from endgamebot. "tell endgamebot hint" if you want a hint or "back" if you want to make a different move
        Matcher m = KIBITZ_PATTERN.matcher(message);
        if (m.find()) {
            String kibitzer = m.group(1);
            String kibitzMsg = m.group(2);
            return new InboundMessage.KibitzMsg(kibitzer, kibitzMsg);
        }
        return null;
    }

/*
    private InboundMessage.G1Game parseG1(String[] lines, InboundMessage.G1Game currentGame) {
        for(int i = 0; i < lines.length; ++i) {
            String line = lines[i].trim();
            if (line.startsWith(G1_MESSAGE)) {
                return parseG1(line);
            }
        }
        return currentGame;
    }
*/

    private void parseG1(String line, InboundMessage.G1Game gameInfo) {
        /*
         *- <g1> 1 p=0 t=blitz r=1 u=1,1 it=5,5 i=8,8 pt=0 rt=1586E,2100 ts=1,0 m=0 n=0
         * <g1> 111 p=0 t=blitz r=1 u=0,0 it=180,0 i=180,0 pt=0 rt=1978,1779 ts=1,1 m=2 n=1
         *
         * (note the - was added so as not to confuse interfaces displaying this
         * helpfile)
         *
         * This is in the format: game_number p=private(1/0) t=type r=rated(1/0)
         * u=white_registered(1/0),black_registered(1/0)
         * it=initial_white_time,initial_black_time
         * i=initial_white_inc,initial_black_inc pt=partner's_game_number(or 0 if none)
         * rt=white_rating(+ provshow character),black_rating(+ provshow character)
         * ts=white_uses_timeseal(0/1),black_uses_timeseal(0/1)
         */
        String[] parts = line.split(" ");
        int j = 1;  // skip G1_START_MESSAGE
        gameInfo.setId(parts[j++]);
        try {
            for (; j < parts.length; ++j) {
                String part = parts[j];
                String[] pair = part.split("=");
                String[] values;
                switch (pair[0]) {
                    case "p":
                        gameInfo.setPrivate(pair[1].equals("1"));
                        break;

                    case "t":
                        gameInfo.setGameType(InboundMessage.GameType.valueOf(pair[1]));
                        break;

                    case "r":
                        gameInfo.setRated(pair[1].equals("1"));
                        break;

                    case "u":
                        values = pair[1].split(",");
                        gameInfo.setWhtieRegistered(values[0].equals("1"));
                        gameInfo.setBlackRegistered(values[1].equals("1"));
                        break;

                    case "it":
                        values = pair[1].split(",");
                        gameInfo.setTime(Integer.valueOf(values[0]));
                        gameInfo.setInc(Integer.valueOf(values[1]));
                        break;

                    case "i":
                        values = pair[1].split(",");
                        gameInfo.setBlackTime(Integer.valueOf(values[0]));
                        gameInfo.setBlackInc(Integer.valueOf(values[1]));
                        break;

                    case "pt":
                        gameInfo.setPartnerGameId(pair[1]);
                        break;

                    case "rt":
                        values = pair[1].split(",");
                        gameInfo.setWhiteElo(values[0]);
                        gameInfo.setBlackElo(values[1]);
                        break;

                    case "ts":
                        values = pair[1].split(",");
                        gameInfo.setWhiteUsingTimeseal(values[0].equals("1"));
                        gameInfo.setBlackUsingTimeseal(values[1].equals("1"));
                        break;

                    case "m":
                    case "n":
                        if(DEBUG) {
                            logger.debug(String.format("Parsing \"%s\", unknown key %s", line, pair[0]));
                        }
                        break;

                    default:
                        throw new Config.PGNException(String.format("Error parsing \"%s\", unknown key %s", line, pair[0]));
                }

                if (!gameInfo.isWhtieRegistered()) {
                    gameInfo.setWhiteElo(Config.FICS_UNKNOWN_RATING);
                }
                if (!gameInfo.isBlackRegistered()) {
                    gameInfo.setBlackElo(Config.FICS_UNKNOWN_RATING);
                }
            }
        } catch (Exception e) {
            logger.error(String.format(Locale.getDefault(), "Error parsing %d item %s for <g1>: \"%s\"", j, parts[j], line), e);
        }
    }

    // https://www.freechess.org/Help/HelpFiles/style12.html
    private void parseS12(String line, final InboundMessage.G1Game gameInfo) {
        /**
         * // position after move
         * <12>rnbqkbnr pppppppp -------- -------- ----P--- -------- PPPP-PPP RNBQKBNR B 4 1 1 1 1 0 100 guestBLARG guestcday 1 10 0 39 39 600 600 1 P/e2-e4 (0:00) e4 1 0 0
         */
        String[] parts = line.split(" ");
        int j = 1;  // skip S12_MESSAGE
        Board board = new Board();
        try {

            for (j = 1; j < 9; ++j) {
                for (int x = 0; x < parts[j].length(); ++x) {
                    char ch = parts[j].charAt(x);
                    if (ch == '-') {
                        board.setPiece(x, 8 - j, Config.EMPTY);
                    } else {
                        int piece = Config.FEN_PIECES.indexOf(ch);
                        board.setPiece(x, 8 - j, piece);
                    }
                }
            }
            int flags = board.getFlags() & ~Config.POSITION_FLAGS;
            if(parts[j++].equals("B")) {    // j == 9
                flags |= Config.BLACK;  // position after move
            }
            int doublePawnPushFile = Integer.valueOf(parts[j++]);   // j == 10
            if(parts[j++].equals("1")) {                            // j == 11
                flags |= Config.FLAGS_W_KING_OK;
            }
            if(parts[j++].equals("1")) {                            // j == 12
                flags |= Config.FLAGS_W_QUEEN_OK;
            }
            if(parts[j++].equals("1")) {                            // j == 13
                flags |= Config.FLAGS_B_KING_OK;
            }
            if(parts[j++].equals("1")) {                            // j == 14
                flags |= Config.FLAGS_B_QUEEN_OK;
            }

            board.setReversiblePlyNum(Integer.valueOf(parts[j++])); // j == 15
            String id = parts[j++];                                 // j == 16
            if(!id.equals(gameInfo.getId())) {      // sanity check
                logger.error(String.format("<12> id does not match <g1> id: %s |= %s for %s", id, gameInfo.getId(), line));
            }
            gameInfo.setWhiteName(parts[j++]);                      // j == 17
            gameInfo.setBlackName(parts[j++]);                      // j == 18
            logger.error(String.format("playing %s vs. %s", gameInfo.getWhiteName(), gameInfo.getBlackName()));
            int state = relation2State(parts[j++]);                 // j == 19
//            gameInfo.setMyRelationToGame(InboundMessage.MyRelationToGame.myRelationToGame(Integer.valueOf(parts[j++])));
            gameInfo.setState(state);
            gameInfo.setTime(Integer.valueOf(parts[j++]));          // j == 20
            gameInfo.setInc(Integer.valueOf(parts[j++]));           // j == 21

            int whiteStrength = Integer.valueOf(parts[j++]);        // j == 22, what is it?
            int blackStrength = Integer.valueOf(parts[j++]);        // j == 23, what is it?
            gameInfo.setWhiteRemainingTime(Integer.valueOf(parts[j++]));    // j == 24
            gameInfo.setBlackRemainingTime(Integer.valueOf(parts[j++]));    // j == 25
            gameInfo.setMoveNumber(Integer.valueOf(parts[j++]));    // j == 26

            String coordinateNotation = parts[j++];                 // j == 27
//                if(doublePawnPushFile != -1) {
//                    // verify consistency
//                    int piece;
//                    if(move != null && (piece = move.getColorlessPiece()) == Config.PAWN &&
//                            move.getFromX() == doublePawnPushFile &&
//                            move.getToX() == doublePawnPushFile) {
//                        piece ^= Config.BLACK;  // his pawn
//                        if(board.getPiece(move.getToX() - 1, move.getToY()) == piece ||
//                                board.getPiece(move.getToX() + 1, move.getToY()) == piece) {
//                            flags |= Config.FLAGS_ENPASSANT_OK;
//                            board.setEnpassantX(doublePawnPushFile);
//                            logger.debug(String.format("Enpass ok: %s:\n%s", board.getEnpassant().toString(), board.toString()));
//                        }
//                    }
//                }

            board.setFlags(flags);
            // this is the next move number!
            int plyNum = 2 * (gameInfo.getMoveNumber() - 1);
            if ((flags & Config.FLAGS_BLACK_MOVE) != 0) {
                ++plyNum;
            }
            board.setPlyNum(plyNum);
            String timeTakenForLastMove = parts[j++];   // j == 28
            gameInfo.getInboundMoves().clear();
            String san = parts[j++];                    // j == 29
            gameInfo.getInboundMoves().add(new InboundMessage.InboundMove(san, board));

            if(!parts[j++].equals("0")) {               // j == 30
                logger.error(String.format("Black on Bottom! %s", line));
            }
            gameInfo.setClockTicking(parts[j++].equals("1"));       // j == 31
            gameInfo.setLagInMillis(Integer.valueOf(parts[j++]));   // j == 32
            gameInfo.setUpdated(true);
//            logger.debug(String.format("state=0x%04x\n%s", gameInfo.getState(), board.toString()));
        } catch (Exception e) {
            logger.error(String.format(Locale.getDefault(), "Error parsing %d item %s for <12>: \"%s\"", j, parts[j], line), e);
        }
    }

    // it does not verify consistency with current <g1> and <12>
    private InboundMessage.G1Game parseMoveList(String[] lines, final InboundMessage.G1Game gameInfo) {
        /*
        Movelist for game 64:
        mosia (1765) vs. leszed (1539) --- Tue May  7, 01:01 EDT 2019
        Rated standard match, initial time: 15 minutes, increment: 0 seconds.
        <12&> qrnkbnrb pppppppp -------- -------- -------- -------- PPPPPPPP QRNKBNRB W -1 1 1 1 1 0 69 laikun zabakov -4 3 0 39 39 108 123 1 none (0:00) none 0 0 0
        Move  mosia                   leszed
        ----  ---------------------   ---------------------
          1.  d4      (0:00.000)      e6      (0:00.000)
          2.  c4      (0:01.807)      c5      (0:02.964)
          3.  Nf3     (0:02.056)      Qf6     (0:04.842)
          4.  Bg5     (0:17.648)      Qg6     (0:16.422)
          5.  e3      (0:12.519)      Nc6     (0:03.036)
          6.  Bd3     (0:07.444)      f5      (0:18.674)
          7.  O-O     (0:25.745)      cxd4    (0:03.249)
          8.  exd4    (0:08.311)      Nxd4    (0:03.232)
          9.  Be3     (0:47.633)      Nxf3+   (0:07.481)
         10.  Qxf3    (0:01.829)      Nh6     (0:18.470)
         11.  h3      (0:08.133)      Be7     (0:18.107)
         12.  c5      (0:17.884)      O-O     (0:04.761)
         13.  b4      (0:02.328)      Bg5     (0:06.787)
         14.  Nc3     (0:37.262)      Bxe3    (0:03.264)
         15.  fxe3    (0:03.899)      Qf6     (0:04.881)
         16.  Rac1    (0:56.080)      e5      (0:07.964)
         17.  Nd5     (0:33.333)      Qc6     (0:10.237)
         18.  Ne7+    (0:29.178)      Kh8     (0:12.905)
         19.  Nxc6    (0:03.753)      dxc6    (0:07.248)
         20.  Qg3     (0:27.430)      e4      (0:17.543)
         21.  Bc4     (0:16.849)      b5      (0:17.097)
         22.  Qd6     (0:13.551)      Ba6     (0:14.586)
         23.  Qxc6    (0:55.155)      bxc4    (0:03.851)
         24.  Qxa6    (0:02.690)      Rf7     (0:15.115)
         25.  Qxc4    (0:25.559)      Rd8     (0:04.747)
         26.  Rcd1    (0:17.579)      Rxd1    (0:59.062)
         27.  Rxd1    (0:02.762)      g6      (0:18.127)
         28.  Rd8+    (0:09.832)      Kg7     (0:11.601)
         29.  c6      (0:03.650)      Ng8     (0:14.356)
         30.  Rd7     (0:34.633)      Ne7     (0:54.566)
         31.  Qd4+    (0:46.919)      Kh6     (0:31.686)
         32.  Qc4     (0:16.914)      Kg7     (0:10.569)
         33.  c7      (0:08.811)      h5      (0:09.584)
         34.  c8=Q    (0:09.546)      Nxc8    (0:07.654)
         35.  Qxf7+   (0:08.106)      Kh6     (0:03.257)
              {Still in progress} *
         */
        int j = 0;
        String line = lines[j++].trim();
        String[] parts = line.split("[ :]");
        String id = parts[parts.length - 1];
        boolean listStarted = false;

        for(; j < lines.length; ++j) {
            line = lines[j].trim();
            if(line.isEmpty()) {
                continue;
            }
            if(listStarted) {
                if(Character.isDigit(line.charAt(0))) {
                    parts = line.split(" +");
                    for(String part : parts) {
                        if(Character.isLetter(part.charAt(0))) {
                            gameInfo.getInboundMoves().add(new InboundMessage.InboundMove(part, null));
                        }
                    }
                }
            } else {
                if(line.startsWith(S12_MESSAGE)) {
                    parseS12(line, gameInfo);
                    continue;
                }
                if(line.startsWith(MOVES_MESSAGE)) {
                    listStarted = true;
//                    continue;
                }
            }

        }
        gameInfo.setUpdated(true);
        return gameInfo;
    }

    private InboundMessage.Challenge parseChallenge(String[] lines) {
        /*
        "Your seek matches one posted by GuestQCSK.
        Challenge: GuestQCSK (----) GuestGZQL (----) unrated standard 30 0.
        You can "accept" or "decline", or propose different parameters.
         */
        for (String l : lines) {
            String line = l.trim();
            if(line.startsWith(CHALLENGE_MESSAGE)) {
                return new InboundMessage.Challenge(line.substring(CHALLENGE_MESSAGE.length()));
            }
        }
        return null;
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
    @SuppressWarnings("unchecked")
    private static final Pair<String, Integer>[] MyRelationToGame = new Pair[] {
        new Pair<>("-3", InboundMessage.ISOLATED_STATE),
        new Pair<>("-2", InboundMessage.OBSERVING_STATE | InboundMessage.EXAMINED_STATE),
        new Pair<>("-1", InboundMessage.PLAYED_STATE | InboundMessage.MY_MOVE_STATE),
        new Pair<>("0", InboundMessage.OBSERVING_STATE | InboundMessage.PLAYED_STATE ),
        new Pair<>("1", InboundMessage.PLAYED_STATE),
        new Pair<>("2", InboundMessage.EXAMINER_STATE | InboundMessage.EXAMINED_STATE),
    };

    private static int relation2State(String relation) {
        for(Pair<String, Integer> p : MyRelationToGame) {
            if(relation.equals(p.first)) {
                return p.second;
            }
        }
        return InboundMessage.NO_STATE;
    }

    private static int getGameId(String line, String tag) {
        int res = -1;
        int j;
        try {
            if ((j = line.indexOf(tag)) > 0) {
                String s = line.substring(j + tag.length());
                Pattern p = Pattern.compile("^(\\d+)");
                Matcher m = p.matcher(s);
                if(m.find()) {
                    res = Integer.valueOf(m.group(1));
                }
//                res = Integer.valueOf(line.substring(j + tag.length()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res;
    }
}
