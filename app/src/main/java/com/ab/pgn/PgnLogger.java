package com.ab.pgn;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 *
 * Created by Alexander Bootman on 12/26/16.
 */

public class PgnLogger {
    private static PrintStream ps = System.out;
    private final String name;
    private boolean separateTagFromMsg = false;

    public static void setPrintStream(PrintStream ps) {
        PgnLogger.ps = ps;
    }

    public static void setFile(String fileName) throws FileNotFoundException {
        if(ps != System.out) {
            close();
        }
        if(fileName == null) {
            ps = System.out;
        } else {
            ps = new PrintStream(new FileOutputStream(fileName));
        }
    }

    public static void close() {
        ps.flush();
        ps.close();
    }

    public PgnLogger(String name) {
        this.name = Config.DEBUG_TAG + name;
    }

    public PgnLogger(String name, boolean separateTagFromMsg) {
        this.name = Config.DEBUG_TAG + name;
        this.separateTagFromMsg = separateTagFromMsg;
    }

    public static PgnLogger getLogger(Class claz) {
        return new PgnLogger(claz.getSimpleName());
    }

    public static PgnLogger getLogger(Class claz, boolean separateTagFromMsg) {
        return new PgnLogger(claz.getName(), separateTagFromMsg);
    }

    public String getName() {
        return name;
    }

    public void debug(Object message) {
        // D/com.ab.droid.chesspad.ChessPad: board onSquareClick (e1)
        if(separateTagFromMsg) {
            ps.println(String.format("D/%s: %s", name, message.toString()));
        } else {
            ps.println(String.format("D/%s: %s", name, message.toString()));
        }
    }

    public void debug(Object message, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        ps.println(String.format("D/%s: %s\n%s", name, message.toString(), sw.toString()));
    }

    public void error(Object message) {
        ps.println(String.format("E/%s: %s", name, message.toString()));
    }

    public void error(Object message, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        ps.println(String.format("E/%s: %s\n%s", name, message.toString(), sw.toString()));
    }

}
