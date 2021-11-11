/*
     Copyright (C) 2021	Alexander Bootman, alexbootman@gmail.com

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

 *
 * Created by Alexander Bootman on 12/26/16.
 */
package com.ab.pgn;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PgnLogger {
    private static PrintStream ps = System.out;
    private final String name;
    private boolean includeTimeStamp;

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

    private static void close() {
        ps.flush();
        ps.close();
    }

    public void setIncludeTimeStamp(boolean includeTimeStamp) {
        this.includeTimeStamp = includeTimeStamp;
    }

    public PgnLogger(String name) {
        this(name, false);
    }

    private PgnLogger(String name, boolean includeTimeStamp) {
        this.name = Config.DEBUG_TAG + name;
        this.includeTimeStamp = includeTimeStamp;
    }

    public static PgnLogger getLogger(Class<?> claz) {
        return getLogger(claz, false);
    }

    public static PgnLogger getLogger(Class<?> claz, boolean includeTimeStamp) {
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
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM-dd HH:mm:ss.SSS ", Locale.getDefault());
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
