package com.ab.droid.chesspad;

import com.ab.pgn.Board;
import com.ab.pgn.Square;

/**
 *
 * Created by Alexander Bootman on 11/27/16.
 */

public interface BoardHolder {
    Board getBoard();
    int getBoardViewSize();
    boolean onSquareClick(Square clicked);
    void onFling(Square clicked);
    int[] getBGResources();
    boolean isFlipped();
}
