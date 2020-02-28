package com.ab.droid.chesspad;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatImageButton;
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

import com.ab.pgn.BitStream;
import com.ab.pgn.Config;

import java.io.IOException;

/**
 * full ChessPad screen
 * Created by Alexander Bootman on 8/20/16.
 */
public class ChessPadView extends View implements ProgressBarHolder {
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    private final ChessPad chessPad;
    RelativeLayout relativeLayoutMain;
    protected CpProgressBar cpProgressBar;

    private CpView cpView;
    static Bitmap checkBitmap = null;

     private BoardHolder mainBoardHolder;
    private TextView title;
    private BoardView boardView;

    public ChessPadView(ChessPad chessPad) {
        super(chessPad);
        this.chessPad = chessPad;
        // fix it!
        checkBitmap = BitmapFactory.decodeResource(chessPad.getResources(), R.drawable.chk);

        try {
            chessPad.getSupportActionBar().hide();
        } catch (Exception e) {
            Log.e(this.getClass().getName(), "getSupportActionBar error", e);
        }

        relativeLayoutMain = chessPad.getMainRelativeLayout();
    }

    public void redraw() {
//        relativeLayoutMain.removeAllViewsInLayout();
        relativeLayoutMain.removeAllViews();
        recalcSizes();

        if (chessPad.getMode() == ChessPad.Mode.Game) {
            cpView = new GameView(chessPad);
        } else if (chessPad.getMode() == ChessPad.Mode.Setup) {
            cpView = new SetupView(chessPad);
        } else if (chessPad.getMode() == ChessPad.Mode.DgtGame) {
            cpView = new DgtBoardView(chessPad);
        }
        cpView.draw();
        boolean showProgressBar = false;
        if (cpProgressBar != null) {
            showProgressBar = cpProgressBar.isVisible();
        }
        cpProgressBar = new CpProgressBar(chessPad, relativeLayoutMain);
        cpProgressBar.show(showProgressBar);
        invalidate();
    }

    public void enableNavigation(boolean enable) {
        if (cpView instanceof GameView) {
            ((GameView)cpView).navigationEnabled = enable;
        }
    }

    @Override
    public void invalidate() {
        if(cpView != null) {
            cpView.invalidate();
        }
        super.invalidate();
    }

    private void recalcSizes() {
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
            Metrics.paneHeight = Metrics.screenHeight - Metrics.titleHeight - Metrics.ySpacing;
            Metrics.squareSize = Metrics.paneHeight / 8;
            Metrics.ySpacing = Metrics.buttonSize / 20;
            if (Metrics.ySpacing <= 1) {
                Metrics.ySpacing = 1;
            } else {
                Metrics.squareSize -= Metrics.ySpacing;
            }
            if (Metrics.xSpacing == 0) {
                Metrics.xSpacing = 1;
            }
            Metrics.xSpacing = Metrics.ySpacing;
            Metrics.buttonSize = Metrics.squareSize - Metrics.xSpacing;
            Metrics.boardViewSize = Metrics.paneHeight;
            Metrics.paneWidth = Metrics.screenWidth - Metrics.boardViewSize - Metrics.xSpacing;
        }
        Metrics.boardViewSize = Metrics.squareSize * 8;
    }

    @SuppressLint("ClickableViewAccessibility")
    static TextView drawTitleBar(ChessPad chessPad, final TitleHolder titleHolder) {
        TextView title = new TextView(chessPad);
        title.setSingleLine();
        title.setBackgroundColor(Color.GREEN);
        title.setTextColor(Color.BLACK);
        title.setText(titleHolder.getTitleText());
        title.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                titleHolder.onTitleClick();
                return true;
            }
        });
        title.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        addTextView(chessPad.getMainRelativeLayout(), title, 0, 0, Metrics.screenWidth - Metrics.buttonSize - Metrics.xSpacing, Metrics.titleHeight);
        addImageButton(chessPad, chessPad.getMainRelativeLayout(), ChessPad.Command.Menu, Metrics.screenWidth - Metrics.buttonSize, 0, Metrics.buttonSize, Metrics.titleHeight, R.drawable.ic_menu);
        return title;
    }

    static BoardView drawBoardView(Context context, RelativeLayout relativeLayout, int x, int y, BoardHolder boardHolder) {
        BoardView boardView = new BoardView(context, Metrics.boardViewSize / 8, boardHolder);
        addView(relativeLayout, boardView, x, y);
        return boardView;
    }

    static void addTextView(RelativeLayout relativeLayout, TextView view, int x, int y, int w, int h) {
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

    static CpImageButton addImageButton(ChessPad chessPad, RelativeLayout relativeLayout, ChessPad.Command command, int x, int y, int resource) {
        return addImageButton(chessPad, relativeLayout, command, x, y, Metrics.buttonSize, Metrics.buttonSize, resource);
    }

    static CpImageButton addImageButton(final ChessPad chessPad, RelativeLayout relativeLayout, final ChessPad.Command command, int x, int y, int width, int height, int resource) {
        CpImageButton btn = new CpImageButton(chessPad, resource);
        btn.setId(command.getValue());
        addView(relativeLayout, btn, x, y, width, height);
        btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                chessPad.onButtonClick(ChessPad.Command.command(v.getId()));
            }
        });
        return btn;
    }

    static void addView(RelativeLayout relativeLayout, View view, int x, int y) {
        addView(relativeLayout, view, x, y, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static void addView(RelativeLayout relativeLayout, View view, int x, int y, int w, int h) {
        RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(w, h);
        rlp.setMargins(x, y, 0, 0);
        relativeLayout.addView(view, rlp);
    }

    public void setButtonEnabled(int indx, boolean enable) {
        if (cpView instanceof GameView) {
            ((GameView)cpView).setButtonEnabled(indx, enable);
        }
    }

    @Override
    public CpProgressBar getProgressBar() {
        return cpProgressBar;
    }

    // how to redraw after keyboard dismissal?
    // remove?
    public static void createLabel(Context context, RelativeLayout relativeLayout, int x, int y, int w1, int h, int rscLabel) {
        TextView label = new TextView(context);
        label.setBackgroundColor(Color.GRAY);
        label.setTextColor(Color.BLACK);
        label.setPadding(Metrics.xSpacing, Metrics.ySpacing, Metrics.xSpacing, Metrics.ySpacing);
        label.setGravity(Gravity.START | Gravity.CENTER);
        label.setText(rscLabel);
        addTextView(relativeLayout, label, x, y, w1, h);
    }

    public static CpEditText createLabeledEditText(Context context, RelativeLayout relativeLayout, int x, int y, int labelWidth, int valueWidth, int h, int rscLabel) {
        return createLabeledEditText(context, relativeLayout, x, y, labelWidth, valueWidth, h, rscLabel, null);
    }

    public static CpEditText createLabeledEditText(Context context, RelativeLayout relativeLayout, int x, int y, int labelWidth, int valueWidth, int h, int rscLabel, StringKeeper stringKeeper) {
        createLabel(context, relativeLayout, x, y, labelWidth, h, rscLabel);

        CpEditText editText = new CpEditText(context);
        editText.setBackgroundColor(Color.GREEN);
        editText.setTextColor(Color.RED);
        editText.setPadding(Metrics.xSpacing, Metrics.ySpacing, Metrics.xSpacing, Metrics.ySpacing);
        editText.setGravity(Gravity.CENTER);
        editText.setSingleLine();
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        addTextView(relativeLayout, editText, x + labelWidth + 2 * Metrics.xSpacing, y, valueWidth, h);
        if(stringKeeper != null) {
            editText.setStringKeeper(stringKeeper);
        }
        return editText;
    }

    public void enableCommentEdit(boolean enable) {
        if (cpView instanceof GameView) {
            ((GameView)cpView).enableCommentEdit(enable);
        }
    }

    static class CpEditText extends AppCompatEditText {
        private ChessPadView.StringKeeper stringKeeper;

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

        public void setStringKeeper(ChessPadView.StringKeeper stringKeeper) {
            this.stringKeeper = stringKeeper;
            this.setText(stringKeeper.getValue());
        }

/*
        @Override
        public boolean onKeyPreIme(int keyCode, KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                ChessPadView.this.redraw();
//                return true;  // So it is not propagated.
            }
            return super.dispatchKeyEvent(event);
        }
*/
    }

    public static class CpImageButton extends AppCompatImageButton {
        public CpImageButton(Context context, int resource) {
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

    public static class CpProgressBar {
        private boolean isVisible;
        private ProgressBar progressBar;
        private TextView progressText;

        public CpProgressBar(Context context, RelativeLayout relativeLayout) {
            int x, y, h, w;
            w = Metrics.boardViewSize;
            x = (Metrics.screenWidth - w) / 2;
            h = Metrics.titleHeight;
            y = Metrics.titleHeight * 2;
            progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setVisibility(View.GONE);
            progressBar.setMax(100);
            ChessPadView.addView(relativeLayout, progressBar, x, y, w, h);

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
            ChessPadView.addView(relativeLayout, progressText, x, y, w, h);
            isVisible = false;
        }

        public boolean isVisible() {
            return this.isVisible;
        }

        public void show(boolean bShow) {
            if (progressBar == null) {
                return;
            }
            isVisible = bShow;
            if (bShow) {
                progressBar.setVisibility(View.VISIBLE);
                progressText.setVisibility(View.VISIBLE);
            } else {
                progressBar.setVisibility(View.GONE);
                progressText.setVisibility(View.GONE);
            }
        }

        public void update(int progress) {
            progressBar.setProgress(progress);
            progressText.setText(String.format("%s%%", progress));
        }
    }

    public static class StringWrapper {
        private String value;
        private ChangeObserver changeObserver;

        public StringWrapper(String value, ChangeObserver changeObserver) {
            this.value = value;
            this.changeObserver = changeObserver;
        }

        public String getValue() {
            return value;
        }

        public String getNumericValue() {
            if (value == null || value.isEmpty()) {
                return "0";
            }
            return getValue();
        }

        public void setValue(String value) {
            if (this.value == null || !this.value.equals(value)) {
                this.value = value;
                if (changeObserver != null) {
                    changeObserver.onValueChanged(this.value);
                }
            }
        }

        public void serialize(BitStream.Writer writer) throws IOException {
            writer.writeString(value);
        }

        public StringWrapper(BitStream.Reader reader, ChangeObserver changeObserver) throws IOException {
            value = reader.readString();
            this.changeObserver = changeObserver;
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

    public static class CpToggleButton extends ChessPadView.CpImageButton {
        private FlagKeeper flagKeeper;

        public CpToggleButton(ChessPad chessPad, RelativeLayout rl, int size, int x, int y, int rscNormal) {
            this(chessPad, rl, size, x, y, rscNormal, null);
        }

        public CpToggleButton(ChessPad chessPad, RelativeLayout rl, int size, int x, int y, int rscNormal, FlagKeeper flagKeeper) {
            super(chessPad, rscNormal);
            this.flagKeeper = flagKeeper;
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(size, size);
            lp.setMargins(x, y, 0, 0);
            rl.addView(this, lp);
            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(CpToggleButton.this.flagKeeper != null) {
                        CpToggleButton.this.flagKeeper.setFlag(!CpToggleButton.this.flagKeeper.getFlag());
                    }
                    CpToggleButton.this.invalidate();
                }
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
        protected final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();
        protected final ChessPad chessPad;
        protected final RelativeLayout relativeLayoutMain;

        protected TextView title;
        protected TextView setupStatus;

        protected BoardView boardView;
        protected int selectedPiece = -1;

        public CpView(ChessPad chessPad) {
            this.chessPad = chessPad;
            this.relativeLayoutMain = chessPad.getMainRelativeLayout();
        }

        public void draw() {
        }

        protected void setStatus(int errNum) {
            if(setupStatus != null) {
                setupStatus.setText(chessPad.getSetupErr(errNum));
                if (errNum == 0) {
                    setupStatus.setTextColor(Color.BLACK);
                    setupStatus.setBackgroundColor(Color.GREEN);
                } else {
                    setupStatus.setTextColor(Color.RED);
                    setupStatus.setBackgroundColor(Color.LTGRAY);
                }
            }
        }

        public void invalidate() {
            chessPad.chessPadView.invalidate();
        }
    }
}
