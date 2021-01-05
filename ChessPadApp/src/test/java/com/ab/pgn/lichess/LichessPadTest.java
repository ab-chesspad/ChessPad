package com.ab.pgn.lichess;

import com.ab.pgn.BaseTest;
import com.ab.pgn.Config;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.PgnLogger;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LichessPadTest extends BaseTest {
    private static String
        USER = "ab-test",
//*
        PASSWORD = "alex50";
/*/
        PASSWORD = null;
//*/
    private final PgnLogger logger = PgnLogger.getLogger(this.getClass(), true);
    private final Object lock = new Object();

    private LichessPad lichessPad;
//    private TestLichessMessageConsumer testLichessMessageConsumer = new TestLichessMessageConsumer();


    @Before
    public void _init() {
//        testLichessMessageConsumer.setCount(1);
        lichessPad = new LichessPad(new LichessPad.LichessMessageConsumer() {
            @Override
            public void consume(LichessPad.LichessMessage message) {
                if (message instanceof LichessPad.LichessMessageLoginOk) {
                    logger.debug("Lichess login ok");
                } else if (message instanceof LichessPad.LichessMessagePuzzle) {
                    logger.debug("got LichessMessagePuzzle");
//                    lichessPad.fetchPuzzle();

//                    PgnGraph puzzle = lichessPad.getPuzzle();
//                    logger.debug(String.format("%s\n%s", puzzle.getInitBoard().toString(), puzzle.toPgn()));
                }
                synchronized (lock) {
                    lock.notify();
                }
            }

            @Override
            public void error(LichessPad.LichessMessage message) {
                logger.error("error!");
            }
        });
    }

    @Test
    public void testParser() throws Config.PGNException {
        testParser("FOYxoF01");
//        testParser(17370);
//        testParser(76856);
    }

    private void testParser(String jsonNum) throws Config.PGNException {
        String srcPath = String.format("%s%s.json", BaseTest.TEST_ROOT, jsonNum);
        String puzzleJson = null;
        File file = new File(srcPath);
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int len = fis.read(data);
            Assert.assertEquals(data.length, len);
            puzzleJson = new String(data, "UTF-8");
            lichessPad.parse(puzzleJson);
            PgnGraph puzzle = lichessPad.getPuzzle();
            logger.debug(String.format("%s\n%s", puzzle.getInitBoard().toString(), puzzle.toPgn()));
        } catch (IOException e) {
            throw new Config.PGNException(String.format("error '%s': %s", file.getAbsolutePath(), e.getMessage()));
        }
    }

    @Test
    public void testLoadPuzzle() {
        LichessPad.LichessSettings settings = new LichessPad.LichessSettings();
        settings.setUsername(USER);
        settings.setPassword(PASSWORD);
        if (PASSWORD != null) {
            lichessPad.login(settings);
            _wait(5);
        }
        int total = 20;
        PgnGraph pgnGraph;
        for (int i = 0; i < total; ++i) {
            while ((pgnGraph = lichessPad.getPuzzle()) == null) {
                _wait(5);
//                pgnGraph = lichessPad.getPuzzle();
            };
//            logger.debug(String.format("%s", pgnGraph));
            lichessPad.recordResult(pgnGraph, 0);
            _wait();
        }

/*
        Object _lock = new Object();
        for (int i = 0; i < total; ++i) {
            PgnGraph pgnGraph;
            int attempt = 0;
            do {
                synchronized (this) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                logger.debug(String.format("attempt %s", attempt));
                pgnGraph = lichessPad.getPuzzle();
            } while (pgnGraph == null);
            lichessPad.recordResult(pgnGraph, 0);
        }
        for (int i = 0; i < total; ++i) {
            PgnGraph pgnGraph = lichessPad.getPuzzle();;
            lichessPad.recordResult(pgnGraph, 0);
        }
*/
        System.out.println("done");
    }

    @Test
    @Ignore("lichess puzzles are not always available")
    public void testLoadPuzzleNum() throws IOException, Config.PGNException {
        final int[] puzzleNums = {68099, 73759, 113620 /*17298, 17370, 49581*/};
        LichessClient lichessClient = new LichessClient();
        for (int puzzleNum : puzzleNums) {
            String html = lichessClient.getPuzzle("" + puzzleNum);
//            int i = html.indexOf(PUZZLE_MARK);
//            if (i > 0) {
//                html = html.substring(i + PUZZLE_MARK.length());
//            }
            lichessPad.parse(html);
            PgnGraph pgnGraph = lichessPad.getPuzzle();
            System.out.println(pgnGraph.toPgn());
        }
    }

    @Test
    @Ignore("does not work")
    public void testLoadPuzzleBatch() throws IOException {
        String res;
        String html = lichessPad.lichessClient.getPuzzle();
        logger.debug(html);
        res = lichessPad.fetchPuzzleBatch();
        logger.debug(res);
    }

    @Test
    public void testRegex() {
        String src = "{\"data\":{\"game\":{\"id\":\"FOYxoF01\",\"perf\":{\"icon\":\"#\",\"name\":\"Rapid\"},\"rated\":true,\"players\":[{\"userId\":\"kreon129\",\"name\":\"Kreon129 (1736)\",\"color\":\"white\"},{\"userId\":\"fennemar\",\"name\":\"fennemar (1828)\",\"color\":\"black\"}],\"pgn\":\"d4 Nf6 c4 g6 Nc3 d5 cxd5 Nxd5 e4 Nxc3 bxc3 Bg7 g3 c5 Ne2 Nc6 d5 Ne5 Bb2 Nc4\",\"clock\":\"10+0\"},\"puzzle\":{\"id\":\"IKLbb\",\"rating\":1452,\"plays\":31,\"initialPly\":19,\"solution\":[\"d1a4\",\"c8d7\",\"a4c4\"],\"themes\":[\"opening\",\"fork\",\"advantage\",\"short\"]},";
        Pattern p = Pattern.compile("\\{\"game\":\\{\"id\":\"(.*?)\",.*?,\"solution\":\\[\"(.*?)\"],");
        Matcher m = p.matcher(src);
        if (m.find()) {
            String g1 = m.group(1);
            String g2 = m.group(2);
            logger.debug(String.format("%s -> %s", g1, g2));
        }
    }

    @Test
    public void testDateFormat() {
        //         "date: Thu, 31 Dec 2020 01:30:48 GMT",
        SimpleDateFormat gmtDateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
        gmtDateFormat.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
        String date = gmtDateFormat.format(new Date());
        logger.debug(String.format("date = %s", date));
    }

    private void _wait() {
        _wait(20);
    }

    private void _wait(int timeoutSec) {
        synchronized (lock) {
            try {
//                logger.debug("waiting...");
                lock.wait(1000 * timeoutSec);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

/*
    private class TestLichessMessageConsumer implements LichessPad.LichessMessageConsumer {
        private int count = 0;
        private int _count = 0;

        @Override
        public void consume(LichessPad.LichessMessage message) {
            if (message instanceof LichessPad.LichessMessageLoginOk) {
                logger.debug("Lichess login ok");
            } else if (message instanceof LichessPad.LichessMessagePuzzle) {
                lichessPad.fetchPuzzle();
//                PgnGraph puzzle = lichessPad.getPuzzle();
//                logger.debug(String.format("%s\n%s", puzzle.getInitBoard().toString(), puzzle.toPgn()));
            }
            if (++_count >= count) {
                synchronized (lock) {
                    lock.notify();
                }
            }
        }

        @Override
        public void error(LichessPad.LichessMessage message) {
            logger.error("error!");
        }


        public void setCount(int count) {
            this.count = count;
        }
    }
*/

}
