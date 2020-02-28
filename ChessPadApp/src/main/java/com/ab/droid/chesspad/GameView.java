package com.ab.droid.chesspad;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ab.pgn.Config;
import com.ab.pgn.Pair;

import java.util.List;
import java.util.Locale;

/**
 *
 * Created by Alexander Bootman on 11/27/16.
 */

class GameView extends ChessPadView.CpView {

    private TextView glyph, move;
    private ChessPadView.CpEditText comment;
    boolean navigationEnabled = true;

    private final ChessPadView.CpImageButton[] imageButtons = new ChessPadView.CpImageButton[ChessPad.Command.total()];

    GameView(ChessPad chessPad) {
        super(chessPad);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public void draw() {
        int x, y, dx, dy;

        title = ChessPadView.drawTitleBar(chessPad, new TitleHolder() {
            @Override
            public int getLength() {
                return Metrics.screenWidth;
            }

            @Override
            public void onTitleClick() {
                chessPad.onButtonClick(ChessPad.Command.EditTags);
            }

            @Override
            public List<Pair<String, String>> getTags() {
                return chessPad.getTags();
            }
        });

        if (Metrics.isVertical) {
            x = (Metrics.screenWidth - Metrics.boardViewSize) / 2;
        } else {
            x = 0;
        }
        y = Metrics.titleHeight + Metrics.ySpacing;
        boardView = ChessPadView.drawBoardView(chessPad, relativeLayoutMain, x, y, chessPad);

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
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.Start, x, y, R.drawable.game_start);
        x += dx;
        y += dy;
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.PrevVar, x, y, R.drawable.prev_variant);
        x += dx;
        y += dy;
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.Prev, x, y, R.drawable.prev_move);
        x += dx;
        y += dy;
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.Stop, x, y, R.drawable.pause);
        x += dx;
        y += dy;
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.Next, x, y, R.drawable.next_move);
        x += dx;
        y += dy;
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.NextVar, x, y, R.drawable.next_variant);
        x += dx;
        y += dy;
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.End, x, y, R.drawable.last_move);

        if (Metrics.isVertical) {
            x = Metrics.paneWidth - Metrics.buttonSize;
        } else {
            y = Metrics.paneHeight - Metrics.buttonSize;
        }
        imageButtons[++indx] = ChessPadView.addImageButton(chessPad, relativeLayoutGame, ChessPad.Command.Flip, x, y, R.drawable.flip_board_view);

        // info fields:
        move = new TextView(chessPad);
        move.setFocusable(false);
        move.setSingleLine();
        move.setBackgroundColor(Color.WHITE);
        move.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

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
                Metrics.paneWidth - Metrics.buttonSize, y, Metrics.buttonSize, Metrics.titleHeight, R.drawable.delete);

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

    @Override
    public void invalidate() {
        title.setText(chessPad.getPgnGraph().getTitle());
        if(chessPad.selectedSquare == null) {
            if(chessPad.getPgnGraph().moveLine.size() > 1) {
                boardView.setSelected(chessPad.getPgnGraph().getCurrentMove().getTo());
            }
        } else {
            boardView.setSelected(chessPad.selectedSquare);
        }

        move.setText(chessPad.getPgnGraph().getNumberedMove());
        int g = chessPad.getPgnGraph().getGlyph();
        if (g == 0) {
            glyph.setText("");
        } else {
            glyph.setText(String.format(Locale.getDefault(), "%s%d", Config.PGN_GLYPH, g));
        }
        comment.setText(chessPad.getPgnGraph().getComment());
        if (navigationEnabled) {
            if (chessPad.isFirstMove()) {
                imageButtons[ChessPad.Command.Start.getValue()].setImageResource(R.drawable.prev_game);
                if(chessPad.getMode() == ChessPad.Mode.Puzzle) {
                    setButtonEnabled(ChessPad.Command.Start.getValue(), false);
                } else {
                    setButtonEnabled(ChessPad.Command.Start.getValue(), chessPad.getPgnGraph().getPgn().getIndex() > 0);
                }
                setButtonEnabled(ChessPad.Command.Prev.getValue(), false);
                setButtonEnabled(ChessPad.Command.PrevVar.getValue(), false);
                setButtonEnabled(ChessPad.Command.Delete.getValue(), chessPad.getPgnGraph().isDeletable());
            } else {
                imageButtons[ChessPad.Command.Start.getValue()].setImageResource(R.drawable.game_start);
                setButtonEnabled(ChessPad.Command.Start.getValue(), true);
                setButtonEnabled(ChessPad.Command.Prev.getValue(), true);
                setButtonEnabled(ChessPad.Command.PrevVar.getValue(), chessPad.getPgnGraph().getVariations() == null);
                setButtonEnabled(ChessPad.Command.Delete.getValue(), true);
            }

            if (chessPad.isLastMove()) {
                setButtonEnabled(ChessPad.Command.Next.getValue(), false);
                setButtonEnabled(ChessPad.Command.NextVar.getValue(), false);
                imageButtons[ChessPad.Command.End.getValue()].setImageResource(R.drawable.next_game);
                if(chessPad.getMode() == ChessPad.Mode.Puzzle) {
                    setButtonEnabled(ChessPad.Command.End.getValue(), chessPad.lastItemIndex() > 1);
                } else {
                    setButtonEnabled(ChessPad.Command.End.getValue(), chessPad.getPgnGraph().getPgn().getIndex() < chessPad.lastItemIndex());
                }
            } else {
                setButtonEnabled(ChessPad.Command.Next.getValue(), true);
                setButtonEnabled(ChessPad.Command.NextVar.getValue(), chessPad.getPgnGraph().getVariations() == null);
                if(chessPad.getMode() == ChessPad.Mode.Puzzle) {
                    imageButtons[ChessPad.Command.End.getValue()].setImageResource(R.drawable.next_game);
                } else {
                    imageButtons[ChessPad.Command.End.getValue()].setImageResource(R.drawable.last_move);
                }
                setButtonEnabled(ChessPad.Command.End.getValue(), true);
            }
        }
        boardView.invalidate();
    }

    void setButtonEnabled(int indx, boolean enable) {
        imageButtons[indx].setEnabled(enable);
    }

    void enableCommentEdit(boolean enable) {
        comment.setEnabled(enable);
    }
}