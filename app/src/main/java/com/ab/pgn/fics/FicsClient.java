package com.ab.pgn.fics;

import com.ab.pgn.PgnGraph;
import com.ab.pgn.PgnLogger;
import com.ab.pgn.fics.chat.FicsParser;
import com.ab.pgn.fics.chat.InboundMessage;

import java.io.*;

/**
 *
 * Created by Alexander Bootman on 4/4/19.
 */
public class FicsClient {
    private boolean DEBUG = false;
/*
    private String DEBUG_COMMAND = null;
/*/
//    private String DEBUG_COMMAND = "tell puzzlebot gettactics";
//    private String DEBUG_COMMAND = "tell puzzlebot gt 02839";
//    private String DEBUG_COMMAND = "examine 37";
//    private String DEBUG_COMMAND = "games";
    private String DEBUG_COMMAND = "seek 30 0 unrated manual";
//*/
    private final String
        INITIAL_TIMESEAL =  "TIMESTAMP|iv|OpenSeal|",
        DEFAULT_SERVER_IP = "freechess.org",
        FICS_PING_MSG = "\n\rfics% ",
        LOGIN_PROMPT = ":",
        LOGIN_OK = "**** Starting FICS session as ",
        dummy_string = null;

//    public final static String INIT_SCRIPT_FILE_NAME = "config/init.script";

    private final int DEFAULT_SERVER_PORT = 5000;

    private final PgnLogger logger = PgnLogger.getLogger(this.getClass());

    private String serverIp;
    private int serverPort;

    private  TimesealSocket socket;
    // on Android socket operations must run in BG threads
    private ReadThread readThread;
    private InboundMessageConsumer serverMessageConsumer;
    private volatile boolean writePending;

    private String loggedIn = null;
    private String[] toServer = {
            "username",
            "password",
    };
    private int toServerIndex = -1;

    private FicsParser ficsParser = new FicsParser();
    private String previousMessage = "";
    private GameHolder gameHolder;
//    private InboundMessage.G1Game currentGame = new InboundMessage.G1Game();
//    private PgnGraph pgnGraph;

    public FicsClient(String user, String password, GameHolder gameHolder, InboundMessageConsumer serverMessageConsumer) {
        toServer[0] = user;
        toServer[1] = password;
        toServerIndex = -1;
        this.gameHolder = gameHolder;
        this.serverMessageConsumer = serverMessageConsumer;
    }

    // on Android must run in BG thread
    public void connect() throws IOException {
        serverIp = DEFAULT_SERVER_IP;
        serverPort = DEFAULT_SERVER_PORT;
        readThread = new ReadThread();
        readThread.keepRunning = true;
        readThread.start();
    }

//    private void _sendAfterLogin() {
///*
//        OrderedProperties initScript = new OrderedProperties(INIT_SCRIPT_FILE_NAME);
//        for (Map.Entry<String, String> entry : initScript.entrySet() ) {
//            System.out.println(String.format("%s %s", entry.getKey(), entry.getValue()));
//            while(writePending) {
//                pause(100);
//            }
//            String command = entry.getKey() + " " +entry.getValue();
//            write(command);
//        }
//*/
//        try {
//            BufferedReader br = new BufferedReader(new FileReader(INIT_SCRIPT_FILE_NAME));
//            String line;
//            while((line = br.readLine()) != null) {
//                if(line.startsWith("#")) {
//                    continue;
//                }
//                line = line.trim();
//                if(line.isEmpty()) {
//                    continue;
//                }
//                logger.debug(line);
//                write(line);
//                while(writePending) {
//                    pause(100);
//                }
//            }
//            if(DEBUG_COMMAND != null) {
//                write(DEBUG_COMMAND);
//            }
//        } catch (IOException e) {
//            logger.error(e.getMessage(), e);
//        }
//
//    }

    private void sendAfterLogin() {
        String[] commands = new AfterLoginCommands().list();
        for(String command : commands) {
            command = command.trim();
            if(command.isEmpty()) {
                continue;
            }
            logger.debug(command);
            write(command);
            while(writePending) {
                pause(100);
            }
        }
        if(DEBUG_COMMAND != null) {
            write(DEBUG_COMMAND);
        }
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
                if (++toServerIndex < toServer.length) {
                    //            Log.d(DEBUG_TAG, String.format("sending %s", messages[toServerIndex]));
                    write(toServer[toServerIndex]);
                }
                // Press return to enter the server as "GuestFQZW":
            } else {
                int start;
                if((start = fromServer.indexOf(LOGIN_OK)) > 0) {
                    start += LOGIN_OK.length();
                    int end = fromServer.substring(start).indexOf(" ");
                    if(end > 0) {
                        loggedIn = fromServer.substring(start, start + end);
                    }
                }
                if(loggedIn != null) {
                    sendAfterLogin();
                }
            }
        }
    }

    // on Android must run in BG thread
    public void close() throws IOException {
        if(readThread != null) {
            readThread.keepRunning = false;
        }
        if(socket != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }).start();
        }
    }

    // on Android must run in BG thread
    public void write(final String value) {
        writePending = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                OutputStream outputStream;
                try {
                    outputStream = socket.getOutputStream();
                    byte[] buffer = (value + "\n").getBytes();
                    outputStream.write(buffer, 0, buffer.length);
                    logger.debug(String.format("*** sent: %s ***", value));
                } catch (IOException e) {
                    logger.error(e.getMessage(), e);
                }
                writePending = false;
            }
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
//        private InboundMessageConsumer serverMessageConsumer;

//        ReadThread(InboundMessageConsumer chatEventConsumer) {
//            this.serverMessageConsumer = chatEventConsumer;
////            this.setPriority(Thread.MAX_PRIORITY);
//        }

        @Override
        public void run() {
            keepRunning = true;
            logger.debug("ReadThread started");
            try {
//                socket = new Socket(serverIp, serverPort);
                socket = new TimesealSocket(serverIp, serverPort, INITIAL_TIMESEAL);
                pause(100);

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
            keepRunning = false;
        }
    }

    interface InboundMessageConsumer {
        void consume(InboundMessage.Info inboundMessage);
    }

    interface GameHolder {
        InboundMessage.G1Game getGame();
    }

}
