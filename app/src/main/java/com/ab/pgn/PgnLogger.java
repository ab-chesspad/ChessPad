package com.ab.pgn;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 *
 * Created by abootman on 12/26/16.
 */

public class PgnLogger {
    private String name;
    private boolean separateTagFromMsg = false;

    public PgnLogger(String name) {
        this.name = name;
    }

    public PgnLogger(String name, boolean separateTagFromMsg) {
        this.name = name;
        this.separateTagFromMsg = separateTagFromMsg;
    }

    public static PgnLogger getLogger(Class claz) {
        return new PgnLogger(claz.getName());
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
            System.out.println(String.format("D/%s:\n%s", name, message.toString()));
        } else {
            System.out.println(String.format("D/%s: %s", name, message.toString()));
        }
    }

    public void debug(Object message, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        System.out.println(String.format("D/%s: %s\n%s", name, message.toString(), sw.toString()));
    }

    public void error(Object message) {
        System.out.println(String.format("E/%s: %s", name, message.toString()));
    }

    public void error(Object message, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        System.out.println(String.format("E/%s: %s\n%s", name, message.toString(), sw.toString()));
    }

}
