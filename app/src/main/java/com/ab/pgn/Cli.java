package com.ab.pgn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;

/**
 *
 * Created by Alexander Bootman on 12/13/18.
 */

public class Cli {
    private static int i = -1;
    enum command {
        none,
        help,
        list,
        merge,
        total;
    };

    private final static String command_names[] = {
        "none",
        "help",
        "list",
        "merge",
    };

    private static int getCommand(String name) {
        for(int i = 0; i < command_names.length; ++i) {
            if(command_names[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    public static void main(String[] args) throws Config.PGNException, FileNotFoundException {
//        System.out.println(String.format("command %s", command.merge.toString()));
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
        switch (cmd) {
            case list :
                doList(args);
                break;

            case merge :
                doMerge(args);
                break;

            case none:
            case help:
                mainHelp();
                break;
        }
    }

    private static void mainHelp() {
        System.out.println("usage:");
        System.out.println("java -jar target/pgn-1.0-SNAPSHOT-jar-with-dependencies.jar <command> <parameters>");
        System.out.println("\ncommands:");

        System.out.println(String.format("\t%s  - print this text", command.help.toString()));
        System.out.println(String.format("\t%s  <directory/zip/pgn_file> - print contents", command.list.toString()));
        System.out.println(String.format("\t%s <moves> <pgn_file> [result] - merge pgn file", command.merge.toString()));
    }

    // <directory/zip/pgn_file> print contents
    // java -jar target/pgn-1.0-SNAPSHOT-jar-with-dependencies.jar list etc/SicilianGrandPrix.zip/SicilianGrandPrix.pgn
    private static void doList(String[] args) throws Config.PGNException {
        if(args.length != 2) {
            System.out.println("invalid/missing parameters");
            mainHelp();
            return;
        }
        PgnItem pgnItem = PgnItem.fromFile(new File(args[1]));
        List<PgnItem> children = pgnItem.getChildrenNames(null);
        for(PgnItem child : children) {
            System.out.println(child.toString());
        }
    }

    // java -jar target/pgn-1.0-SNAPSHOT-jar-with-dependencies.jar merge "1.e4 c5 2. Nc3 Nc6 3.f4" etc/SicilianGrandPrix.zip/SicilianGrandPrix.pgn
    static void doMerge(String[] args) throws Config.PGNException, FileNotFoundException {
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
            PgnItem.setRoot(new File(workDir));
        }

        PgnItem.Pgn pgnItem = new PgnItem.Pgn(path);
        PgnGraph.MergeData md = new PgnGraph.MergeData(pgnItem);
        md.end = md.start = -1;
        md.annotate = true;
        graph.merge(md, null);
        String s = graph.toPgn();
        if(args.length < 4) {
            System.out.println(s);
        } else {
            PrintStream ps = new PrintStream(new FileOutputStream(args[3]));
            ps.print(s);
            ps.flush();
            ps.close();
        }
    }

    static List<PgnGraph> _parse(String pgn) throws Config.PGNException {
        List<PgnGraph> res = new LinkedList<>();
        BufferedReader br = new BufferedReader(new StringReader(pgn));
        final List<PgnItem> items = new LinkedList<>();
        PgnItem.parsePgnItems(null, br, new PgnItem.EntryHandler() {
            @Override
            public boolean handle(PgnItem entry, BufferedReader bufferedReader) throws Config.PGNException {
                items.add(entry);
                return true;
            }

            @Override
            public boolean getMoveText(PgnItem entry) {
                return true;
            }

            @Override
            public void addOffset(int length, int totalLength) {

            }
        });
        for (PgnItem item : items) {
            res.add(new PgnGraph((PgnItem.Item) item, null));
        }
        return res;
    }
}
