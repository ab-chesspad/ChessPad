package com.ab.droid.chesspad;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Pair;
import com.ab.pgn.Setup;
import com.ab.pgn.Square;

import java.util.List;

/**
 *
 * Created by Alexander Bootman on 11/27/16.
 */

public class SetupView extends ChessPadView.CpView {
    protected final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();
    private BoardView piecesView;
    protected ChessPadView.CpToggleButton btnWhiteMove, btnBlackMove;
    protected ChessPadView.CpToggleButton btnWhiteQueenCastle, btnWhiteKingCastle, btnBlackQueenCastle, btnBlackKingCastle;
    ChessPadView.CpEditText enPassEditText, hmClockEditText, moveNumEditText;

    public SetupView(ChessPad chessPad) {
        super(chessPad);
    }

    protected Board getBoard() {
        return chessPad.setup.getBoard();
    }

    protected Setup getSetup() {
        return chessPad.setup;
    }

    protected BoardHolder getMainBoardHolder() {
        return new BoardHolder() {
            @Override
            public Board getBoard() {
                return getSetup().getBoard();
            }

            @Override
            public int[] getBGResources() {
                return new int[]{R.drawable.bsquare, R.drawable.wsquare};
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
                if (selectedPiece > 0) {
                    getSetup().getBoard().setPiece(clicked, selectedPiece);
                    invalidate();
                }
                return true;
            }

            @Override
            public boolean onFling(Square clicked) {
                Log.d(DEBUG_TAG, String.format("board onFling (%s)", clicked.toString()));
                getSetup().getBoard().setPiece(clicked, Config.EMPTY);
                invalidate();
                return true;
            }
        };
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public void draw() {
        int x, y;

        Log.d(DEBUG_TAG, "draw()");
        title = ChessPadView.drawTitleBar(chessPad, new TitleHolder() {
            @Override
            public String getTitleText() {
                return getSetup().getTitleText();
            }

            @Override
            public void onTitleClick() {
                chessPad.onButtonClick(ChessPad.Command.EditHeaders);
            }

            @Override
            public List<Pair<String, String>> getHeaders() {
                return getSetup().getHeaders();
            }
        });

        if (Metrics.isVertical) {
            x = (Metrics.screenWidth - Metrics.boardViewSize) / 2;
        } else {
            x = 0;
        }
        y = Metrics.titleHeight + Metrics.ySpacing;
        boardView = ChessPadView.drawBoardView(chessPad, relativeLayoutMain, x, y, getMainBoardHolder());

        RelativeLayout relativeLayoutSetup = new RelativeLayout(chessPad);
        relativeLayoutSetup.setBackgroundColor(Color.BLACK);

        setupStatus = new TextView(chessPad);
        setupStatus.setBackgroundColor(Color.CYAN);
        setupStatus.setGravity(Gravity.START | Gravity.CENTER);
        setupStatus.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP && getSetup().getErrNum() == 0) {
                    try {
                        chessPad.setPgnGraph(null);
                    } catch (Config.PGNException e) {
                        Log.e(DEBUG_TAG, "endSetup failed", e);
                    }
                }
                // true if the event was handled and should not be given further down to other views.
                return true;
            }
        });

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

        // functionality:
        if(btnBlackMove != null) {
            // setup/setupmess mode
            btnBlackMove.setFlagKeeper(new SetupFlagKeeper(Config.FLAGS_BLACK_MOVE));
            btnWhiteMove.setFlagKeeper(new SetupFlagKeeper(Config.FLAGS_BLACK_MOVE) {
                @Override
                public void setFlag(boolean set) {
                    super.setFlag(!set);
                }

                @Override
                public boolean getFlag() {
                    return !super.getFlag();
                }
            });

            btnWhiteQueenCastle.setFlagKeeper(new SetupFlagKeeper(Config.FLAGS_W_QUEEN_OK));
            btnWhiteKingCastle.setFlagKeeper(new SetupFlagKeeper(Config.FLAGS_W_KING_OK));
            btnBlackQueenCastle.setFlagKeeper(new SetupFlagKeeper(Config.FLAGS_B_QUEEN_OK));
            btnBlackKingCastle.setFlagKeeper(new SetupFlagKeeper(Config.FLAGS_B_KING_OK));

            enPassEditText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            enPassEditText.setStringKeeper(new ChessPadView.StringKeeper() {
                @Override
                public void setValue(TextView v, String str) {
                    getSetup().setEnPass(str);
                }

                @Override
                public String getValue(TextView v) {
                    return getSetup().getEnPass();
                }
            });

            hmClockEditText.setStringKeeper(new ChessPadView.StringKeeper() {
                @Override
                public void setValue(TextView v, String str) {
                    int hmClock = getNumericValue(str);
                    getBoard().setReversiblePlyNum(hmClock);
                }

                @Override
                public String getValue(TextView v) {
                    return "" + getBoard().getReversiblePlyNum();
                }
            });

            moveNumEditText.setStringKeeper(new ChessPadView.StringKeeper() {
                @Override
                public void setValue(TextView v, String str) {
                    int moveNum = getNumericValue(str) * 2;
                    if ((getBoard().getFlags() & Config.FLAGS_BLACK_MOVE) != 0) {
                        ++moveNum;
                    }
                    getBoard().setPlyNum(moveNum);
                }

                @Override
                public String getValue(TextView v) {
                    return "" + getBoard().getPlyNum() / 2;
                }
            });
        }
        Log.d(DEBUG_TAG, "draw() done");
        invalidate();
    }

    protected void drawSetupVerticalLayout(RelativeLayout relativeLayoutSetup) {
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
        btnWhiteMove = new ChessPadView.CpToggleButton(chessPad,relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kw);
        y += toggleButtonSize + Metrics.ySpacing;
        btnBlackMove = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kb);

        // castle buttons
        x = toggleButtonSize + Metrics.xSpacing;
        y = Metrics.squareSize * 2 + Metrics.ySpacing;
        btnWhiteQueenCastle = new ChessPadView.CpToggleButton(chessPad,relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle);
        x += toggleButtonSize + Metrics.xSpacing;
        btnWhiteKingCastle = new ChessPadView.CpToggleButton(chessPad,relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle);

        x = toggleButtonSize + Metrics.xSpacing;
        y += toggleButtonSize + Metrics.ySpacing;
        btnBlackQueenCastle = new ChessPadView.CpToggleButton(chessPad,relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle);
        x += toggleButtonSize + Metrics.xSpacing;
        btnBlackKingCastle = new ChessPadView.CpToggleButton(chessPad,relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle);

        // texts:
        x += toggleButtonSize + 2 * Metrics.xSpacing;
        y = Metrics.squareSize * 2 + Metrics.ySpacing;
        int height = (2 * toggleButtonSize - 2 * Metrics.ySpacing) / 3;
        enPassEditText = ChessPadView.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_en_pass);

        y += height + Metrics.ySpacing;
        hmClockEditText = ChessPadView.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_halfmove_clock);
        y += height + Metrics.ySpacing;
        moveNumEditText = ChessPadView.createLabeledEditText(chessPad, relativeLayoutSetup, x, y,
                Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, height, R.string.label_move_num);
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
        ChessPadView.addTextView(relativeLayoutSetup, setupStatus, x, y, width, height);
    }

    protected void drawSetupHorizonlalLayout(RelativeLayout relativeLayoutSetup) {
        int x, y;

        int toggleButtonSize = Metrics.squareSize;
        x = Metrics.xSpacing; y = 0;

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
        btnWhiteMove = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kw);
        x += toggleButtonSize + Metrics.xSpacing;
        btnBlackMove = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.kb);

        // castle buttons
        x = 2 * Metrics.squareSize + 2 * Metrics.xSpacing;
        y = y0 + Metrics.squareSize + Metrics.ySpacing;
        btnWhiteQueenCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle);
        y += toggleButtonSize + Metrics.ySpacing;
        btnWhiteKingCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle);

        x += Metrics.squareSize + Metrics.xSpacing;
        y = y0 + Metrics.squareSize + Metrics.ySpacing;
        btnBlackQueenCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.l_castle);
        y += toggleButtonSize + Metrics.ySpacing;
        btnBlackKingCastle = new ChessPadView.CpToggleButton(chessPad, relativeLayoutSetup, toggleButtonSize, x, y, R.drawable.s_castle);
        y0 = y + toggleButtonSize + Metrics.ySpacing;

        x = 2 * Metrics.squareSize + 2 * Metrics.xSpacing;
        int width = Metrics.paneWidth - x;
        y = y0;
        height = Metrics.paneHeight - y;
        ChessPadView.addTextView(relativeLayoutSetup, setupStatus, x, y, width, height);
    }

    private void createPiecesView(RelativeLayout relativeLayout, int x, int y, int[][] _pieces) {
        final Board pieces = new Board(_pieces);
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

    @Override
    public void invalidate() {
        getSetup().validate();
        setStatus(getSetup().getErrNum());
        super.invalidate();
        title.setText(getSetup().getTitleText());
        invalidateViewGroup(relativeLayoutMain);
    }

    public static void invalidateViewGroup(ViewGroup viewGroup) {
        for(int i = 0; i < viewGroup.getChildCount(); ++i) {
            View child = viewGroup.getChildAt(i);
            if(child instanceof  ViewGroup) {
                invalidateViewGroup((ViewGroup)child);
            } else {
                child.invalidate();
            }
        }
    }

    class SetupFlagKeeper implements ChessPadView.FlagKeeper {
        int flag;

        public SetupFlagKeeper(int flag) {
            this.flag = flag;
        }

        @Override
        public void setFlag(boolean set) {
            getSetup().setFlag(flag, set);
            invalidate();
        }

        @Override
        public boolean getFlag() {
            return getSetup().getFlag(flag) != 0;
        }
    }

}
