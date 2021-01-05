package com.ab.droid.chesspad.layout;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ab.droid.chesspad.ChessPad;
import com.ab.droid.chesspad.R;
import com.ab.pgn.Config;
import com.ab.pgn.lichess.LichessPad;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 *
 * Created by Alexander Bootman on 11/27/16.
 */

class GameView extends ChessPadLayout.CpView {
    private final ChessPadLayout chessPadLayout;
    private final RelativeLayout gameLayout;
    private final HashMap<ChessPad.Command, ChessPadLayout.CpImageButton> imageButtons = new HashMap<>();

    private final TextView glyph, move, analysis;
    private final ChessPadLayout.CpEditText comment;
    private final LichessChartView chart;


    @SuppressLint("ClickableViewAccessibility")
    GameView(ChessPadLayout chessPadLayout) {
        super(chessPadLayout);
        this.chessPadLayout = chessPadLayout;

        gameLayout = new RelativeLayout(chessPad);
        gameLayout.setBackgroundColor(Color.BLACK);
        chessPadLayout.addView(gameLayout);

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

        chart = new LichessChartView(chessPad);
chart.setBackgroundColor(Color.LTGRAY);

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

    private ChessPadLayout.CpEditText createTextView() {
        ChessPadLayout.CpEditText view = new ChessPadLayout.CpEditText(chessPad);
        view.setPadding(0, 0, 0, 0);
        view.setTextSize(16);
        view.setTextColor(Color.DKGRAY);
        gameLayout.addView(view);
        return view;
    }

    private void createImageButton(ChessPad.Command command, int resource) {
        ChessPadLayout.CpImageButton cpImageButton = chessPadLayout.createImageButton(gameLayout, command, resource);
        imageButtons.put(command, cpImageButton);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public void draw() {
        int x, y, dx, dy;

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
        chessPadLayout.moveTo(gameLayout, x, y, Metrics.paneWidth, Metrics.paneHeight);

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
            y = Metrics.paneHeight - Metrics.titleHeight - Metrics.buttonSize;
        }
        chessPadLayout.moveTo(imageButtons.get(ChessPad.Command.Flip), x, y, Metrics.buttonSize, Metrics.buttonSize);

        // info fields:
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

        chessPadLayout.moveTo(move, x, y, w, Metrics.titleHeight);
        y += dy;

        w = Metrics.maxMoveWidth / 2;
        if (chessPad.mode != ChessPad.Mode.LichessPuzzle) {
            chessPadLayout.moveTo(glyph, x1, y, w, Metrics.titleHeight);
        }

        x1 += w;
        dx = (Metrics.paneWidth - x1) / 2 - Metrics.buttonSize;
        chessPadLayout.moveTo(imageButtons.get(ChessPad.Command.Analysis), x1 + dx, y, Metrics.buttonSize, Metrics.titleHeight);
        chessPadLayout.moveTo(imageButtons.get(ChessPad.Command.Delete),
                Metrics.paneWidth - Metrics.buttonSize, y, Metrics.buttonSize, Metrics.titleHeight);

        y += Metrics.titleHeight + Metrics.ySpacing;
        int h = Metrics.paneHeight - y;
        if (chessPad.mode == ChessPad.Mode.LichessPuzzle) {
            chessPadLayout.moveTo(chart, x, y, Metrics.paneWidth, h);
        } else {
            chessPadLayout.moveTo(comment, x, y, Metrics.paneWidth, h);
        }

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
    void hideAllWidgets() {
        gameLayout.setVisibility(View.INVISIBLE);
//        glyph.setVisibility(View.INVISIBLE);
//        move.setVisibility(View.INVISIBLE);
//        analysis.setVisibility(View.INVISIBLE);
//        comment.setVisibility(View.INVISIBLE);
//        chart.setVisibility(View.INVISIBLE);
    }

    @Override
    public void invalidate() {
        gameLayout.setVisibility(View.VISIBLE);
        relativeLayoutMain.title.setText(chessPad.getPgnGraph().getTitle());
        if(chessPad.selectedSquare == null) {
            if(chessPad.getPgnGraph().moveLine.size() > 1) {
                relativeLayoutMain.boardView.setSelected(chessPad.getPgnGraph().getCurrentMove().getTo());
            }
        } else {
            relativeLayoutMain.boardView.setSelected(chessPad.selectedSquare);
        }
        relativeLayoutMain.boardView.setHints(chessPad.getHints());

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

        if (chessPad.mode == ChessPad.Mode.LichessPuzzle) {
            LichessPad.User user;
            if ((user = chessPad.lichessPad.getUser()) == null) {
                chart.setVisibility(View.INVISIBLE);
            } else {
                chart.setVisibility(View.VISIBLE);
                List<Double> values = new ArrayList<>();
                if (user.history.length > 0) {
                    for (LichessPad.Attempt attempt : user.history) {
                        values.add((double) attempt.priorRating);
                    }
                }
                values.add((double) user.rating);
                chart.setValues(values);
            }
        } else {
            comment.setText(chessPad.getPgnGraph().getComment());
        }

        if (chessPad.isNavigationEnabled()) {
            boolean puzzleMode = chessPad.puzzleMode();
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
                    setButtonEnabled(ChessPad.Command.End, chessPad.mode == ChessPad.Mode.LichessPuzzle || chessPad.lastItemIndex() > 1);
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
        relativeLayoutMain.boardView.invalidate();
    }

    void setButtonEnabled(ChessPad.Command command, boolean enable) {
        imageButtons.get(command).setEnabled(enable);
    }

    void enableCommentEdit(boolean enable) {
        comment.setEnabled(enable);
    }
}