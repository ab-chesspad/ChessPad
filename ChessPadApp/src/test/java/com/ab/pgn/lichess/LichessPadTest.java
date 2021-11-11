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

public class LichessPadTest extends BaseTest {
    private static final String PUZZLE_MARK = "lichess.puzzle = ";
    private static String
        USER = "ab-test",
/*
        PASSWORD = "alex50";
/*/
        PASSWORD = null;
//*/
    private final PgnLogger logger = PgnLogger.getLogger(this.getClass(), true);
    private final Object lock = new Object();

    private LichessPad lichessPad;

    @Before
    public void _init() {
        lichessPad = new LichessPad(new LichessPad.LichessMessageConsumer() {
            @Override
            public void consume(LichessPad.LichessMessage message) {
                if (message instanceof LichessPad.LichessMessageLoginOk) {
                    logger.debug("Lichess login ok");
                } else if (message instanceof LichessPad.LichessMessagePuzzle) {
                    PgnGraph puzzle = lichessPad.getPuzzle();
                    logger.debug(String.format("%s\n%s", puzzle.getInitBoard().toString(), puzzle.toPgn()));
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
        testParser(17370);
        testParser(76856);
    }

    private void testParser(int jsonNum) throws Config.PGNException {
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
        }
        lichessPad.getPuzzle();
        // async result:
        _wait();
    }

    @Test
    @Ignore
    public void testLoadPuzzleNum() throws IOException, Config.PGNException {
        final int[] puzzleNums = {17298, 17370, 49581};
        LichessClient lichessClient = new LichessClient();
        for (int puzzleNum : puzzleNums) {
            String html = lichessClient.getPuzzle(puzzleNum);
            int i = html.indexOf(PUZZLE_MARK);
            if (i > 0) {
                html = html.substring(i + PUZZLE_MARK.length());
            }
            lichessPad.parse(html);
            PgnGraph pgnGraph = lichessPad.getPuzzle();
            System.out.println(pgnGraph.toPgn());
        }
    }

 /*   @Test
    @Ignore("does not work")
    public void testLoadPuzzleBatch() throws IOException {
        String res;
        String html = lichessPad.lichessClient.getPuzzle();
        logger.debug(html);
        res = lichessPad.fetchPuzzleBatch();
        logger.debug(res);
    }
*/
    private void _wait() {
        synchronized (lock) {
            try {
                lock.wait(50000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
