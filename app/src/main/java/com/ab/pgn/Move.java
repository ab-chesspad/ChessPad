package com.ab.pgn;

import java.io.IOException;

/**
 * move data - pgnTree node
 * Created by Alexander Bootman on 8/6/16.
 */
public class Move {
    public Square from;                  // from.x == -1 for null move or a placeholder
    public Square to;
    public int piecePromoted = Config.EMPTY;
    public int moveFlags;
    public String comment;
    public int glyph;                          // one glyph per move, but can store more as bytes in the future

    transient public int pieceTaken = Config.EMPTY;
    transient public int piece = Config.EMPTY;
    transient public Pack pack = null;
    transient public Board snapshot;                     // board after the move
    transient public Move nextMove;
    transient public Move prevMove;
    transient public Move variation;

    public Move(int piece, Square from, Square to) {
        this.piece = piece;
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
        m.pieceTaken = this.pieceTaken;
        m.piecePromoted = this.piecePromoted;
        m.comment = this.comment;
        m.glyph = this.glyph;
        // do not copy references
        return m;
    }

    public void serialize(BitStream.Writer writer) throws IOException {
        from.serialize(writer);
        to.serialize(writer);
        writer.write(moveFlags, 16);
        if(piecePromoted == Config.EMPTY) {
            writer.write(0, 1);
        } else {
            writer.write(1, 1);
            writer.write((piecePromoted & ~Config.PIECE_COLOR), 3);
        }
        if(glyph == 0) {
            writer.write(0, 1);
        } else {
            writer.write(1, 1);
            writer.write(glyph, 8);
        }
        if(comment == null) {   // empty?
            writer.write(0, 1);
        } else {
            writer.write(1, 1);
            writer.writeString(comment);
        }
    }

    public Move(BitStream.Reader reader, Board board) throws IOException {
        from = new Square(reader);
        to = new Square(reader);
        moveFlags = reader.read(16);
        if(reader.read(1) == 1) {
            piecePromoted = reader.read(3) | (moveFlags & Config.PIECE_COLOR);
        }
        if(reader.read(1) == 1) {
            glyph = reader.read(8);
        }
        if(reader.read(1) == 1) {
            comment = reader.readString();
        }

        piece = board.getPiece(from);
        pieceTaken = board.getPiece(to);    // todo: verify en passant
        snapshot = board.clone();
        snapshot.doMove(this);
        pack = new Pack(snapshot);
    }

    public boolean equals(Move that) {
        return this.from.equals(that.from) && this.to.equals(that.to) && this.piecePromoted == that.piecePromoted;
    }

    public String toNumString() {
        int plyNum;
        if(snapshot != null) {
            plyNum = snapshot.plyNum;
        } else if(this.prevMove != null && this.prevMove.snapshot != null) {
            plyNum = this.prevMove.snapshot.plyNum + 1;
        } else {
            plyNum = 0;
        }
        return "" + ((plyNum + 1) / 2) + ". "
                + ((plyNum & 1) == 1 ? "" : "... ")
                + toString();
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

            if (pieceTaken != Config.EMPTY ||
                    getColorlessPiece() == Config.PAWN && to.x != from.x) {
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
