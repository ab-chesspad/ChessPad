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

* Opening book for UCI engine
* many repeating comments (opening names) stored in commentStrings,
* the move comments are in a special format, refer to this array
* Created by Alexander Bootman on 8/6/19.
 */
package com.ab.pgn;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

public class Book extends PgnGraph {
    String[] commentStrings;            // all comment strings, moves contain references on them

    Book() {
        super();
    }

    public Book(InputStream is, long totalLength) throws Config.PGNException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is)) ) {
            String line = br.readLine();
            int length = line.length();
            int size = Integer.valueOf(line);
            commentStrings = new String[size];
            for (int i = 0; i < size; ++i) {
                line = br.readLine();
                length += line.length();
                commentStrings[i] = line;
            }
            StringBuilder sb = new StringBuilder((int)totalLength - length);
            while((line = br.readLine()) != null) {
                sb.append(line);
            }
            String moves = new String(sb);
            parseMoves(moves);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public String getComment(String bookComment) {
        if (bookComment == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String[] parts = bookComment.split(Config.BOOK_COMMENT_STRING_TAG);
        String sep = "";
        for (String part : parts) {
            try {
                int indx = Integer.valueOf(part);
                sb.append(sep).append(commentStrings[indx]);
                sep = "; ";
            } catch (Exception e) {
                // ignore
            }
        }
        return new String(sb);
    }

    public List<Move> getMoves(Board board) {
        Pack key;
        try {
            key = new Pack(board.pack());
        } catch (Config.PGNException e) {
            e.printStackTrace();
            return null;
        }
        board = positions.get(key);
        Move move;
        if (board == null || (move = board.getMove()) == null) {
            return null;
        }
        List<Move> moves = new LinkedList<>();
        do {
            moves.add(move);
            move = move.getVariation();
        } while (move != null);
        return moves;
    }
}
