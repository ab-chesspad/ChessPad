package com.ab.pgn;

import java.io.IOException;

/**
 * chess board square
 * Created by Alexander Bootman on 8/6/16.
 */
public class Square {
    public int x = -1, y = -1;

    public Square() {
    }

    public Square(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Square(String sq) {
        if(sq != null && sq.length() == 2) {
            int x = fromX(sq.charAt(0));
            int y = fromY(sq.charAt(1));
            if( x >= 0 && x < Config.BOARD_SIZE && y >= 0 && y < Config.BOARD_SIZE) {
                this.x = x;
                this.y = y;
            }
        }
    }

    public int getX() {
        return this.x;
    }

    public String x2String() {
        return "" + (char) ('a' + getX());
    }

    public String y2String() {
        return "" + (char) ('1' + getY());
    }

    public int getY() {
        return this.y;
    }

    public Square clone() {
        return new Square(this.x, this.y);
    }

    public boolean equals(Square that) {
        return this.x == that.x && this.y == that.y;
    }

    public void serialize(BitStream.Writer writer) throws IOException {
        writer.write(x, 3);
        writer.write(y, 3);
    }

    public Square(BitStream.Reader reader) throws IOException {
        this.x = reader.read(3);
        this.y = reader.read(3);
    }

    public static String x2String(int x) {
        return "" + (char) ('a' + x);
    }

    public static int fromX(char x) {
        return x - 'a';
    }

    public static String y2String(int y) {
        return "" + (char) ('1' + y);
    }

    public static int fromY(char y) {
        return y - '1';
    }

    @Override
    public String toString() {
        return x2String() + y2String();
    }
}
