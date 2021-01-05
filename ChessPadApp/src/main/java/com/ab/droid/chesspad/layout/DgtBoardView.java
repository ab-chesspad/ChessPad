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
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ab.droid.chesspad.BoardHolder;
import com.ab.droid.chesspad.ChessPad;
import com.ab.droid.chesspad.R;
import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Setup;
import com.ab.pgn.dgtboard.DgtBoardPad;

import java.util.Locale;

/**
 *
 * Created by Alexander Bootman on 1/15/19.
 */

public class DgtBoardView extends SetupView {
    public static boolean DEBUG = false;

    private ChessPadLayout.CpImageButton btnFlipView, btnFlipBoard;
    private EditText moveText;
    private TextView glyph;
    private ChessPadLayout.CpEditText comment;

    DgtBoardView(ChessPadLayout chessPadLayout) {
        super(chessPadLayout);
    }

    @Override
    Board getBoard() {
        return getDgtBoardPad().getBoard();
    }

    @Override
    Setup getSetup() {
        return getDgtBoardPad().getSetup();
    }

    private DgtBoardPad getDgtBoardPad() {
        return null;
//        return chessPad.dgtBoardPad;
    }

    @Override
    protected BoardHolder getMainBoardHolder() {
        return null;
//        return new BoardHolder() {
//            @Override
//            public Board getBoard() {
//                return DgtBoardView.this.getBoard();
//            }
//
//            @Override
//            public int getBoardViewSize() {
//                return Metrics.boardViewSize;
//            }
//
//            @Override
//            public int[] getBGResources() {
//                return new int[]{R.drawable.bsquare, R.drawable.wsquare};
//            }
//
//            @Override
//            public boolean onSquareClick(Square clicked) {
//                return false;
//            }
//
//            @Override
//            public void onFling(Square clicked) {
//            }
//        };
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void draw() {
        Log.d(DEBUG_TAG, String.format("start draw() %s", getDgtBoardPad().getBoardStatus().toString()));
        moveText = new EditText(chessPad);
        moveText.setGravity(Gravity.BOTTOM);
        moveText.setFocusable(false);
        moveText.setSingleLine(false);
        moveText.setBackgroundColor(Color.WHITE);

        if (getDgtBoardPad().getBoardStatus() == DgtBoardPad.BoardStatus.Game) {
            glyph = new TextView(chessPad);
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

            comment = new ChessPadLayout.CpEditText(chessPad);
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
                    getDgtBoardPad().getPgnGraph().setComment(editable.toString());
                }
            });
        }

        super.draw();

        // functionality:

        relativeLayoutMain.setupStatus.setOnTouchListener((v, event) -> {
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
        });
        Log.d(DEBUG_TAG, String.format("draw() done %s", getDgtBoardPad().getBoardStatus().toString()));
    }

    protected void drawSetupVerticalLayout(RelativeLayout relativeLayoutSetup) {
/*
        int x, y, x0, y0;

        int toggleButtonSize = Metrics.squareSize;
        int size = (Metrics.paneHeight - 6 * Metrics.ySpacing) / 4;
        if (toggleButtonSize > size) {
            toggleButtonSize = size;
        }

        int width, height;
        y0 = Metrics.ySpacing;
        if (getDgtBoardPad().getBoardStatus() != DgtBoardPad.BoardStatus.Game) {
            // white / black move buttons
            x = Metrics.xSpacing;
            y = y0;
            btnWhiteMove = new ChessPadLayout.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kw);
            y += toggleButtonSize + Metrics.ySpacing;
            btnBlackMove = new ChessPadLayout.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kb);

            // castle buttons
            x0 = x + toggleButtonSize + Metrics.xSpacing;
            x = x0;
            y = y0;
            btnWhiteQueenCastle = new ChessPadLayout.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle);
            x += toggleButtonSize + Metrics.xSpacing;
            btnWhiteKingCastle = new ChessPadLayout.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle);

            x = x0;
            y += toggleButtonSize + Metrics.ySpacing;
            btnBlackQueenCastle = new ChessPadLayout.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle);
            x += toggleButtonSize + Metrics.xSpacing;
            btnBlackKingCastle = new ChessPadLayout.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle);

            // texts:
            width = Metrics.halfMoveClockLabelWidth + Metrics.moveLabelWidth / 2 + Metrics.xSpacing;
            x = Metrics.paneWidth - width;
            y = y0;
            height = (2 * toggleButtonSize - 2 * Metrics.ySpacing) / 3;
            enPassEditText = ChessPadLayout.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                    Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_en_pass);

            y += height + Metrics.ySpacing;
            hmClockEditText = ChessPadLayout.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                    Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_halfmove_clock);
            y += height + Metrics.ySpacing;
            moveNumEditText = ChessPadLayout.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                    Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_move_num);
            y += height + 2 * Metrics.ySpacing;

        } else {
            y = y0;
            height = 2 * toggleButtonSize + Metrics.ySpacing;
            width = Metrics.maxMoveWidth / 2;
            x = Metrics.paneWidth - Metrics.xSpacing - width;
            ChessPadLayout.addTextView(relativeLayoutSetup, glyph, x, y, width, height);

            x = Metrics.xSpacing;
            width = Metrics.paneWidth - 3 * Metrics.xSpacing - width;
            ChessPadLayout.addTextView(relativeLayoutSetup, comment, x, y, width, height);
            y += height + Metrics.ySpacing;
        }

        y += Metrics.ySpacing;
        x = Metrics.xSpacing;
        width = (Metrics.paneWidth - x - Metrics.squareSize - 4 * Metrics.xSpacing) / 2;
        height = 2 * Metrics.squareSize + Metrics.ySpacing;
        ChessPadLayout.addTextView(relativeLayoutSetup, moveText, x, y, width, height);
        moveText.setPadding(0, 0, 0, 20);

        x += width + Metrics.xSpacing;
        ChessPadLayout.addTextView(relativeLayoutSetup, setupStatus, x, y, width, height);
        x += width + Metrics.xSpacing;
        btnFlipView = ChessPadLayout.addImageButton(chessPad, relativeLayoutSetup, ChessPad.Command.Flip, x, y, R.drawable.flip_board_view);
        btnFlipBoard = ChessPadLayout.addImageButton(chessPad, relativeLayoutSetup, ChessPad.Command.Flip, x, y + Metrics.squareSize + Metrics.ySpacing, R.drawable.turn_board);
*/
    }

//    @Override
    protected void drawSetupHorizonlalLayout(RelativeLayout relativeLayoutSetup) {
/*
        int x, y, x0, y0, height;

        int toggleButtonSize = Metrics.squareSize;
        x0 = Metrics.xSpacing;
        x = x0;
        y = 0;

        if (getDgtBoardPad().getBoardStatus() != DgtBoardPad.BoardStatus.Game) {
            // texts:
            height = (2 * toggleButtonSize - 2 * Metrics.ySpacing) / 3;
            enPassEditText = ChessPadLayout.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                    Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_en_pass);

            y += height + Metrics.ySpacing;
            hmClockEditText = ChessPadLayout.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                    Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_halfmove_clock);
            y += height + Metrics.ySpacing;
            moveNumEditText = ChessPadLayout.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                    Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_move_num);

            y0 = y + height + 2 * Metrics.ySpacing;
            // white / black move buttons
            x = x0;
            y = y0;
            btnWhiteMove = new ChessPadLayout.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kw);
            y += toggleButtonSize + Metrics.ySpacing;
            btnBlackMove = new ChessPadLayout.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kb);

            // castle buttons
            x0 = x + toggleButtonSize + Metrics.xSpacing;
            x = x0;
            y = y0;
            btnWhiteQueenCastle = new ChessPadLayout.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle);
            x += toggleButtonSize + Metrics.xSpacing;
            btnWhiteKingCastle = new ChessPadLayout.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle);

            x = x0;
            y += toggleButtonSize + Metrics.ySpacing;
            btnBlackQueenCastle = new ChessPadLayout.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle);
            x += toggleButtonSize + Metrics.xSpacing;
            btnBlackKingCastle = new ChessPadLayout.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle);
            y0 = y + toggleButtonSize + 3 * Metrics.ySpacing;
        } else {
            int w = Metrics.maxMoveWidth / 2;
            int width = Metrics.halfMoveClockLabelWidth + Metrics.moveLabelWidth / 2 - (w + Metrics.xSpacing);
            height = 4 * toggleButtonSize + 4 * Metrics.ySpacing;
            ChessPadLayout.addTextView(relativeLayoutSetup, comment, x, y, width, height);

            x += width + Metrics.xSpacing;
            int h = 2 * toggleButtonSize + Metrics.ySpacing;
            ChessPadLayout.addTextView(relativeLayoutSetup, glyph, x, y, w, h);
            y0 = y + height + 2 * Metrics.ySpacing;
        }
        height = 2 * toggleButtonSize + Metrics.xSpacing;
        int width = Metrics.halfMoveClockLabelWidth + Metrics.moveLabelWidth / 2 - (toggleButtonSize + Metrics.xSpacing);
        x = Metrics.xSpacing;
        y = Metrics.paneHeight - height - Metrics.ySpacing;
        ChessPadLayout.addTextView(relativeLayoutSetup, setupStatus, x, y, width, height);

        x += width + Metrics.xSpacing;
        btnFlipView = ChessPadLayout.addImageButton(chessPad, relativeLayoutSetup, ChessPad.Command.Flip, x, y, R.drawable.flip_board_view);
        btnFlipBoard = ChessPadLayout.addImageButton(chessPad, relativeLayoutSetup, ChessPad.Command.FlipBoard, x, y + Metrics.squareSize + Metrics.ySpacing, R.drawable.turn_board);

        x = Metrics.xSpacing;
        int h = y - y0 - 2 * Metrics.ySpacing;
        if(height > h) {
            height = h;
        }
        y = y0 + Metrics.ySpacing;
        width += toggleButtonSize + Metrics.xSpacing;
        ChessPadLayout.addTextView(relativeLayoutSetup, moveText, x, y, width, height);
*/
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void invalidate() {
        try {
            if (getDgtBoardPad().getBoardStatus() == DgtBoardPad.BoardStatus.Game) {
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
            if (getDgtBoardPad().getBoardStatus() == DgtBoardPad.BoardStatus.Game) {
                relativeLayoutMain.setupStatus.setText(R.string.dgt_status_game);
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
            moveText.setText(TextUtils.join("\n", getDgtBoardPad().getMovesText()));
            moveText.setSelection(moveText.getText().length());
        } catch (Exception e) {
            Log.e(DEBUG_TAG, "invalidate() 2", e);
        }
    }

}