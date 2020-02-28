package com.ab.pgn.fics;

import com.ab.pgn.PgnLogger;
import com.ab.pgn.fics.chat.FicsParser;
import com.ab.pgn.fics.chat.InboundMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * Created by Alexander Bootman on 4/4/19.
 */
class FicsClient {
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_THROTTLE = false;
    private static final boolean DEBUG_SETUP = false;

//*
    private final String DEBUG_COMMAND = null;
/*/
    //    private String DEBUG_COMMAND = "tell puzzlebot gettactics";
    //    private String DEBUG_COMMAND = "tell puzzlebot gt 02839";
    //    private String DEBUG_COMMAND = "examine 37";
    //    private String DEBUG_COMMAND = "games";
        private String DEBUG_COMMAND = "seek 30 0 unrated manual";
//*/

    private static final String
        INITIAL_TIMESEAL =  "TIMESTAMP|iv|OpenSeal|",
        DEFAULT_SERVER_IP = "freechess.org",
        FICS_PING_MSG = "\n\rfics% ",
        LOGIN_PROMPT = ":",
        LOGIN_OK = "**** Starting FICS session as ",
        dummy_string = null;

    private static final Pattern LOGIN_PATTERN = Pattern.compile("^([a-zA-Z]+)");

    private static final int DEFAULT_SERVER_PORT = 5000;

    private final PgnLogger logger = PgnLogger.getLogger(this.getClass());

    private String serverIp;
    private int serverPort;

    // on Android socket operations must run in BG threads
    private volatile TimesealSocket socket;
    private volatile boolean writePending;
    private volatile boolean isReady;
    private ReadThread readThread;
    private final InboundMessageConsumer serverMessageConsumer;

    private String loggedIn = null;
    private final String[] toServer = {
            "username",
            "password",
    };
    private int toServerIndex;

    private final FicsParser ficsParser = new FicsParser();
    private String previousMessage = "";
    private final GameHolder gameHolder;

    FicsClient(String user, String password, GameHolder gameHolder, InboundMessageConsumer serverMessageConsumer) {
        toServer[0] = user;
        toServer[1] = password;
        toServerIndex = -1;
        this.gameHolder = gameHolder;
        this.serverMessageConsumer = serverMessageConsumer;
    }

    // on Android must run in BG thread
    void connect() {
        serverIp = DEFAULT_SERVER_IP;
        serverPort = DEFAULT_SERVER_PORT;
        readThread = new ReadThread();
        readThread.start();
    }

    String getLoggedIn() {
        return loggedIn;
    }

    private void sendAfterLogin() {
        String[] commands = new AfterLoginCommands().list();
        for(String command : commands) {
            command = command.trim();
            if(command.isEmpty()) {
                continue;
            }
            if(DEBUG_SETUP) {
                logger.debug(command);
            }
            write(command);
            while(writePending) {
                pause(100);
            }
        }
        if(DEBUG_COMMAND != null) {
            write(DEBUG_COMMAND);
        }
        isReady = true;
        serverMessageConsumer.consume(new InboundMessage.Info(InboundMessage.MessageType.Ready));
    }

    private void handleServerMessage(String msg) {
        if(loggedIn != null) {
            msg = previousMessage + msg;
            previousMessage = "";
            String[] messages = (msg).split(FICS_PING_MSG);
            int totalMessages = messages.length;
            if(!msg.endsWith(FICS_PING_MSG)) {
                // incomplete last message
                --totalMessages;
                previousMessage = messages[totalMessages];
            }
            for(int i = 0; i < totalMessages; ++i) {
                String m = messages[i].trim();
                if(DEBUG) {
                    logger.debug(String.format("from fics: %s", m));
                }
                InboundMessage.Info inboundMessage = ficsParser.parse(m, gameHolder.getGame());
                serverMessageConsumer.consume(inboundMessage);
            }
        } else {
            logger.debug(msg);
            String fromServer = msg.trim();
            if (fromServer.endsWith(LOGIN_PROMPT)) {
                // Press return to enter the server as "GuestFQZW":
                if (++toServerIndex < toServer.length) {
                    logger.debug(String.format("sending \"%s\"", toServer[toServerIndex]));
                    write(toServer[toServerIndex]);
                }
            } else {
                int start;
                if((start = fromServer.indexOf(LOGIN_OK)) > 0) {
                    start += LOGIN_OK.length();
                    Matcher m = LOGIN_PATTERN.matcher(fromServer.substring(start));
                    if (m.find()) {
                        loggedIn = m.group();
                    } else {
                        // sanity check
                        int end = fromServer.substring(start).indexOf(" ");
                        if (end > 0) {
                            loggedIn = fromServer.substring(start, start + end);
                        }
                    }
                }
                if(loggedIn != null) {
                    sendAfterLogin();
                }
            }
        }
    }

    boolean isReady() {
        return isReady;
    }

    // on Android must run in BG thread
    public void close() {
        if(readThread != null) {
            readThread.keepRunning = false;
        }
        if(socket != null) {
            new Thread(() -> {
                try {
                    if(socket != null) {
                        socket.close();
                    }
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
                socket = null;
                serverMessageConsumer.consume(new InboundMessage.Info(InboundMessage.MessageType.Closed));
            }).start();
        }
        isReady = false;
    }

    // on Android must run in BG thread
    public void write(final String value) {
        writePending = true;
        new Thread(() -> {
            if(DEBUG_THROTTLE) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }

            OutputStream outputStream;
            try {
                outputStream = socket.getOutputStream();
                byte[] buffer = (value + "\n").getBytes();
                outputStream.write(buffer, 0, buffer.length);
                if(DEBUG) {
                    logger.debug(String.format("*** sent: %s ***", value));
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
            writePending = false;
        }).start();
    }

    private void pause(int timeoutMillisec) {
        try {
            Thread.sleep(timeoutMillisec);
        } catch (InterruptedException e) {
//            e.printStackTrace();
            logger.error(e.getMessage(), e);
        }
    }

    public boolean isAlive() {
        return readThread.keepRunning;
    }

    class ReadThread extends Thread {
        private volatile boolean keepRunning;

        @Override
        public void run() {
            logger.debug("ReadThread started");
            try {
                socket = new TimesealSocket(serverIp, serverPort, INITIAL_TIMESEAL);
                pause(100);
                keepRunning = true;

                // Get input and output stream references
                InputStream inputStream = socket.getInputStream();
                byte[] buffer = new byte[10000];
                while (keepRunning) {
                    if(DEBUG) {
                        logger.debug("ReadThread hanging on read");
                    }
                    int len = inputStream.read(buffer);
                    if(len < 0) {
                        throw new IOException("ReadThread while reading socket");
                    }
                    if(len == 0) {
                        logger.debug("ReadThread read 0 bytes");
                        continue;
                    }
                    String msg = new String(buffer, 0, len);
                    handleServerMessage(msg);
                }
            } catch (IOException e) {
                logger.error(e);
            }
            logger.debug("ReadThread done");
            close();
        }
    }

    interface InboundMessageConsumer {
        void consume(InboundMessage.Info inboundMessage);
    }

    interface GameHolder {
        InboundMessage.G1Game getGame();
    }

}
