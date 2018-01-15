package com.ab.pgn;

/**
 *
 * Created by Alexander Bootman on 10/29/17.
 */
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
            newMove.piece = Config.KING;
            newMove.from.x = 4;
            newMove.to.x = moveText.length() == 3 ? 6 : 2;
            newMove.from.y =
            newMove.to.y = (newMove.moveFlags & Config.FLAGS_BLACK_MOVE) == 0 ? 0 : 7;
            newMove.moveFlags |= Config.FLAGS_CASTLE;
        } else {
            ch = moveText.charAt(i);
            if (Character.isUpperCase(ch)) {
                // dxe8=Q, etc.
                newMove.piecePromoted = Config.FEN_PIECES.indexOf(ch);
                newMove.piecePromoted |= (newMove.moveFlags & Config.FLAGS_BLACK_MOVE);
                i -= 2;        // verify '='?
            }

            int y = Square.fromY(moveText.charAt(i));
            if (y < 0 || y > 7)
                throw new Config.PGNException("invalid move " + _moveText);
            int x = Square.fromX(moveText.charAt(--i));
            if (x < 0 || x > 7)
                throw new Config.PGNException("invalid move " + _moveText);
            newMove.to.x = x;
            newMove.to.y = y;
//            newMove.pieceTaken = moveHandler.getBoard().getPiece(x, y);  // can be empty: en passant

            // Re5, Rae5, Rxe5, R1xe5, Rbxe5, e4, dxe5, dxe8=Q+, etc.
            ch = moveText.charAt(0);
            if (Character.isUpperCase(ch)) {
                // Re5, Rxe5, R1xe5, Rbxe5, etc.
                newMove.piece = Config.FEN_PIECES.indexOf(ch);
            } else {
                newMove.piece = Config.PAWN;
            }

            if (--i > 0) {
                ch = moveText.charAt(i);
                if (ch == Config.MOVE_CAPTURE.charAt(0)) {
                    if (newMove.piece == Config.PAWN) {
                        newMove.from.x = Square.fromX(moveText.charAt(0));
                    }
                    --i;
                }
            }

            // ambiguity:
            if (i >= 0) {
                ch = moveText.charAt(i);
                if (Character.isDigit(ch)) {
                    newMove.from.y = Square.fromY(ch);
                    if (newMove.piece != Config.PAWN) {
                        newMove.moveFlags |= Config.FLAGS_Y_AMBIG;
                    }
                    --i;
                }
            }
            if (i >= 0) {
                ch = moveText.charAt(i);
                if (ch >= 'a' && ch <= 'h') {
                    newMove.from.x = Square.fromX(ch);
                    if (newMove.piece != Config.PAWN) {
                        newMove.moveFlags |= Config.FLAGS_X_AMBIG;
                    }
                    --i;
                }
            }

        }
        newMove.piece |= newMove.moveFlags & Config.FLAGS_BLACK_MOVE;
    }
}
