package com.ab.droid.chesspad;

import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Pair;
import com.ab.pgn.Square;

import java.util.List;

/**
 *
 * Created by Alexander Bootman on 11/27/16.
 */

public class GameView {
    protected final String DEBUG_TAG = this.getClass().getName();

    private final ChessPad chessPad;
    private final RelativeLayout relativeLayoutMain;

    private TextView title;
    private BoardView boardView;

    private TextView glyph, move;
    private ChessPadView.CpEditText comment;
    boolean navigationEnabled = true;
    private BoardHolder mainBoardHolder;

    private ChessPadView.CpImageButton[] imageButtons = new ChessPadView.CpImageButton[ChessPad.Command.total()];

    public GameView(ChessPad chessPad, RelativeLayout relativeLayoutMain) {
        this.chessPad = chessPad;
        this.relativeLayoutMain = relativeLayoutMain;
    }

    public void draw() {
        int x, y, dx, dy;

        title = ChessPadView.drawTitleBar(chessPad, relativeLayoutMain, new TitleHolder() {
            @Override
            public String getTitleText() {
                return chessPad.getTitleText();
            }

            @Override
            public void onTitleClick() {
                chessPad.onButtonClick(ChessPad.Command.EditHeaders);
            }

            @Override
            public List<Pair<String, String>> getHeaders() {
                return chessPad.getHeaders();
            }
        });

        if (Metrics.isVertical) {
            x = (Metrics.screenWidth - Metrics.boardViewSize) / 2;
        } else {
            x = 0;
        }
        y = Metrics.titleHeight + Metrics.ySpacing;

        mainBoardHolder = new BoardHolder() {
            @Override
            public Board getBoard() {
                return chessPad.getBoard();
            }

            @Override
            public int[] getBGResources() {
                return new int[] {R.drawable.bsquare, R.drawable.wsquare};
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
                Log.d(DEBUG_TAG, String.format("board onSquareClick (%s)", clicked.toString()));
                return chessPad.onSquareClick(clicked);
            }

            @Override
            public boolean onFling(Square clicked) {
                Log.d(DEBUG_TAG, String.format("board onFling (%s)", clicked.toString()));
                return chessPad.onFling(clicked);
            }
        };
        boardView = ChessPadView.drawBoardView(chessPad, relativeLayoutMain, x, y, mainBoardHolder);

        // next line/column - navigator + reverseBoard
        if (Metrics.isVertical) {
            x = 0; // xSpacing;
            y = Metrics.titleHeight + Metrics.ySpacing + Metrics.boardViewSize + Metrics.ySpacing;
            dx = Metrics.buttonSize + Metrics.xSpacing;
            dy = 0;
        } else {
            x = Metrics.boardViewSize + Metrics.xSpacing;
            y = Metrics.titleHeight + Metrics.ySpacing;
            dx = 0;
            dy = Metrics.buttonSize + Metrics.ySpacing;
        }

        RelativeLayout relativeLayoutGame = new RelativeLayout(chessPad);
        relativeLayoutGame.setBackgroundColor(Color.BLACK);
        ChessPadView.addView(relativeLayoutMain, relativeLayoutGame, x, y, Metrics.paneWidth, Metrics.paneHeight);

        x = 0; y = 0;
        int indx = 0;
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.Start, x, y, android.R.drawable.ic_media_previous);
        x += dx;
        y += dy;
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.PrevVar, x, y, android.R.drawable.ic_media_rew);
        x += dx;
        y += dy;
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.Prev, x, y, R.drawable.ic_menu_back);
        x += dx;
        y += dy;
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.Stop, x, y, android.R.drawable.ic_media_pause);
        x += dx;
        y += dy;
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.Next, x, y, R.drawable.ic_menu_forward);
        x += dx;
        y += dy;
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.NextVar, x, y, android.R.drawable.ic_media_ff);
        x += dx;
        y += dy;
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.End, x, y, android.R.drawable.ic_media_next);

        if (Metrics.isVertical) {
            x = Metrics.paneWidth - Metrics.buttonSize;
        } else {
            y = Metrics.paneHeight - Metrics.buttonSize;
        }
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.Reverse, x, y, R.drawable.ic_menu_refresh);

        // info fields:
        move = new TextView(chessPad);
        move.setFocusable(false);
        move.setSingleLine();
        move.setBackgroundColor(Color.WHITE);
        move.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        glyph = new TextView(chessPad);
        glyph.setSingleLine();
        glyph.setBackgroundColor(Color.GREEN);
        glyph.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    chessPad.onButtonClick(ChessPad.Command.ShowGlyphs);
                }
                // true if the event was handled and should not be given further down to other views.
                return true;
            }
        });
        glyph.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        comment = new ChessPadView.CpEditText(chessPad);
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

        int _paneWidth;
        if (Metrics.isVertical) {
            x = 0;  // xSpacing;a
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

        ChessPadView.addTextView(relativeLayoutGame, move, x, y, w, Metrics.titleHeight);
        y += dy;

        w = Metrics.maxMoveWidth / 2;
        ChessPadView.addTextView(relativeLayoutGame, glyph, x1, y, w, Metrics.titleHeight);
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.Delete,
                Metrics.paneWidth - Metrics.buttonSize, y, Metrics.buttonSize, Metrics.titleHeight, android.R.drawable.ic_delete);

        y += Metrics.titleHeight + Metrics.ySpacing;
        int h = Metrics.screenHeight - y;
        ChessPadView.addTextView(relativeLayoutGame, comment, x, y, Metrics.paneWidth, h);
        if (Metrics.isVertical) {
            Metrics.cpScreenWidth = Metrics.screenWidth;
            Metrics.cpScreenHeight = y + h;
        } else {
            Metrics.cpScreenWidth = Metrics.screenWidth;
            Metrics.cpScreenHeight = y + h;
        }
        setButtonEnabled(ChessPad.Command.Stop.getValue(), false);
    }

    public void invalidate() {
        title.setText(chessPad.pgnTree.getTitle());
        if(chessPad.selected == null) {
            boardView.setSelected(chessPad.pgnTree.getCurrentToSquare());
        } else {
            boardView.setSelected(chessPad.selected);
        }

        move.setText(chessPad.pgnTree.getCurrentMove());
        int g = chessPad.pgnTree.getGlyph();
        if (g == 0) {
            glyph.setText("");
        } else {
            glyph.setText(String.format("%s%d", Config.PGN_GLYPH, g));
        }
        comment.setText(chessPad.pgnTree.getComment());
        if (navigationEnabled) {
            if (chessPad.isFirstMove()) {
                setButtonEnabled(ChessPad.Command.Start.getValue(), false);
                setButtonEnabled(ChessPad.Command.Prev.getValue(), false);
                setButtonEnabled(ChessPad.Command.PrevVar.getValue(), false);
                setButtonEnabled(ChessPad.Command.Delete.getValue(), true);
            } else {
                setButtonEnabled(ChessPad.Command.Start.getValue(), true);
                setButtonEnabled(ChessPad.Command.Prev.getValue(), true);
                setButtonEnabled(ChessPad.Command.PrevVar.getValue(), chessPad.pgnTree.getVariations() == null);
                setButtonEnabled(ChessPad.Command.Delete.getValue(), true);
            }

            if (chessPad.isLastMove()) {
                setButtonEnabled(ChessPad.Command.Next.getValue(), false);
                setButtonEnabled(ChessPad.Command.NextVar.getValue(), false);
                setButtonEnabled(ChessPad.Command.End.getValue(), false);
            } else {
                setButtonEnabled(ChessPad.Command.Next.getValue(), true);
                setButtonEnabled(ChessPad.Command.NextVar.getValue(), chessPad.pgnTree.getVariations() == null);
                setButtonEnabled(ChessPad.Command.End.getValue(), true);
            }
        }
        boardView.invalidate();
    }

    void setButtonEnabled(int indx, boolean enable) {
        imageButtons[indx].setEnabled(enable);
    }

    public void enableCommentEdit(boolean enable) {
        comment.setEnabled(enable);
    }
}