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
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatImageButton;

import com.ab.droid.chesspad.BoardHolder;
import com.ab.droid.chesspad.ChessPad;
import com.ab.droid.chesspad.R;
import com.ab.pgn.Config;
import com.ab.pgn.Pair;

import java.util.List;

/**
 * full ChessPad screen
 * Created by Alexander Bootman on 8/20/16.
 */
public class ChessPadLayout implements ProgressBarHolder {
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    static Bitmap checkBitmap;

    final ChessPad chessPad;
    final RelativeLayout mainLayout;
    private ChessPadLayout.CpProgressBar cpProgressBar;
    CpImageButton menuButton;
    TextView title;
    final BoardView boardView;

    private final ChessPadLayout.CpImageButton[] imageButtons = new ChessPadLayout.CpImageButton[ChessPad.Command.total()];

    private CpView cpView;
    private final GameView gameView;
    private final SetupView setupView;
    private final DgtBoardView dgtBoardView;

    public ChessPadLayout(ChessPad chessPad) {
        this.chessPad = chessPad;
        mainLayout = chessPad.getMainLayout();

        try {
            chessPad.getSupportActionBar().hide();
        } catch (Exception e) {
            Log.e(this.getClass().getName(), "getSupportActionBar error", e);
        }

        checkBitmap = BitmapFactory.decodeResource(chessPad.getResources(), R.drawable.chk);

        createTitleBar();
        boardView = new BoardView(chessPad);
        mainLayout.addView(boardView);

        gameView = new GameView(this);
        setupView = new SetupView(this);
        dgtBoardView = new DgtBoardView(this);

        cpView = gameView;
    }

    CpImageButton createImageButton(final RelativeLayout relativeLayout, final ChessPad.Command command, int resource) {
        CpImageButton btn = new CpImageButton(chessPad, resource);
        btn.setId(command.getValue());
        btn.setOnClickListener((v) -> {
            chessPad.onButtonClick(ChessPad.Command.command(v.getId()));
        });
        relativeLayout.addView(btn);
        return btn;
    }

    public void redraw() {
        cpView.hideAllWidgets();
        recalcSizes();

        int x = 0, w, xb = 0, yb = Metrics.titleHeight + Metrics.ySpacing;
        moveTo(menuButton, Metrics.screenWidth - Metrics.buttonSize, 0, Metrics.buttonSize, Metrics.titleHeight);
        if (Metrics.isVertical) {
            xb = (Metrics.screenWidth - Metrics.boardViewSize) / 2;
        } else {
            if (Metrics.screenHeight - Metrics.boardViewSize < Metrics.titleHeight) {
                x = Metrics.boardViewSize + 2 * Metrics.xSpacing;
                yb = 0;
            }
        }
        w = Metrics.screenWidth - x - Metrics.buttonSize - Metrics.xSpacing;
        moveTo(title, x, 0, w, Metrics.titleHeight);

        moveTo(boardView, xb, yb, Metrics.boardViewSize, Metrics.boardViewSize);

        switch (chessPad.getMode()) {
            case Game:
            case Puzzle:
                cpView = gameView;
                break;

            case Setup:
                cpView = setupView;
                break;

            case DgtGame:
                cpView = dgtBoardView;
                break;

        }
        cpView.controlPaneLayout.setVisibility(View.VISIBLE);
        cpView.draw();
        boolean showProgressBar = false;
        if (cpProgressBar != null) {
            showProgressBar = cpProgressBar.isVisible();
        }
        cpProgressBar = new CpProgressBar(chessPad, mainLayout);
        cpProgressBar.show(showProgressBar);
        invalidate();
    }

    public void invalidate() {
        if(cpView != null) {
            cpView.invalidate();
        }
        mainLayout.invalidate();
    }

    private void recalcSizes() {
        int resource = chessPad.getResources().getIdentifier("status_bar_height", "dimen", "android");
        Metrics.statusBarHeight = 0;
        if (resource > 0) {
            Metrics.statusBarHeight = chessPad.getResources().getDimensionPixelSize(resource);
        }
        DisplayMetrics metrics = chessPad.getResources().getDisplayMetrics();
        Metrics.screenWidth = metrics.widthPixels;
        Metrics.screenHeight = metrics.heightPixels - Metrics.statusBarHeight;

        int orientation = chessPad.getResources().getConfiguration().orientation;
        Metrics.isVertical = orientation == Configuration.ORIENTATION_PORTRAIT;
        if (!Metrics.isVertical) {
            // almost square screens will have vertical layout in both orientations
            Metrics.isVertical = (float)Metrics.screenWidth / Metrics.screenHeight <= 1.3f;
        }

        TextView dummy = new TextView(chessPad);
        dummy.setSingleLine();
        dummy.setTextSize(16);
        dummy.setText("100. ... Qa2xa8+");
        dummy.measure(0, 0);
        Metrics.titleHeight = dummy.getMeasuredHeight();
        Metrics.maxMoveWidth = dummy.getMeasuredWidth();

        dummy = new TextView(chessPad);
        dummy.setSingleLine();
        dummy.setTextSize(16);
        dummy.setText(R.string.label_en_pass);
        dummy.measure(0, 0);
        Metrics.moveLabelWidth = dummy.getMeasuredWidth();

        dummy = new TextView(chessPad);
        dummy.setSingleLine();
        dummy.setTextSize(16);
        dummy.setText(R.string.label_halfmove_clock);
        dummy.measure(0, 0);
        Metrics.halfMoveClockLabelWidth = dummy.getMeasuredWidth();

        dummy = new TextView(chessPad);
        dummy.setSingleLine();
        dummy.setTextSize(16);
        dummy.setText("0:00:00 ");
        dummy.measure(0, 0);
        Metrics.timeWidth = dummy.getMeasuredWidth();

        if (Metrics.isVertical) {
            // Setup screen based on board filling the whole screen width
            Metrics.squareSize = Metrics.screenWidth / 8;
            Metrics.xSpacing = Metrics.squareSize / 20;
            if (Metrics.xSpacing <= 1) {
                Metrics.xSpacing = 1;
            }
            Metrics.ySpacing = Metrics.xSpacing;
            Metrics.buttonSize = Metrics.squareSize - Metrics.xSpacing;

            int size;
            // Setup screen based on 2 text views and 8 rows for board, 4 rows for pieces and buttons)
            // game layout
            size = (Metrics.screenHeight - Metrics.titleHeight - 2 * Metrics.ySpacing - 8 * Metrics.squareSize) / 4;
            if (Metrics.buttonSize > size) {
                Metrics.buttonSize = size;
                Metrics.xSpacing = Metrics.buttonSize / 20;
                if (Metrics.xSpacing <= 1) {
                    Metrics.xSpacing = 1;
                }
                Metrics.buttonSize -= Metrics.xSpacing;
            }

            if (5 * Metrics.buttonSize < 4 * Metrics.squareSize) {
                Metrics.squareSize = (Metrics.screenHeight - Metrics.titleHeight) / 12;
                Metrics.xSpacing = Metrics.squareSize / 20;
                if (Metrics.xSpacing <= 1) {
                    Metrics.xSpacing = 1;
                }
                Metrics.ySpacing = Metrics.xSpacing;
                Metrics.buttonSize = Metrics.squareSize - Metrics.xSpacing;
            }

            // Setup screen, buttons part
            // 2 button row, w text rows
            size = (Metrics.screenWidth - Metrics.moveLabelWidth / 2 - Metrics.halfMoveClockLabelWidth - 4 * Metrics.xSpacing) / 3;
            if (Metrics.squareSize > size) {
                Metrics.squareSize = size;
            }

            Metrics.boardViewSize = Metrics.squareSize * 8;
            Metrics.paneHeight = Metrics.screenHeight - Metrics.boardViewSize - Metrics.titleHeight - Metrics.ySpacing;
            Metrics.paneWidth = Metrics.screenWidth;
        } else {
            Metrics.paneHeight = Metrics.screenHeight - Metrics.titleHeight;
            Metrics.squareSize = Metrics.screenHeight / 8;
            Metrics.boardViewSize = Metrics.squareSize * 8;
            Metrics.ySpacing = Metrics.squareSize / 20;
            if (Metrics.ySpacing <= 1) {
                Metrics.ySpacing = 1;
            }
            Metrics.xSpacing = Metrics.ySpacing;

            Metrics.buttonSize = Metrics.paneHeight / Config.BOARD_SIZE - Metrics.xSpacing;
            Metrics.paneWidth = Metrics.screenWidth - Metrics.boardViewSize - Metrics.xSpacing;

            if (2 * Metrics.boardViewSize > 3 * Metrics.paneWidth) {    // adjustment for close to square screens
                Metrics.squareSize = Metrics.screenWidth * 3 / 5 / 8;
                Metrics.boardViewSize = Metrics.squareSize * 8;
                Metrics.paneWidth = Metrics.screenWidth - Metrics.boardViewSize - Metrics.xSpacing;
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    void createTitleBar() {
        title = new TextView(chessPad);
        title.setSingleLine();
        title.setBackgroundColor(Color.GREEN);
        title.setTextColor(Color.BLACK);

        final TitleHolder titleHolder = new TitleHolder() {
            @Override
            public void onTitleClick() {
                chessPad.onButtonClick(ChessPad.Command.EditTags);
            }

            @Override
            public List<Pair<String, String>> getTags() {
                return chessPad.getTags();
            }
        };

        title.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                titleHolder.onTitleClick();
            }
            return true;
        });
        title.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        this.addView(title);
        menuButton = this.createImageButton(mainLayout, ChessPad.Command.Menu, R.drawable.menu);
    }

    void moveTo(View v, int x, int y, int w, int h) {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);

        layoutParams.leftMargin = x;
        layoutParams.topMargin = y;
        layoutParams.width = w;
        layoutParams.height = h;
        v.setLayoutParams(layoutParams);
        v.setVisibility(View.VISIBLE);
    }

    void addView(View view) {
        mainLayout.addView(view);
    }

    static void addView(RelativeLayout relativeLayout, View view, int x, int y, int w, int h) {
        RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(w, h);
        rlp.setMargins(x, y, 0, 0);
        relativeLayout.addView(view, rlp);
    }

    public void setButtonEnabled(ChessPad.Command command, boolean enable) {
        if (cpView instanceof GameView) {
            ((GameView)cpView).setButtonEnabled(command, enable);
        }
    }

    public void enableCommentEdit(boolean enable) {
        if (cpView instanceof GameView) {
            ((GameView)cpView).enableCommentEdit(enable);
        }
    }

    @Override
    public void showProgressBar(boolean doShow) {
        cpProgressBar.show(doShow);
    }

    @Override
    public void updateProgressBar(int progress) {
        cpProgressBar.update(progress);
    }

    static class CpEditText extends AppCompatEditText {
        private ChessPadLayout.StringKeeper stringKeeper;

        public CpEditText(Context context) {
            super(context);
            this.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (stringKeeper == null) {
                        return;
                    }
                    String text = s.toString().toLowerCase();
                    String oldText = stringKeeper.getValue();
                    if(!text.equals(oldText)) {
                        stringKeeper.setValue(text);
                    }
                }
            });
        }

        public void setStringKeeper(ChessPadLayout.StringKeeper stringKeeper) {
            this.stringKeeper = stringKeeper;
        }

        public void draw() {
            if (stringKeeper != null) {
                this.setText(stringKeeper.getValue());
            }
        }
    }

    static class CpImageButton extends AppCompatImageButton {
        CpImageButton(Context context, int resource) {
            super(context);
            setBackgroundResource(R.drawable.btn_background);
            if (resource != -1) {
                setImageResource(resource);
            }
            setPadding(1, 1, 1, 1);
            setScaleType(ImageView.ScaleType.FIT_START);
            setAdjustViewBounds(true);
        }
    }

    public interface ChangeObserver {
        void onValueChanged(Object value);
    }

    public abstract static class StringKeeper {
        public void setValue(String str) {}
        public String getValue() { return null; }
        public int getNumericValue(String value) {
            int res = 0;
            if(value != null && !value.isEmpty()) {
                try {
                    res = Integer.valueOf(value);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return res;
        }
    }

    public interface FlagKeeper {
        void setFlag(boolean flag);
        boolean getFlag();
    }

    static class CpToggleButton extends ChessPadLayout.CpImageButton {
        private final FlagKeeper flagKeeper;

        public CpToggleButton(RelativeLayout chessPadLayout, int rscNormal, FlagKeeper flagKeeper) {
            super(chessPadLayout.getContext(), rscNormal);
            this.flagKeeper = flagKeeper;
            chessPadLayout.addView(this);
            setOnClickListener((v) -> {
                if(CpToggleButton.this.flagKeeper != null) {
                    CpToggleButton.this.flagKeeper.setFlag(!CpToggleButton.this.flagKeeper.getFlag());
                    CpToggleButton.this.invalidate();
                }
            });
        }

        @Override
        public void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (CpToggleButton.this.flagKeeper.getFlag()) {
                canvas.drawBitmap(checkBitmap, 3, 3, null);
            }
        }
    }

    static abstract class CpView {
        final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();
        ChessPad chessPad;
        ChessPadLayout chessPadLayout;
        RelativeLayout controlPaneLayout;
        BoardHolder boardHolder;
        int selectedPiece = -1;

        CpView() {
        }

        CpView(ChessPadLayout chessPadLayout) {
            init(chessPadLayout);
        }

        protected void init(ChessPadLayout chessPadLayout) {
            this.chessPadLayout = chessPadLayout;
            this.chessPad = chessPadLayout.chessPad;
            this.boardHolder = chessPadLayout.chessPad;

            controlPaneLayout = new RelativeLayout(chessPad);
            chessPadLayout.addView(controlPaneLayout);
        }

        void draw() {
            Log.d(DEBUG_TAG, String.format("draw %s", Thread.currentThread().getName()));
            int x, y;

            if (Metrics.isVertical) {
                x = 0; // xSpacing;
                y = Metrics.titleHeight + Metrics.ySpacing + Metrics.boardViewSize + Metrics.ySpacing;
            } else {
                x = Metrics.boardViewSize + Metrics.xSpacing;
                y = Metrics.titleHeight + Metrics.ySpacing;
            }
            chessPadLayout.moveTo(controlPaneLayout, x, y, Metrics.paneWidth, Metrics.paneHeight);
            controlPaneLayout.setVisibility(View.VISIBLE);
        }

        ChessPadLayout.CpEditText createTextView() {
            ChessPadLayout.CpEditText view = new ChessPadLayout.CpEditText(chessPad);
            view.setPadding(0, 0, 0, 0);
            view.setTextSize(16);
            view.setTextColor(Color.DKGRAY);
            controlPaneLayout.addView(view);
            return view;
        }

        void hideAllWidgets() {
            controlPaneLayout.setVisibility(View.GONE);
        }

        abstract void invalidate();

    }

    static class CpProgressBar {
        private boolean isVisible;
        private final ProgressBar progressBar;
        private final TextView progressText;

        CpProgressBar(Context context, RelativeLayout relativeLayout) {
            int x, y, h, w;
            w = Metrics.boardViewSize;
            x = (Metrics.screenWidth - w) / 2;
            h = Metrics.titleHeight;
            y = Metrics.titleHeight * 2;
            progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setVisibility(View.GONE);
            progressBar.setMax(100);
            addView(relativeLayout, progressBar, x, y, w, h);

            w = Metrics.maxMoveWidth / 2;
            x = (Metrics.screenWidth - w) / 2;
            y += h + Metrics.ySpacing;
            h = Metrics.titleHeight;
            progressText = new TextView(context);
            progressText.setSingleLine();
            progressText.setBackgroundColor(Color.RED);
            progressText.setTextColor(Color.WHITE);
            progressText.setGravity(Gravity.CENTER);
            progressText.setVisibility(View.GONE);
            addView(relativeLayout, progressText, x, y, w, h);
            isVisible = false;
        }

        boolean isVisible() {
            return this.isVisible;
        }

        void show(boolean doShow) {
            if (progressBar == null) {
                return;
            }
            isVisible = doShow;
            if (doShow) {
                progressBar.setVisibility(View.VISIBLE);
                progressText.setVisibility(View.VISIBLE);
            } else {
                progressBar.setVisibility(View.GONE);
                progressText.setVisibility(View.GONE);
            }
        }

        void update(int progress) {
            progressBar.setProgress(progress);
            progressText.setText(String.format("%s%%", progress));
        }
    }
}

