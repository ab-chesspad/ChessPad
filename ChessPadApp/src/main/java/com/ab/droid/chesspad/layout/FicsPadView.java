package com.ab.droid.chesspad.layout;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ab.pgn.Board;
import com.ab.pgn.Move;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.Square;
import com.ab.pgn.fics.FicsPad;
import com.ab.pgn.fics.chat.InboundMessage;

import java.util.List;
import java.util.Locale;

class FicsPadView extends ChessPadLayout.CpView {
    private static final boolean DEBUG = false;

    private TextView moveText;
    private EditText commandToFics;
    private ChessPadLayout.CpEditText conversation;
    private ChessPadLayout.CpImageButton buttonSend, buttonCommandHistoryUp, buttonCommandHistoryDown;
    private PlayerHolder topPlayerHolder, bottomPlayerHolder;
    private OnPlayerClick onTopPlayerClick, onBottomPlayerClick;
    private boolean flipped;

    FicsPadView(ChessPadLayout chessPadLayout) {
        super(chessPadLayout);
        setBoardHolder();
    }

    private void setBoardHolder() {
/*
        boardHolder = new BoardHolder() {
            @Override
            public Board getBoard() {
                return FicsPadView.this.getBoard();
            }

            @Override
            public int[] getBGResources() {
                return new int[]{R.drawable.bsquare, R.drawable.wsquare};
            }


            @Override
            public int getBoardViewSize() {
                if (Metrics.isVertical) {
                    return Metrics.boardViewSize;
                }
                return Metrics.screenHeight - 2 * (Metrics.titleHeight + Metrics.ySpacing);
            }

            @Override
            public boolean onSquareClick(Square clicked) {
                Log.d(DEBUG_TAG, String.format("board onSquareClick (%s)", clicked.toString()));
                PgnGraph pgnGraph = getFicsPad().getPgnGraph();
                int nMoves = pgnGraph.moveLine.size();
                boolean res = chessPad.onSquareClick(clicked);
                if(res) {
                    if(nMoves < pgnGraph.moveLine.size()) {
                        String move = pgnGraph.getCurrentMove().toString();
                        pgnGraph.delCurrentMove();
                        getFicsPad().send(move);
                    }
                }
                return res;
            }

            @Override
            public void onFling(Square clicked) {
            }

        };
*/
    }

    private Board getBoard() {
        return getFicsPad().getBoard();
    }

    private FicsPad getFicsPad() {
        return null;
//        return chessPad.ficsPad;
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public void draw() {
        super.draw();
        chessPadLayout.boardView.draw(chessPad);

/*
        getFicsPad().resetKibitzHistory();
        Log.d(DEBUG_TAG, String.format("draw %s", Thread.currentThread().getName()));
        onTopPlayerClick = () -> topPlayerClicked();
        onBottomPlayerClick = () -> bottomPlayerClicked();

        moveText = new EditText(chessPad);
        moveText.setFocusable(false);
        moveText.setSingleLine(true);
        moveText.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        moveText.setBackgroundColor(Color.WHITE);

        conversation = new ChessPadLayout.CpEditText(chessPad) {
            @Override
            protected void onDraw(Canvas canvas) {
                if(DEBUG) {
                    String txt = conversation.getText().toString();
                    Log.d(DEBUG_TAG, String.format("draw %s, current:\n%s", Thread.currentThread().getName(), txt));
                }
                super.onDraw(canvas);
            }
        };
        conversation.setGravity(Gravity.BOTTOM);
        conversation.setFocusable(false);
        conversation.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        conversation.setBackgroundColor(Color.GREEN);
        conversation.setHint(R.string.hint_from_server);

        commandToFics = new EditText(chessPad);
        commandToFics.setBackgroundColor(Color.GREEN);
        commandToFics.setHint(R.string.hint_send_to_fics);
        commandToFics.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }
            @Override
            public void afterTextChanged(Editable editable) {
                // fix it: called on every character, I only need the final value
                String text = editable.toString();
                getFicsPad().setUserCommand2Fics(text);
            }
        });

        RelativeLayout relativeLayoutFicsPad = new RelativeLayout(chessPad);
        relativeLayoutFicsPad.setBackgroundColor(Color.BLACK);

        int x, y, w, h;
        x = 0;
        w = Metrics.screenWidth - 2 * x;
        y = Metrics.titleHeight + Metrics.ySpacing;
        h = Metrics.screenHeight - Metrics.titleHeight - Metrics.ySpacing;
        ChessPadLayout.addView(relativeLayoutMain, relativeLayoutFicsPad, x, y, w, h);

        if (Metrics.isVertical) {
            drawVerticalLayout(relativeLayoutFicsPad);
        } else {
            drawHorizonlalLayout(relativeLayoutFicsPad);
        }
        updateCommandToFics();
        buttonCommandHistoryUp.setOnClickListener((v) -> getPrevFicsCommand());
        buttonCommandHistoryDown.setOnClickListener((v) -> getNextFicsCommand());
        buttonSend.setOnClickListener((v) -> {
            getFicsPad().send();
            commandToFics.setText("");
        });
*/
    }

    @SuppressLint("ClickableViewAccessibility")
    private PlayerHolder drawPlayerBar(int width, final OnPlayerClick onPlayerClick) {
        PlayerHolder playerHolder = new PlayerHolder();
/*
        View.OnTouchListener playerOnTouchListener = (v, event) -> {
            if(event.getAction() == MotionEvent.ACTION_UP) {
                onPlayerClick.clicked();
            }
            return true;
        };

        final int bgColor = Color.GREEN;
        RelativeLayout relativeLayout = new RelativeLayout(chessPad);
        relativeLayout.setBackgroundColor(Color.BLACK);
        relativeLayout.setOnTouchListener(playerOnTouchListener);

        int timeWidth = Metrics.timeWidth;

        TextView player = new TextView(chessPad);
        player.setSingleLine();
        player.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        player.setBackgroundColor(bgColor);
        player.setOnTouchListener(playerOnTouchListener);

        TextView playerTime = new TextView(chessPad);
        playerTime.setSingleLine();
        playerTime.setGravity(Gravity.END);
        playerTime.setBackgroundColor(bgColor);
        playerTime.setOnTouchListener(playerOnTouchListener);

        int w = width - timeWidth - 2 * Metrics.xSpacing;
        ChessPadLayout.addTextView(relativeLayout, player, 0, 0, w, Metrics.titleHeight);
        ChessPadLayout.addTextView(relativeLayout, playerTime, width - timeWidth, 0, timeWidth, Metrics.titleHeight);

        playerHolder.relativeLayout = relativeLayout;
        playerHolder.player = player;
        playerHolder.playerTime = playerTime;
*/
        return playerHolder;
    }

/*
    private void drawVerticalLayout(RelativeLayout relativeLayout) {
        int x, x0, y, y0, w, h;

        x = (Metrics.screenWidth - Metrics.boardViewSize) / 2;
        boardView = ChessPadLayout.drawBoardView(chessPad, relativeLayout, x, 0, boardHolder);

        w = Metrics.screenWidth - Metrics.buttonSize - Metrics.xSpacing;
        topPlayerHolder = drawPlayerBar(w, onTopPlayerClick);
        ChessPadLayout.addImageButton(chessPad, chessPad.getMainRelativeLayout(), ChessPad.Command.Menu, 0, 0,
                Metrics.buttonSize, Metrics.titleHeight, R.drawable.menu);
        ChessPadLayout.addView(relativeLayoutMain, topPlayerHolder.relativeLayout, Metrics.buttonSize + Metrics.xSpacing, 0,
                w, Metrics.titleHeight);

        w = Metrics.screenWidth;
        bottomPlayerHolder = drawPlayerBar(w, onBottomPlayerClick);
        y = Metrics.boardViewSize;
        h = Metrics.titleHeight;
        ChessPadLayout.addView(relativeLayout, bottomPlayerHolder.relativeLayout, 0, y, w, h);

        y += h + Metrics.ySpacing;
        w = Metrics.screenWidth - (Metrics.buttonSize + Metrics.xSpacing);
        h = Metrics.titleHeight;
        ChessPadLayout.addTextView(relativeLayout, moveText, 0, y, w, h);

        x0 = Metrics.screenWidth - Metrics.buttonSize;
        ImageButton buttonFlip = ChessPadLayout.addImageButton(chessPad, relativeLayout, ChessPad.Command.Flip, x0, y, R.drawable.flip_board_view);

        y0 = y + h + Metrics.ySpacing; // for conversation
        y = Metrics.screenHeight - Metrics.titleHeight - Metrics.buttonSize;
        w = Metrics.screenWidth - 2 * (Metrics.buttonSize + Metrics.xSpacing);
        h = Metrics.buttonSize;
        ChessPadLayout.addTextView(relativeLayout, commandToFics, 0, y, w, h);

        x = x0;
        buttonCommandHistoryDown = ChessPadLayout.addImageButton(chessPad, relativeLayout, ChessPad.Command.None, x, y, R.drawable.arrow_down);

        x -= Metrics.buttonSize + Metrics.xSpacing;
        buttonSend = ChessPadLayout.addImageButton(chessPad, relativeLayout, ChessPad.Command.None, x, y, R.drawable.send);

        w = Metrics.screenWidth - Metrics.buttonSize - Metrics.xSpacing;
        h = y - y0 - Metrics.ySpacing;
        y = y0;
        ChessPadLayout.addTextView(relativeLayout, conversation, 0, y, w, h);

        x = x0;
        y = Metrics.screenHeight - Metrics.titleHeight - 2 * Metrics.buttonSize - Metrics.ySpacing;
        buttonCommandHistoryUp = ChessPadLayout.addImageButton(chessPad, relativeLayout, ChessPad.Command.None, x, y, R.drawable.arrow_up);
    }

    private void drawHorizonlalLayout(RelativeLayout relativeLayout) {
        int x, x0, y, w, h;
        int timeWidth = Metrics.timeWidth;

        boardView = ChessPadLayout.drawBoardView(chessPad, relativeLayout, 0, 0, boardHolder);

        w = boardHolder.getBoardViewSize() - Metrics.buttonSize - Metrics.xSpacing;
        topPlayerHolder = drawPlayerBar(w, onTopPlayerClick);
        ChessPadLayout.addImageButton(chessPad, chessPad.getMainRelativeLayout(), ChessPad.Command.Menu, 0, 0,
                Metrics.buttonSize, Metrics.titleHeight, R.drawable.menu);
        ChessPadLayout.addView(relativeLayoutMain, topPlayerHolder.relativeLayout, Metrics.buttonSize + Metrics.xSpacing, 0,
                w, Metrics.titleHeight);

        w = boardHolder.getBoardViewSize();
        bottomPlayerHolder = drawPlayerBar(w, onBottomPlayerClick);
        y = boardHolder.getBoardViewSize();
        h = Metrics.titleHeight;
        ChessPadLayout.addView(relativeLayout, bottomPlayerHolder.relativeLayout, 0, y, w, h);
        x = boardHolder.getBoardViewSize() + Metrics.ySpacing;
        w = Metrics.screenWidth - x;
        h = 5 * Metrics.titleHeight;
        ChessPadLayout.addTextView(relativeLayout, conversation, x, 0, w, h);

        x = 0;
        y = Metrics.boardViewSize - h;

        y -= h + Metrics.ySpacing;
        x0 = x;
        buttonCommandHistoryDown = ChessPadLayout.addImageButton(chessPad, relativeLayout, ChessPad.Command.None, x, y, R.drawable.arrow_down);

        x -= Metrics.buttonSize + Metrics.xSpacing;
        buttonSend = ChessPadLayout.addImageButton(chessPad, relativeLayout, ChessPad.Command.None, x, y, R.drawable.send);

        x = x0;
        y = Metrics.screenHeight - Metrics.titleHeight - 2 * Metrics.buttonSize - Metrics.ySpacing;
        buttonCommandHistoryUp = ChessPadLayout.addImageButton(chessPad, relativeLayout, ChessPad.Command.None, x, y, R.drawable.arrow_up);
    }
*/

    private String toHMS(int time) {
        int seconds = time % 60;
        int minutes = (time  / 60) % 60;
        int hours = (time  / 3600) % 60;
        return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
    }

    private void topPlayerClicked() {
        Log.d(DEBUG_TAG, "topPlayerClicked()");
    }

    private void bottomPlayerClicked() {
        Log.d(DEBUG_TAG, "bottomPlayerClicked()");
    }

/*
    @Override
    void hideAllWidgets() {
        // todo!
    }
*/

    @Override
    public void invalidate() {
        InboundMessage.G1Game currentGame = getFicsPad().getCurrentGame();

        String moveText = "";
        PgnGraph pgnGraph = getFicsPad().getPgnGraph();
        Square selected = new Square();
        if(pgnGraph.moveLine.size() > 1) {
            int i = pgnGraph.moveLine.size() - 1;
            if(pgnGraph.moveLine.size() > 2) {
                --i;
            }
            Move move = pgnGraph.moveLine.get(i);
            moveText = pgnGraph.getMoveNum(move) + move.toString();
            if(++i < pgnGraph.moveLine.size()) {
                move = pgnGraph.moveLine.get(i);
                if(moveText.contains("... ")) {
                    moveText += pgnGraph.getMoveNum(move);
                }
                moveText += move.toString();
            }
            selected = move.getTo();
        }
        chessPadLayout.boardView.setSelected(selected);
        this.moveText.setText(moveText);

        TextView topPlayer, topPlayerTime, bottomPlayer, bottomPlayerTime;
        if(chessPad.isFlipped()) {
            topPlayer = bottomPlayerHolder.player;
            topPlayerTime = bottomPlayerHolder.playerTime;
            bottomPlayer = topPlayerHolder.player;
            bottomPlayerTime = topPlayerHolder.playerTime;
        } else {
            topPlayer = topPlayerHolder.player;
            topPlayerTime = topPlayerHolder.playerTime;
            bottomPlayer = bottomPlayerHolder.player;
            bottomPlayerTime = bottomPlayerHolder.playerTime;
        }

        if(currentGame != null) {
            topPlayer.setText(currentGame.getBlackName());
            topPlayerTime.setText(toHMS(currentGame.getBlackRemainingTime()));
            bottomPlayer.setText(currentGame.getWhiteName());
            bottomPlayerTime.setText(toHMS(currentGame.getWhiteRemainingTime()));
        }
        List<String> newMsgs = getFicsPad().getNewKibitzMsgs();
        if(newMsgs == null) {
            conversation.setText("");
        } else {
            String newTxt = TextUtils.join("\n", newMsgs);
            if(!newTxt.isEmpty()) {
                conversation.append("\n" + newTxt);
            }
        }
        String txt = conversation.getText().toString();

        if(DEBUG) {
            Log.d(DEBUG_TAG, String.format("draw %s, current:\n%s", Thread.currentThread().getName(), txt));
        }
        conversation.invalidate();
    }

    private void getPrevFicsCommand() {
        getFicsPad().toPrevCommand();
        commandToFics.setText(getFicsPad().getUserCommand2Fics());
    }

    private void getNextFicsCommand() {
        getFicsPad().toNextCommand();
        commandToFics.setText(getFicsPad().getUserCommand2Fics());
    }

    private void updateCommandToFics() {
        commandToFics.setText(getFicsPad().getUserCommand2Fics());
    }

    private static class PlayerHolder {
        RelativeLayout relativeLayout;
        TextView player, playerTime;
    }

    private interface OnPlayerClick {
        void clicked();
    }
}
