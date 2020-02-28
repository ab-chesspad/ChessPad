package com.ab.pgn;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 *
 * Created by Alexander Bootman on 7/30/16.
 */
public class Config {
    public static final String version = "1.0";
    public static final String DEBUG_TAG = "chesspad-debug.";
    public static final boolean USE_BIT_STREAMS = false;

    public static final int
        MY_BUF_SIZE = 0x2000,
        STRING_BUF_SIZE = 0x1000,
        PGN_OUTPUT_LINE_SIZE = 128,     // or a little more
        BOARD_SIZE = 8,

        // pieces, as defined in Config.FEN_PIECES
        WHITE = 0x00,
        BLACK = 0x01,
        EMPTY = 0x00,
        KING = 0x02,
        QUEEN = 0x04,
        BISHOP = 0x06,
        KNIGHT = 0x08,
        ROOK = 0x0a,
        PAWN = 0x0c,
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
        FLAGS_BLACK_MOVE	= BLACK,            // == 0x0001
        FLAGS_B_QUEEN_OK	= 0x0002,			// castle
        FLAGS_B_KING_OK		= 0x0004,			// castle
        FLAGS_W_QUEEN_OK	= 0x0008,			// castle
        FLAGS_W_KING_OK		= 0x0010,			// castle
        FLAGS_REPETITION	= 0x0020,
        FLAGS_ENPASSANT_OK	= 0x0040,           // include in Move serialization
        INIT_POSITION_FLAGS	= (FLAGS_W_QUEEN_OK | FLAGS_W_KING_OK | FLAGS_B_QUEEN_OK | FLAGS_B_KING_OK),
        POSITION_FLAGS		= (FLAGS_BLACK_MOVE | FLAGS_ENPASSANT_OK | INIT_POSITION_FLAGS),

        // move flags:
        FLAGS_CHECKMATE		= 0x0080,
        FLAGS_CASTLE		= 0x0100,
        FLAGS_X_AMBIG		= 0x0200,
        FLAGS_Y_AMBIG		= 0x0400,
        FLAGS_AMBIG		    = (FLAGS_X_AMBIG | FLAGS_Y_AMBIG),
        FLAGS_CHECK			= 0x0800,
        FLAGS_NULL_MOVE		= 0x1000,	        // any move
        FLAGS_STALEMATE		= 0x2000,
        FLAGS_CAPTURE		= 0x8000,           // exclude from Move serialization

        MOVE_FLAGS          = (FLAGS_BLACK_MOVE | FLAGS_CAPTURE | FLAGS_AMBIG |FLAGS_CHECK |
                FLAGS_CHECKMATE | FLAGS_STALEMATE | FLAGS_CASTLE | FLAGS_NULL_MOVE),

        // validate move options:
        VALIDATE_PGN_MOVE			= 0x0001,
        VALIDATE_CHECK				= 0x0004,
        VALIDATE_USER_MOVE			= 0x0008,

        LAST_GLYPH = 139,

        dummy_int = 0;

    public static final byte
        // messages from Dgt Board and FICS
        MSG_NOTHING = 0,
        MSG_DGT_BOARD_SETUP_MESS = 1,
        MSG_DGT_BOARD_GAME = MSG_DGT_BOARD_SETUP_MESS + 1,

        //  MESSAGE_BIT is set in each message byte[0]:
        DGT_BOARD_MESSAGE_BIT = (byte) 0x80,
        DGT_BOARD_MESSAGE_MASK = (byte) (0xff & ~DGT_BOARD_MESSAGE_BIT),

        MSG_DGT_BOARD_LAST = 0x13,      // DGT_VERSION

        MSG_OOM = MSG_DGT_BOARD_LAST + 1,

        MSG_FICS_FIRST = MSG_OOM + 1,

        dummy_byte = 0;

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
        FEN_PIECES = "  KkQqBbNnRrPp", // piece offset is its internal representation
        PROMOTION_PIECES = "QBNR",
        PGN_K_CASTLE = "O-O",
        PGN_Q_CASTLE = "O-O-O",
        PGN_K_CASTLE_ALT = "0-0",
        PGN_Q_CASTLE_ALT = "0-0-0",
        PGN_NULL_MOVE = "--",
        PGN_NULL_MOVE_ALT = "<>",
        PGN_OLD_GLYPHS = "?!",

        // http://en.wikipedia.org/wiki/Portable_Game_Notation#Tag_pairs
        TAG_Event = "Event",    // 0
        TAG_Site = "Site",      // 1
        TAG_Date = "Date",      // 2
        TAG_Round = "Round",    // 3
        TAG_White = "White",    // 4
        TAG_Black = "Black",    // 5
        TAG_Result = "Result",  // 6
        TAG_FEN = "FEN",

        TAG_UNKNOWN_VALUE = "?",

        CP_DATE_FORMAT = "yyyy-MM-dd",

        FICS_UNKNOWN_RATING = "++++",
        FICS_NO_MOVE = "none",

        dummy_str = null;

    // STR - Seven Tag Roster
    public static final List<String> STR = Arrays.asList(TAG_Event, TAG_Site, TAG_Date, TAG_Round, TAG_White, TAG_Black, TAG_Result);

    public static final HashMap<String, Integer> old_glyph_translation;
    static {
        old_glyph_translation = new HashMap<>();
        old_glyph_translation.put("!", 1);
        old_glyph_translation.put("?", 2);
        old_glyph_translation.put("!!", 3);
        old_glyph_translation.put("??", 4);
        old_glyph_translation.put("!?", 5);
        old_glyph_translation.put("?!", 6);
    }

    public static final String[] titleTags = {
            TAG_White, TAG_Black, TAG_Date, TAG_Event, TAG_Site,
    };

    public static final int[] titleTagIndexes = {4, 5, 2, 0, 1 };

    static public class PGNException extends Exception {
        static final long serialVersionUID = 1989L;

        public PGNException(String t) {
            super(t);
        }

        public PGNException(Throwable t) {
            super(t);
        }
    }
}
