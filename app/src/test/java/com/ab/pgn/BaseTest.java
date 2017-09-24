package com.ab.pgn;

import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;

/**
 * base class for all test classes
 * Created by Alexander Bootman on 8/7/16.
 */
public class BaseTest {
    /* comment for AndroidStudio prior to 2.3.3?
    public static final String TEST_ROOT = "../etc/test/";
    public static final String TEST_TMP_ROOT = "../etc/test_tmp/";
    /*/
    public static final String TEST_ROOT = "etc/test/";
    public static final String TEST_TMP_ROOT = "etc/test_tmp/";
    //*/

    public static final String MY_HEADER = "Final";
    public static final int ERR = -1;

    final PgnLogger logger = PgnLogger.getLogger(this.getClass(), true);

    @BeforeClass
    public static void init() {
//        Config.initLogger(Level.FATAL);
        PgnItem.setRoot(new File(TEST_ROOT));
        File tmpTest = new File(TEST_TMP_ROOT);
        deleteDirectory(tmpTest);
        tmpTest.mkdirs();
    }

    @After
    public void restore() {
        PgnItem.setRoot(new File(TEST_ROOT));
    }

    public static boolean deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (null != files) {
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isDirectory()) {
                        deleteDirectory(files[i]);
                    } else {
                        files[i].delete();
                    }
                }
            }
        }
        return (directory.delete());
    }

    public void copyDirectory(File sourceLocation, File targetLocation)
            throws IOException {
        if (sourceLocation.isDirectory()) {
            if (!targetLocation.exists()) {
                targetLocation.mkdir();
            }
            String[] children = sourceLocation.list();
            for (int i = 0; i < sourceLocation.listFiles().length; i++) {
                copyDirectory(new File(sourceLocation, children[i]),
                        new File(targetLocation, children[i]));
            }
        } else {
            InputStream in = new FileInputStream(sourceLocation);
            OutputStream out = new FileOutputStream(targetLocation);
            // Copy the bits from instream to outstream
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        }
    }

    public boolean areEqual(Move m1, Move m2) {
        if(m1 == null && m2 == null) {
            return true;
        } else if(m1 == null || m2 == null) {
            return false;
        }
        if(m1.prevMove != null || m2.prevMove != null) {
            if (!checkPrevMove(m1)) {
                return false;
            }
            if (!checkPrevMove(m2)) {
                return false;
            }
        }

        if((m1.moveFlags & Config.FLAGS_NULL_MOVE) != 0 || (m2.moveFlags & Config.FLAGS_NULL_MOVE) != 0) {
            return (m1.moveFlags & Config.FLAGS_NULL_MOVE) == (m2.moveFlags & Config.FLAGS_NULL_MOVE);
        }

        if((m1.moveFlags & Config.FLAGS_NULL_MOVE) != (m2.moveFlags & Config.FLAGS_NULL_MOVE)) {
            return false;
        }
        if(m1.piece != m2.piece) {
            return false;
        }
        if(m1.pieceTaken != m2.pieceTaken) {
            return false;
        }
        if(m1.moveFlags != m2.moveFlags) {
            return false;
        }
        if(m1.glyph != m2.glyph) {
            return false;
        }
        if(!m1.pack.equals(m2.pack)) {
            return false;
        }
        if(m1.comment == null || m2.comment == null) {
            if(m1.comment != null || m2.comment != null) {
                return false;
            }
        } else if(!m1.comment.equals(m2.comment)) {
            return false;
        }
        if(!m1.equals(m2)) {
            return false;
        }
        if(!areEqual(m1.nextMove, m2.nextMove)) {
            return false;
        }
        if(!areEqual(m1.variation, m2.variation)) {
            return false;
        }
        return true;
    }

    private boolean checkPrevMove(Move m) {
        if(m.prevMove == null) {
            return false;
        }
        Move sibling = m.prevMove.nextMove;
        while(sibling != null) {
            if(sibling == m) {
                return true;
            }
            sibling = sibling.variation;
        }
        return false;
    }

    public String finalFen2Header(String fen) {
        return String.format("[%s \"%s\"]\n", MY_HEADER, fen);
    }

    public List<PgnTree> parse(String pgn) throws IOException {
        List<PgnTree> res = new LinkedList<>();
        BufferedReader br = new BufferedReader(new StringReader(pgn));
        final List<PgnItem> items = new LinkedList<>();
        PgnItem.parsePgnItems(null, br, new PgnItem.EntryHandler() {
            @Override
            public boolean handle(PgnItem entry, BufferedReader bufferedReader) throws IOException {
                items.add(entry);
                return true;
            }

            @Override
            public boolean getMoveText(PgnItem entry) {
                return true;
            }

            @Override
            public void addOffset(int length) {

            }
        });

        for (PgnItem item : items) {
            logger.debug(item.toString());
            res.add(new PgnTree((PgnItem.Item) item));
        }
        return res;
    }

    public Move invert(Move src) {
        Move trg = src.clone();
        trg.moveFlags ^= Config.FLAGS_BLACK_MOVE;
        trg.to.y = Config.BOARD_SIZE - 1 - trg.to.y;
        if(trg.from.y != -1) {
            trg.from.y = Config.BOARD_SIZE - 1 - trg.from.y;
        }
        trg.piece ^= Config.PIECE_COLOR;
        if(trg.piecePromoted != Config.EMPTY) {
            trg.piecePromoted ^= Config.PIECE_COLOR;
        }
        if(trg.pieceTaken != Config.EMPTY) {
            trg.pieceTaken ^= Config.PIECE_COLOR;
        }
        return trg;
    }

    public Board invert(Board src) {
        Board trg = new Board();
        trg.toEmpty();
        for(int y = 0; y < Config.BOARD_SIZE; ++y) {
            for(int x = 0; x < Config.BOARD_SIZE; ++x) {
                int piece = src.getPiece(x, y);
                if(piece != Config.EMPTY) {
                    piece ^= Config.PIECE_COLOR;
                }
                trg.setPiece(x, Config.BOARD_SIZE -1 - y, piece);
            }
        }
        trg.enpass = src.enpass;
        trg.bKing = src.wKing.clone();
        trg.bKing.y = Config.BOARD_SIZE -1 - trg.bKing.y;
        trg.wKing = src.bKing.clone();
        trg.wKing.y = Config.BOARD_SIZE -1 - trg.wKing.y;
        trg.flags = invertFlags(src.flags);
/*
        trg.flags = src.flags;
        trg.flags ^= Config.FLAGS_BLACK_MOVE;
        trg.flags &= ~Config.INIT_POSITION_FLAGS;
        if((src.flags & Config.FLAGS_W_KING_OK) != 0) {
            trg.flags |= Config.FLAGS_B_KING_OK;
        }
        if((src.flags & Config.FLAGS_B_KING_OK) != 0) {
            trg.flags |= Config.FLAGS_W_KING_OK;
        }
        if((src.flags & Config.FLAGS_W_QUEEN_OK) != 0) {
            trg.flags |= Config.FLAGS_B_QUEEN_OK;
        }
        if((src.flags & Config.FLAGS_B_QUEEN_OK) != 0) {
            trg.flags |= Config.FLAGS_W_QUEEN_OK;
        }
*/
        return trg;
    }

    int invertFlags(int flags) {
        int res = flags;
        res ^= Config.FLAGS_BLACK_MOVE;
        res &= ~Config.INIT_POSITION_FLAGS;
        if((flags & Config.FLAGS_W_KING_OK) != 0) {
            res |= Config.FLAGS_B_KING_OK;
        }
        if((flags & Config.FLAGS_B_KING_OK) != 0) {
            res |= Config.FLAGS_W_KING_OK;
        }
        if((flags & Config.FLAGS_W_QUEEN_OK) != 0) {
            res |= Config.FLAGS_B_QUEEN_OK;
        }
        if((flags & Config.FLAGS_B_QUEEN_OK) != 0) {
            res |= Config.FLAGS_W_QUEEN_OK;
        }
        return res;
    }

    /**
     * For the supplied position validate each move as receied via UI and as read from pgn file
     * revert position and check the same move made by opponent
     * Limitation: conversion from userMove to pgnMove incorrect for ambiguous moves.
     * @param fen position
     * @param moves array of moves with expected result
     * @throws IOException
     */

    public void testMoves(String fen, Pair<String, Integer>[] moves) throws IOException {
        String pgn = String.format("[FEN \"%s\"]", fen);
        BufferedReader br = new BufferedReader(new StringReader(pgn));
        final List<PgnItem> items = new LinkedList<>();
        PgnItem.parsePgnItems(null, br, new PgnItem.EntryHandler() {
            @Override
            public boolean handle(PgnItem entry, BufferedReader bufferedReader) throws IOException {
                items.add(entry);
                return true;
            }

            @Override
            public boolean getMoveText(PgnItem entry) {
                return true;
            }

            @Override
            public void addOffset(int length) {

            }
        });
        Assert.assertEquals(items.size(), 1);
        PgnTree pgnTree = new PgnTree((PgnItem.Item) items.get(0));
        Board board = pgnTree.getBoard().clone();
        Board invertedBoard = invert(board);
        MoveParser moveParser;
        for (Pair<String, Integer> entry : moves) {
            int expectedFlags = entry.second;
            pgnTree.currentMove.snapshot = board.clone();
            moveParser = new MoveParser(pgnTree);
            Move move = moveParser.parseMove(entry.first);
            move.moveFlags &= ~Config.FLAGS_AMBIG;   // todo: verify
            if(expectedFlags != ERR) {
                if((board.flags & Config.FLAGS_BLACK_MOVE) != 0) {
                    move.moveFlags |= Config.FLAGS_BLACK_MOVE;
                }
            }
            Move invertedMove = invert(move);
            Move pgnMove = move.clone();
//            pgnMove.moveFlags &= ~Config.FLAGS_AMBIG;   // todo: verify
            String strPgnMove = pgnMove.toString();
            pgnMove = moveParser.parseMove(strPgnMove);

            boolean res;

            // validate as pgn move
            if(expectedFlags != ERR) {
                res = pgnTree.addPgnMove(pgnMove);
                Assert.assertEquals(String.format("%s\n%s%s error!", entry.first, pgnTree.getBoard().toString(), pgnMove.toString()), true, res);
                Assert.assertEquals(String.format("%s\n%s%s flags 0x%04x != 0x%04x", entry.first, pgnTree.getBoard().toString(), pgnMove.toString(),
                        pgnTree.getBoard().flags, expectedFlags & Config.POSITION_FLAGS), expectedFlags & Config.POSITION_FLAGS, pgnTree.getBoard().flags);
            }

            // validate as user move
            pgnTree.currentMove.snapshot = board.clone();
            res = pgnTree.validateUserMove(move);
            if (expectedFlags == ERR) {
                Assert.assertEquals(String.format("%s must be error", move.toString()), false, res);
            } else {
                pgnTree.addUserMove(move);
                Assert.assertEquals(String.format("%s\n%s%s flags 0x%04x != 0x%04x", entry.first, pgnTree.getBoard().toString(), pgnMove.toString(),
                    pgnTree.getBoard().flags, expectedFlags & Config.POSITION_FLAGS), expectedFlags & Config.POSITION_FLAGS, pgnTree.getBoard().flags);
                int moveFlags = (move.moveFlags & Config.MOVE_FLAGS) ^ Config.FLAGS_BLACK_MOVE;
                int _expectedFlags = expectedFlags & Config.MOVE_FLAGS;
                Assert.assertEquals(String.format("%s\n%s%s flags 0x%04x != 0x%04x", entry.first, pgnTree.getBoard().toString(), move.toString(),
                        moveFlags, _expectedFlags), _expectedFlags, moveFlags);
            }

            // test inverted board:
            pgnTree.currentMove.snapshot = invertedBoard.clone();
            moveParser = new MoveParser(pgnTree);
            Move invertedPgnMove = invertedMove.clone();
            invertedPgnMove.moveFlags &= ~Config.FLAGS_AMBIG;   // todo: verify
            String strInvertedPgnMove = invertedPgnMove.toString();
            invertedPgnMove = moveParser.parseMove(strInvertedPgnMove);
            int invertedExpectedFlags = invertFlags(expectedFlags);

            if(expectedFlags != ERR) {
                res = pgnTree.addPgnMove(invertedPgnMove);
                Assert.assertEquals(String.format("%s\n%s%s error!", entry.first, pgnTree.getBoard().toString(), invertedPgnMove.toString()), true, res);
                Assert.assertEquals(String.format("%s\n%s%s flags 0x%04x != 0x%04x", entry.first, pgnTree.getBoard().toString(), invertedPgnMove.toString(),
                        pgnTree.getBoard().flags, invertedExpectedFlags & Config.POSITION_FLAGS), invertedExpectedFlags & Config.POSITION_FLAGS, pgnTree.getBoard().flags);
            }

            // validate as user move
            pgnTree.currentMove.snapshot = invertedBoard.clone();
            res = pgnTree.validateUserMove(invertedMove);
            if (expectedFlags == ERR) {
                Assert.assertEquals(String.format("%s must be error", invertedMove.toString()), false, res);
            } else {
                pgnTree.addUserMove(invertedMove);
                Assert.assertEquals(String.format("%s\n%s%s flags 0x%04x != 0x%04x", entry.first, pgnTree.getBoard().toString(), invertedMove.toString(),
                        pgnTree.getBoard().flags, invertedExpectedFlags & Config.POSITION_FLAGS), invertedExpectedFlags & Config.POSITION_FLAGS, pgnTree.getBoard().flags);
                int moveFlags = (invertedMove.moveFlags & Config.MOVE_FLAGS) ^ Config.FLAGS_BLACK_MOVE;
                int _invertedExpectedFlags = invertedExpectedFlags & Config.MOVE_FLAGS;
                Assert.assertEquals(String.format("%s\n%s%s flags 0x%04x != 0x%04x", entry.first, pgnTree.getBoard().toString(), move.toString(),
                        moveFlags, _invertedExpectedFlags), _invertedExpectedFlags, moveFlags);
            }
        }
    }

}
