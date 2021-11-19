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

 * Created by Alexander Bootman on 11/27/16.
 */
package com.ab.droid.chesspad;

import com.ab.pgn.Board;
import com.ab.pgn.Square;

public interface BoardHolder {
    Board getBoard();
    int getBoardViewSize();
    boolean onSquareClick(Square clicked);
    void onFling(Square clicked);
    int[] getBGResources();
    boolean isFlipped();
}
