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

 * chess board square
 * Created by Alexander Bootman on 8/6/16.
 */
package com.ab.pgn;

import java.io.IOException;

public class Square {
    public int x = -1, y = -1;

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

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

    String x2String() {
        return "" + (char) ('a' + getX());
    }

    String y2String() {
        return "" + (char) ('1' + getY());
    }

    public int getY() {
        return this.y;
    }

    public Square clone() {
        return new Square(this.x, this.y);
    }

    @Override
    public int hashCode() {
        return (this.x <<3) + this.y;
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

    static int fromX(char x) {
        return x - 'a';
    }

    public static String y2String(int y) {
        return "" + (char) ('1' + y);
    }

    static int fromY(char y) {
        return y - '1';
    }

    @Override
    public String toString() {
        return x2String() + y2String();
    }
}
