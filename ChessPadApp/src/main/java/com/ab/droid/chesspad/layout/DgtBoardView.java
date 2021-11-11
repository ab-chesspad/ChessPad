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
*/
package com.ab.droid.chesspad.layout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import com.ab.droid.chesspad.BoardHolder;
import com.ab.droid.chesspad.ChessPad;
import com.ab.droid.chesspad.R;
import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Setup;
import com.ab.pgn.Square;
import com.ab.pgn.dgtboard.DgtBoardPad;

import java.util.Locale;

/**
 *
 * Created by Alexander Bootman on 1/15/19.
 */

public class DgtBoardView extends SetupView {
    public static boolean DEBUG = false;

    private final ChessPadLayout.CpImageButton btnFlipView, btnFlipBoard;
    private final EditText moveText;
    private final TextView glyph;
    private final ChessPadLayout.CpEditText comment;

    @SuppressLint("ClickableViewAccessibility")
    DgtBoardView(ChessPadLayout chessPadLayout) {
        init(chessPadLayout);
        createCommonFields();
        btnFlipView = chessPadLayout.createImageButton(controlPaneLayout, ChessPad.Command.Flip, R.drawable.flip_board_view);
        btnFlipBoard = chessPadLayout.createImageButton(controlPaneLayout, ChessPad.Command.FlipBoard, R.drawable.turn_board);

        moveText = createTextView();
        moveText.setFocusable(false);
        moveText.setBackgroundColor(Color.WHITE);
        moveText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        moveText.setSingleLine(false);

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

        // override super:
        setupStatus.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP && getSetup().getErrNum() == 0) {
                    if(getDgtBoardPad().getBoardStatus() == DgtBoardPad.BoardStatus.Game) {
                        getDgtBoardPad().setBoardStatus(DgtBoardPad.BoardStatus.SetupMess, false);
                    } else {
                        getDgtBoardPad().setBoardStatus(DgtBoardPad.BoardStatus.Game, true);
                    }
                    DgtBoardView.this.draw();
                }
                // true if the event was handled and should not be given further down to other views.
                return true;
            }
        });
    }

/*
    ChessPadLayout.CpEditText createTextView(RelativeLayout relativeLayout) {
        ChessPadLayout.CpEditText view = new ChessPadLayout.CpEditText(chessPad);
        view.setPadding(0, 0, 0, 0);
        view.setTextSize(16);
        view.setTextColor(Color.DKGRAY);
        relativeLayout.addView(view);
        return view;
    }
*/

    @Override
    Board getBoard() {
        DgtBoardPad dgtBoardPad = getDgtBoardPad();
        if (dgtBoardPad != null) {
            return dgtBoardPad.getBoard();
        }
        return null;
    }

    @Override
    Setup getSetup() {
        DgtBoardPad dgtBoardPad = getDgtBoardPad();
        if (dgtBoardPad != null) {
            return dgtBoardPad.getSetup();
        }
        return null;
    }

    private DgtBoardPad getDgtBoardPad() {
        return chessPad.dgtBoardPad;
    }

    @Override
    protected BoardHolder getMainBoardHolder() {
        return new BoardHolder() {
            @Override
            public Board getBoard() {
                return DgtBoardView.this.getBoard();
            }

            @Override
            public int getBoardViewSize() {
                return Metrics.boardViewSize;
            }

            @Override
            public int[] getBGResources() {
                return new int[]{R.drawable.bsquare, R.drawable.wsquare};
            }

            @Override
            public boolean isFlipped() {
                DgtBoardPad dgtBoardPad = getDgtBoardPad();
                if (dgtBoardPad != null) {
                    return dgtBoardPad.isFlipped();
                }
                return false;
            }

            // disable the rest:
            @Override
            public boolean onSquareClick(Square clicked) {
                return false;
            }

            @Override
            public void onFling(Square clicked) {
            }
        };
    }

    private void hideAllVidgets() {
        enPassEditText.setVisibility(View.GONE);
        hmClockEditText.setVisibility(View.GONE);
        moveNumEditText.setVisibility(View.GONE);
        moveText.setVisibility(View.GONE);
        glyph.setVisibility(View.GONE);
        comment.setVisibility(View.GONE);

        btnWhiteMove.setVisibility(View.GONE);
        btnBlackMove.setVisibility(View.GONE);
        btnWhiteQueenCastle.setVisibility(View.GONE);
        btnWhiteKingCastle.setVisibility(View.GONE);
        btnBlackQueenCastle.setVisibility(View.GONE);
        btnBlackKingCastle.setVisibility(View.GONE);;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void draw() {
        Log.d(DEBUG_TAG, String.format("start draw() %s", getDgtBoardPad().getBoardStatus().toString()));
        hideAllVidgets();

        int x, y;

        if (Metrics.isVertical) {
            x = 0; // xSpacing;
            y = Metrics.titleHeight + Metrics.ySpacing + Metrics.boardViewSize + Metrics.ySpacing;
        } else {
            x = Metrics.boardViewSize + Metrics.xSpacing;
            y = Metrics.titleHeight + Metrics.ySpacing;
        }
        chessPadLayout.moveTo(controlPaneLayout, x, y, Metrics.paneWidth, Metrics.paneHeight);
        controlPaneLayout.setVisibility(View.GONE);
        super.draw();
        Log.d(DEBUG_TAG, String.format("draw() done %s", getDgtBoardPad().getBoardStatus().toString()));
//        invalidate();
    }

    @Override
    protected void drawSetupVerticalLayout() {
        int x, y, w, h;
        if (getDgtBoardPad().getBoardStatus() != DgtBoardPad.BoardStatus.Game) {
            // setup/mess
            super.drawSetupVerticalLayout();
            int y0 = 2 * Metrics.squareSize + 5 * Metrics.ySpacing;
            h = Metrics.paneHeight - y0;
            int toggleButtonSize = (h - 2 * Metrics.ySpacing) / 2;
            x = Metrics.paneWidth - toggleButtonSize;
            y = y0;
            chessPadLayout.moveTo(btnFlipView, x, y, toggleButtonSize, toggleButtonSize);
            y += toggleButtonSize + 2 * Metrics.ySpacing;
            chessPadLayout.moveTo(btnFlipBoard, x, y, toggleButtonSize, toggleButtonSize);

            w = x / 2 - Metrics.xSpacing;
            x = 0;
            y = y0;
            chessPadLayout.moveTo(moveText, x, y, w, h);
            x += w + Metrics.ySpacing;
            chessPadLayout.moveTo(setupStatus, x, y, w, h);
        } else {
            y = 0;
            w = Metrics.maxMoveWidth / 2;
            x = Metrics.paneWidth - w;
            chessPadLayout.moveTo(glyph, x, y, w, Metrics.titleHeight);

            int toggleButtonSize = Metrics.squareSize;
            w = x - Metrics.xSpacing;

            x = 0;
            y = 0;
            h = Metrics.paneHeight - 2 * (toggleButtonSize + Metrics.ySpacing);
            chessPadLayout.moveTo(comment, x, y, w, h);

            int y0 = y + h + Metrics.ySpacing;
            y = y0;
            x = Metrics.paneWidth - toggleButtonSize;
            chessPadLayout.moveTo(btnFlipView, x, y, toggleButtonSize, toggleButtonSize);
            y += toggleButtonSize + Metrics.ySpacing;
            chessPadLayout.moveTo(btnFlipBoard, x, y, toggleButtonSize, toggleButtonSize);

            w = x / 2 - Metrics.xSpacing;
            x = 0;
            y = y0;
            chessPadLayout.moveTo(moveText, x, y, w, h);
            x += w + Metrics.xSpacing;
            chessPadLayout.moveTo(setupStatus, x, y, w, h);
        }
    }

    @Override
    protected void drawSetupHorizontalLayout() {
        int x, y, w, h;
        if (getDgtBoardPad().getBoardStatus() != DgtBoardPad.BoardStatus.Game) {
            // setup/mess
            int toggleButtonSize = Metrics.squareSize;

            h = 2 * toggleButtonSize / 3 - Metrics.ySpacing;
            x = 0;
            y = 0;
            enPassEditText.moveTo(x, y, Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, h);
            y += h + Metrics.ySpacing;
            hmClockEditText.moveTo(x, y, Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, h);
            y += h + Metrics.ySpacing;
            moveNumEditText.moveTo(x, y, Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, h);

            int y0 = y + h + Metrics.ySpacing;

            x = Metrics.xSpacing;
            y = y0;
            chessPadLayout.moveTo(btnWhiteMove, x, y, toggleButtonSize, toggleButtonSize);
            y += toggleButtonSize + Metrics.ySpacing;
            chessPadLayout.moveTo(btnBlackMove, x, y, toggleButtonSize, toggleButtonSize);

            y = y0;
            x += toggleButtonSize + Metrics.xSpacing;
            chessPadLayout.moveTo(btnWhiteQueenCastle, x, y, toggleButtonSize, toggleButtonSize);
            y += toggleButtonSize + Metrics.ySpacing;
            chessPadLayout.moveTo(btnWhiteKingCastle, x, y, toggleButtonSize, toggleButtonSize);

            y = y0;
            x += toggleButtonSize + Metrics.xSpacing;
            chessPadLayout.moveTo(btnBlackQueenCastle, x, y, toggleButtonSize, toggleButtonSize);
            y += toggleButtonSize + Metrics.ySpacing;
            chessPadLayout.moveTo(btnBlackKingCastle, x, y, toggleButtonSize, toggleButtonSize);

            y0 = y + toggleButtonSize;
            h = (Metrics.paneHeight - y0 - 2 * Metrics.ySpacing) / 2;

            x = Metrics.paneWidth - toggleButtonSize;
            y = Metrics.paneHeight - 2 * toggleButtonSize - 2 * Metrics.ySpacing;
            chessPadLayout.moveTo(btnFlipView, x, y, toggleButtonSize, toggleButtonSize);
            y += toggleButtonSize + 2 * Metrics.ySpacing;
            chessPadLayout.moveTo(btnFlipBoard, x, y, toggleButtonSize, toggleButtonSize);

            w = x - Metrics.xSpacing;
            x = Metrics.xSpacing;
            y = y0 + 2 * Metrics.ySpacing;
            chessPadLayout.moveTo(moveText, x, y, w, h);

            y = Metrics.paneHeight - h;
            chessPadLayout.moveTo(setupStatus, x, y, w, h);
        } else {
            y = 0;
            w = Metrics.maxMoveWidth / 2;
            x = Metrics.paneWidth - w;
            chessPadLayout.moveTo(glyph, x, y, w, Metrics.titleHeight);

            int toggleButtonSize = Metrics.squareSize;

            int w0 = x - Metrics.xSpacing;

            x = 0;
            y = 0;
            w = w0;
            int y1 = Metrics.paneHeight - 4 * (toggleButtonSize + Metrics.ySpacing);
            h = y1;
            chessPadLayout.moveTo(comment, x, y, w, h);

            int y0 = Metrics.paneHeight - 2 * toggleButtonSize - Metrics.ySpacing;
            y = y0;
            x = Metrics.paneWidth - toggleButtonSize;
            chessPadLayout.moveTo(btnFlipView, x, y, toggleButtonSize, toggleButtonSize);
            y += toggleButtonSize + Metrics.ySpacing;
            chessPadLayout.moveTo(btnFlipBoard, x, y, toggleButtonSize, toggleButtonSize);


            w = x - Metrics.xSpacing;
            x = Metrics.xSpacing;
            y = y0;
            h = Metrics.paneHeight - y;
            chessPadLayout.moveTo(setupStatus, x, y, w, h);

            y = y1 + Metrics.ySpacing;
            if (w < 2 * Metrics.maxMoveWidth) {
                w = Metrics.paneWidth;
            }
            chessPadLayout.moveTo(moveText, x, y, w, h);
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void invalidate() {
        DgtBoardPad dgtBoardPad = getDgtBoardPad();
        try {
            if (dgtBoardPad != null && dgtBoardPad.getBoardStatus() == DgtBoardPad.BoardStatus.Game) {
                InputMethodManager imm = (InputMethodManager) chessPad.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(comment.getWindowToken(), 0);
                    Log.d(DEBUG_TAG, "kbd closed");
                }
            }
        } catch (Exception e) {
            Log.e(DEBUG_TAG, "invalidate() 1", e);
        }

        super.invalidate();
        try {
            if (dgtBoardPad != null && dgtBoardPad.getBoardStatus() == DgtBoardPad.BoardStatus.Game) {
                setupStatus.setText(R.string.dgt_status_game);
                int g = getDgtBoardPad().getPgnGraph().getGlyph();
                if (g == 0) {
                    glyph.setText("");
                } else {
                    glyph.setText(String.format(Locale.getDefault(), "%s%d", Config.PGN_GLYPH, g));
                }
                comment.setText(getDgtBoardPad().getPgnGraph().getComment());
            } else {
                moveText.setText("");
            }
//            moveText.setSingleLine(false);
            String text = TextUtils.join("\n", getDgtBoardPad().getMovesText());
            moveText.setText(text);
            moveText.setSelection(moveText.getText().length());
        } catch (Exception e) {
            Log.e(DEBUG_TAG, "invalidate() 2", e);
        }
    }

}