package com.ab.droid.chesspad;

import com.ab.pgn.Board;
import com.ab.pgn.Square;

/**
 *
 * Created by Alexander Bootman on 11/27/16.
 */

public interface BoardHolder {
    Board getBoard();
    boolean onSquareClick(Square clicked);
    boolean onFling(Square clicked);
    int[] getBGResources();
    void setReversed(boolean reversed);
    boolean isReversed();
}
