package com.ab.pgn;

import com.ab.pgn.dgtboard.DgtBoardInterface;
import com.ab.pgn.dgtboard.DgtBoardPad;
import com.ab.pgn.fics.FicsPad;
import com.ab.pgn.fics.chat.InboundMessage;

import java.io.*;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * Created by Alexander Bootman on 12/13/18.
 */

public class Cli {
    public static boolean DEBUG = false;

    public static boolean doPrint = true;
    // fics software is buggy and seems not too popular

    public final static String FICS_LOG_FILE_NAME = "log/fics.log";

    enum command {
        none,
        help,
        list,
        merge,
        dgtboard,
//        fics,     // fics software is buggy and seems not too popular
        total,
    }

    private final static String[] command_names = {
        "none",
        "help",
        "list",
        "merge",
        "dgtboard",
//        "fics",   // fics software is buggy and seems not too popular
    };

    private final PgnLogger logger = PgnLogger.getLogger(this.getClass(), true);
    private DgtBoardPad dgtBoardPad;

    private static int getCommand(String name) {
        for(int i = 0; i < command_names.length; ++i) {
            if(command_names[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    public static void main(String[] args) throws Config.PGNException, IOException {
        System.out.println("parameters:");
        for (String a : args) {
            System.out.println(a);
        }
        System.out.println();

        if(args.length == 0 || args[0].equals("-h") ) {
            mainHelp();
            return;
        }
        command cmd = command.none;
        try {
            cmd = command.valueOf(args[0]);
        } catch (java.lang.IllegalArgumentException e) {
            // ignore
        }
        Cli cli = new Cli();
        switch (cmd) {
            case list :
                cli.doList(args);
                break;

            case merge :
                cli.doMerge(args);
                break;

            case dgtboard :
                cli.doDgtBoard(args);
                break;

// fics software is buggy and seems not too popular
//            case fics :
//                cli.doFICS(args);
//                break;

            case none:
            case help:
                mainHelp();
                break;
        }
    }

    private static void mainHelp() {
        System.out.println("usage:");
        String path = new java.io.File(Cli.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .getPath()).getPath();
        String wdir = System.getProperty("user.dir");
        String jarPath = path.substring(wdir.length() + 1);

        System.out.println(String.format("java -jar %s <command> <parameters>", jarPath));
        System.out.println("\ncommands:");

        System.out.println(String.format("\t%s - print this text", command.help.toString()));
        System.out.println(String.format("\t%s <directory/zip/pgn_file> - print contents", command.list.toString()));
        System.out.println(String.format("\t%s <moves> <pgn_file> [result] - merge pgn file", command.merge.toString()));
        System.out.println(String.format("\t%s <port, e.g. /dev/ttyUSB0> - record DGT board moves", command.dgtboard.toString()));
    }

    // <directory/zip/pgn_file> print contents
    // java -jar target/pgn-1.0-SNAPSHOT-jar-with-dependencies.jar list etc/SicilianGrandPrix.zip/SicilianGrandPrix.pgn
    private void doList(String[] args) throws Config.PGNException {
        if(args.length != 2) {
            System.out.println("invalid/missing parameters");
            mainHelp();
            return;
        }
        CpFile cpFile = CpFile.fromFile(new File(args[1]));
        List<CpFile> children = cpFile.getChildrenNames(null);
        for(CpFile child : children) {
            System.out.println(child.toString());
        }
    }

    // java -jar target/pgn-1.0-SNAPSHOT-jar-with-dependencies.jar merge "1.e4 c5 2. Nc3 Nc6 3.f4" etc/SicilianGrandPrix.zip/SicilianGrandPrix.pgn
    private void doMerge(String[] args) throws Config.PGNException, FileNotFoundException {
        if(args.length < 3) {
            System.out.println("invalid/missing parameters");
            mainHelp();
            return;
        }

        String pgn = args[1];
        List<PgnGraph> graphs = _parse(pgn);
        if(graphs.size() != 1) {
            throw new Config.PGNException(String.format("Invalid move line: %s", args[1]));
        }
        PgnGraph graph = graphs.get(0);

        String path = args[2];
        if(!path.startsWith("/")) {
            String workDir = System.getProperty("user.dir");
            CpFile.setRoot(new File(workDir));
        }

        CpFile.Pgn cpFile = new CpFile.Pgn(path);
        PgnGraph.MergeData md = new PgnGraph.MergeData(cpFile);
        md.end = md.start = -1;
        md.annotate = true;
        graph.merge(md, null);
        String s = graph.toPgn();
        if(args.length < 4) {
            System.out.println(s);
            return;
        }
        try (PrintStream ps = new PrintStream(new FileOutputStream(args[3]))) {
            ps.print(s);
        }
    }

    private List<PgnGraph> _parse(String pgn) throws Config.PGNException {
        List<PgnGraph> res = new LinkedList<>();
        BufferedReader br = new BufferedReader(new StringReader(pgn));
        final List<CpFile> items = new LinkedList<>();
        CpFile.parsePgnFiles(null, br, new CpFile.EntryHandler() {
            @Override
            public boolean handle(CpFile entry, BufferedReader bufferedReader) {
                items.add(entry);
                return true;
            }

            @Override
            public boolean getMoveText(CpFile entry) {
                return true;
            }

            @Override
            public boolean addOffset(int length, int totalLength) {
                return false;
            }

            @Override
            public boolean skip(CpFile entry) {
                return false;
            }
        });
        for (CpFile item : items) {
            res.add(new PgnGraph((CpFile.Item) item, null));
        }
        return res;
    }

    private void doDgtBoard(String[] args) throws Config.PGNException, IOException {
        if (args.length < 2) {
            System.out.println("invalid/missing parameters");
            mainHelp();
            return;
        }

        String libPath = null;
        if (args.length >= 3) {
            libPath = args[2];
        }

        System.out.println(String.format("Running dgtboard with DEBUG=%b", DEBUG));
//        DgtBoardWatcher.DEBUG = DEBUG;
        DgtBoardPad.DEBUG = DEBUG;

        System.out.println("Please wait while connection is being set up.");
        DgtBoardInterface dgtBoardInterface = new DgtBoardInterface(libPath, args[1]);
        dgtBoardInterface.open();

        dgtBoardPad = new DgtBoardPad(dgtBoardInterface, System.getProperty("user.dir"), new CpEventObserver() {
            @Override
            public void update(byte msgId) {
                statusChanged();
            }
        });
        dgtBoardPad.resume();
        final boolean[] done = {false};
        while (!done[0]) {
            if (System.in.available() > 0) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            }
        }
        dgtBoardPad.finish();
        dgtBoardInterface.close();

/*
        if (!done[0]) {
            System.out.println(dgtBoardWatcher.getPgnGraph().getInitBoard().toString());
            System.out.println(dgtBoardWatcher.getPgnGraph().toPgn());
        }
*/


    }

    private DgtBoardPad.BoardStatus boardStatus = DgtBoardPad.BoardStatus.None;

    private void statusChanged() {
        DgtBoardPad.BoardStatus boardStatus = dgtBoardPad.getBoardStatus();
        if(this.boardStatus == boardStatus) {
            return;
        }
        switch(boardStatus) {
            case Game:
                System.out.println("\nMove pieces or click Enter to finish:");
                this.boardStatus = boardStatus;
                break;
            case SetupMess:
                System.out.println("\nRestore position on the board or start a new game.");
                this.boardStatus = boardStatus;
                break;
        }
    }

    // fics software is buggy and seems not too popular
//    private void doFICS(String[] args) throws IOException {
//        String user = "guest";
//        String password = "";
////        if (args.length < 2) {
////            System.out.println("invalid/missing parameters");
////            mainHelp();
////            return;
////        }
//
//        if (args.length == 3) {
//            user = args[1];
//            password = args[2];
//        } else  if (args.length != 1) {
//            System.out.println("invalid/missing parameters");
//            mainHelp();
//            return;
//        }
//
//        System.out.println(String.format("Running FICS with DEBUG=%b", DEBUG));
//        final int[] count = {0};
//        PgnLogger.setFile(FICS_LOG_FILE_NAME);
//        FicsPad ficsPad = new FicsPad(user, password, new FicsPad.InboundMessageConsumer() {
//            @Override
//            public void consume(InboundMessage.Info inboundMessage) {
//                logger.debug(inboundMessage.toString());
//                // todo!
//            }
//        });
//        System.out.println("\nEnter FICS command or 'bye' to finish:");
//
//        while (!ficsPad.isConnected()) {
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                // ignore
//            }
//        }
//
//        final String[] commands = {
////            "tell puzzlebot gt3",
////            "tell puzzlebot solve",
////            "tell endgamebot help"
////            "tell endgamebot play 8/k7/8/8/8/6Q1/8/1K6 --"
////            "tell endgamebot play kqk"
//                "games"
//        };
//        for (String command : commands) {
////            ficsPad.send(String.format(command, i));
//            ficsPad.send(command);
//        }
//
//        byte[] commandBuffer = new byte[256];
//        while (ficsPad.isConnected()) {
//            if (System.in.available() > 0) {
//                int len = System.in.read(commandBuffer);
//                String command = new String(commandBuffer, 0, len).trim();
//                ficsPad.send(command);
//            }
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                break;
//            }
//        }
//        ficsPad.close();
//        PgnLogger.setFile(null);
//        System.out.println("Done");
//    }
}
