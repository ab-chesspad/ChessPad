package com.ab.pgn.dgtboard;

import java.io.*;
import java.util.Locale;

/**
 export JAVA_HOME=/usr/lib/jvm/java-8-oracle (or something like this)
 navigate to jni directory
 javac -h . -d ../target  ../com/ab/pgn/dgtboard/DgtBoardInterface.java ../../app/src/main/java/com/ab/pgn/dgtboard/DgtBoardIO.java ../../app/src/main/java/com/ab/pgn/dgtboard/DgtBoardProtocol.java
 or
 javac -h . -d ../target  ../com/ab/pgn/dgtboard/DgtBoardInterface.java ../../src/main/java/com/ab/pgn/dgtboard/DgtBoardIO.java ../../src/main/java/com/ab/pgn/dgtboard/DgtBoardProtocol.java
 edit dgtlib.cpp if needed
 g++ -fPIC -shared -I$JAVA_HOME/include -I$JAVA_HOME/include/linux -I. -o lib/linux/dgtlib.so dgtlib.cpp
 *
 * Created by Alexander Bootman on 1/7/19.
 */
public class DgtBoardInterface extends DgtBoardIO {
    public static final int TIMEOUT_MSEC = 50;
    public static final String LINUX_JNI_LIBRARY_PATH = "/lib/linux/dgtlib.so";
    public static final String MACOSX_JNI_LIBRARY_PATH = "/lib/macosx/dgtlib.dylib";
    public static final String WINDOWS_JNI_LIBRARY_PATH = "/lib/windows/dgtlib.dll";

    public native void _open(String ttyName);
    public native void _close();
    public native void _write(byte command);
    public native int _read(byte[] buffer, int offset, int length, int timeout_msec);

    private String libraryPath;
    private String ttyPort;

    int timeout_msec = TIMEOUT_MSEC;    // to use in JNI
//    private long tty = -99;             // to use in JNI
//    private long read_thread;           // to use in JNI for Windows to abort IO

    public DgtBoardInterface(String libraryPath, String ttyPort) {
        String OS = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
        if ((OS.indexOf("mac") >= 0) || (OS.indexOf("darwin") >= 0)) {
            System.out.println(String.format("running on Mac"));
            setupPort(ttyPort);
            setupParams(libraryPath, MACOSX_JNI_LIBRARY_PATH);
        } else if (OS.indexOf("win") >= 0) {
            System.out.println(String.format("running on Windows"));
            setupPortWin(ttyPort);
            setupParams(libraryPath, WINDOWS_JNI_LIBRARY_PATH);
        } else if (OS.indexOf("nux") >= 0) {
            System.out.println(String.format("running on Linux"));
            setupPort(ttyPort);
            setupParams(libraryPath, LINUX_JNI_LIBRARY_PATH);
        } else {
            System.out.println(String.format("running on unknown OS"));
            this.libraryPath = libraryPath;
            this.ttyPort = ttyPort;
        }
        System.load(this.libraryPath);
    }

    private void setupPort(String ttyPort) {
        if(ttyPort.startsWith("/")) {
            this.ttyPort = ttyPort;
        } else {
            this.ttyPort = "/dev/" + ttyPort;
        }
    }

    private void setupPortWin(String ttyPort) {
        if(ttyPort.startsWith("\\")) {
            this.ttyPort = ttyPort;
        } else {
            this.ttyPort = "\\\\.\\" + ttyPort;
        }
    }

    private void setupParams(String libraryPath, String defaultLibraryPath) {
        if (libraryPath == null) {
            // find path to jar file
            File jarFile = new java.io.File(this.getClass().getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .getPath());
            libraryPath = jarFile.getParent() + defaultLibraryPath;
//            String wdir = System.getProperty("user.dir");
        }
        File f = new File(libraryPath);
        this.libraryPath = f.getAbsolutePath();
    }

    public void open() throws IOException {
        _open(ttyPort);
        init();
    }

    public void close() {
        _close();
    }

    @Override
    public void write(byte command) throws IOException {
        _write(command);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        return _read(buffer, offset, length, TIMEOUT_MSEC);
    }

}
