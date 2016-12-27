package com.ab.pgn;

import android.os.Environment;

//import org.apache.log4j.ConsoleAppender;
//import org.apache.log4j.FileAppender;
//import org.apache.log4j.Level;
//import org.apache.log4j.Logger;
//import org.apache.log4j.PatternLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

//import de.mindpipe.android.logging.log4j.LogConfigurator;

/**
 *
 * Created by Alexander Bootman on 7/30/16.
 */
public class Config {
    public static final String version = "1.0";
//    public static Level logLevel = Level.FATAL;

    public static final int
            MY_BUF_SIZE = 0x2000,
            STRING_BUF_SIZE = 0x1000,
            BOARD_SIZE = 8,

            // pieces, as defined in Config.FEN_PIECES
            WHITE = 0x00,
            BLACK = 0x08,
            EMPTY = 0x00,
            KING = 0x01,
            QUEEN = 0x02,
            BISHOP = 0x03,
            KNIGHT = 0x04,
            ROOK = 0x05,
            PAWN = 0x06,
            WHITE_KING = WHITE | KING,
            WHITE_QUEEN = WHITE | QUEEN,
            WHITE_BISHOP = WHITE | BISHOP,
            WHITE_KNIGHT = WHITE | KNIGHT,
            WHITE_ROOK = WHITE | ROOK,
            WHITE_PAWN = WHITE | PAWN,
            BLACK_KING = BLACK | KING,
            BLACK_QUEEN = BLACK | QUEEN,
            BLACK_BISHOP = BLACK | BISHOP,
            BLACK_KNIGHT = BLACK | KNIGHT,
            BLACK_ROOK = BLACK | ROOK,
            BLACK_PAWN = BLACK | PAWN,
            PIECE_COLOR = BLACK,

            // GUI:
            SELECTED_SQUARE_INDEX       = 15,
            POOL_BG_INDEX               = 16,
            TOTAL_PIECE_BITMAPS         = POOL_BG_INDEX + 1,

            // position flags:
            FLAGS_W_QUEEN_OK	= 0x0001,			// castle
            FLAGS_W_KING_OK		= 0x0002,			// castle
            FLAGS_B_QUEEN_OK	= 0x0004,			// castle
            FLAGS_BLACK_MOVE	= BLACK,            // == 0x0008
            FLAGS_B_KING_OK		= 0x0010,			// castle
            FLAGS_ENPASSANT_OK	= 0x0020,
            INIT_POSITION_FLAGS	= (FLAGS_W_QUEEN_OK | FLAGS_W_KING_OK | FLAGS_B_QUEEN_OK | FLAGS_B_KING_OK),
            POSITION_FLAGS		= (FLAGS_BLACK_MOVE | FLAGS_ENPASSANT_OK | INIT_POSITION_FLAGS),

            // move flags:
            FLAGS_CURRENT_MOVE	= 0x0040,           // for serialization
            FLAGS_REPETITION	= 0x0080,
            FLAGS_X_AMBIG		= 0x0100,
            FLAGS_Y_AMBIG		= 0x0200,
            FLAGS_AMBIG		    = (FLAGS_X_AMBIG | FLAGS_Y_AMBIG),
            FLAGS_CHECK			= 0x0400,
            FLAGS_CHECKMATE		= 0x0800,
            FLAGS_PROMOTION		= 0x1000,
            FLAGS_CASTLE		= 0x2000,
            FLAGS_ENPASSANT		= 0x4000,
            FLAGS_NULL_MOVE		= 0x8000,	        // any move

            // validate move options:
            VALIDATE_PGN_MOVE			= 0x0001,
            VALIDATE_CHECK				= 0x0004,
    		VALIDATE_USER_MOVE			= 0x0008,

            dummy_int = 0;

    public static final String
            PGN_GLYPH = "$",
            COMMENT_OPEN = "{",
            COMMENT_CLOSE = "}",
            VARIANT_OPEN = "(",
            VARIANT_CLOSE = ")",
            MOVE_PROMOTION = "=",
            MOVE_CAPTURE = "x",
            MOVE_CHECK = "+",
            MOVE_CHECKMATE = "#",

            //            0123456789abcde
            FEN_PIECES = " KQBNRP  kqbnrp", // piece offset is its internal representation
            PGN_K_CASTLE = "O-O",
            PGN_Q_CASTLE = "O-O-O",
            PGN_K_CASTLE_ALT = "0-0",
            PGN_Q_CASTLE_ALT = "0-0-0",
            PGN_NULL_MOVE = "--",
            PGN_NULL_MOVE_ALT = "<>",
            PGN_OLD_GLYPHS = "?!",

            // http://en.wikipedia.org/wiki/Portable_Game_Notation#Tag_pairs
            HEADER_Event = "Event",
            HEADER_Site = "Site",
            HEADER_Date = "Date",
            HEADER_Round = "Round",
            HEADER_White = "White",
            HEADER_Black = "Black",
            HEADER_Result = "Result",
            HEADER_FEN = "FEN",
            dummy_str = null;

    // STR - Seven Tag Roster
    public static final List<String> STR = Arrays.asList(HEADER_Event, HEADER_Site, HEADER_Date, HEADER_Round, HEADER_White, HEADER_Black, HEADER_Result);

    public static final HashMap<String, Integer> old_glyph_translation;

    static {
        old_glyph_translation = new HashMap<String, Integer>();
        old_glyph_translation.put("!", 1);
        old_glyph_translation.put("?", 2);
        old_glyph_translation.put("!!", 3);
        old_glyph_translation.put("??", 4);
        old_glyph_translation.put("!?", 5);
        old_glyph_translation.put("?!", 6);

    }

    public static String[] titleHeaders = {
            HEADER_White, HEADER_Black, HEADER_Date, HEADER_Event, HEADER_Site,
    };

/*
    static {
            LogConfigurator logConfigurator = new LogConfigurator();
        String s = Environment.getExternalStorageDirectory()
                + File.separator + "MyApp" + File.separator + "logs"
                + File.separator + "log4j.txt";
            logConfigurator.setFileName(Environment.getExternalStorageDirectory()
                    + File.separator + "MyApp" + File.separator + "logs"
                    + File.separator + "log4j.txt");
            logConfigurator.setRootLevel(Level.DEBUG);
            logConfigurator.setLevel("org.apache", Level.ERROR);
            logConfigurator.setFilePattern("%d %-5p [%c{2}]-[%L] %m%n");
            logConfigurator.setMaxFileSize(1024 * 1024 * 5);
            logConfigurator.setImmediateFlush(true);
            logConfigurator.configure();

    }
*/

//    public static void initLogger(Level logLevel) {
//        Config.logLevel = logLevel;
//        Logger rootLogger = Logger.getRootLogger();
//        ConsoleAppender console = new ConsoleAppender(); //create appender
//        //configure the appender
//        String PATTERN = "%d [%p|%c|%C{1}] %m%n";
//        console.setLayout(new PatternLayout(PATTERN));
//        console.setThreshold(logLevel);
//        console.activateOptions();
//        //add appender to any Logger (here is root)
////        Logger.getRootLogger().addAppender(console);
//        rootLogger.addAppender(console);
//
//        FileAppender fa = new FileAppender();
//        fa.setName("FileLogger");
//        fa.setFile("../logs/appLog.log");
//        fa.setLayout(new PatternLayout("%d %-5p [%c{1}] %m%n"));
//        fa.setThreshold(logLevel);
//        fa.setAppend(true);
//        fa.activateOptions();
//
//        //add appender to any Logger (here is root)
////        Logger.getRootLogger().addAppender(fa);
//        rootLogger.addAppender(fa);
//    }
/*

    static Logger rootLogger = Logger.getRootLogger();

    static {
        ConsoleAppender console = new ConsoleAppender(); //create appender
        //configure the appender
        String PATTERN = "%d [%p|%c|%C{1}] %m%n";
        console.setLayout(new PatternLayout(PATTERN));
        console.setThreshold(Level.FATAL);
        console.activateOptions();
        //add appender to any Logger (here is root)
//        Logger.getRootLogger().addAppender(console);
        rootLogger.addAppender(console);

        FileAppender fa = new FileAppender();
        fa.setName("FileLogger");
        fa.setFile("../logs/appLog.log");
        fa.setLayout(new PatternLayout("%d %-5p [%c{1}] %m%n"));
        fa.setThreshold(Level.DEBUG);
        fa.setAppend(true);
        fa.activateOptions();

        //add appender to any Logger (here is root)
//        Logger.getRootLogger().addAppender(fa);
        rootLogger.addAppender(fa);
    }

    // to init rootLogger
    public static org.apache.log4j.Logger getLogger(Class clazz) {
        Logger log = Logger.getLogger(clazz);
        log.info("My Application Created");
        return log;
    }
*/

    static public class PGNException extends RuntimeException {
        static final long serialVersionUID = 1989L;

        public PGNException(String t) {
            super(t);
        }
    }
}
