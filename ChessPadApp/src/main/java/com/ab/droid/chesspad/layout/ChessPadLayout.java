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
import android.view.ViewGroup;
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

    private final ChessPad chessPad;
    final RelativeLayout mainLayout;
    private ChessPadLayout.CpProgressBar cpProgressBar;
    CpImageButton menuButton;
    TextView title;
    BoardView boardView;

    TextView setupStatus;
//    BoardHolder boardHolder;
//    TextView glyph, move, analysis;
//    ChessPadLayout.CpEditText comment;
//    LichessChartView chart;


    private final ChessPadLayout.CpImageButton[] imageButtons = new ChessPadLayout.CpImageButton[ChessPad.Command.total()];
    private final Bitmap checkBitmap;

    private CpView cpView;
    private final GameView gameView;
    private final SetupView setupView;
    private final DgtBoardView dgtBoardView;
    private final FicsPadView ficsPadView;

    public ChessPadLayout(ChessPad chessPad) {
//        super(chessPad);
        this.chessPad = chessPad;
        mainLayout = chessPad.getMainLayout();

        try {
            chessPad.getSupportActionBar().hide();
        } catch (Exception e) {
            Log.e(this.getClass().getName(), "getSupportActionBar error", e);
        }

//        RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(
//                RelativeLayout.LayoutParams.MATCH_PARENT,
//                RelativeLayout.LayoutParams.MATCH_PARENT);
//        setBackgroundColor(Color.CYAN);
//        chessPad.setContentView(this, rlp);

        checkBitmap = BitmapFactory.decodeResource(chessPad.getResources(), R.drawable.chk);

        createTitleBar();
/*
        title = createTitleBar(chessPad, new TitleHolder() {
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
*/
        boardView = new BoardView(chessPad);
        mainLayout.addView(boardView);

        gameView = new GameView(this);
        setupView = new SetupView(this);
        dgtBoardView = new DgtBoardView(this);
        ficsPadView = new FicsPadView(this);

        cpView = gameView;
    }

    CpImageButton createImageButton(final RelativeLayout relativeLayout, final ChessPad.Command command, int resource) {
        CpImageButton btn = new CpImageButton(chessPad, resource);
        btn.setId(command.getValue());
        btn.setOnClickListener((v) -> {
//            Log.d(Config.DEBUG_TAG + "ImageBtn", String.format("OnClick %s", command.toString()));
            chessPad.onButtonClick(ChessPad.Command.command(v.getId()));
        });
        relativeLayout.addView(btn);
        return btn;
    }

/*
    private void  hideAllWidgets() {
        gameView.hideAllWidgets();
        setupView.hideAllWidgets();
        dgtBoardView.hideAllWidgets();
        ficsPadView.hideAllWidgets();
    }
*/

    public void redraw() {
//        relativeLayoutMain.removeAllViewsInLayout();
//        removeAllViews();

        cpView.hideAllWidgets();
        recalcSizes();
//        ChessPad chessPad = (ChessPad)getContext();
        if (Metrics.isVertical) {
            moveTo(boardView, 0, Metrics.titleHeight + Metrics.ySpacing, Metrics.boardViewSize, Metrics.boardViewSize);
        } else {
            moveTo(boardView, 0, 0, Metrics.boardViewSize, Metrics.boardViewSize);
        }

        boardView.draw();
/*
// DEBUG!! {
        for (int i = 0; i < 10; ++i) {
            boardView.draw();
            System.gc();
        }
        System.gc();
// DEBUG!! }
//*/

//        CpView cpView = null;
        drawTitleBar();

        switch (chessPad.getMode()) {
            case LichessPuzzle:
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

            case FicsConnection:
                cpView = ficsPadView;
                break;

        }
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
//        ChessPad chessPad = (ChessPad)getContext();
        int orientation = chessPad.getResources().getConfiguration().orientation;
        Metrics.isVertical = orientation == Configuration.ORIENTATION_PORTRAIT;

        int resource = chessPad.getResources().getIdentifier("status_bar_height", "dimen", "android");
        Metrics.statusBarHeight = 0;
        if (resource > 0) {
            Metrics.statusBarHeight = chessPad.getResources().getDimensionPixelSize(resource);
        }
        DisplayMetrics metrics = chessPad.getResources().getDisplayMetrics();
        Metrics.screenWidth = metrics.widthPixels;
        Metrics.screenHeight = metrics.heightPixels - Metrics.statusBarHeight;
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
            Metrics.paneHeight = Metrics.screenHeight;
            Metrics.squareSize = Metrics.paneHeight / 8;
//            Metrics.ySpacing = Metrics.buttonSize / 20;
            Metrics.ySpacing = Metrics.squareSize / 20;
            if (Metrics.ySpacing <= 1) {
                Metrics.ySpacing = 1;
//            } else {
//                Metrics.squareSize -= Metrics.ySpacing;
            }
            Metrics.xSpacing = Metrics.ySpacing;
            Metrics.buttonSize = (Metrics.paneHeight - Metrics.titleHeight) / ChessPad.Command.Flip.getValue() - Metrics.xSpacing;
            Metrics.boardViewSize = Metrics.paneHeight;
            Metrics.paneWidth = Metrics.screenWidth - Metrics.boardViewSize - Metrics.xSpacing;
        }
        Metrics.boardViewSize = Metrics.squareSize * 8;
    }

    @SuppressLint("ClickableViewAccessibility")
    void createTitleBar() {
        title = new TextView(chessPad);
        title.setSingleLine();
        title.setBackgroundColor(Color.GREEN);
        title.setTextColor(Color.BLACK);

        final TitleHolder titleHolder = new TitleHolder() {
//            @Override
//            public int getLength() {
//                return Metrics.screenWidth;
//            }

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

    private void drawTitleBar() {
        int x, w;
        moveTo(menuButton, Metrics.screenWidth - Metrics.buttonSize, 0, Metrics.buttonSize, Metrics.titleHeight);
        if (Metrics.isVertical) {
            x = 0;
        } else {
            x = Metrics.boardViewSize + 2 * Metrics.xSpacing;
        }

/*
        if (Metrics.isVertical) {
            x = 0;
            x = Metrics.screenWidth - Metrics.buttonSize;
        } else {
            x = Metrics.boardViewSize;
        }

        moveTo(menuButton, x, 0, Metrics.buttonSize, Metrics.titleHeight);
*/
        w = Metrics.screenWidth - x - Metrics.buttonSize - Metrics.xSpacing;
        moveTo(title, x, 0, w, Metrics.titleHeight);

//
//        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
//                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
//
//        layoutParams.leftMargin = 0;
//        layoutParams.topMargin = 0;
//        layoutParams.width = Metrics.buttonSize;
//        layoutParams.height = Metrics.titleHeight;
//        menuButton.setLayoutParams(layoutParams);
//
//        layoutParams.leftMargin = layoutParams.width + Metrics.xSpacing;
//        layoutParams.width = Metrics.screenWidth - layoutParams.leftMargin;
//        title.setLayoutParams(layoutParams);
    }

    void moveTo(View v, int x, int y, int w, int h) {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);

        layoutParams.leftMargin = x;
        layoutParams.topMargin = y;
        layoutParams.width = w;
        layoutParams.height = h;
        v.setLayoutParams(layoutParams);
    }

    void moveTo(View v, int x, int y) {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);

        layoutParams.leftMargin = x;
        layoutParams.topMargin = y;
        v.setLayoutParams(layoutParams);
    }

/*
    BoardView drawBoardView(Context context, RelativeLayout relativeLayout, int x, int y, BoardHolder boardHolder) {
        BoardView boardView = new BoardView(context, boardHolder);
        addView(relativeLayout, boardView, x, y);
        return boardView;
    }
*/

    void addTextView(RelativeLayout relativeLayout, TextView view, int x, int y, int w, int h) {
        view.setPadding(0, 0, 0, 0);
        view.setTextSize(16);
        view.setTextColor(Color.DKGRAY);
        if (w == 0) {
            w = RelativeLayout.LayoutParams.MATCH_PARENT;
        }
        if (h == 0) {
            h = RelativeLayout.LayoutParams.MATCH_PARENT;
        }
        addView(relativeLayout, view, x, y, w, h);
    }

    CpImageButton addImageButton(ChessPad chessPad, RelativeLayout relativeLayout, ChessPad.Command command, int x, int y, int resource) {
        return addImageButton(chessPad, relativeLayout, command, x, y, Metrics.buttonSize, Metrics.buttonSize, resource);
    }

    CpImageButton addImageButton(final ChessPad chessPad, RelativeLayout relativeLayout, final ChessPad.Command command, int x, int y, int width, int height, int resource) {
        CpImageButton btn = new CpImageButton(chessPad, resource);
        btn.setId(command.getValue());
        addView(relativeLayout, btn, x, y, width, height);
        btn.setOnClickListener((v) -> {
//            Log.d(Config.DEBUG_TAG + "ImageBtn", String.format("OnClick %s", command.toString()));
            chessPad.onButtonClick(ChessPad.Command.command(v.getId()));
        });
        return btn;
    }

    void addView(View view) {
        mainLayout.addView(view);
    }

    private void addView(RelativeLayout relativeLayout, View view, int x, int y) {
        addView(relativeLayout, view, x, y, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static void addView(RelativeLayout relativeLayout, View view, int x, int y, int w, int h) {
        RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(w, h);
        rlp.setMargins(x, y, 0, 0);
        relativeLayout.addView(view, rlp);
//        relativeLayout.setLayoutParams();
    }

    public void setButtonEnabled(int indx, boolean enable) {
//        if (cpView instanceof GameView) {
//            ((GameView)cpView).setButtonEnabled(indx, enable);
//        }
    }

    // how to redraw after keyboard dismissal?
    // remove?
    private void createLabel(Context context, RelativeLayout relativeLayout, int x, int y, int w1, int h, int rscLabel) {
        TextView label = new TextView(context);
        label.setBackgroundColor(Color.GRAY);
        label.setTextColor(Color.BLACK);
        label.setPadding(Metrics.xSpacing, Metrics.ySpacing, Metrics.xSpacing, Metrics.ySpacing);
        label.setGravity(Gravity.START | Gravity.CENTER);
        label.setText(rscLabel);
        addTextView(relativeLayout, label, x, y, w1, h);
    }

    public CpEditText createLabeledEditText(Context context, RelativeLayout relativeLayout, int x, int y, int labelWidth, int valueWidth, int h, int rscLabel) {
        createLabel(context, relativeLayout, x, y, labelWidth, h, rscLabel);
        CpEditText editText = new CpEditText(context);
        editText.setBackgroundColor(Color.GREEN);
        editText.setTextColor(Color.RED);
        editText.setPadding(Metrics.xSpacing, Metrics.ySpacing, Metrics.xSpacing, Metrics.ySpacing);
        editText.setGravity(Gravity.CENTER);
        editText.setSingleLine();
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        addTextView(relativeLayout, editText, x + labelWidth + 2 * Metrics.xSpacing, y, valueWidth, h);
        return editText;
    }

    public void enableCommentEdit(boolean enable) {
//        if (cpView instanceof GameView) {
//            ((GameView)cpView).enableCommentEdit(enable);
//        }
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
            this.setText(stringKeeper.getValue());
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

    public class CpToggleButton extends ChessPadLayout.CpImageButton {
        private FlagKeeper flagKeeper;

        public CpToggleButton(ChessPad chessPad, RelativeLayout rl, int size, int x, int y, int rscNormal) {
            super(chessPad, rscNormal);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(size, size);
            lp.setMargins(x, y, 0, 0);
            rl.addView(this, lp);
            setOnClickListener((v) -> {
                if(CpToggleButton.this.flagKeeper != null) {
                    CpToggleButton.this.flagKeeper.setFlag(!CpToggleButton.this.flagKeeper.getFlag());
                }
                CpToggleButton.this.invalidate();
            });
        }

        public void setFlagKeeper(FlagKeeper flagKeeper) {
            this.flagKeeper = flagKeeper;
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
        final ChessPad chessPad;
        final ChessPadLayout relativeLayoutMain;
        final BoardHolder boardHolder;
//        TextView setupStatus;

//        TextView title;
//
//        BoardView boardView;
        int selectedPiece = -1;

/*
        CpView(ChessPad chessPad) {
            this.relativeLayoutMain = chessPad.chessPadLayout;
            this.chessPad = chessPad;
            this.boardHolder = chessPad;
        }
*/

        CpView(ChessPadLayout chessPadLayout) {
            this.relativeLayoutMain = chessPadLayout;
            this.chessPad = chessPadLayout.chessPad;
            this.boardHolder = chessPadLayout.chessPad;
        }

        void draw() {
            Log.d(DEBUG_TAG, String.format("draw %s", Thread.currentThread().getName()));
        }

        void setStatus(int errNum) {
            if(relativeLayoutMain.setupStatus != null) {
                relativeLayoutMain.setupStatus.setText(chessPad.getSetupErr(errNum));
                if (errNum == 0) {
                    relativeLayoutMain.setupStatus.setTextColor(Color.BLACK);
                    relativeLayoutMain.setupStatus.setBackgroundColor(Color.GREEN);
                } else {
                    relativeLayoutMain.setupStatus.setTextColor(Color.RED);
                    relativeLayoutMain.setupStatus.setBackgroundColor(Color.LTGRAY);
                }
            }
        }

//        void invalidate() {
//            chessPad.chessPadLayout.invalidate();
//        }
        abstract void hideAllWidgets();
        abstract void invalidate();
    }

    class CpProgressBar {
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

