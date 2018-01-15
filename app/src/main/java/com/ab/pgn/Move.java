package com.ab.pgn;

import java.io.IOException;

/**
 * move - PgnGraph edge, stored in Board - PgnGraph vertex
 * Created by Alexander Bootman on 8/6/16.
 */
public class Move {
    public Square from;                         // from.x == -1 for null move or a placeholder
    public Square to;
    public int piecePromoted = Config.EMPTY;
    public int moveFlags;
    public String comment;
    public int glyph;                           // one glyph per move, but can store more as bytes in the future

    transient public int piece = Config.EMPTY;
    transient public int[] packData;            // board after the move
    transient public Move variation;

    public Move(Board board, Square from, Square to) {
        this.moveFlags = board.getFlags();
        this.piece = board.getPiece(from.getX(), from.getY());
        this.from = from;
        this.to = to;
    }

    public Move(int flags) {
        this.moveFlags = flags;
        from = new Square();
        to = new Square();
    }

    public Move clone() {
        Move m = new Move(this.moveFlags);
        m.from = this.from.clone();
        m.to = this.to.clone();
        m.piece = this.piece;
        m.piecePromoted = this.piecePromoted;
        m.comment = this.comment;
        m.glyph = this.glyph;
        // do not copy references
        return m;
    }

    public void serialize(BitStream.Writer writer) throws Config.PGNException {
        try {
            from.serialize(writer);
            to.serialize(writer);
            writer.write(moveFlags, 16);
            if (piecePromoted == Config.EMPTY) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                writer.write((piecePromoted & ~Config.PIECE_COLOR), 3);
            }
            if (glyph == 0) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                writer.write(glyph, 8);
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
        new Pack(this.packData).serialize(writer);
    }

    public Move(BitStream.Reader reader) throws Config.PGNException {
        try {
            from = new Square(reader);
            to = new Square(reader);
            moveFlags = reader.read(16);
            if (reader.read(1) == 1) {
                piecePromoted = reader.read(3) | (moveFlags & Config.PIECE_COLOR);
            }
            if (reader.read(1) == 1) {
                glyph = reader.read(8);
            }
            if (reader.read(1) == 1) {
                comment = reader.readString();
            }
            Pack pack = new Pack(reader);
            packData = pack.getPackData();
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public Move(BitStream.Reader reader, Board board) throws Config.PGNException {
        try {
            from = new Square(reader);
            to = new Square(reader);
            moveFlags = reader.read(16);
            if (reader.read(1) == 1) {
                piecePromoted = reader.read(3) | (moveFlags & Config.PIECE_COLOR);
            }
            if (reader.read(1) == 1) {
                glyph = reader.read(8);
            }
            if (reader.read(1) == 1) {
                comment = reader.readString();
            }

            piece = board.getPiece(from);

            Pack pack = new Pack(reader);
            packData = pack.getPackData();
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public boolean isSameAs(Move that) {
        if(that == null) {
            return false;
        }

        if((this.moveFlags & Config.FLAGS_NULL_MOVE) != 0 || (that.moveFlags & Config.FLAGS_NULL_MOVE) != 0) {
            return (this.moveFlags & Config.FLAGS_NULL_MOVE) == (that.moveFlags & Config.FLAGS_NULL_MOVE);
        }
        if(this.moveFlags != that.moveFlags) {
            return false;
        }
        if(this.piece != that.piece) {
            return false;
        }
        if(this.piecePromoted != that.piecePromoted) {
            return false;
        }
        if(!this.from.equals(that.from)) {
            return false;
        }
        return new Pack(this.packData).equals(new Pack(that.packData));
    }

    public Move getVariation() {
        return variation;
    }

    public void setVariation(Move variation) {
        this.variation = variation;
    }

    public boolean isNullMove() {
        return (moveFlags & Config.FLAGS_NULL_MOVE) != 0;
    }

    public void setNullMove() {
        moveFlags |= Config.FLAGS_NULL_MOVE;
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
            if (to.getX() - from.getX() > 0) {
                res.append(Config.PGN_K_CASTLE);
            } else {
                res.append(Config.PGN_Q_CASTLE);
            }
        } else {
            if ((piece & ~Config.BLACK) != Config.PAWN) {
                res.append(Config.FEN_PIECES.charAt(piece & ~Config.BLACK));
            }

            if ((longNotation || (moveFlags & Config.FLAGS_X_AMBIG) != 0)) {
                res.append(from.x2String());
            }
            if ((longNotation || (moveFlags & Config.FLAGS_Y_AMBIG) != 0)) {
                res.append(from.y2String());
            }
            if (!longNotation && getColorlessPiece() == Config.PAWN && to.x != from.x) {
                res.append(from.x2String());
            }

            if ((moveFlags & Config.FLAGS_CAPTURE) != 0
//                    || getColorlessPiece() == Config.PAWN && to.x != from.x
                    ) {
                res.append(Config.MOVE_CAPTURE);
            }

            res.append(to.toString());

            if (piecePromoted != Config.EMPTY) {
                res.append(Config.MOVE_PROMOTION).append(Config.FEN_PIECES.charAt(piecePromoted & ~Config.BLACK));
            }
        }
        if ((moveFlags & Config.FLAGS_CHECKMATE) != 0) {
            res.append(Config.MOVE_CHECKMATE);
        } else if ((moveFlags & Config.FLAGS_CHECK) != 0) {
            res.append(Config.MOVE_CHECK);
        }
        return new String(res.append(" "));
    }

    public int getColorlessPiece() {
        return this.piece & ~Config.PIECE_COLOR;
    }
}
