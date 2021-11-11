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

 * test various moves
 * Created by Alexander Bootman on 8/27/16.
 */
package com.ab.pgn;

import org.junit.Test;

public class MoveValidationTest extends BaseTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testPawn_user() throws Config.PGNException {
        String fen = "r1bqkbnr/pPp4p/4pp2/3pP3/6p1/5P2/P1PP2PP/RNBQKBNR w KQkq d6 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
            new Pair<>("e5xd6", Config.INIT_POSITION_FLAGS | Config.FLAGS_CAPTURE | FLAGS_ENPASSANT),
            new Pair<>("e5xf6", Config.INIT_POSITION_FLAGS | Config.FLAGS_CAPTURE),
            new Pair<>("h2h4", Config.INIT_POSITION_FLAGS | Config.FLAGS_ENPASSANT_OK),
            new Pair<>("h2h3", Config.INIT_POSITION_FLAGS),
            new Pair<>("b7b8=B", Config.INIT_POSITION_FLAGS | FLAGS_PROMOTION),
            new Pair<>("b7xc8=R", Config.INIT_POSITION_FLAGS | Config.FLAGS_CAPTURE | FLAGS_PROMOTION),
            new Pair<>("f3f4", Config.INIT_POSITION_FLAGS),
            new Pair<>("e5e4", ERR),
            new Pair<>("f3xe3", ERR),
            new Pair<>("f3f5", ERR),
            new Pair<>("g2xf3", ERR),
            new Pair<>("g2g4", ERR),
            new Pair<>("g2xh3", ERR),
            new Pair<>("b7b8", Config.INIT_POSITION_FLAGS | FLAGS_PROMOTION), // promotion is not verified
            new Pair<>("b7xc8=P", ERR),
            new Pair<>("b7xc8=K", ERR),
            new Pair<>("b7b7", ERR),
        };
        testUserMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPawn_pgn() throws Config.PGNException {
        String fen = "r1bqkbnr/1Pp4p/4pp2/pP1pP3/6p1/5P2/P2P2PP/RNBQ1BNR w KQkq d6 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
            new Pair<>("exd6", Config.INIT_POSITION_FLAGS | Config.FLAGS_CAPTURE | FLAGS_ENPASSANT),
            new Pair<>("exf6", Config.INIT_POSITION_FLAGS | Config.FLAGS_CAPTURE),
            new Pair<>("h4", Config.INIT_POSITION_FLAGS | Config.FLAGS_ENPASSANT_OK),
            new Pair<>("h3", Config.INIT_POSITION_FLAGS),
            new Pair<>("b8=B", Config.INIT_POSITION_FLAGS | FLAGS_PROMOTION),
            new Pair<>("bxc8=R", Config.INIT_POSITION_FLAGS | Config.FLAGS_CAPTURE | FLAGS_PROMOTION),
            new Pair<>("f4", Config.INIT_POSITION_FLAGS),
            new Pair<>("e4", ERR),
            new Pair<>("fxe3", ERR),
            new Pair<>("f3f5", ERR),
            new Pair<>("gxf3", ERR),
            new Pair<>("g4", ERR),
            new Pair<>("gxh3", ERR),
//            new Pair<>("b8", ERR),        // parses into b8=Q, no error
            new Pair<>("bxc8=K", ERR),
            new Pair<>("b7b7", ERR),
            new Pair<>("bxc8=P", ERR),
            new Pair<>("bxa6", ERR),
            new Pair<>("bxc6", ERR),
        };
        testPgnMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testKing() throws Config.PGNException {
//        String fen = "r3k2r/8/8/1b3b2/8/8/5n3/R3K2R w KQkq - 0 1";
        String fen = "r3k2r/8/8/1b3b2/8/6n1/8/R3K2R w KQkq - 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
            new Pair<>("o-o-o", Config.FLAGS_CASTLE | Config.FLAGS_B_QUEEN_OK | Config.FLAGS_B_KING_OK),
            new Pair<>("0-0-0", Config.FLAGS_CASTLE | Config.FLAGS_B_QUEEN_OK | Config.FLAGS_B_KING_OK),
            new Pair<>("Ke1xf2", Config.FLAGS_B_QUEEN_OK | Config.FLAGS_B_KING_OK),
            new Pair<>("Ke1d2", Config.FLAGS_B_QUEEN_OK | Config.FLAGS_B_KING_OK),
            new Pair<>("Ke1e2", ERR),
            new Pair<>("o-o", ERR),
            new Pair<>("0-0", ERR),
            new Pair<>("Ke1g3", ERR),
        };
        testUserMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRook() throws Config.PGNException {
        String fen = "8/4r3/8/4Q3/8/8/2R1r1k1/1K6 b - - 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
            new Pair<>("Re7xe5", Config.FLAGS_CAPTURE | Config.FLAGS_BLACK_MOVE),
            new Pair<>("Re7b7+", Config.FLAGS_CHECK | Config.FLAGS_BLACK_MOVE),
            new Pair<>("Re2e1+", ERR),
            new Pair<>("Re7e7", ERR),
            new Pair<>("Rc2xe2", ERR),
        };
        testUserMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRook_pgn() throws Config.PGNException {
        String fen = "8/4r3/8/4Q3/8/8/2R1r1k1/1K6 b - - 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("Rxe5", Config.FLAGS_CAPTURE | Config.FLAGS_BLACK_MOVE),
        };
        testPgnMoves(fen, moves);
    }
    @Test
    @SuppressWarnings("unchecked")
    public void testRook1() throws Config.PGNException {
        String fen = "r1b1kb1r/5ppp/p2qpn2/1p6/8/3B1N2/PPP2PPP/R1BQ1RK1 b kq - 0 4";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("Ra8b8", Config.FLAGS_B_KING_OK | Config.FLAGS_BLACK_MOVE),
        };
        testUserMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMate() throws Config.PGNException {
        String fen = "rnbqkbnr/ppp2ppp/8/8/8/8/PPPPP2P/RNBQKBNR b KQkq - 1 1";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("Qd8h4#", Config.INIT_POSITION_FLAGS | Config.FLAGS_CHECKMATE | Config.FLAGS_BLACK_MOVE),
        };
        testUserMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMateDoubleCheck() throws Config.PGNException {
        String fen = "r2qkbnr/pP1ppNpp/8/4P2Q/1p6/8/P1PP2pP/RNB1K2R w KQkq - 99 6";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("Nf7d6#", Config.INIT_POSITION_FLAGS | Config.FLAGS_CHECKMATE),
        };
        testUserMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMateEnpass() throws Config.PGNException {
        String fen = "7r/8/Q2R4/6pk/7p/4PP2/3K2P1/7R w - - 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("g2g4#", Config.FLAGS_ENPASSANT_OK | Config.FLAGS_CHECKMATE),
        };
        testUserMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMateEnpass_m() throws Config.PGNException {
        String fen = "r7/8/4R2Q/kp6/p7/2PP4/1P2K3/R7 w - - 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("b2b4#", Config.FLAGS_ENPASSANT_OK | Config.FLAGS_CHECKMATE),
        };
        testUserMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMateCastle() throws Config.PGNException {
        String fen = "r1rkr3/2p1p3/8/8/8/8/8/R3K3 w Q - 0 4";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("0-0-0#", Config.FLAGS_CASTLE | Config.FLAGS_CHECKMATE),
        };
        testUserMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCheck() throws Config.PGNException {
        String fen = "r2qkbnr/pP1ppNpp/8/4P3/1p6/8/P1PP2pP/RNBQK2R w KQkq - 99 6";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("Nf7d6+", Config.INIT_POSITION_FLAGS | Config.FLAGS_CHECK),
        };
        testUserMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCheck1() throws Config.PGNException {
        String fen = "7r/8/Q2R4/6pk/7p/4PP2/3K2PB/7R w - - 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("g2g4+", Config.FLAGS_ENPASSANT_OK | Config.FLAGS_CHECK),
        };
        testUserMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCheck1_m() throws Config.PGNException {
        String fen = "r7/8/4R2Q/kp6/p7/2PP4/BP2K3/R7 w - - 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("b2b4+", Config.FLAGS_ENPASSANT_OK | Config.FLAGS_CHECK),
        };
        testUserMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCheck2() throws Config.PGNException {
        String fen = "r2kr3/2p1p3/8/8/8/8/8/R3K3 w Q - 0 4";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("0-0-0+", Config.FLAGS_CASTLE | Config.FLAGS_CHECK),
        };
        testUserMoves(fen, moves);
    }

    @SuppressWarnings("unchecked")
    private void testMove(String moveText, Pair<String, Integer>[] positions) throws Config.PGNException {
        for (Pair<String, Integer> position : positions) {
            String fen = position.first;
            Pair<String, Integer>[] moves = new Pair[] {new Pair<>(moveText, position.second)};
            testUserMoves(fen, moves);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testQCastle() throws Config.PGNException {
        final String move = "o-o-o";    // o-o-o
        final Pair<String, Integer>[] fens = new Pair[]{
            new Pair<>("r3k2r/8/8/5b2/8/8/8/R3K2R w KQkq - 0 1", Config.FLAGS_CASTLE | Config.FLAGS_B_KING_OK | Config.FLAGS_B_QUEEN_OK),
            new Pair<>("r3k2r/8/8/8/6b1/8/8/R3K2R w KQkq - 0 1", ERR),
            new Pair<>("r3k2r/8/8/8/7b/8/8/R3K2R w KQkq - 0 1", ERR),
            new Pair<>("r3k2r/8/8/5b2/8/8/8/R3K2R w Kkq - 0 1", ERR),
            new Pair<>("r3k2r/8/8/5b2/8/8/8/N3K2R w KQkq - 0 1", ERR),
            new Pair<>("r3k2r/8/8/5b2/8/8/8/RN2K2R w KQkq - 0 1", ERR),
        };
        testMove(move, fens);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testKCastle() throws Config.PGNException {
        final String move = "o-o";    // o-o-o
        final Pair<String, Integer>[] fens = new Pair[]{
            new Pair<>("r3k2r/8/8/4b3/8/8/8/R3K2R w KQkq - 0 1", Config.FLAGS_CASTLE | Config.FLAGS_B_KING_OK | Config.FLAGS_B_QUEEN_OK),
            new Pair<>("r3k2r/8/8/8/3b4/8/8/R3K2R w KQkq - 0 1", ERR),
            new Pair<>("r3k2r/8/8/8/2b5/8/8/R3K2R w KQkq - 0 1", ERR),
            new Pair<>("r3k2r/8/8/1b6/8/8/8/R3K2R w Kkq - 0 1", ERR),
            new Pair<>("r3k2r/8/8/4b3/8/8/8/R3K2R w Qkq - 0 1", ERR),
            new Pair<>("r3k2r/8/8/5b2/8/8/8/R3K1NR w KQkq - 0 1", ERR),
        };
        testMove(move, fens);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testKnight1() throws Config.PGNException {
        String fen = "7k/N7/8/4N3/8/8/8/1K6 w - - 0 1";
        final Pair<String, Integer>[] pgnMoves = new Pair[] {
            new Pair<>("Nac6", Config.FLAGS_X_AMBIG),
            new Pair<>("Nec6", Config.FLAGS_X_AMBIG),
        };
        testPgnMoves(fen, pgnMoves);

        final Pair<String, Integer>[] userMoves = new Pair[] {
            new Pair<>("Na7c6", Config.FLAGS_X_AMBIG),
            new Pair<>("Ne5c6", Config.FLAGS_X_AMBIG),
        };
        testUserMoves(fen, userMoves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testKnight2() throws Config.PGNException {
        String fen = "7k/N3N3/8/4N3/8/8/8/1K6 w - - 0 1";
        final Pair<String, Integer>[] pgnMoves = new Pair[] {
            new Pair<>("Nac6", Config.FLAGS_X_AMBIG),
            new Pair<>("Ne5c6", Config.FLAGS_AMBIG),
            new Pair<>("Ne7c6", Config.FLAGS_AMBIG),
        };
        testPgnMoves(fen, pgnMoves);

        final Pair<String, Integer>[] userMoves = new Pair[] {
            new Pair<>("Na7c6", Config.FLAGS_X_AMBIG),
            new Pair<>("Ne5c6", Config.FLAGS_AMBIG),
            new Pair<>("Ne7c6", Config.FLAGS_AMBIG),
            new Pair<>("Ne1c6", ERR),
        };
        testUserMoves(fen, userMoves);
    }

    // todo: bishop, queen

    @Test
    @SuppressWarnings("unchecked")
    public void testStalemate() throws Config.PGNException {
        final Pair<String, String>[] fenAndMoves = new Pair[]{
            new Pair<>("k7/8/8/8/8/8/8/1Q2K3 w - - 0 1", "Qb1b6"),
            new Pair<>("5bnr/4p1pq/4Qpkr/7p/7P/4P3/PPPP1PP1/RNB1KBNR w - - 0 1", "Rh1h3"),
            new Pair<>("8/8/8/8/3b4/1kNP4/p1P5/K1N3r b - - 0 1", "Kb3a3"),

        };
        for (Pair<String, String> entry : fenAndMoves) {
            Board initBoard = new Board(entry.first);
            testUserMove(initBoard, entry.second, Config.FLAGS_STALEMATE | (initBoard.getFlags() & Config.BLACK));
            Board invertedInitBoard = invert(initBoard);
            String invertedMove = invert(entry.second);
            testUserMove(invertedInitBoard, invertedMove, Config.FLAGS_STALEMATE | (invertedInitBoard.getFlags() & Config.BLACK));
        }
    }
}
