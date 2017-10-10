package com.ab.pgn;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;

/**
 * test various moves
 * Created by Alexander Bootman on 8/27/16.
 */
public class MoveValidationTest extends BaseTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testPawn() throws Config.PGNException {
        String fen = "r1bqkbnr/pPp4p/4pp2/3pP3/6p1/5P2/P1PP2PP/RNBQKBNR w KQkq d6 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("e5xd6", Config.INIT_POSITION_FLAGS | Config.FLAGS_BLACK_MOVE | Config.FLAGS_ENPASSANT),
                new Pair<>("e5xf6", Config.INIT_POSITION_FLAGS | Config.FLAGS_BLACK_MOVE),
                new Pair<>("h2h4", Config.INIT_POSITION_FLAGS | Config.FLAGS_ENPASSANT_OK | Config.FLAGS_BLACK_MOVE),
                new Pair<>("h2h3", Config.INIT_POSITION_FLAGS | Config.FLAGS_BLACK_MOVE),
                new Pair<>("b7b8", Config.INIT_POSITION_FLAGS | Config.FLAGS_BLACK_MOVE | Config.FLAGS_PROMOTION),
                new Pair<>("b7xc8", Config.INIT_POSITION_FLAGS | Config.FLAGS_BLACK_MOVE | Config.FLAGS_PROMOTION),
                new Pair<>("f3f4", Config.INIT_POSITION_FLAGS | Config.FLAGS_BLACK_MOVE),
                new Pair<>("f2f4", ERR),
                new Pair<>("f2xe3", ERR),
                new Pair<>("f3f5", ERR),
                new Pair<>("g2xf3", ERR),
                new Pair<>("g2g4", ERR),
                new Pair<>("g2h3", ERR),
        };
        testMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testKing() throws Config.PGNException {
        String fen = "r3k2r/8/8/1b3b2/8/8/5n3/R3K2R w KQkq - 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("o-o-o", Config.FLAGS_CASTLE | Config.FLAGS_B_QUEEN_OK | Config.FLAGS_B_KING_OK | Config.FLAGS_BLACK_MOVE),
                new Pair<>("0-0-0", Config.FLAGS_CASTLE | Config.FLAGS_B_QUEEN_OK | Config.FLAGS_B_KING_OK | Config.FLAGS_BLACK_MOVE),
                new Pair<>("Ke1xf2", Config.FLAGS_B_QUEEN_OK | Config.FLAGS_B_KING_OK | Config.FLAGS_BLACK_MOVE),
                new Pair<>("Ke1d2", Config.FLAGS_B_QUEEN_OK | Config.FLAGS_B_KING_OK | Config.FLAGS_BLACK_MOVE),
                new Pair<>("Ke1e2", ERR),
                new Pair<>("o-o", ERR),
                new Pair<>("0-0", ERR),
                new Pair<>("Ke1g3", ERR),
        };
        testMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRook() throws Config.PGNException {
        String fen = "8/4r3/8/4Q3/8/8/2R1r1k1/1K6 b - - 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("Re7xe5", 0),
                new Pair<>("Re7b7+", Config.FLAGS_CHECK),
                new Pair<>("Re2e1+", ERR),
        };
        testMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testRook1() throws Config.PGNException {
        String fen = "r1b1kb1r/5ppp/p2qpn2/1p6/8/3B1N2/PPP2PPP/R1BQ1RK1 b kq - 0 4";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("Ra8b8", Config.FLAGS_B_KING_OK),
        };
        testMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMate() throws Config.PGNException {
        String fen = "rnbqkbnr/ppp2ppp/8/8/8/8/PPPPP2P/RNBQKBNR b KQkq - 1 1";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("Qd8h4#", Config.INIT_POSITION_FLAGS | Config.FLAGS_CHECKMATE),
        };
        testMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMateDoubleCheck() throws Config.PGNException {
        String fen = "r2qkbnr/pP1ppNpp/8/4P2Q/1p6/8/P1PP2pP/RNB1K2R w KQkq - 99 6";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("Nf7d6#", Config.INIT_POSITION_FLAGS | Config.FLAGS_BLACK_MOVE | Config.FLAGS_CHECKMATE),
        };
        testMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMateEnpass() throws Config.PGNException {
        String fen = "7r/8/Q2R4/6pk/7p/4PP2/3K2P1/7R w - - 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("g2g4#", Config.FLAGS_ENPASSANT_OK | Config.FLAGS_BLACK_MOVE | Config.FLAGS_CHECKMATE),
        };
        testMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMateCastle() throws Config.PGNException {
        String fen = "r1rkr3/2p1p3/8/8/8/8/8/R3K3 w Q - 0 4";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("0-0-0#", Config.FLAGS_BLACK_MOVE | Config.FLAGS_CASTLE | Config.FLAGS_CHECKMATE),
        };
        testMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCheck() throws Config.PGNException {
        String fen = "r2qkbnr/pP1ppNpp/8/4P3/1p6/8/P1PP2pP/RNBQK2R w KQkq - 99 6";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("Nf7d6+", Config.INIT_POSITION_FLAGS | Config.FLAGS_CHECK | Config.FLAGS_BLACK_MOVE),
        };
        testMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCheck1() throws Config.PGNException {
        String fen = "7r/8/Q2R4/6pk/7p/4PP2/3K2PB/7R w - - 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("g2g4+", Config.FLAGS_ENPASSANT_OK | Config.FLAGS_CHECK | Config.FLAGS_BLACK_MOVE),
        };
        testMoves(fen, moves);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCheck2() throws Config.PGNException {
        String fen = "r2kr3/2p1p3/8/8/8/8/8/R3K3 w Q - 0 4";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("0-0-0+", Config.FLAGS_CASTLE | Config.FLAGS_CHECK | Config.FLAGS_BLACK_MOVE),
        };
        testMoves(fen, moves);
    }

    @SuppressWarnings("unchecked")
    private void testMove(String moveText, Pair<String, Integer>[] positions) throws Config.PGNException {
        for (Pair<String, Integer> position : positions) {
            String fen = position.first;
            Pair<String, Integer>[] moves = new Pair[] {new Pair<>(moveText, position.second)};
            testMoves(fen, moves);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testQCastle() throws Config.PGNException {
        final String move = "o-o-o";    // o-o-o
        final Pair<String, Integer>[] fens = new Pair[]{
                new Pair<>("r3k2r/8/8/5b2/8/8/8/R3K2R w KQkq - 0 1", Config.FLAGS_CASTLE | Config.FLAGS_B_KING_OK | Config.FLAGS_B_QUEEN_OK | Config.FLAGS_BLACK_MOVE),
                new Pair<>("r3k2r/8/8/8/6b1/8/8/R3K2R w KQkq - 0 1", ERR),
                new Pair<>("r3k2r/8/8/8/7b/8/8/R3K2R w KQkq - 0 1", ERR),
                new Pair<>("r3k2r/8/8/8/5b2/8/8/R3K2R w KQkq - 0 1", ERR),
        };
        testMove(move, fens);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testKnight1() throws Config.PGNException {
        String fen = "7k/N7/8/4N3/8/8/8/1K6 w - - 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("Na7c6", Config.FLAGS_X_AMBIG | Config.FLAGS_BLACK_MOVE),
                new Pair<>("Nd4c6", Config.FLAGS_X_AMBIG | Config.FLAGS_BLACK_MOVE),
        };
        testMoves(fen, moves);

        Pair<Integer, Integer> p;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testKnight2() throws Config.PGNException {
        String fen = "7k/N3N3/8/4N3/8/8/8/1K6 w - - 0 1";
        final Pair<String, Integer>[] moves = new Pair[] {
                new Pair<>("Na7c6", Config.FLAGS_X_AMBIG | Config.FLAGS_BLACK_MOVE),
                new Pair<>("Nd4c6", Config.FLAGS_X_AMBIG | Config.FLAGS_BLACK_MOVE),
                new Pair<>("Ne7c6", Config.FLAGS_AMBIG | Config.FLAGS_BLACK_MOVE),
        };
        testMoves(fen, moves);

        Pair<Integer, Integer> p;
    }

    // todo: bishop, queen
}
