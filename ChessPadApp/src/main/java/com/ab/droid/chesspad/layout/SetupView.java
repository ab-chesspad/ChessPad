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
 *
 * Created by Alexander Bootman on 11/27/16.
 */
package com.ab.droid.chesspad.layout;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.ab.droid.chesspad.BoardHolder;
import com.ab.droid.chesspad.R;
import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Setup;
import com.ab.pgn.Square;

public class SetupView extends ChessPadLayout.CpView {
    final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    private final Board verticalPoolBoard, horizontalPoolBoard;
    private BoardView poolView;
    protected TextView setupStatus;
    protected final BoardHolder mainBoardHolder = getMainBoardHolder();

    protected ChessPadLayout.CpToggleButton btnWhiteMove, btnBlackMove;
    protected ChessPadLayout.CpToggleButton btnWhiteQueenCastle, btnWhiteKingCastle, btnBlackQueenCastle, btnBlackKingCastle;
    protected LabeledEditText enPassEditText, hmClockEditText, moveNumEditText;
    protected ChessPadLayout.CpToggleButton btnPredefinedSetups;

    private int poolSquareSize;

    private static final int
        predefinedPosition_none = 0x0,
        predefinedPosition_init = 0x1,
        predefinedPosition_empty = 0x2,
        predefinedPosition_mask = predefinedPosition_init | predefinedPosition_empty
    ;

    SetupView() {
        verticalPoolBoard = null;
        horizontalPoolBoard = null;
    }

    SetupView(ChessPadLayout chessPadLayout) {
        init(chessPadLayout);
        createCommonFields();
        verticalPoolBoard = new Board(new int[][]{
            {Config.WHITE_KING, Config.WHITE_QUEEN, Config.WHITE_BISHOP, Config.WHITE_KNIGHT, Config.WHITE_ROOK, Config.WHITE_PAWN},
            {Config.BLACK_KING, Config.BLACK_QUEEN, Config.BLACK_BISHOP, Config.BLACK_KNIGHT, Config.BLACK_ROOK, Config.BLACK_PAWN}
        });
        horizontalPoolBoard = new Board(new int[][]{
            {Config.WHITE_PAWN,Config.BLACK_PAWN},
            {Config.WHITE_ROOK, Config.BLACK_ROOK},
            {Config.WHITE_KNIGHT, Config.BLACK_KNIGHT},
            {Config.WHITE_BISHOP, Config.BLACK_BISHOP},
            {Config.WHITE_QUEEN, Config.BLACK_QUEEN},
            {Config.WHITE_KING, Config.BLACK_KING},
        });

        poolView = new BoardView(chessPad, new BoardHolder() {
            @Override
            public Board getBoard() {
                if (Metrics.isVertical) {
                    return verticalPoolBoard;
                } else {
                    return horizontalPoolBoard;
                }
            }

            @Override
            public int getBoardViewSize() {
                int xSize = getBoard().getXSize();
                int ySize = getBoard().getYSize();
                int size = Math.max(xSize, ySize);
                return size * poolSquareSize;
            }

            @Override
            public int[] getBGResources() {
                return new int[] {R.drawable.btn_disabled};
            }

            @Override
            public boolean isFlipped() {
                return false;
            }

            @Override
            public boolean onSquareClick(Square clicked) {
                Log.d(DEBUG_TAG, String.format("pieces onSquareClick (%s)", clicked.toString()));
                poolView.setSelected(clicked);
                if (Metrics.isVertical) {
                    selectedPiece = verticalPoolBoard.getPiece(clicked);
                } else {
                    selectedPiece = horizontalPoolBoard.getPiece(clicked);
                }

                poolView.invalidate();
                return true;
            }

            @Override
            public void onFling(Square clicked) {
                Log.d(DEBUG_TAG, String.format("pieces onFling (%s)", clicked.toString()));
            }
        });
        controlPaneLayout.addView(poolView);
        poolView.setVisibility(View.GONE);
    }

    @SuppressLint("ClickableViewAccessibility")
    protected void createCommonFields() {
        setupStatus = createTextView();
        setupStatus.setBackgroundColor(Color.CYAN);
        setupStatus.setGravity(Gravity.START | Gravity.CENTER);
        setupStatus.setOnTouchListener((v, event) -> {
            Setup setup = getSetup();
            if (setup != null && event.getAction() == MotionEvent.ACTION_UP && setup.getErrNum() == 0) {
                try {
                    chessPad.setPgnGraph(-1, null);
                } catch (Config.PGNException e) {
                    Log.e(DEBUG_TAG, "endSetup failed", e);
                }
            }
            // true if the event was handled and should not be given further down to other views.
            return true;
        });
        // for API 22:
        setupStatus.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setupStatus.setSingleLine(false);

        // white / black move buttons
        btnWhiteMove = new ChessPadLayout.CpToggleButton(controlPaneLayout, R.drawable.kw, new SetupFlagKeeper(Config.FLAGS_BLACK_MOVE) {
            @Override
            public void setFlag(boolean set) {
                super.setFlag(!set);
                setMoveNum(getMoveNum());
            }

            @Override
            public boolean getFlag() {
                return !super.getFlag();
            }
        });
        btnBlackMove = new ChessPadLayout.CpToggleButton(controlPaneLayout, R.drawable.kb, new SetupFlagKeeper(Config.FLAGS_BLACK_MOVE) {
            @Override
            public void setFlag(boolean set){
                super.setFlag(set);
                setMoveNum(getMoveNum());
            }
        });

        btnWhiteQueenCastle = new ChessPadLayout.CpToggleButton(controlPaneLayout, R.drawable.l_castle, new SetupFlagKeeper(Config.FLAGS_W_QUEEN_OK){});
        btnWhiteKingCastle = new ChessPadLayout.CpToggleButton(controlPaneLayout, R.drawable.s_castle, new SetupFlagKeeper(Config.FLAGS_W_KING_OK));
        btnBlackQueenCastle = new ChessPadLayout.CpToggleButton(controlPaneLayout, R.drawable.l_castle, new SetupFlagKeeper(Config.FLAGS_B_QUEEN_OK));
        btnBlackKingCastle = new ChessPadLayout.CpToggleButton(controlPaneLayout, R.drawable.s_castle, new SetupFlagKeeper(Config.FLAGS_B_KING_OK));

        enPassEditText = createLabeledEditText(R.string.label_en_pass, new ChessPadLayout.StringKeeper() {
            @Override
            public void setValue(String enPass) {
                Setup setup = getSetup();
                if (setup == null) {
                    return;
                }
                String oldEnPass = setup.getEnPass();
                if (!oldEnPass.equals(enPass)) {
                    getSetup().setEnPass(enPass);
                    invalidate();
                }
            }
            @Override
            public String getValue() {
                Setup setup = getSetup();
                if (setup == null) {
                    return "";
                }
                return setup.getEnPass();
            }
        });
        enPassEditText.cpEditText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        hmClockEditText = createLabeledEditText(R.string.label_halfmove_clock, new ChessPadLayout.StringKeeper() {
            @Override
            public void setValue(String str) {
                Board board = getBoard();
                if (board == null) {
                    return;
                }
                int hmClock = getNumericValue(str);
                int oldHm = board.getReversiblePlyNum();
                if (oldHm != hmClock) {
                    board.setReversiblePlyNum(hmClock);
                    invalidate();
                }
            }
            @Override
            public String getValue() {
                Board board = getBoard();
                if (board == null) {
                    return "";
                }
                return "" + board.getReversiblePlyNum();
            }
        });

        moveNumEditText = createLabeledEditText(R.string.label_move_num, new ChessPadLayout.StringKeeper() {
            @Override
            public void setValue(String str) {
                int moveNum = getNumericValue(str);
                if (moveNum <= 0) {
                    moveNum = 1;
                }
                int oldMoveNum = getMoveNum();
                if (oldMoveNum != moveNum) {
                    setMoveNum(moveNum);
                    invalidate();
                }
            }
            @Override
            public String getValue() {
                return "" + getMoveNum();
            }
        });

        btnPredefinedSetups = new ChessPadLayout.CpToggleButton(controlPaneLayout, R.drawable.delete, new SetupFlagKeeper(predefinedPosition_mask));
        controlPaneLayout.setVisibility(View.GONE);
    }

    Board getBoard() {
        if (chessPad.setup == null) {
            return null;
        }
        return chessPad.setup.getBoard();
    }

    Setup getSetup() {
        return chessPad.setup;
    }

    BoardHolder getMainBoardHolder() {
        return new BoardHolder() {
            @Override
            public Board getBoard() {
                Setup setup = getSetup();
                if (setup == null) {
                    return null;
                }
                return getSetup().getBoard();
            }

            @Override
            public int getBoardViewSize() {
                return Metrics.boardViewSize;
            }

            @Override
            public int[] getBGResources() {
                return new int[]{R.drawable.bsquare, R.drawable.wsquare};
            }

            @Override
            public boolean onSquareClick(Square clicked) {
                Log.d(DEBUG_TAG, String.format("board onSquareClick (%s)", clicked.toString()));
                if (selectedPiece > 0) {
                    Setup setup = getSetup();
                    if (setup == null) {
                        return true;
                    }
                    setup.getBoard().setPiece(clicked, selectedPiece);
                    invalidate();
                }
                return true;
            }

            @Override
            public void onFling(Square clicked) {
                Log.d(DEBUG_TAG, String.format("board onFling (%s)", clicked.toString()));
                Setup setup = getSetup();
                if (setup == null) {
                    return;
                }
                setup.getBoard().setPiece(clicked, Config.EMPTY);
                invalidate();
            }

            @Override
            public boolean isFlipped() {
                return false;
            }
        };
    }

    @Override
    void hideAllWidgets() {
        super.hideAllWidgets();
        if (poolView != null) {
            poolView.setVisibility(View.GONE);
        }
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    void draw() {
        super.draw();
        chessPadLayout.boardView.draw(mainBoardHolder);

        if (Metrics.isVertical) {
            drawSetupVerticalLayout();
        } else {
            drawSetupHorizontalLayout();
        }

        if (poolView != null) {
            poolView.setVisibility(View.VISIBLE);
            poolView.draw();
        }

        enPassEditText.cpEditText.draw();
        hmClockEditText.cpEditText.draw();
        moveNumEditText.cpEditText.draw();
    }

    private void setStatus(int errNum) {
        if (setupStatus != null) {
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

    private int getMoveNum() {
        Board board = getBoard();
        if (board == null) {
            return 0;
        }
        return getBoard().getPlyNum() / 2 + 1;
    }

    private void setMoveNum(int moveNum) {
        Board board = getBoard();
        if (board == null) {
            return;
        }
        // this is the next move number!
        int plyNum = 2 * (moveNum - 1);
        if ((board.getFlags() & Config.FLAGS_BLACK_MOVE) != 0) {
            ++plyNum;
        }
        board.setPlyNum(plyNum);
    }

    protected void drawSetupVerticalLayout() {
        int x, y, w = 0, h = 0;

        if (poolView != null) {
            poolSquareSize = Metrics.squareSize;
            w = verticalPoolBoard.getXSize() * poolSquareSize;
            h = verticalPoolBoard.getYSize() * poolSquareSize;
            chessPadLayout.moveTo(poolView, 0, 0, w, h);
        }

        int toggleButtonSize = Metrics.squareSize;

        x = 0;
        y = h + 2 * Metrics.ySpacing;
        chessPadLayout.moveTo(btnWhiteMove, x, y, toggleButtonSize, toggleButtonSize);
        y += toggleButtonSize + Metrics.ySpacing;
        chessPadLayout.moveTo(btnBlackMove, x, y, toggleButtonSize, toggleButtonSize);
        y += toggleButtonSize + 2 * Metrics.ySpacing;

        int h0;
        if (Metrics.paneHeight - y >= Metrics.squareSize) {
            x = 0;
            w = Metrics.paneWidth;
            h0 = Metrics.squareSize;
        } else {
            x = w + 2 * Metrics.xSpacing;
            w = Metrics.paneWidth - x;
            y = 0;
            h0 = 2 * Metrics.squareSize + Metrics.ySpacing;
        }
        chessPadLayout.moveTo(setupStatus, x, y, w, h0);

        x = toggleButtonSize + Metrics.ySpacing;
        int y0 = h + 2 * Metrics.ySpacing;
        y = y0;
        chessPadLayout.moveTo(btnWhiteQueenCastle, x, y, toggleButtonSize, toggleButtonSize);
        y += toggleButtonSize + Metrics.ySpacing;
        chessPadLayout.moveTo(btnBlackQueenCastle, x, y, toggleButtonSize, toggleButtonSize);

        x += toggleButtonSize + Metrics.ySpacing;
        y = y0;
        chessPadLayout.moveTo(btnWhiteKingCastle, x, y, toggleButtonSize, toggleButtonSize);
        y += toggleButtonSize + Metrics.ySpacing;
        chessPadLayout.moveTo(btnBlackKingCastle, x, y, toggleButtonSize, toggleButtonSize);

        h = 2 * toggleButtonSize / 3 - Metrics.ySpacing;
        x += toggleButtonSize + Metrics.xSpacing;
        y = y0;
        enPassEditText.moveTo(x, y, Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, h);
        y += h + Metrics.ySpacing;
        hmClockEditText.moveTo(x, y, Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, h);
        y += h + Metrics.ySpacing;
        moveNumEditText.moveTo(x, y, Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, h);

// TODO!        chessPadLayout.moveTo(btnPredefinedSetups, x, y, Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2);
    }

    protected void drawSetupHorizontalLayout() {
        int x, y, w, h;

        int toggleButtonSize = Metrics.squareSize;

        h = 2 * toggleButtonSize / 3 - Metrics.ySpacing;
        x = 0;
        y = 0;
        enPassEditText.moveTo(x, y, Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, h);
        y += h + Metrics.ySpacing;
        hmClockEditText.moveTo(x, y, Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, h);
        y += h + Metrics.ySpacing;
        moveNumEditText.moveTo(x, y, Metrics.halfMoveClockLabelWidth, Metrics.moveLabelWidth / 2, h);

        y += h + Metrics.ySpacing;
        poolSquareSize = Metrics.squareSize;
        if (y + horizontalPoolBoard.getYSize() * Metrics.squareSize > Metrics.paneHeight) {
            poolSquareSize = (Metrics.paneHeight - y) / horizontalPoolBoard.getYSize();
        }

        x = Metrics.xSpacing;
        y = Metrics.paneHeight - horizontalPoolBoard.getYSize() * poolSquareSize;
        w = horizontalPoolBoard.getXSize() * poolSquareSize;
        h = horizontalPoolBoard.getYSize() * poolSquareSize;
        chessPadLayout.moveTo(poolView, x, y, w, h);

        int x0 = x + w + Metrics.xSpacing;

        x = x0;
        chessPadLayout.moveTo(btnWhiteMove, x, y, toggleButtonSize, toggleButtonSize);
        x += toggleButtonSize + Metrics.xSpacing;
        chessPadLayout.moveTo(btnBlackMove, x, y, toggleButtonSize, toggleButtonSize);

        x = x0;
        y += toggleButtonSize + Metrics.ySpacing;
        chessPadLayout.moveTo(btnWhiteQueenCastle, x, y, toggleButtonSize, toggleButtonSize);
        x += toggleButtonSize + Metrics.xSpacing;
        chessPadLayout.moveTo(btnWhiteKingCastle, x, y, toggleButtonSize, toggleButtonSize);

        x = x0;
        y += toggleButtonSize + Metrics.ySpacing;
        chessPadLayout.moveTo(btnBlackQueenCastle, x, y, toggleButtonSize, toggleButtonSize);
        x += toggleButtonSize + Metrics.ySpacing;
        chessPadLayout.moveTo(btnBlackKingCastle, x, y, toggleButtonSize, toggleButtonSize);

        x = x0;
        y += toggleButtonSize + Metrics.ySpacing;
        w = Metrics.paneWidth - x;
        h = Metrics.paneHeight - y;
        chessPadLayout.moveTo(setupStatus, x, y, w, h);
    }

    LabeledEditText createLabeledEditText(int rscLabel, ChessPadLayout.StringKeeper stringKeeper) {
        LabeledEditText labeledEditText = new LabeledEditText();
        TextView label = new TextView(chessPad);
        label.setBackgroundColor(Color.LTGRAY);
        label.setTextColor(Color.BLACK);
        label.setPadding(Metrics.xSpacing, Metrics.ySpacing, Metrics.xSpacing, Metrics.ySpacing);
        label.setGravity(Gravity.START | Gravity.CENTER);
        label.setText(rscLabel);
        controlPaneLayout.addView(label);
        labeledEditText.label = label;

        ChessPadLayout.CpEditText editText = new ChessPadLayout.CpEditText(chessPad);
        editText.setBackgroundColor(Color.GREEN);
        editText.setTextColor(Color.BLACK);
        editText.setPadding(Metrics.xSpacing, Metrics.ySpacing, Metrics.xSpacing, Metrics.ySpacing);
        editText.setGravity(Gravity.CENTER);
        editText.setSingleLine();
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setStringKeeper(stringKeeper);
        controlPaneLayout.addView(editText);
        labeledEditText.cpEditText = editText;

        return labeledEditText;
    }

    private boolean alreadyThere = false;   // quick and dirty
    @Override
    void invalidate() {
        if (alreadyThere) {
            return;
        }
        alreadyThere = true;
        chessPadLayout.boardView.setHints(null);
        Setup setup = getSetup();
        if (setup == null) {
            return;
        }
        setup.validate();
        setStatus(getSetup().getErrNum());
        chessPadLayout.title.setText(setup.getTitleText());
        alreadyThere = false;
    }

    private static void invalidateViewGroup(ViewGroup viewGroup) {
        for(int i = 0; i < viewGroup.getChildCount(); ++i) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof  ViewGroup) {
                invalidateViewGroup((ViewGroup)child);
            } else {
                child.invalidate();
            }
        }
    }

    class SetupFlagKeeper implements ChessPadLayout.FlagKeeper {
        final int flag;

        SetupFlagKeeper(int flag) {
            this.flag = flag;
        }

        @Override
        public void setFlag(boolean set) {
            Setup setup = getSetup();
            if (setup != null) {
                setup.setFlag(flag, set);
                invalidate();
            }
        }

        @Override
        public boolean getFlag() {
            Setup setup = getSetup();
            if (setup != null) {
                return setup.getFlag(flag) != 0;
            } return false;
        }
    }

    class LabeledEditText {
        TextView label;
        ChessPadLayout.CpEditText cpEditText;

        public void moveTo(int x, int y, int labelWidth, int textWidth, int height) {
            chessPadLayout.moveTo(label, x, y, labelWidth, height);
            chessPadLayout.moveTo(cpEditText, x + labelWidth + Metrics.xSpacing, y, textWidth, height);
        }

        public void setVisibility(int visibility) {
            label.setVisibility(visibility);
            cpEditText.setVisibility(visibility);
        }
    }
}
