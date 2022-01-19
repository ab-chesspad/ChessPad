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

 * move - PgnGraph edge, stored in Board - PgnGraph vertex
 * todo: optimize serialization
 * Created by Alexander Bootman on 8/6/16.
 */
package com.ab.pgn;

import java.io.IOException;

public class Move {
    final static int
        // moveData:
        COORD_MASK = Board.COORD_MASK,                          // 0x0007,
        COORD_LENGTH = Board.COORD_LENGTH,                      //  3
        FROM_X_OFFSET = 0,
        FROM_Y_OFFSET = FROM_X_OFFSET + COORD_LENGTH,           //  3
        TO_X_OFFSET = FROM_Y_OFFSET + COORD_LENGTH,             //  6
        TO_Y_OFFSET = TO_X_OFFSET + COORD_LENGTH,               //  9
        PIECE_PROMOTED_OFFSET = TO_Y_OFFSET + COORD_LENGTH,     // 12
        PIECE_PROMOTED_LENGTH = 2,
        PIECE_PROMOTED_MASK = 0x0003,
        GLYPH_OFFSET = PIECE_PROMOTED_OFFSET + PIECE_PROMOTED_LENGTH,     // 14
        GLYPH_LENGTH = 8,
        GLYPH_MASK = 0x00ff,
        // 22
        PIECE_OFFSET = GLYPH_OFFSET + GLYPH_LENGTH,             // 22
        PIECE_LENGTH = 3,
        PIECE_MASK = 0x0007,
        MOVEDATA_TOTAL_LEN = PIECE_OFFSET + PIECE_LENGTH,
        MOVEDATA_MASK = (1 << MOVEDATA_TOTAL_LEN) - 1,

    // there is never a next move todo: cleanup flags!
        HAS_NEXT_MOVE_OFFSET = MOVEDATA_TOTAL_LEN,
        HAS_VARIATION_OFFSET = HAS_NEXT_MOVE_OFFSET + 1,
        HAS_COMMENT_OFFSET = HAS_VARIATION_OFFSET + 1,

    // there is a variation when variation != null todo: cleanup flags!
        HAS_NEXT_MOVE = 1 << HAS_NEXT_MOVE_OFFSET,
        HAS_VARIATION = 1 << HAS_VARIATION_OFFSET,
        HAS_COMMENT = 1 << HAS_COMMENT_OFFSET,

        FROM_X_SET_OFFSET = 30,
        FROM_Y_SET_OFFSET = 31,

        dummy_int = 0;

    private int moveData;        // from, to, piecePromoted, glyph, piece

    public int moveFlags;
    public String comment;
    public Move variation;

    transient int[] packData;            // board after the move

    public Move(Board board, Square from, Square to) {
        this.moveFlags = board.getFlags();
        this.setPiece(board.getPiece(from.getX(), from.getY()));
        this.setFrom(from);
        this.setTo(to);
    }

    public Move(int flags) {
        this.moveFlags = flags;
        this.moveData = 0;  // redundant
        markFromXUnset();
        markFromYUnset();
    }

    public Move clone() {
        Move m = new Move(this.moveFlags);
        m.moveData = this.moveData;
        m.comment = this.comment;
        // do not copy references
        return m;
    }

    int getFromX() {
        return Util.getValue(moveData, COORD_MASK, FROM_X_OFFSET);
    }

    int getFromY() {
        return Util.getValue(moveData, COORD_MASK, FROM_Y_OFFSET);
    }

    public Square getFrom() {
        return new Square(getFromX(), getFromY());
    }

    void setFromX(int x) {
        moveData = Util.setValue(moveData, x, COORD_MASK, FROM_X_OFFSET);
        markFromXSet();
    }

    void setFromY(int y) {
        moveData = Util.setValue(moveData, y, COORD_MASK, FROM_Y_OFFSET);
        markFromYSet();
    }

    void setFrom(int x, int y) {
        setFromX(x);
        setFromY(y);
    }

    public void setFrom(Square from) {
        setFromX(from.getX());
        setFromY(from.getY());
    }

    boolean isFromSet() {
        return isFromXSet() && isFromYSet();
    }


    boolean isFromXSet() {
        return Util.getValue(moveData, 1, FROM_X_SET_OFFSET) == 0;
    }

    private void markFromXSet() {
        moveData = Util.clearBits(moveData, 1, 1, FROM_X_SET_OFFSET);
    }

    private void markFromXUnset() {
        moveData = Util.setBits(moveData, 1, 1, FROM_X_SET_OFFSET);
    }

    boolean isFromYSet() {
        return Util.getValue(moveData, 1, FROM_Y_SET_OFFSET) == 0;
    }

    private void markFromYSet() {
        moveData = Util.clearBits(moveData, 1, 1, FROM_Y_SET_OFFSET);
    }

    private void markFromYUnset() {
        moveData = Util.setBits(moveData, 1, 1, FROM_Y_SET_OFFSET);
    }

    int getToX() {
        return Util.getValue(moveData, COORD_MASK, TO_X_OFFSET);
    }

    int getToY() {
        return Util.getValue(moveData, COORD_MASK, TO_Y_OFFSET);
    }

    public Square getTo() {
        return new Square(getToX(), getToY());
    }

    void setToX(int x) {
        moveData = Util.setValue(moveData, x, COORD_MASK, TO_X_OFFSET);
    }

    void setToY(int y) {
        moveData = Util.setValue(moveData, y, COORD_MASK, TO_Y_OFFSET);
    }

    void setTo(int x, int y) {
        setToX(x);
        setToY(y);
    }

    public void setTo(Square to) {
        setToX(to.getX());
        setToY(to.getY());
    }

    int getPiecePromoted() {
        if (!isPromotion()) {
            return Config.EMPTY;
        }
        int bits = Util.getValue(moveData, PIECE_PROMOTED_MASK, PIECE_PROMOTED_OFFSET);
        return (bits << 1) + Config.QUEEN + (moveFlags & Config.BLACK);
    }

    public void setPiecePromoted(int piecePromoted) {
        int bits = (piecePromoted - Config.QUEEN) >> 1;
        moveData = Util.setValue(moveData, bits, PIECE_PROMOTED_MASK, PIECE_PROMOTED_OFFSET);
    }

    int getGlyph() {
        return Util.getValue(moveData, GLYPH_MASK, GLYPH_OFFSET);
    }

    public void setGlyph(int glyph) {
        moveData = Util.setValue(moveData, glyph, GLYPH_MASK, GLYPH_OFFSET);
    }

    public int getPiece() {
        int bits = Util.getValue(moveData, PIECE_MASK, PIECE_OFFSET);
        if (bits == 0) {
            return Config.EMPTY;
        }
        return (bits << 1) + (moveFlags & Config.BLACK);
    }

    public void setPiece(int piece) {
        int bits = piece >> 1;
        moveData = Util.setValue(moveData, bits, PIECE_MASK, PIECE_OFFSET);
    }

    boolean hasVariation() {
        return (this.moveData & Move.HAS_VARIATION) != 0;
    }

    boolean hasNextMove() {
        return (this.moveData & Move.HAS_NEXT_MOVE) != 0;
    }

    void cleanupSerializationFlags() {
        this.moveData &= ~(HAS_VARIATION | HAS_NEXT_MOVE | HAS_COMMENT);
    }

    public boolean isPromotion() {
        return getPiece() == Config.WHITE_PAWN && getTo().getY() == Config.BOARD_SIZE - 1 ||
                getPiece() == Config.BLACK_PAWN && getTo().getY() == 0;
    }

    private int promotedToSerialized(int piece) {
        return (piece - Config.QUEEN) >> 1;
    }

    private int serializedToPromoted(int serialized, int flags) {
        return (serialized << 1) + Config.QUEEN + (flags & Config.BLACK);
    }

    public void serialize(BitStream.Writer writer) throws Config.PGNException {
        serialize(writer, true);
    }

    public void serialize(BitStream.Writer writer, boolean serializePack) throws Config.PGNException {
        try {
            getFrom().serialize(writer);
            getTo().serialize(writer);
            // serialize flags from FLAGS_ENPASSANT_OK to FLAGS_STALEMATE
            writer.write(moveFlags >> 6, 8);
            if (isPromotion()) {
                writer.write(promotedToSerialized(getPiecePromoted()), 2);
            }
            if (getGlyph() == 0) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                writer.write(getGlyph(), 8);
            }
            if (comment == null) {   // empty?
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                writer.writeString(comment);
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
        if (serializePack) {
            new Pack(this.packData).serialize(writer);
        }
    }

    public Move(BitStream.Reader reader, Board previousBoard) throws Config.PGNException {
        this(reader, previousBoard, true);
    }

    public Move(BitStream.Reader reader, Board previousBoard, boolean unserializePack) throws Config.PGNException {
        try {
            setFrom(new Square(reader));
            setTo(new Square(reader));
            moveFlags = reader.read(8) << 6;
            if (previousBoard != null) {
                moveFlags |= previousBoard.getFlags() & Config.FLAGS_BLACK_MOVE;
                setPiece(previousBoard.getPiece(getFrom()));
                if (previousBoard.getPiece(getTo()) != Config.EMPTY || previousBoard.isEnPassant(this)) {
                    moveFlags |= Config.FLAGS_CAPTURE;
                }
            }
            if (isPromotion()) {
                setPiecePromoted(serializedToPromoted(reader.read(2), moveFlags));
            }
            if (reader.read(1) == 1) {
                setGlyph(reader.read(8));
            }
            if (reader.read(1) == 1) {
                comment = reader.readString();
            }
            if (unserializePack) {
                Pack pack = new Pack(reader);
                packData = pack.getPackData();
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    // cannot do full check with flags because it is called in PgnGraph.addMove
    public boolean isSameAs(Move that) {
        if (that == null) {
            return false;
        }
        if ((this.moveFlags & Config.FLAGS_NULL_MOVE) != 0 || (that.moveFlags & Config.FLAGS_NULL_MOVE) != 0) {
            return (this.moveFlags & Config.FLAGS_NULL_MOVE) == (that.moveFlags & Config.FLAGS_NULL_MOVE);
        }
        if (this.getPiece() != that.getPiece()) {
            return false;
        }
        if (this.getPiecePromoted() != that.getPiecePromoted()) {
            return false;
        }
        if (!this.getFrom().equals(that.getFrom())) {
            return false;
        }
        return this.getTo().equals(that.getTo());
    }

    Move getVariation() {
        return variation;
    }

    void setVariation(Move variation) {
        this.variation = variation;
    }

    boolean isNullMove() {
        return (moveFlags & Config.FLAGS_NULL_MOVE) != 0;
    }

    void setNullMove() {
        moveFlags |= Config.FLAGS_NULL_MOVE;
    }

    public String toCommentedString() {
        String res =  toString(false);
        if (getGlyph() > 0) {
            res += "$" + getGlyph() + " ";
        }
        if (comment != null) {
            res += String.format("{%s}", comment);
        }
        return res;
    }

    public String toString() {
        return toString(false);
    }

    public String toString(boolean longNotation) {
        StringBuilder res = new StringBuilder();
        if (isNullMove()) {
            res.append(Config.PGN_NULL_MOVE).append(" ");
            return new String(res);
        }

        if ((moveFlags & Config.FLAGS_CASTLE) != 0) {
            if (getTo().getX() - getFrom().getX() > 0) {
                res.append(Config.PGN_K_CASTLE);
            } else {
                res.append(Config.PGN_Q_CASTLE);
            }
        } else {
            if ((getPiece() & ~Config.BLACK) != Config.PAWN) {
                res.append(Config.FEN_PIECES.charAt(getPiece() & ~Config.BLACK));
            }

            if ((longNotation || (moveFlags & Config.FLAGS_X_AMBIG) != 0)) {
                res.append(getFrom().x2String());
            }
            if ((longNotation || (moveFlags & Config.FLAGS_Y_AMBIG) != 0)) {
                res.append(getFrom().y2String());
            }
            if (!longNotation && getColorlessPiece() == Config.PAWN && getTo().x != getFrom().x) {
                res.append(getFrom().x2String());
            }

            if ((moveFlags & Config.FLAGS_CAPTURE) != 0) {
                res.append(Config.MOVE_CAPTURE);
            }

            res.append(getTo().toString());

            if (getPiecePromoted() != Config.EMPTY) {
                res.append(Config.MOVE_PROMOTION).append(Config.FEN_PIECES.charAt(getPiecePromoted() & ~Config.BLACK));
            }
        }
        if ((moveFlags & Config.FLAGS_CHECKMATE) != 0) {
            res.append(Config.MOVE_CHECKMATE);
        } else if ((moveFlags & Config.FLAGS_CHECK) != 0) {
            res.append(Config.MOVE_CHECK);
        }
        return new String(res.append(" "));
    }

    int getColorlessPiece() {
        return this.getPiece() & ~Config.PIECE_COLOR;
    }
}
