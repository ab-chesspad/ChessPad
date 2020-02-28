package com.ab.pgn;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 *
 * Created by Alexander Bootman on 12/26/16.
 */

public class PgnLogger {
    private static PrintStream ps = System.out;
    private final String name;
    private boolean includeTimeStamp = false;

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

    public void setIncludeTimeStamp(boolean includeTimeStamp) {
        this.includeTimeStamp = includeTimeStamp;
    }

    public PgnLogger(String name) {
        this(name, false);
    }

    public PgnLogger(String name, boolean includeTimeStamp) {
        this.name = Config.DEBUG_TAG + name;
        this.includeTimeStamp = includeTimeStamp;
    }

    public static PgnLogger getLogger(Class claz) {
        return getLogger(claz, false);
    }

    public static PgnLogger getLogger(Class claz, boolean includeTimeStamp) {
        return new PgnLogger(claz.getSimpleName(), includeTimeStamp);
    }

    public String getName() {
        return name;
    }

    public void debug(Object message) {
        // D/com.ab.droid.chesspad.ChessPad: board onSquareClick (e1)
        ps.println(String.format("%sD/%s: %s", getTS(), name, message.toString()));
    }

    private String getTS() {
        String ts = "";
        if(includeTimeStamp) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS ");
            ts = simpleDateFormat.format(new Date());
        }
        return ts;
    }

    public void debug(Object message, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        ps.println(String.format("%sD/%s: %s\n%s", getTS(), name, message.toString(), sw.toString()));
    }

    public void error(Object message) {
        ps.println(String.format("%sE/%s: %s", getTS(), name, message.toString()));
    }

    public void error(Object message, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        ps.println(String.format("%sE/%s: %s\n%s", getTS(), name, message.toString(), sw.toString()));
    }

}
