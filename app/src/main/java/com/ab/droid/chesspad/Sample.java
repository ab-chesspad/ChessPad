package com.ab.droid.chesspad;

import com.ab.pgn.PgnItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * sample
 * Created by Alexander Bootman on 8/28/16.
 */
public class Sample {
    private ChessPad chessPad;

    public Sample(ChessPad chessPad) {
        this.chessPad = chessPad;
    }

    public void createPgnTest() {
        try {
            // 8/pp6/5qpp/1Q2Np1k/5P2/P5PK/1Pr4P/8 b - - 0 1
            String pgn =
                "[White \"white\\\\repetition, promotion\"]\n" +
                "[Black \"black \\\"black\\\"\"]\n" +
                "[FEN \"r3kbnr/pPp2p1p/4p3/3pP3/8/5P2/P1PP2pP/RNBQK2R w KQkq d6 92 3\"]\n" +
                "1. Nc3 Bd6 2. Nb1 Bf8 3. Nc3 Bd6 4. Nb1 {Sample to test ChessPad. 4. ... Bf8 creates 50 moves situation. 5. Nc3 creates 3-fold repetition.}" +
                "\n" +
                "\n" +

                "[White \"white\\\\spec chars, checkmate\"]\n" +
                "[Black \"black \\\"black\\\"\"]\n" +
                "[Bla\\\"bla \"\\\"bla\\\\bla\\\"bla\\\"\"]\n"+
                "[FEN \"r2qkbnr/pP1pp2p/8/4P3/1p6/8/P1PP2pP/RNBQK2R w KQkq - 99 6\"]\n" +
                "\n" +

                "[White \"N. Dekhanov\"]\n" +
                "[Black \"H. Yusupov\"]\n" +
                "[Date \"1981.??.??\"]\n" +
                "[FEN \"8/pp6/5qpp/1Q2Np1k/5P2/P5PK/1Pr4P/8 b - - 0 1\"]\n" +
                "\n" +

                "[Event \"Amsterdam\"]\n" +
                "[Site \"?\"]\n" +
                "[Date \"1950.??.??\"]\n" +
                "[Round \"?\"]\n" +
                "[White \"Pilnik\"]\n" +
                "[Black \"Kramer\"]\n" +
                "[Result \"1-0\"]\n" +
                "[Annotator \"01: Active Bishop\"]\n" +
                "[SetUp \"1\"]\n" +
                "[FEN \"r1bq1rk1/4bppp/p1n2n2/1pppp3/4P3/2PP1N2/PPB2PPP/R1BQRNK1 w - - 0 1\"]\n" +
                "[PlyCount \"27\"]\n" +
                "[Source \"Hays Publishing\"]\n" +
                "[SourceDate \"1964.01.01\"]\n" +
                "[Final \"4nk2/1b3pp1/p2b4/1p6/2p4B/2P5/PPB2PPP/5NK1 b - - 1 14\"]" +
                "\n" +
                "{An active bishop obtained at the cost of a backward pawn  With his last move .\n" +
                ".. Pd5, Black has taken the initiative in the centre, and now threatens either\n" +
                "to gain space and freedom by ... Pd4 or else release tension by ... Pdxe4.  \n" +
                " Although after exchanges on e4 Black will have a weak point at d5, which\n" +
                "White may exploit by Ne3-d5, but under the circumstances this is less\n" +
                "important} 1. exd5 (1. -- dxe4 2. dxe4 -- 3. Bb3 3... c4 {\n" +
                "White's bishop isn't active}) 1... Qxd5 2. Qe2 2... Bb7 3. Bg5 $1 (3. Nxe5 Nxe5\n" +
                "4. Qxe5 Qxg2#) (3. Bb3 {threat to win the pawn} 3... Qd7 4. Nxe5 Nxe5 5. Qxe5\n" +
                "5... Bd6 $44 {a strong attack}) 3... Rfe8 4. Bh4 {\n" +
                "threatening to win the e-pawn with Bg3 as well as making room for Ng5 with Bb3}\n" +
                "4... Rad8 {Apparently Black still stands very well. His pressure on the d-pawn\n" +
                "seems to condemn the Bishop to passivity} 5. Bb3 $3 {\n" +
                "This deep moves demonstrates otherwise} (5. Red1 {\n" +
                "relieves Black's chief worry of protecting the e-pawn}) (5. Rad1 Qxa2) 5...\n" +
                "Qxd3 6. Qxd3 Rxd3 7. Nxe5 Nxe5 8. Rxe5 8... c4 $2 {The Black pieces are\n" +
                "awkardly ties up, while the White ones have developed great activity. With the\n" +
                "text move Black hopes to persuade the dangerous bishop to be more modest, but\n" +
                "he doesn't succeed} (8... Bd6 $2 {the point of Bb3} 9. Rxe8+ Nxe8 10. Bc2 Rd5\n" +
                "11. Be4 11... Rh5 12. Bxb7 Rxh4 13. Bxa6 $16 {winning ending}) (8... Rdd8 $1 {\n" +
                "best defence} 9. Rae1 Kf8) 9. Bc2 Rdd8 (9... Rd7 10. Rae1 Nd5 11. Bf5 Rc7 12.\n" +
                "Bg3 $18 {Rippis\n" +
                "}) 10. Rae1 10... Kf8 11. Bxh7 $1 11... Bd6 (11... Nxh7 12.\n" +
                "Bxe7+ $16) (11... g6 12. Bg5 12... Ng8 13. Bc1 (13. Bxg8 Bxg5 14. Rxe8+ Rxe8\n" +
                "15. Rxe8+ 15... Kxe8 $19) 13... -- {there is no avoiding Bxg8.\n" +
                "} (13... Nf6\n" +
                "14. Bh6#) 14. Bxg8) 12. Rxe8+ Rxe8 13. Rxe8+ Nxe8 14. Bc2 $18 1-0\n"+
                "\n" +

                "[Event \"Lloyds Bank op\"]\n" +
                "[Site \"London\"]\n" +
                "[Date \"1984.??.??\"]\n" +
                "[Round \"1\"]\n" +
                "[White \"Adams, Michael\"]\n" +
                "[Black \"Sedgwick, David\"]\n" +
                "[Result \"1-0\"]\n" +
                "[WhiteElo \"\"]\n" +
                "[BlackElo \"\"]\n" +
                "[ECO \"C05\"]\n" +
                "\n" +
                "1.e4 e6 2.d4 d5 3.Nd2 Nf6 4.e5 Nfd7 5.f4 c5 6.c3 Nc6 7.Ndf3 cxd4 8.cxd4 f6\n" +
                "9.Bd3 Bb4+ 10.Bd2 Qb6 11.Ne2 fxe5 12.fxe5 O-O 13.a3 Be7 14.Qc2 Rxf3 15.gxf3 Nxd4\n" +
                "16.Nxd4 Qxd4 17.O-O-O Nxe5 18.Bxh7+ Kh8 19.Kb1 Qh4 20.Bc3 Bf6 21.f4 Nc4 22.Bxf6 Qxf6\n" +
                "23.Bd3 b5 24.Qe2 Bd7 25.Rhg1 Be8 26.Rde1 Bf7 27.Rg3 Rc8 28.Reg1 Nd6 29.Rxg7 Nf5\n" +
                "30.R7g5 Rc7 31.Bxf5 exf5 32.Rh5+  1-0\n" +

                "[Event \"Lloyds Bank op\"]\n" +
                "[Site \"London\"]\n" +
                "[Date \"1984.??.??\"]\n" +
                "[Round \"3\"]\n" +
                "[White \"Adams, Michael\"]\n" +
                "[Black \"Dickenson, Neil F\"]\n" +
                "[Result \"1-0\"]\n" +
                "[WhiteElo \"\"]\n" +
                "[BlackElo \"2230\"]\n" +
                "[ECO \"C07\"]\n" +
                "\n" +
                "1.e4 e6 2.d4 d5 3.Nd2 c5 4.exd5 Qxd5 5.Ngf3 cxd4 6.Bc4 Qd6 7.O-O Nf6 8.Nb3 Nc6\n" +
                "9.Nbxd4 Nxd4 10.Nxd4 a6 11.Nf3 b5 12.Bd3 Bb7 13.a4 Ng4 14.Re1 Qb6 15.Qe2 Bc5\n" +
                "16.Rf1 b4 17.h3 Nf6 18.Bg5 Nh5 19.Be3 Bxe3 20.Qxe3 Qxe3 21.fxe3 Ng3 22.Rfe1 Ne4\n" +
                "23.Ne5 Nc5 24.Bc4 Ke7 25.a5 Rhd8 26.Red1 Rac8 27.b3 Rc7 28.Rxd8 Kxd8 29.Nd3 Nxd3\n" +
                "30.Bxd3 Rc5 31.Ra4 Kc7 32.Kf2 g6 33.g4 Bc6 34.Rxb4 Rxa5 35.Rf4 f5 36.g5 Rd5\n" +
                "37.Rh4 Rd7 38.Bxa6 Rd2+ 39.Ke1 Rxc2 40.Rxh7+ Kd6 41.Bc4 Bd5 42.Rg7 Rh2 43.Rxg6 Rxh3\n" +
                "44.Kd2 Rg3 45.Rg8 Bxc4 46.bxc4 Kc5 47.g6 Kd6 48.c5+ Kc7 49.g7 Kb7 50.c6+  1-0\n" +

                "[Event \"Lloyds Bank op\"]\n" +
                "[Site \"London\"]\n" +
                "[Date \"1984.??.??\"]\n" +
                "[Round \"4\"]\n" +
                "[White \"Hebden, Mark\"]\n" +
                "[Black \"Adams, Michael\"]\n" +
                "[Result \"1-0\"]\n" +
                "[WhiteElo \"2480\"]\n" +
                "[BlackElo \"\"]\n" +
                "[ECO \"B10\"]\n" +
                "\n" +
                "1.e4 c6 2.c4 d5 3.exd5 cxd5 4.cxd5 Nf6 5.Nc3 g6 6.Bc4 Bg7 7.Nf3 O-O 8.O-O Nbd7\n" +
                "9.d3 Nb6 10.Qb3 Bf5 11.Re1 h6 12.a4 Nfd7 13.Be3 a5 14.Nd4 Nxc4 15.dxc4 Nc5\n" +
                "16.Qa3 Nd3 17.Nxf5 gxf5 18.Red1 Ne5 19.b3 Ng4 20.Qc1 f4 21.Bd4 Bxd4 22.Rxd4 e5\n" +
                "23.Rd2 Qh4 24.h3 Nf6 25.Qe1 Qg5 26.Ne4 Nxe4 27.Qxe4 f5 28.Qxe5 Rae8 29.h4 Qxh4\n" +
                "30.Qc3 Re4 31.d6 Qg5 32.f3 Re3 33.Qxa5 Rfe8 34.Rf2 Qf6 35.Rd1 R3e5 36.d7  1-0";

            File f = new File(new File(PgnItem.getRoot(), ChessPad.DEFAULT_DIRECTORY), "sample.pgn");
            FileOutputStream fos = new FileOutputStream(f);
            byte[] buf = pgn.getBytes("UTF-8");
            fos.write(buf, 0, buf.length);
            fos.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
