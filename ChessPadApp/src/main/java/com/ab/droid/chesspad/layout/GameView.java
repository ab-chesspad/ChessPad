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
 *
 * Created by Alexander Bootman on 11/27/16.
 */
package com.ab.droid.chesspad.layout;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.ab.droid.chesspad.ChessPad;
import com.ab.droid.chesspad.R;
import com.ab.pgn.Config;
import com.ab.pgn.Square;

import java.util.HashMap;
import java.util.Locale;

class GameView extends ChessPadLayout.CpView {
    private final HashMap<ChessPad.Command, ChessPadLayout.CpImageButton> imageButtons = new HashMap<>();

    private final TextView glyph, move, analysis;
    private final ChessPadLayout.CpEditText comment;


    @SuppressLint("ClickableViewAccessibility")
    GameView(ChessPadLayout chessPadLayout) {
        super(chessPadLayout);

        // info fields:
        move = createTextView();
        move.setFocusable(false);
        move.setSingleLine();
        move.setBackgroundColor(Color.WHITE);
        move.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        glyph = createTextView();
        glyph.setSingleLine();
        glyph.setBackgroundColor(Color.GREEN);
        glyph.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                chessPad.onButtonClick(ChessPad.Command.ShowGlyphs);
            }
            // true if the event was handled and should not be given further down to other views.
            return true;
        });
        glyph.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        comment = createTextView();
        comment.setBackgroundColor(Color.GREEN);
        comment.setGravity(Gravity.START | Gravity.TOP);
        // todo: enforce paired brackets {}
        comment.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }
            @Override
            public void afterTextChanged(Editable editable) {
                // fix it: called on every character, I only need the final value
                chessPad.setComment(editable.toString());
            }
        });

        analysis = createTextView();
        analysis.setFocusable(false);
        analysis.setBackgroundColor(Color.CYAN);
        analysis.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        createImageButton(ChessPad.Command.Start, R.drawable.game_start);
        createImageButton(ChessPad.Command.PrevVar, R.drawable.prev_variant);
        createImageButton(ChessPad.Command.Prev, R.drawable.prev_move);
        createImageButton(ChessPad.Command.Stop, R.drawable.pause);
        createImageButton(ChessPad.Command.Next, R.drawable.next_move);
        createImageButton(ChessPad.Command.NextVar, R.drawable.next_variant);
        createImageButton(ChessPad.Command.End, R.drawable.last_move);
        createImageButton(ChessPad.Command.Flip, R.drawable.flip_board_view);
        createImageButton(ChessPad.Command.Analysis, R.drawable.analysis);
        createImageButton(ChessPad.Command.Delete, R.drawable.delete);
    }

    private void createImageButton(ChessPad.Command command, int resource) {
        ChessPadLayout.CpImageButton cpImageButton = chessPadLayout.createImageButton(controlPaneLayout, command, resource);
        imageButtons.put(command, cpImageButton);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public void draw() {
        super.draw();
        chessPadLayout.boardView.draw(chessPad);

        int x, y, dx, dy;

        if (Metrics.isVertical) {
            dx = Metrics.buttonSize + Metrics.xSpacing;
            dy = 0;
        } else {
            dx = 0;
            dy = Metrics.buttonSize + Metrics.ySpacing;
        }

        // navigator + reverseBoard
        x = y = 0;
        chessPadLayout.moveTo(imageButtons.get(ChessPad.Command.Start), x, y, Metrics.buttonSize, Metrics.buttonSize);
        x += dx;
        y += dy;
        chessPadLayout.moveTo(imageButtons.get(ChessPad.Command.PrevVar), x, y, Metrics.buttonSize, Metrics.buttonSize);
        x += dx;
        y += dy;
        chessPadLayout.moveTo(imageButtons.get(ChessPad.Command.Prev), x, y, Metrics.buttonSize, Metrics.buttonSize);
        x += dx;
        y += dy;
        chessPadLayout.moveTo(imageButtons.get(ChessPad.Command.Stop), x, y, Metrics.buttonSize, Metrics.buttonSize);
        x += dx;
        y += dy;
        chessPadLayout.moveTo(imageButtons.get(ChessPad.Command.Next), x, y, Metrics.buttonSize, Metrics.buttonSize);
        x += dx;
        y += dy;
        chessPadLayout.moveTo(imageButtons.get(ChessPad.Command.NextVar), x, y, Metrics.buttonSize, Metrics.buttonSize);
        x += dx;
        y += dy;
        chessPadLayout.moveTo(imageButtons.get(ChessPad.Command.End), x, y, Metrics.buttonSize, Metrics.buttonSize);

        if (Metrics.isVertical) {
            x = Metrics.paneWidth - Metrics.buttonSize;
        } else {
            y = Metrics.paneHeight - Metrics.buttonSize;
        }
        chessPadLayout.moveTo(imageButtons.get(ChessPad.Command.Flip), x, y, Metrics.buttonSize, Metrics.buttonSize);

        // info fields:
        int _paneWidth;
        if (Metrics.isVertical) {
            x = 0;  // xSpacing
            y += Metrics.buttonSize + Metrics.ySpacing;
            _paneWidth = Metrics.paneWidth;
        } else {
            x = Metrics.buttonSize + 2 * Metrics.xSpacing;
            y = 0;
            _paneWidth = Metrics.paneWidth - Metrics.buttonSize - 2 * Metrics.xSpacing;
        }

        dy = 0;
        int w = Metrics.maxMoveWidth, x1;
        if (w * 3 / 2 + Metrics.buttonSize + 2 * Metrics.xSpacing > _paneWidth) {
            w = _paneWidth - Metrics.xSpacing;
            dy = Metrics.titleHeight + Metrics.ySpacing;
            x1 = x;
        } else {
            x1 = x + Metrics.maxMoveWidth + Metrics.xSpacing;
        }

        chessPadLayout.moveTo(move, x, y, w, Metrics.titleHeight);
        y += dy;

        w = Metrics.maxMoveWidth / 2;
        chessPadLayout.moveTo(glyph, x1, y, w, Metrics.titleHeight);

        x1 += w;
        dx = (Metrics.paneWidth - x1) / 2 - Metrics.buttonSize;
        chessPadLayout.moveTo(imageButtons.get(ChessPad.Command.Analysis), x1 + dx, y, Metrics.buttonSize, Metrics.titleHeight);
        chessPadLayout.moveTo(imageButtons.get(ChessPad.Command.Delete),
                Metrics.paneWidth - Metrics.buttonSize, y, Metrics.buttonSize, Metrics.titleHeight);

        y += Metrics.titleHeight + Metrics.ySpacing;
        int h = Metrics.paneHeight - y;
        chessPadLayout.moveTo(comment, x, y, Metrics.paneWidth, h);

        int ah;
        if (Metrics.isVertical) {
            analysis.setSingleLine(true);
            ah = Metrics.titleHeight;
            Metrics.cpScreenWidth = Metrics.screenWidth;
            Metrics.cpScreenHeight = y + h;
            y = Metrics.paneHeight - ah;
        } else {
            analysis.setSingleLine(false);
            ah = 2 * Metrics.titleHeight;
            Metrics.cpScreenWidth = Metrics.screenWidth;
            Metrics.cpScreenHeight = y + h;
            y = Metrics.paneHeight - Metrics.titleHeight - ah;
        }
        chessPadLayout.moveTo(analysis, x, y, Metrics.paneWidth, ah);
        setButtonEnabled(ChessPad.Command.Stop, false);
    }

    @Override
    public void invalidate() {
        this.chessPadLayout.title.setText(chessPad.getPgnGraph().getTitle());
        if (chessPad.mode == ChessPad.Mode.Puzzle) {
            this.chessPadLayout.boardView.setSelected(new Square());
        } else if(chessPad.selectedSquare == null) {
            if(chessPad.getPgnGraph().moveLine.size() > 1) {
                this.chessPadLayout.boardView.setSelected(chessPad.getPgnGraph().getCurrentMove().getTo());
            }
        } else {
            this.chessPadLayout.boardView.setSelected(chessPad.selectedSquare);
        }
        this.chessPadLayout.boardView.setHints(chessPad.getHints());

        move.setText(chessPad.getPgnGraph().getNumberedMove());
        int g = chessPad.getPgnGraph().getGlyph();
        if (g == 0) {
            glyph.setText("");
        } else {
            glyph.setText(String.format(Locale.getDefault(), "%s%d", Config.PGN_GLYPH, g));
        }

        if(chessPad.doAnalysis) {
            analysis.setVisibility(View.VISIBLE);
            analysis.setText(chessPad.getAnalysisMessage());
            imageButtons.get(ChessPad.Command.Analysis).setImageResource(R.drawable.noanalysis);
        } else {
            analysis.setVisibility(View.GONE);
            imageButtons.get(ChessPad.Command.Analysis).setImageResource(R.drawable.analysis);
            setButtonEnabled(ChessPad.Command.Analysis, chessPad.isAnalysisOk());
        }

        comment.setText(chessPad.getPgnGraph().getComment());

        if (chessPad.isNavigationEnabled()) {
            boolean puzzleMode = chessPad.isPuzzleMode();
            if (chessPad.isFirstMove()) {
                imageButtons.get(ChessPad.Command.Start).setImageResource(R.drawable.prev_game);
                if (puzzleMode) {
                    setButtonEnabled(ChessPad.Command.Start, false);
                } else {
                    setButtonEnabled(ChessPad.Command.Start, chessPad.getPgnGraph().getPgn().getIndex() > 0);
                }
                setButtonEnabled(ChessPad.Command.Prev, false);
                setButtonEnabled(ChessPad.Command.PrevVar, false);
                setButtonEnabled(ChessPad.Command.Delete, !puzzleMode && chessPad.getPgnGraph().isDeletable());
            } else {
                imageButtons.get(ChessPad.Command.Start).setImageResource(R.drawable.game_start);
                setButtonEnabled(ChessPad.Command.Start, true);
                setButtonEnabled(ChessPad.Command.Prev, true);
                setButtonEnabled(ChessPad.Command.PrevVar, !puzzleMode && chessPad.getPgnGraph().getVariations() == null);
                setButtonEnabled(ChessPad.Command.Delete, !puzzleMode);
            }

            if (chessPad.isLastMove()) {
                setButtonEnabled(ChessPad.Command.Next, false);
                setButtonEnabled(ChessPad.Command.NextVar, false);
                imageButtons.get(ChessPad.Command.End).setImageResource(R.drawable.next_game);
                if (puzzleMode) {
                    setButtonEnabled(ChessPad.Command.End, true);
                } else {
                    setButtonEnabled(ChessPad.Command.End, chessPad.getPgnGraph().getPgn().getIndex() < chessPad.lastItemIndex());
                }
            } else {
                setButtonEnabled(ChessPad.Command.Next, true);
                setButtonEnabled(ChessPad.Command.NextVar, !puzzleMode && chessPad.getPgnGraph().getVariations() == null);
                if (puzzleMode) {
                    imageButtons.get(ChessPad.Command.End).setImageResource(R.drawable.next_game);
                } else {
                    imageButtons.get(ChessPad.Command.End).setImageResource(R.drawable.last_move);
                }
                setButtonEnabled(ChessPad.Command.End, true);
            }
        }
    }

    void setButtonEnabled(ChessPad.Command command, boolean enable) {
        imageButtons.get(command).setEnabled(enable);
    }

    void enableCommentEdit(boolean enable) {
        comment.setEnabled(enable);
    }
}
