/*
     Copyright (C) 2021-2022	Alexander Bootman, alexbootman@gmail.com

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

 * Created by Alexander Bootman on 8/6/16.
 */
package com.ab.pgn;

import com.ab.pgn.io.CpFile;

import java.util.Locale;
import java.util.StringTokenizer;

class PgnParser {
    private static final boolean DEBUG = false;
    private static final boolean COMMENTS_USE_MATCHING_BRACES = false;

    private static final String
        DELIMITERS = "[ \t\n]\\." + Config.COMMENT_OPEN + Config.VARIANT_OPEN + Config.VARIANT_CLOSE + Config.PGN_OLD_GLYPHS,
        dummy_str = null;


    static void parseMoves(String moveText, MoveTextHandler moveTextHandler, boolean showProgress) throws Config.PGNException {
        StringTokenizer st = new StringTokenizer(moveText, DELIMITERS, true);
        int offset = 0;
        if (showProgress) {
            CpFile.progressNotifier.setTotalLength(moveText.length());
        }
        String oldGlyph = "";
        String token;
        String ch;
        StringBuilder comment;
        int count;
        try {
            while (st.hasMoreTokens()) {
                token = st.nextToken(DELIMITERS).trim();
                if (DEBUG) {
                    System.out.println(String.format(Locale.US, "%d: \"%s\"", offset, token));
                }
                offset += token.length() + 1;   // approximate
                if (showProgress) {
                    CpFile.progressNotifier.setOffset(offset);
                }
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
                        moveTextHandler.onGlyph("$" + newGlyph);
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
                    if (showProgress) {
                        CpFile.progressNotifier.setOffset(offset);
                    }
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
        } catch (OutOfMemoryError e) {
            throw e;
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