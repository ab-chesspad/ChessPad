package com.ab.pgn.fics;

import com.ab.pgn.Pair;
import com.ab.pgn.fics.chat.InboundMessage;

import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Alexander Bootman on 10/2/19.
 */
@Ignore("fics software is buggy and seems not too popular")
public class FicsTest extends BaseFicsTest{

    @Test
    @Ignore("Used for initial fics command testing")
    public void testServerRaw() throws IOException {
        final String[] commands = {
//            "games",
//             "tell puzzlebot gm3",
//            "tell puzzlebot gs 01539",
//            "tell puzzlebot solve",
//            "tell puzzlebot stop",
//            "seek unrated manual",
                "sought",
        };
        final int total = 1;
        final int[] count = {0};

        openFicsPad((inboundMessage) -> {
            if(inboundMessage instanceof InboundMessage.G1Game) {
                if(((InboundMessage.G1Game)inboundMessage).isInState(InboundMessage.EXAMINER_STATE)) {
                    testDone = ++count[0] >= total;
                    System.out.println(String.format("examine #%d, ", count[0]));
                }
            }
            if(inboundMessage instanceof InboundMessage.Challenge) {
                System.out.println(inboundMessage.toString());
            } else {
                System.out.println(inboundMessage.toString());
            }
        });

        for (int i = 1; i <= total; ++i) {
            for (String command : commands) {
                send(String.format(command, i));
            }
        }
        waitUntilDone(100000);
    }

    @Test
    public void testGames() throws IOException {

        openFicsPad((inboundMessage) -> {
            if(inboundMessage instanceof InboundMessage.InboundList) {
                testDone = true;
            }
            System.out.println(inboundMessage.toString());
        });

        ficsPad.send(FicsPad.COMMAND_GAMES);

        waitUntilDone(10000);
    }

    @Test
    public void testChallenge() throws IOException {
        final String command = "seek unrated manual";

        openFicsPad((inboundMessage) -> {
            if(inboundMessage instanceof InboundMessage.Challenge) {
                System.out.println(inboundMessage.toString());
                testDone = true;
            }
        });

        send(command);
        waitUntilDone(100000);
    }

    @Test
    public void testSought() throws IOException {
        final String command = "sought";

        openFicsPad((inboundMessage) -> {
            if(inboundMessage instanceof InboundMessage.InboundList) {
                System.out.println(inboundMessage.toString());
                testDone = true;
            }
        });

        send(command);
        waitUntilDone(1000);
    }


    private int puzzleNumber = -1;
    private int moveNumber;
    private String[] moves;
    private String gameId;
    private String botName;

    @Test
    @SuppressWarnings("unchecked")
    public void testBots() throws IOException {
        final Pair<String, String>[] puzzles = new Pair[] {
            new Pair<>("tell puzzlebot gt 01046", "Rb1 Bxd6 Rxb3 Bxd6(backed) f3+ Kxf3 Rxb3+"),    // 1 erroneous move backed
            new Pair<>("tell puzzlebot gm 01653", "Rxg5+ Kxg5 Qh7 Kxg5(backed) Qg7+ Kh5 Qg4#"),    // 1 erroneous move backed
            new Pair<>("tell puzzlebot gm 01653", "Qxh6 (backed) Rxg5+ Kxg5 Qg7+ Kh5 Qg4#"),       // 1 erroneous move backed
            new Pair<>("tell puzzlebot gs 01539", "Be2 g2 Nh5+ Kf5 Bf1 gxf1=N a6"),
            new Pair<>("tell endgamebot play 8/k7/8/8/8/6Q1/8/1K6 --", "Qb3 Ka8 Ka2 Ka7 Ka3 Ka8 Ka4 Ka7 Ka5 Ka8 Kb6 Kb8 Qg8#"),
        };
//        final String finalPuzzleMsg = "puzzlebot stopped examining game ";
        final String finalPuzzleMsg = "You solved problem number ";
        final String finalEndgameMsg = "Thank you for playing endgamebot.";

        allSend = true;     // per puzzle
//        gameId = null;

        openFicsPad((inboundMessage) -> {
            String msg;
            if ((msg = inboundMessage.getMessage()) != null) {
                if("puzzlebot".equals(botName)) {
                    if(msg.startsWith(finalPuzzleMsg)) {
//                            int dot = msg.indexOf(".");
                        String id = msg.substring(finalPuzzleMsg.length(), msg.length() - 1);
                        send(FicsPad.COMMAND_UNEXAMINE);
//                            allSend = id.equals(gameId);
                        allSend = true;
                        gameId = null;
                    }
                } else if (msg.endsWith(finalEndgameMsg)) {    // endgamebot
                    send(FicsPad.COMMAND_UNEXAMINE);
                    allSend = true;
                    gameId = null;
                }
            }

            if (allSend) {
                allSend = false;     // per puzzle
                if (++puzzleNumber >= puzzles.length) {
                    testDone = true;
                    return;
                }
                String command = puzzles[puzzleNumber].first;
                botName = command.split("\\s+")[1];
                moves = puzzles[puzzleNumber].second.split("\\s+");
                moveNumber = -1;
//                    String[] parts = command.split("\\s+");
//                    gameId = parts[parts.length - 1];     // last part
                send(command);
                return;
            }


            if (inboundMessage instanceof InboundMessage.G1Game &&
                    ((InboundMessage.G1Game) inboundMessage).isInState(InboundMessage.EXAMINER_STATE)) {
                gameId = ((InboundMessage.G1Game) inboundMessage).getId();
                if(moveNumber < moves.length) {
                    System.out.println(ficsPad.getPgnGraph().toPgn());
                    ++moveNumber;
                    if ((moveNumber % 2) == 0) {
                        send(moves[moveNumber]);
                    }
                }
            }
            if (inboundMessage instanceof InboundMessage.KibitzMsg) {
                String kibitzer = ((InboundMessage.KibitzMsg)inboundMessage).getKibitzer();
                if(ficsPad.hePlaying(kibitzer)) {
                    System.out.println(String.format("%s kibitzed: %s", kibitzer, inboundMessage.getMessage()));
                }
            }
        });
        waitUntilDone(40000);
        System.out.println(ficsPad.getPgnGraph().toPgn());
        System.out.println("done");
    }

    @Test
    public void testRegex() {
        String msg = "puzzlebot(TD)(----)[101] kibitzes: You solved problem number [01046] in 02m16s";
        Pattern p = Pattern.compile("kibitzes: You solved problem number \\[(\\d+)] ");
        Matcher m = p.matcher(msg);
        if(m.find()) {
            String g1 = m.group(1);
            System.out.println(g1);
        }
    }
}
