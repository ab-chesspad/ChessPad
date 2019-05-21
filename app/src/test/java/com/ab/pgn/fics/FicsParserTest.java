package com.ab.pgn.fics;

import com.ab.pgn.fics.chat.FicsParser;
import com.ab.pgn.fics.chat.InboundMessage;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class FicsParserTest {
    FicsParser ficsParser = new FicsParser();
    InboundMessage.G1Game currentGame = new InboundMessage.G1Game();

    @Test
    public void testParseGameInfo() {
        String msg =
            "1 (Exam.    0 Plummer        0 McCartney ) [ uu  0   0] B:  1\n" +
            " 22 (Exam.    0 EvertonSono    0 puzzlebot ) [ uu  0   0] B:  4\n" +
            " 34 (Exam.    0 LectureNova    0 LectureNov) [ uu  0   0] W:  1\n" +
            " 46 (Exam.    0 GuestHDWB      0 Karpovsky ) [ bu  5   0] W: 30\n" +
            "  7 (Exam. 1093 Dududornele    0 cavalodada) [ sr 60  30] W: 65\n" +
            " 33 (Exam.    0 AidenLi     1339 AllySK    ) [ uu  0   0] W:  9\n" +
            " 35 (Exam. 1800 LoganFive   1800 LoganFive ) [ uu  0   0] W:  1\n" +
            "114 (Exam. 2319 cviht       2642 NilsSKBB  ) [ Br  2   0] W:  1\n" +
            "  3 ++++ syakila     ++++ Paspartu   [ su 30   0]  29:25 - 28:41 (37-36) W:  9\n" +
            "  4 ++++ Karpovsky   ++++ Makaski    [ bu  5   0]   4:42 -  4:13 (36-36) B: 11\n" +
            "  6 ++++ GuestDFQY   ++++ GuestBCMG  [ bu 10   5]   9:29 -  9:17 (38-38) W: 15\n" +
            "  9 ++++ Theraposabl ++++ GuestXZVG  [ bu 10   0]   0:10 -  1:55 (37-37) B: 26\n" +
            " 15 ++++ GuestYBHH   ++++ saresu     [ bu  5   0]   5:00 -  5:00 (39-39) B:  1\n" +
            "100 ++++ guestmms    1537 papernoose [pbu  5   0]   4:42 -  4:20 (38-38) W: 11\n" +
            " 32 1007 OlympicTurt 1104 Talmerge   [ br  4   3]   4:00 -  4:00 (39-39) W:  1\n" +
            " 14 1239 inemuri     1486 gojets     [plr  2   0]   1:59 -  1:52 (39-39) B:  8\n" +
            " 84 1434 oneparadox  1293 JustMicgo  [ br  5   3]   4:36 -  3:53 (31-31) B: 16\n" +
            " 95 1125 donifirda   1655 jefersonlt [ sr 30   0]  30:00 - 30:00 (39-39) B:  1\n" +
            " 16 ++++ GuestTZBS   ++++ GuestHDBF  [ bu  5   0]   3:56 -  3:40 (32-32) B: 16\n"+
            "194 2358 IMAablingTh 2414 FMOlsen    [ su120   0]  20:16 - 40:00 ( 6- 6) W: 55\n" +
            "\n" +
            "  114 games displayed.";

        InboundMessage.Info inboundMessage = ficsParser.parse(msg, currentGame);
        Assert.assertNotNull(inboundMessage);
        System.out.println(inboundMessage.toString());
/*

        Assert.assertTrue(chatEvent.getType() == ChatType.GAMES);
        Assert.assertEquals(20, chatEvent.getGameInfos().size());
        for(GameInfo gameInfo : chatEvent.getGameInfos()) {
            System.out.println(gameInfo.toString());
        }
*/
    }

    @Test
    public void testSought() {
        String msg =
            "18 2311 MrsLurKing(C)      30   0 rated   standard               0-9999 mf\n" +
            " 23 ++++ pehee               2  12 unrated blitz                  1-9999 \n" +
            " 24 2311 MrsLurKing(C)      15   0 rated   standard               0-9999 mf\n" +
            " 30 ++++ Dimmsdale          15  20 unrated standard               0-9999 m\n" +
            " 34 2333 Bliep(C)           20   0 rated   standard               0-9999 mf\n" +
            " 46 2730 exeComp(C)          2  12 rated   blitz                  0-9999 \n" +
            " 61 1976 chesspickle(C)      5   0 rated   blitz                  0-9999 \n" +
            " 82 2294 Knightsmasher(C)    3   0 rated   blitz                  0-9999 f\n" +
            " 84 2368 Knightsmasher(C)   15   0 rated   standard               0-9999 f\n" +
            " 96 2601 exeComp(C)         15   5 rated   standard               0-9999 \n" +
            "103 1057 adriandekock        5   1 rated   blitz                  0-9999 \n" +
            "113 ++++ GuestWRCY           1   5 unrated blitz      [white]     0-9999 \n" +
            "116 1668 Beeboop             3   0 rated   blitz                  0-9999 \n" +
            "121 ++++ GuestJXFS           2   5 unrated blitz                  0-9999 \n" +
            "124 2354 Sillycon(C)        15   0 rated   standard               0-9999 f\n" +
            "125 1445 CALBOT              5   0 rated   blitz                  0-9999 \n" +
            "16 ads displayed.";

        InboundMessage.Info inboundMessage = ficsParser.parse(msg, currentGame);
        Assert.assertNotNull(inboundMessage);
        System.out.println(inboundMessage.toString());
/*
        ChatEvent chatEvent = FicsParser.parse(msg);
        Assert.assertNotNull(chatEvent);
        Assert.assertTrue(chatEvent.getType() == ChatType.SEEKS);
        Assert.assertEquals(16, chatEvent.getGameInfos().size());
        for(GameInfo gameInfo : chatEvent.getGameInfos()) {
            System.out.println(gameInfo.toString());
        }
*/
    }

    @Test
    public void testG1() {
        String msg = "<g1> 111 p=0 t=blitz r=1 u=0,0 it=180,0 i=180,0 pt=0 rt=1978,1779 ts=1,1 m=2 n=1";
        InboundMessage.Info inboundMessage = ficsParser.parse(msg, currentGame);
        Assert.assertNotNull(inboundMessage);
        System.out.println(inboundMessage.toString());
    }

    @Test
    @Ignore("test cases must contail <g1> and <12> with move = 'none'")
    public void testS12() {
        String[] msg = {
//            "<g1> 100 p=0 t=untimed r=0 u=0,0 it=0,0 i=0,0 pt=0 rt=0,0 ts=0,0 m=0 n=0"
//            + "\n" +
            "<12> rnbqkbnr pppppppp -------- -------- ----P--- -------- PPPP-PPP RNBQKBNR B 4 1 1 1 1 0 100 guestBLARG guestcday 1 10 0 39 39 600 600 1 P/e2-e4 (0:00) e4 1 0 0",
            "<12> ----q--k ------pp --p----Q -P-p---- -------- --R-P-P- r------P --R---K- B -1 0 0 0 0 0 34 blore GrandLapin 0 15 0 23 18 346972 461280 36 Q/h3-h6 (2:27.278) Qxh6 0 1 813",
            "<12> ----q--k -------p --p----p -P-p---- -------- --R-P-P- r------P --R---K- W -1 0 0 0 0 0 34 blore GrandLapin 0 15 0 14 18 346972 456856 37 P/g7-h6 (0:04.424) gxh6 0 1 57",
            "<12> ----q--k -------p --P----p ---p---- -------- --R-P-P- r------P --R---K- B -1 0 0 0 0 0 34 blore GrandLapin 0 15 0 14 17 345380 456856 37 P/b5-c6 (0:01.592) bxc6 0 1 327",
            "<12> q--r---k -bRQp--p p--pBbp- -p-P---- -P--PP-- ----B--- P-----PP ------K- W -1 0 0 0 0 0 99 abfics puzzlebot -2 0 0 0 0 0 0 1 none (0:00.000) none 0 0 0",
        };
        for(String s12 : msg) {
            InboundMessage.Info inboundMessage = ficsParser.parse(s12, currentGame);
            Assert.assertNotNull(inboundMessage);
            System.out.println(inboundMessage.toString());
        }
    }
}
