package com.ab.droid.chesspad;

import com.ab.pgn.Pair;

import java.util.List;

/**
 *
 * Created by abootman on 11/29/16.
 */

public interface TitleHolder {
    String getTitleText();
    void onTitleClick();
    List<Pair<String, String>> getHeaders();
}
