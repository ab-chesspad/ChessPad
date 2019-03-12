package com.ab.droid.chesspad;

import android.graphics.Color;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.text.InputType;
import android.text.Layout;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ab.pgn.Board;
import com.ab.pgn.Setup;
import com.ab.pgn.Square;
import com.ab.pgn.dgtboard.DgtBoardPad;

import java.util.List;

/**
 *
 * Created by Alexander Bootman on 1/15/19.
 */

public class DgtBoardView extends SetupView {
    private ChessPadView.CpImageButton btnRevertView, btnTurnBoard;
    private TextView moveText;


    public DgtBoardView(ChessPad chessPad) {
        super(chessPad);
    }

    @Override
    protected Board getBoard() {
        return getDgtBoardPad().getBoard();
    }

    @Override
    protected Setup getSetup() {
        return getDgtBoardPad().getSetup();
    }

    protected DgtBoardPad getDgtBoardPad() {
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
            public int[] getBGResources() {
                return new int[]{R.drawable.bsquare, R.drawable.wsquare};
            }

            @Override
            public void setReversed(boolean reversed) {
                chessPad.setReversed(reversed);
            }

            @Override
            public boolean isReversed() {
                return chessPad.isReversed();
            }

            @Override
            public boolean onSquareClick(Square clicked) {
                return false;
            }

            @Override
            public boolean onFling(Square clicked) {
                return false;
            }
        };
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void draw() {
        moveText = new TextView(chessPad);
        moveText.setElegantTextHeight(true);
        moveText.setGravity(Gravity.FILL_VERTICAL);
        moveText.setFocusable(false);
        moveText.setSingleLine(false);
        moveText.setImeOptions(EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        moveText.setBackgroundColor(Color.WHITE);
        moveText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        moveText.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                Layout layout = moveText.getLayout();
                if(layout != null) {
                    List<String> movesText = getDgtBoardPad().getMovesText();
//                    Log.d(DEBUG_TAG, String.format("onGlobalLayout(), movesText=%s", movesText));
                    if(movesText != null) {
                        updateMoveText(movesText);
                    }
                }
                return true;
            }
        });
        super.draw();

        setupStatus.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP && getSetup().getErrNum() == 0) {
                    if(getDgtBoardPad().getBoardStatus() == DgtBoardPad.BoardStatus.Game) {
                        getDgtBoardPad().setBoardStatus(DgtBoardPad.BoardStatus.SetupMess, false);
                    } else {
                        getDgtBoardPad().setBoardStatus(DgtBoardPad.BoardStatus.Game, true);
                    }

                }
                // true if the event was handled and should not be given further down to other views.
                return true;
            }
        });
    }

    private void updateMoveText(List<String> movesText) {
        if(movesText == null || movesText.size() == 0) {
            return;
        }
//        Log.d(DEBUG_TAG, String.format("onGlobalLayout(), movesText=%s", movesText));
        int height = moveText.getHeight();
        Layout layout = moveText.getLayout();
        int totalLines = moveText.getLineCount();
        if(height > layout.getLineBottom(totalLines - 1)) {
            return;     // whole text is visible, no truncation
        }
        int rows = 0;
        for(int i = 0; i < totalLines; ++i) {
            if(height <= layout.getLineBottom(i)) {
                rows = i;
                break;
            }
        }
        int skip = movesText.size() - rows;
        moveText.setText(TextUtils.join("\n", movesText.subList(skip, movesText.size())));
    }

    @Override
    protected void drawSetupVerticalLayout(RelativeLayout relativeLayoutSetup) {
        int x, y, x0, y0;

        int toggleButtonSize = Metrics.squareSize;
        int size = (Metrics.paneHeight - 6 * Metrics.ySpacing) / 4;
        if (toggleButtonSize > size) {
            toggleButtonSize = size;
        }

        int width, height;
        x = 0;
        y0 = Metrics.ySpacing;

        // white / black move buttons
        x = (Metrics.screenWidth - (Metrics.halfMoveClockLabelWidth + Metrics.moveLabelWidth / 2 + 3 * toggleButtonSize + 4 * Metrics.xSpacing)) / 2;
        y = y0;
        btnWhiteMove = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kw);
        y += toggleButtonSize + Metrics.ySpacing;
        btnBlackMove = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kb);

        // castle buttons
        x0 = x + toggleButtonSize + Metrics.xSpacing;
        x = x0;
        y = y0;
        btnWhiteQueenCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle);
        x += toggleButtonSize + Metrics.xSpacing;
        btnWhiteKingCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle);

        x = x0;
        y += toggleButtonSize + Metrics.ySpacing;
        btnBlackQueenCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle);
        x += toggleButtonSize + Metrics.xSpacing;
        btnBlackKingCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle);

        // texts:
        x += toggleButtonSize + 3 * Metrics.xSpacing;
        y = y0;
        height = (2 * toggleButtonSize - 2 * Metrics.ySpacing) / 3;
        enPassEditText = ChessPadView.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_en_pass);

        y += height + Metrics.ySpacing;
        hmClockEditText = ChessPadView.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_halfmove_clock);
        y += height + Metrics.ySpacing;
        moveNumEditText = ChessPadView.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_move_num);
        y += height + Metrics.ySpacing;

        x = Metrics.xSpacing;
        width = (Metrics.paneWidth - x - Metrics.squareSize - 4 * Metrics.xSpacing) / 2;
        height = 2 * Metrics.squareSize + Metrics.ySpacing;
        ChessPadView.addTextView(relativeLayoutSetup, moveText, x, y, width, height);
        moveText.setPadding(0, 0, 0, 20);

        x += width + Metrics.xSpacing;
        ChessPadView.addTextView(relativeLayoutSetup, setupStatus, x, y, width, height);
        x += width + Metrics.xSpacing;
        btnRevertView = ChessPadView.addImageButton(chessPad, relativeLayoutSetup, ChessPad.Command.Reverse, x, y, R.drawable.ic_menu_refresh);
        btnTurnBoard = ChessPadView.addImageButton(chessPad, relativeLayoutSetup, ChessPad.Command.Reverse, x, y + Metrics.squareSize + Metrics.ySpacing, R.drawable.turn_board);
    }

    @Override
    protected void drawSetupHorizonlalLayout(RelativeLayout relativeLayoutSetup) {
        int x, y, x0, y0;

        int toggleButtonSize = Metrics.squareSize;
        x0 = Metrics.xSpacing;
        x = x0;
        y = 0;

        // texts:
        int height = (2 * toggleButtonSize - 2 * Metrics.ySpacing) / 3;
        enPassEditText = ChessPadView.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_en_pass);

        y += height + Metrics.ySpacing;
        hmClockEditText = ChessPadView.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_halfmove_clock);
        y += height + Metrics.ySpacing;
        moveNumEditText = ChessPadView.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_move_num);

        y0 = y + height + 2 * Metrics.ySpacing;
        // white / black move buttons
        x = x0;
        y = y0;
        btnWhiteMove = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kw);
        y += toggleButtonSize + Metrics.ySpacing;
        btnBlackMove = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kb);

        // castle buttons
        x0 = x + toggleButtonSize + Metrics.xSpacing;
        x = x0;
        y = y0;
        btnWhiteQueenCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle);
        x += toggleButtonSize + Metrics.xSpacing;
        btnWhiteKingCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle);

        x = x0;
        y += toggleButtonSize + Metrics.ySpacing;
        btnBlackQueenCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle);
        x += toggleButtonSize + Metrics.xSpacing;
        btnBlackKingCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle);
        y0 = y + toggleButtonSize;

        height = 2 * toggleButtonSize + Metrics.xSpacing;
        int width = Metrics.halfMoveClockLabelWidth + Metrics.moveLabelWidth / 2 - (toggleButtonSize + Metrics.xSpacing);
        x = Metrics.xSpacing;
        y = Metrics.paneHeight - height - Metrics.ySpacing;
        ChessPadView.addTextView(relativeLayoutSetup, setupStatus, x, y, width, height);

        x += width + Metrics.xSpacing;
        btnRevertView = ChessPadView.addImageButton(chessPad, relativeLayoutSetup, ChessPad.Command.Reverse, x, y, R.drawable.ic_menu_refresh);
        btnTurnBoard = ChessPadView.addImageButton(chessPad, relativeLayoutSetup, ChessPad.Command.TurnBoard, x, y + Metrics.squareSize + Metrics.ySpacing, R.drawable.turn_board);

        x = Metrics.xSpacing;
        int h = y - y0 - 2 * Metrics.ySpacing;
        if(height > h) {
            height = h;
        }
        y = y0 + Metrics.ySpacing;
        width += toggleButtonSize + Metrics.xSpacing;
        ChessPadView.addTextView(relativeLayoutSetup, moveText, x, y, width, height);
    }

    @Override
    public void invalidate() {
        boolean enable = false;
        if (getDgtBoardPad().getBoardStatus() == DgtBoardPad.BoardStatus.SetupMess) {
            enable = true;
        }
        btnWhiteMove.setEnabled(enable);
        btnBlackMove.setEnabled(enable);
        btnWhiteQueenCastle.setEnabled(enable);
        btnWhiteKingCastle.setEnabled(enable);
        btnBlackQueenCastle.setEnabled(enable);
        btnBlackKingCastle.setEnabled(enable);
        enPassEditText.setEnabled(enable);
        hmClockEditText.setEnabled(enable);
        moveNumEditText.setEnabled(enable);
        btnTurnBoard.setEnabled(enable);

        enPassEditText.setText(getSetup().getEnPass());
        hmClockEditText.setText("" + getBoard().getReversiblePlyNum());
        moveNumEditText.setText("" + getBoard().getPlyNum() / 2);

        super.invalidate();
        if (getDgtBoardPad().getBoardStatus() == DgtBoardPad.BoardStatus.Game) {
            moveNumEditText.setStringKeeper(new ChessPadView.StringKeeper() {
                @Override
                public void setValue(TextView v, String str) {
                    // do not update board plyNum
                }

                @Override
                public String getValue(TextView v) {
                    return "" + (getBoard().getPlyNum() + 1) / 2;
                }
            });

            setupStatus.setText(R.string.dgt_status_game);
            moveText.setText(TextUtils.join("\n", getDgtBoardPad().getMovesText()));
        } else {
            moveText.setText("");
        }
        moveText.setText(TextUtils.join("\n", getDgtBoardPad().getMovesText()));
    }

}