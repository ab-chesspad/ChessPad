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

 * Created by Alexander Bootman on 10/29/17.
 */
package com.ab.pgn;

public class Util {
    // bitstore methods:
    public static int setBits(int bitStorage, int value, int mask, int offset) {
        return bitStorage | ((value & mask) << offset);
    }

    public static int clearBits(int bitStorage, int value, int mask, int offset) {
        return bitStorage & ~((value & mask) << offset);
    }

    public static int invertBits(int bitStorage, int value, int mask, int offset) {
        return bitStorage ^ ((value & mask) << offset);
    }

    public static int setValue(int bitStorage, int value, int mask, int offset) {
        bitStorage = clearBits( bitStorage,-1, mask, offset);
        return setBits(bitStorage, value, mask, offset);
    }

    public static int getValue(int bitStorage, int mask, int offset) {
        return (bitStorage >> offset) & mask;
    }

    public static int incrementValue(int bitStorage, int increment, int mask, int offset) {
        int value = getValue(bitStorage, mask, offset);
        return setValue(bitStorage, value + increment, mask, offset);
    }

    // newMove needs FLAGS_BLACK_MOVE to be set
    public static void parseMove(Move newMove, String _moveText) throws Config.PGNException {
        if (_moveText.equals(Config.PGN_NULL_MOVE) || _moveText.equals(Config.PGN_NULL_MOVE_ALT)) {
            newMove.setNullMove();
            return;
        }
        char ch;
        int i = _moveText.length();
        while (--i > 0) {
            ch = _moveText.charAt(i);
            if (ch == Config.MOVE_CHECK.charAt(0)) {
                newMove.moveFlags |= Config.FLAGS_CHECK;
            } else if (ch == Config.MOVE_CHECKMATE.charAt(0)) {
                newMove.moveFlags |= Config.FLAGS_CHECKMATE;
            } else if (Config.PGN_OLD_GLYPHS.indexOf(ch) >= 0) {
                // do nothing
            } else if (Character.isLetterOrDigit(ch)) {
                break;
            }
        }

        String moveText = _moveText.substring(0, i + 1);
        if (moveText.equalsIgnoreCase(Config.PGN_K_CASTLE)
                || moveText.equalsIgnoreCase(Config.PGN_Q_CASTLE)
                || moveText.equalsIgnoreCase(Config.PGN_K_CASTLE_ALT)
                || moveText.equalsIgnoreCase(Config.PGN_Q_CASTLE_ALT)) {
            // 0-0, O-O, o-o-o, etc.
            newMove.setPiece(Config.KING);
            newMove.setFromX(4);
            newMove.setToX(moveText.length() == 3 ? 6 : 2);
            int y = (newMove.moveFlags & Config.FLAGS_BLACK_MOVE) == 0 ? 0 : 7;
            newMove.setFromY(y);
            newMove.setToY(y);
            newMove.moveFlags |= Config.FLAGS_CASTLE;
        } else {
            ch = moveText.charAt(i);
            if (Character.isUpperCase(ch)) {
                // dxe8=Q, etc.
                if(Config.PROMOTION_PIECES.indexOf(ch) < 0) {
                    throw new Config.PGNException("invalid promotion " + _moveText);
                }
                newMove.setPiecePromoted(Config.FEN_PIECES.indexOf(ch));
                i -= 2;        // verify '='?
            }

            int y = Square.fromY(moveText.charAt(i));
            if (y < 0 || y > 7)
                throw new Config.PGNException("invalid move " + _moveText);
            int x = Square.fromX(moveText.charAt(--i));
            if (x < 0 || x > 7)
                throw new Config.PGNException("invalid move " + _moveText);
            newMove.setTo(x, y);

            // Re5, Rae5, Rxe5, R1xe5, Rbxe5, e4, dxe5, dxe8=Q+, etc.
            ch = moveText.charAt(0);
            if (Character.isUpperCase(ch)) {
                // Re5, Rxe5, R1xe5, Rbxe5, etc.
                newMove.setPiece(Config.FEN_PIECES.indexOf(ch));
            } else {
                newMove.setPiece(Config.PAWN);
            }

            if (--i > 0) {
                ch = moveText.charAt(i);
                if (ch == Config.MOVE_CAPTURE.charAt(0)) {
                    if (newMove.getColorlessPiece() == Config.PAWN) {
                        newMove.setFromX(Square.fromX(moveText.charAt(0)));
                    }
                    --i;
                }
            }

            // ambiguity:
            if (i >= 0) {
                ch = moveText.charAt(i);
                if (Character.isDigit(ch)) {
                    newMove.setFromY(Square.fromY(ch));
                    if (newMove.getColorlessPiece() != Config.PAWN) {
                        newMove.moveFlags |= Config.FLAGS_Y_AMBIG;
                    }
                    --i;
                }
            }
            if (i >= 0) {
                ch = moveText.charAt(i);
                if (ch >= 'a' && ch <= 'h') {
                    newMove.setFromX(Square.fromX(ch));
                    if (newMove.getColorlessPiece() != Config.PAWN) {
                        newMove.moveFlags |= Config.FLAGS_X_AMBIG;
                    }
                    --i;
                }
            }

        }
    }
}
