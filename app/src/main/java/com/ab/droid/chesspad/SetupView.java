package com.ab.droid.chesspad;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.InputType;
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

import java.io.IOException;
import java.util.List;

/**
 *
 * Created by Alexander Bootman on 11/27/16.
 */

public class SetupView {
    protected final String DEBUG_TAG = this.getClass().getName();

    private final ChessPad chessPad;
    private final RelativeLayout relativeLayoutMain;

    private TextView title;

    private BoardView boardView;
    private BoardView piecesView;
    private int selectedPiece = -1;
    private int errNum;

    private TextView setupStatus;

    public SetupView(ChessPad chessPad, RelativeLayout relativeLayoutMain) {
        this.chessPad = chessPad;
        this.relativeLayoutMain = relativeLayoutMain;
    }

    public void draw() {
        title = ChessPadView.drawTitleBar(chessPad, relativeLayoutMain, new TitleHolder() {
            @Override
            public String getTitleText() {
                return chessPad.setup.getTitleText();
            }

            @Override
            public void onTitleClick() {
                chessPad.onButtonClick(ChessPad.Command.EditHeaders);
            }

            @Override
            public List<Pair<String, String>> getHeaders() {
                return chessPad.setup.getHeaders();
            }
        });

        int x, y;
        if (Metrics.isVertical) {
            x = (Metrics.screenWidth - Metrics.boardViewSize) / 2;
        } else {
            x = 0;
        }
        y = Metrics.titleHeight + Metrics.ySpacing;
        boardView = ChessPadView.drawBoardView(chessPad, relativeLayoutMain, x, y, new BoardHolder() {
            @Override
            public Board getBoard() {
                return chessPad.setup.getBoard();
            }

            @Override
            public int[] getBGResources() {
                return new int[] {R.drawable.bsquare, R.drawable.wsquare};
            }

            @Override
            public void setReversed(boolean reversed) {
            }

            @Override
            public boolean isReversed() {
                return false;
            }

            @Override
            public boolean onSquareClick(Square clicked) {
                Log.d(DEBUG_TAG, String.format("board onSquareClick (%s)", clicked.toString()));
                if(selectedPiece > 0) {
                    boardView.invalidate();
                    return chessPad.setup.onSquareClick(clicked, selectedPiece);
                }
                return true;
            }

            @Override
            public boolean onFling(Square clicked) {
                Log.d(DEBUG_TAG, String.format("board onFling (%s)", clicked.toString()));
                boardView.invalidate();
                return chessPad.setup.onFling(clicked);
            }
        });

        RelativeLayout relativeLayoutSetup = new RelativeLayout(chessPad);
        relativeLayoutSetup.setBackgroundColor(Color.BLACK);

        if (Metrics.isVertical) {
            x = 0; // xSpacing;
            y = Metrics.titleHeight + Metrics.ySpacing + Metrics.boardViewSize + Metrics.ySpacing;
            ChessPadView.addView(relativeLayoutMain, relativeLayoutSetup, x, y, Metrics.paneWidth, Metrics.paneHeight);
            drawSetupVerticalLayout(relativeLayoutSetup);
        } else {
            x = Metrics.boardViewSize + Metrics.xSpacing;
            y = Metrics.titleHeight + Metrics.ySpacing;
            ChessPadView.addView(relativeLayoutMain, relativeLayoutSetup, x, y, Metrics.paneWidth, Metrics.paneHeight);
            drawSetupHorizonlalLayout(relativeLayoutSetup);
        }

        setupStatus.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP && errNum == 0) {
                try {
                    chessPad.setPgnGraph(null);
                } catch (Config.PGNException e) {
                    Log.e(DEBUG_TAG, String.format("endSetup failed"), e);
                }
            }
            // true if the event was handled and should not be given further down to other views.
            return true;
            }
        });
    }

    private void drawSetupVerticalLayout(RelativeLayout relativeLayoutSetup) {
        int x, y;

        int toggleButtonSize = Metrics.squareSize;
        int size = (Metrics.paneHeight - 3 * Metrics.ySpacing) / 4;
        if(toggleButtonSize > size) {
            toggleButtonSize = size;
        }

        x = 0; y = 0;
        createPiecesView(relativeLayoutSetup, x, y, new int[][]{
                {Config.WHITE_KING, Config.WHITE_QUEEN, Config.WHITE_BISHOP, Config.WHITE_KNIGHT, Config.WHITE_ROOK,Config.WHITE_PAWN},
                {Config.BLACK_KING, Config.BLACK_QUEEN, Config.BLACK_BISHOP, Config.BLACK_KNIGHT, Config.BLACK_ROOK,Config.BLACK_PAWN}
            });

        // white / black move buttons
        x = 0;
        y = Metrics.squareSize * 2 + Metrics.ySpacing;
        final ChessPadView.CpToggleButton btnWhiteMove = new ChessPadView.CpToggleButton(chessPad,relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kw, 0);
        y += toggleButtonSize + Metrics.ySpacing;
        final ChessPadView.CpToggleButton btnBlackMove = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kb , 0);
        btnBlackMove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChessPadView.CpToggleButton btn = (ChessPadView.CpToggleButton)v;
                chessPad.setup.setFlag(!btn.isChecked(), Config.FLAGS_BLACK_MOVE); // reversed check
                btn.toggle();
                btnWhiteMove.toggle();
            }
        });
        btnBlackMove.setChecked(chessPad.setup.getFlag(Config.FLAGS_BLACK_MOVE) != 0);
        btnWhiteMove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChessPadView.CpToggleButton btn = (ChessPadView.CpToggleButton)v;
                chessPad.setup.setFlag(btn.isChecked(), Config.FLAGS_BLACK_MOVE); // reversed check
                btn.toggle();
                btnBlackMove.toggle();
            }
        });
        btnWhiteMove.setChecked(chessPad.setup.getFlag(Config.FLAGS_BLACK_MOVE) == 0);

        // castle buttons
        x = toggleButtonSize + Metrics.xSpacing;
        y = Metrics.squareSize * 2 + Metrics.ySpacing;
        ChessPadView.CpToggleButton btnWhiteQueenCastle =
                new ChessPadView.CpToggleButton(chessPad,relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle, Config.FLAGS_W_QUEEN_OK);
        x += toggleButtonSize + Metrics.xSpacing;
        ChessPadView.CpToggleButton btnWhiteKingCastle =
                new ChessPadView.CpToggleButton(chessPad,relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle, Config.FLAGS_W_KING_OK);

        x = toggleButtonSize + Metrics.xSpacing;
        y += toggleButtonSize + Metrics.ySpacing;
        ChessPadView.CpToggleButton btnBlackQueenCastle =
                new ChessPadView.CpToggleButton(chessPad,relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle, Config.FLAGS_B_QUEEN_OK);
        x += toggleButtonSize + Metrics.xSpacing;
        ChessPadView.CpToggleButton btnBlackKingCastle =
                new ChessPadView.CpToggleButton(chessPad,relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle, Config.FLAGS_B_KING_OK);

        // texts:
        x += toggleButtonSize + 2 * Metrics.xSpacing;
        y = Metrics.squareSize * 2 + Metrics.ySpacing;
        int height = (2 * toggleButtonSize - 2 * Metrics.ySpacing) / 3;
        ChessPadView.CpEditText enPass = ChessPadView.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_en_pass, chessPad.setup.enPass);
        enPass.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        y += height + Metrics.ySpacing;
        ChessPadView.CpEditText halfMoveClock = ChessPadView.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_halfmove_clock, chessPad.setup.hmClock);
        y += height + Metrics.ySpacing;
        ChessPadView.CpEditText moveNum = ChessPadView.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_move_num, chessPad.setup.moveNum);
        y += height;

        int width;
        if(Metrics.paneHeight - y - 2 * Metrics.ySpacing >= Metrics.squareSize) {
            x = 0;
            width = Metrics.paneWidth;
            y = Metrics.paneHeight - Metrics.squareSize - 2 * Metrics.ySpacing;
            height = Metrics.squareSize;
        } else {
            x = Metrics.squareSize * 6 + 2 * Metrics.xSpacing;
            width = Metrics.paneWidth - x;
            y = 0;
            height = 2 * Metrics.squareSize;
        }
        setupStatus = new TextView(chessPad);
        setupStatus.setBackgroundColor(Color.CYAN);
        setupStatus.setGravity(Gravity.START | Gravity.CENTER);
        ChessPadView.addTextView(relativeLayoutSetup, setupStatus, x, y, width, height);
    }

    private void drawSetupHorizonlalLayout(RelativeLayout relativeLayoutSetup) {
        int x, y;

        int toggleButtonSize = Metrics.squareSize;
        x = Metrics.xSpacing; y = 0;

        // texts:
        int height = (2 * toggleButtonSize - 2 * Metrics.ySpacing) / 3;
        ChessPadView.CpEditText enPass = ChessPadView.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_en_pass, chessPad.setup.enPass);
        enPass.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        y += height + Metrics.ySpacing;
        ChessPadView.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_halfmove_clock, chessPad.setup.hmClock);
        y += height + Metrics.ySpacing;
        ChessPadView.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_move_num, chessPad.setup.moveNum);
        y += height + Metrics.ySpacing;
        int y0 = y;

        createPiecesView(relativeLayoutSetup, x, y, new int[][]{
                {Config.WHITE_PAWN,Config.BLACK_PAWN},
                {Config.WHITE_ROOK, Config.BLACK_ROOK},
                {Config.WHITE_KNIGHT, Config.BLACK_KNIGHT},
                {Config.WHITE_BISHOP, Config.BLACK_BISHOP},
                {Config.WHITE_QUEEN, Config.BLACK_QUEEN},
                {Config.WHITE_KING, Config.BLACK_KING},
        });

        x = 2 * Metrics.squareSize + 2 * Metrics.xSpacing;
        y = y0;
        final ChessPadView.CpToggleButton btnWhiteMove = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kw, 0 );
        x += toggleButtonSize + Metrics.xSpacing;
        final ChessPadView.CpToggleButton btnBlackMove = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kb , 0);
        btnBlackMove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChessPadView.CpToggleButton btn = (ChessPadView.CpToggleButton)v;
                btn.toggle();
                btnWhiteMove.toggle();
                chessPad.setup.setFlag(btn.isChecked(), Config.FLAGS_BLACK_MOVE);
                chessPad.setup.validate();
            }
        });
        btnBlackMove.setChecked(chessPad.setup.getFlag(Config.FLAGS_BLACK_MOVE) != 0);
        btnWhiteMove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ChessPadView.CpToggleButton btn = (ChessPadView.CpToggleButton)v;
                btn.toggle();
                btnBlackMove.toggle();
                chessPad.setup.setFlag(!btn.isChecked(), Config.FLAGS_BLACK_MOVE);
                chessPad.setup.validate();
            }
        });
        btnWhiteMove.setChecked(chessPad.setup.getFlag(Config.FLAGS_BLACK_MOVE) == 0);

        // castle buttons
        x = 2 * Metrics.squareSize + 2 * Metrics.xSpacing;
        y = y0 + Metrics.squareSize + Metrics.ySpacing;
        ChessPadView.CpToggleButton btnWhiteQueenCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle, Config.FLAGS_W_QUEEN_OK );
        y += toggleButtonSize + Metrics.ySpacing;
        ChessPadView.CpToggleButton btnWhiteKingCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle, Config.FLAGS_W_KING_OK );

        x += Metrics.squareSize + Metrics.xSpacing;
        y = y0 + Metrics.squareSize + Metrics.ySpacing;
        ChessPadView.CpToggleButton btnBlackQueenCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle, Config.FLAGS_B_QUEEN_OK );
        y += toggleButtonSize + Metrics.ySpacing;
        ChessPadView.CpToggleButton btnBlackKingCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle, Config.FLAGS_B_KING_OK );
        y0 = y + toggleButtonSize + Metrics.ySpacing;

        x = 2 * Metrics.squareSize + 2 * Metrics.xSpacing;
        int width = Metrics.paneWidth - x;
        y = y0;
        height = Metrics.paneHeight - y;
        setupStatus = new TextView(chessPad);
        setupStatus.setGravity(Gravity.START | Gravity.CENTER);
        ChessPadView.addTextView(relativeLayoutSetup, setupStatus, x, y, width, height);
    }

    private void createPiecesView(RelativeLayout relativeLayout, int x, int y, int[][] _pieces) {
        final Board pieces = new Board(_pieces);
        String s = pieces.toString();
        piecesView = ChessPadView.drawBoardView(chessPad, relativeLayout, x, y, new BoardHolder() {
            @Override
            public Board getBoard() {
                return pieces;
            }

            @Override
            public int[] getBGResources() {
                return new int[] {R.drawable.btn_disabled};
            }

            @Override
            public void setReversed(boolean reversed) {
            }

            @Override
            public boolean isReversed() {
                return false;
            }

            @Override
            public boolean onSquareClick(Square clicked) {
                Log.d(DEBUG_TAG, String.format("pieces onSquareClick (%s)", clicked.toString()));
                piecesView.setSelected(clicked);
                selectedPiece = pieces.getPiece(clicked);
                piecesView.invalidate();
                return true;
            }

            @Override
            public boolean onFling(Square clicked) {
                Log.d(DEBUG_TAG, String.format("pieces onFling (%s)", clicked.toString()));
                return true;
            }
        });
    }

    public void setStatus(int errNum) {
        this.errNum = errNum;
        setupStatus.setText(chessPad.getSetupErr(errNum));
        if(errNum == 0) {
            setupStatus.setTextColor(Color.BLACK);
            setupStatus.setBackgroundColor(Color.GREEN);
        } else {
            setupStatus.setTextColor(Color.RED);
            setupStatus.setBackgroundColor(Color.LTGRAY);
        }
    }

    public void invalidate() {
        title.setText(chessPad.setup.getTitleText());
        boardView.invalidate();
        piecesView.invalidate();
    }

}
