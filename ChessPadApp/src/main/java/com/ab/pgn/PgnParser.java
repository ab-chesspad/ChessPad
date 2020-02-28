package com.ab.pgn;

import java.util.StringTokenizer;

/**
 *
 * Created by Alexander Bootman on 8/6/16.
 */
class PgnParser {
    private static final boolean DEBUG = false;
    private static final boolean COMMENTS_USE_MATCHING_BRACES = false;

    private static final String
            DELIMITERS = "[ \t\n]\\." + Config.COMMENT_OPEN + Config.VARIANT_OPEN + Config.VARIANT_CLOSE + Config.PGN_OLD_GLYPHS,
            dummy_str = null;


    static void parseMoves(String moveText, MoveTextHandler moveTextHandler, CpFile.ProgressObserver progressObserver) throws Config.PGNException {
        StringTokenizer st = new StringTokenizer(moveText, DELIMITERS, true);
        int offset = 0;
        CpFile.ProgressNotifier progressNotifier = new CpFile.ProgressNotifier(progressObserver);
        String oldGlyph = "";
        String token;
        String ch;
        StringBuilder comment;
        int count;
        try {
            while (st.hasMoreTokens()) {
                token = st.nextToken(DELIMITERS).trim();
                if(DEBUG) {
                    System.out.println(String.format("%d: \"%s\"", offset, token));
                }
                offset += token.length() + 1;   // approximate
                progressNotifier.setOffset(offset, moveText.length());
                if (token.isEmpty() || token.equals(".")) {
                    continue;
                }
                if (Character.isDigit(token.charAt(0)) && token.length() < 2) {
                    continue;
                }
                ch = token.substring(0, 1);
                if (Config.PGN_GLYPH.equals(ch)) {
                    moveTextHandler.onGlyph(token);
                    continue;
                } else if (Config.PGN_OLD_GLYPHS.contains(ch)) {
                    oldGlyph += token;
                    continue;
                }
                if (!oldGlyph.isEmpty()) {
                    Integer newGlyph = Config.old_glyph_translation.get(oldGlyph);
                    if (newGlyph != null) {
                        moveTextHandler.onGlyph("$" + newGlyph.toString());
                    }
                    oldGlyph = "";
                }
                if (Config.COMMENT_OPEN.equals(ch)) {
                    // https://en.wikipedia.org/wiki/Portable_Game_Notation#Comments:
                    // Comments are inserted by either a ; (a comment that continues to the end of the line) or a { (which continues until a matching }
                    // we do not support single-line comments
                    comment = new StringBuilder();
                    count = 1;
                    do {
                        if (COMMENTS_USE_MATCHING_BRACES) {
                            // continue until a matching close brace found
                            token = st.nextToken(Config.COMMENT_CLOSE + Config.COMMENT_OPEN);
                        } else {
                            // find 1st close brace
                            token = st.nextToken(Config.COMMENT_CLOSE);
                        }
                        if (DEBUG) {
                            System.out.println(String.format("comment: \"%s\"", token));
                        }
                        comment.append(token);
                        if (token.equals(Config.COMMENT_CLOSE)) {
                            --count;
                        } else if (token.equals(Config.COMMENT_OPEN)) {
                            ++count;
                        }
                    } while (count > 0);
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
                    if (!moveTextHandler.onMove(token)) {
                        return;         // abort
                    }
                } else if (Config.VARIANT_OPEN.equals(ch)) {
                    moveTextHandler.onVariantOpen();
                } else if (Config.VARIANT_CLOSE.equals(ch)) {
                    moveTextHandler.onVariantClose();
                }
            }
        } catch (Throwable t) {
            throw new Config.PGNException(t);
        }
    }

    public interface MoveTextHandler {
        void onComment(String value);
        void onGlyph(String value);
        boolean onMove(String moveText) throws Config.PGNException;     // return false to abort
        void onVariantOpen();
        void onVariantClose();
    }

}