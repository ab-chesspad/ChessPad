package com.ab.droid.chesspad.layout;

import com.ab.pgn.Pair;

import java.util.List;

/**
 *
 * Created by abootman on 11/27/16.
 */

public interface TitleHolder {
//    int getLength();
    void onTitleClick();
    List<Pair<String, String>> getTags();
}
