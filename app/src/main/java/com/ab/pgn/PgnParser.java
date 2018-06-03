package com.ab.pgn;

import java.util.Observer;
import java.util.StringTokenizer;

/**
 *
 * Created by Alexander Bootman on 8/6/16.
 */
public class PgnParser {

    public static final String
            DELIMITERS = "[ \t\n]\\." + Config.COMMENT_OPEN + Config.VARIANT_OPEN + Config.VARIANT_CLOSE + Config.PGN_OLD_GLYPHS,
            dummy_str = null;


    public static void parseMoves(String moveText, MoveTextHandler moveTextHandler, PgnItem.ProgressObserver progressObserver) throws Config.PGNException {
        StringTokenizer st = new StringTokenizer(moveText, DELIMITERS, true);
        int offset = 0;
        PgnItem.ProgressNotifier progressNotifier = new PgnItem.ProgressNotifier(progressObserver);
        while (st.hasMoreTokens()) {
            String token = st.nextToken(DELIMITERS).trim();
            offset += token.length() + 1;   // approximate
            progressNotifier.setOffset(offset, moveText.length());
            token = token.trim();
            if(token.isEmpty() || token.equals(".")) {
                continue;
            }
            if(Character.isDigit(token.charAt(0)) && token.length() < 2) {
                continue;
            }
            String ch = token.substring(0, 1);
            if (Config.PGN_GLYPH.equals(ch)) {
                moveTextHandler.onGlyph(token);
            } else if (Config.COMMENT_OPEN.equals(ch)) {
                // https://en.wikipedia.org/wiki/Portable_Game_Notation#Comments:
                // Comments are inserted by either a ; (a comment that continues to the end of the line) or a { (which continues until a matching }
                // we do not support single-line comments
                StringBuilder comment = new StringBuilder();
                int count = 1;
                do {
                    // continue until a matching close bracket found
                    token = st.nextToken(Config.COMMENT_CLOSE + Config.COMMENT_OPEN);
                    comment.append(token);
                    if(token.equals(Config.COMMENT_CLOSE)) {
                        --count;
                    } else if(token.equals(Config.COMMENT_OPEN)) {
                        ++count;
                    }
                } while(count > 0);
                offset += comment.length() + 1;   // approximate
                progressNotifier.setOffset(offset, moveText.length());
                comment.deleteCharAt(comment.length() - 1);
                token = new String(comment).replaceAll("(?s)\\s+", " ");
                if (!Config.COMMENT_CLOSE.equals(token.substring(0, 1))) {    // ignore empty comments
                    moveTextHandler.onComment(token);
                }
            } else if (Character.isLetter(ch.charAt(0))
                    || token.startsWith(Config.PGN_K_CASTLE_ALT)
                    || token.equals(Config.PGN_NULL_MOVE)
                    || token.equals(Config.PGN_NULL_MOVE_ALT)
                    ) {
                if(!moveTextHandler.onMove(token)) {
                    return;         // abort
                }
            } else if (Config.VARIANT_OPEN.equals(ch)) {
                moveTextHandler.onVariantOpen();
            } else if (Config.VARIANT_CLOSE.equals(ch)) {
                moveTextHandler.onVariantClose();
            }
        }
    }

    public interface MoveTextHandler {
        void onComment(String value);

        void onGlyph(String value);

        // return false to abort
        boolean onMove(String moveText) throws Config.PGNException;

        void onVariantOpen();

        void onVariantClose();

    }

}