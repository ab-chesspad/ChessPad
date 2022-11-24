/*
     Copyright (C) 2021-2022	Alexander Bootman, alexbootman@gmail.com

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

 * create sample file to show CP features
 * Created by Alexander Bootman on 8/28/16.
*/

package com.ab.droid.chesspad;

import com.ab.droid.chesspad.io.DocFilAx;
import com.ab.pgn.io.FilAx;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class Sample {
    public void createSample(String fileName) {
        String pgn =
            "[White \"Fischer, R\"]\n" +
            "[Black \"Petrosian, T\"]\n" +
            "\n"+
            "1. e4 e6 2. d4 d5 3. Nc3 Nf6 4. Bg5 de4 5. Ne4 Be7 6. Bf6 gf6 7. g3 f5 8. Nc3 Bf6 9. Nge2 Nc6 10. d5 ed5 11. Nd5 Bb2 12. Bg2 O-O 13. O-O Bh8 14. Nef4 Ne5 " +
            "15. Qh5 Ng6 16. Rad1 c6 17. Ne3 Qf6 18. Kh1 Bg7 19. Bh3 Ne7 20. Rd3 Be6 21. Rfd1 Bh6 22. Rd4 Bf4 23. Rf4 Rad8 24. Rd8 Rd8 25. Bf5 Nf5 26. Nf5 Rd5 27. g4 Bf5 28. gf5 h6 29. h3 Kh7 " +
            "30. Qe2 Qe5 31. Qh5 Qf6 32. Qe2 Re5 33. Qd3 Rd5 {34. Qe2 - repetition}\n"+
            "\n" +

            "[White \"white\\\\repetition, promotion\"]\n" +
            "[Black \"black \\\"black\\\"\"]\n" +
            "[FEN \"r3kbnr/pPp2p1p/4p3/3pP3/8/5P2/P1PP2pP/RNBQK2R w KQkq - 92 3\"]\n" +
            "3. Nc3 Bd6 4. Nb1 Bf8 5. Nc3 Bd6 6. Nb1 {Sample to test ChessPad. 6. ... Be7 creates 50 moves situation. 6. ... Bf8 creates 3-fold repetition.}" +
            "\n" +
            "\n" +

            "[White \"white\\\\spec chars, checkmate\"]\n" +
            "[Black \"black \\\"black\\\"\"]\n" +
            "[Bla\\\"bla \"\\\"bla\\\\bla\\\"bla\\\"\"]\n"+
            "[FEN \"r2qkbnr/pP1pp2p/2p6/4P3/1p6/8/P1PPPPpP/RNBQK2R w KQkq - 99 6\"]\n" +
            "6. bxa8=Q" +
            "\n" +

            "[White \"N. Dekhanov\"]\n" +
            "[Black \"H. Yusupov\"]\n" +
            "[Date \"1981.??.??\"]\n" +
            "[FEN \"8/pp6/5qpp/1Q2Np1k/5P2/P5PK/1Pr4P/8 b - - 0 1\"]\n" +
            "1.Qa6 {Blunder, forced checkmate in 4}" +
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
            "{An active bishop obtained at the cost of a backward pawn  With his last move ." +
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

            "[White \"stalemate\"]\n" +
            "[Black \"after Rh3\"]\n" +
            "[FEN \"5bnr/4p1pq/4Qpkr/7p/7P/4P3/PPPP1PP1/RNB1KBNR w - - 0 1\"]\n" +
            "\n" +
            "Rh3" +
            "\n" +

            "[White \"Same position\"]\n" +
            "[Black \"after different moves\"]\n" +
            "1.e4 e5 2.Nf3 {main} (2.Bc4 {v1} Nc6 {v1} 3.Nf3 {v1} Nf6 {v1} $4 4.c3{v1} $7) (2.Bc4 {v2} Nf6 {v2} 3.Nf3 {v2} Nc6 {v2} $20 4.c3{v2} $21) 2. ... Nc6 {main} 3.Bc4{main} Nf6{main} $5 4.c3{main}\n" +
            "\n" +
            ""
            ;

        FilAx sample = new DocFilAx(fileName);
        try (
            OutputStream os = sample.getOutputStream()) {
            byte[] buf = pgn.getBytes(StandardCharsets.UTF_8);
            os.write(buf, 0, buf.length);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
