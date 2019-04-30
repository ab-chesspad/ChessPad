package com.ab.droid.chesspad;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Square;

/**
 * visual board
 * Created by Alexander Bootman on 8/20/16.
 */
public class BoardView extends View {
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();
    public static boolean DEBUG = false;    // todo: config

    private static Bitmap[] pieces;
    private int squareSize, margin = 0;
    private Square selected = new Square();
    private BoardHolder boardHolder;
    private Bitmap[] bgBitmaps;

    @SuppressLint("ClickableViewAccessibility")
    public BoardView(Context context, int squareSize, BoardHolder boardHolder) {
        super(context);
        this.boardHolder = boardHolder;
        this.squareSize = squareSize;
        this.setBackgroundColor(Color.BLACK);
        initBitmaps();
        initBitmaps(boardHolder.getBGResources());
        this.setOnTouchListener(new BoardOnTouchListener());
    }

    private void initBitmaps(int... bgResources) {
        Resources r = getContext().getResources();

        bgBitmaps = new Bitmap[bgResources.length];
        for (int i = 0; i < bgResources.length; ++i) {
            bgBitmaps[i] = BitmapFactory.decodeResource(r, bgResources[i]);
        }
        recalcBitmapSizes(bgBitmaps);
    }

    private void initBitmaps() {
        Resources r = getContext().getResources();

        pieces = new Bitmap[Config.TOTAL_PIECE_BITMAPS];
        pieces[Config.EMPTY] = BitmapFactory.decodeResource(r, R.drawable.wsquare);
        pieces[Config.EMPTY | Config.BLACK] = BitmapFactory.decodeResource(r, R.drawable.bsquare);
        pieces[Config.KING] = BitmapFactory.decodeResource(r, R.drawable.kw);
        pieces[Config.BLACK_KING] = BitmapFactory.decodeResource(r, R.drawable.kb);
        pieces[Config.QUEEN] = BitmapFactory.decodeResource(r, R.drawable.qw);
        pieces[Config.BLACK_QUEEN] = BitmapFactory.decodeResource(r, R.drawable.qb);
        pieces[Config.BISHOP] = BitmapFactory.decodeResource(r, R.drawable.bw);
        pieces[Config.BLACK_BISHOP] = BitmapFactory.decodeResource(r, R.drawable.bb);
        pieces[Config.KNIGHT] = BitmapFactory.decodeResource(r, R.drawable.nw);
        pieces[Config.BLACK_KNIGHT] = BitmapFactory.decodeResource(r, R.drawable.nb);
        pieces[Config.ROOK] = BitmapFactory.decodeResource(r, R.drawable.rw);
        pieces[Config.BLACK_ROOK] = BitmapFactory.decodeResource(r, R.drawable.rb);
        pieces[Config.PAWN] = BitmapFactory.decodeResource(r, R.drawable.pw);
        pieces[Config.BLACK_PAWN] = BitmapFactory.decodeResource(r, R.drawable.pb);

        pieces[Config.SELECTED_SQUARE_INDEX] = BitmapFactory.decodeResource(r, R.drawable.selected);
        pieces[Config.POOL_BG_INDEX] = BitmapFactory.decodeResource(r, R.drawable.greenbg);

        recalcBitmapSizes(pieces);
    }

    private void recalcBitmapSizes(Bitmap[] pieces) {
        for (int i = 0; i < pieces.length; ++i) {
            if (pieces[i] != null) {
                pieces[i] = Bitmap.createScaledBitmap(pieces[i], squareSize, squareSize, true);
            }
        }
    }

    public void setSelected(Square selected) {
        this.selected = selected;
    }

    // translate board coords to local screen coords
    protected Point board2screen(int x, int y) {
        Point res = new Point();
        Board board = boardHolder.getBoard();
        if (board == null) {
            return res;
        }
        if (boardHolder.isReversed()) {
            int xSize = board.getXSize();
            res.x = margin + (xSize - 1 - x) * squareSize;
            res.y = margin + y * squareSize;
        } else {
            res.x = margin + x * squareSize;
            int ySize = board.getYSize();
            res.y = margin + (ySize - 1 - y) * squareSize;
        }
        return res;
    }

    // translate local screen coords to board coords
    protected Square screen2board(int x, int y) {
        Square res = new Square();
        Board board = boardHolder.getBoard();
        if (board == null) {
            return res;
        }
        if (boardHolder.isReversed()) {
            res.x = board.getXSize() - 1 - (x - margin) / squareSize;
            res.y = (y - margin) / squareSize;
        } else {
            res.x = (x - margin) / squareSize;
            res.y = board.getYSize() - 1 - (y - margin) / squareSize;
        }
        return res;
    }

    @Override
    public void invalidate() {
        if (DEBUG) {
            Log.d(DEBUG_TAG, String.format("BoardView.invalidate(%s)", this.getClass().getName()));
        }
        super.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawBoard(canvas);
    }

    private void drawBoard(Canvas canvas) {
        if (DEBUG) {
            Log.d(DEBUG_TAG, "BoardView.drawBoard()");
        }
        Board board = boardHolder.getBoard();
        if (board == null) {
            return;
        }
        for (int x = 0; x < board.getXSize(); ++x) {
            for (int y = 0; y < board.getYSize(); ++y) {
                int i = (x + y) % bgBitmaps.length;
                Point where = board2screen(x, y);
                canvas.drawBitmap(bgBitmaps[i], where.x, where.y, null);

                int piece;
                if ((piece = board.getPiece(x, y)) != Config.EMPTY) {
                    canvas.drawBitmap(pieces[piece], where.x, where.y, null);
                }
            }
        }
        if (selected.x >= 0) {
            Point where = board2screen(selected.x, selected.y);
            canvas.drawBitmap(pieces[Config.SELECTED_SQUARE_INDEX], where.x, where.y, null);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    class BoardOnTouchListener implements OnTouchListener {
        Square selected = null;
        float startX, startY;
        boolean fling;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Square clicked = screen2board((int) event.getX(), (int) event.getY());
            if (DEBUG) {
                Log.d(DEBUG_TAG, String.format("BoardView (%s) %s", clicked.toString(), event.toString()));
            }
            Board board = boardHolder.getBoard();
            if (board == null) {
                return true;
            }

            int action = event.getActionMasked();
            switch (action) {
                case (MotionEvent.ACTION_DOWN):
                    if (clicked.getX() < 0 || clicked.getX() >= board.getXSize() ||
                            clicked.getY() < 0 || clicked.getY() >= board.getYSize()) {
                        return true;
                    }
                    selected = clicked;
                    startX = event.getX();
                    startY = event.getY();
                    fling = false;
                    return true;

                case (MotionEvent.ACTION_MOVE):
                    if (selected != null) {
                        float dx = startX - event.getX();
                        float dy = startY - event.getY();
                        if (dx * dx + dy * dy >= squareSize * squareSize) {
                            fling = true;
                        }
                    }
                    return true;

                case (MotionEvent.ACTION_UP):
                    if (selected != null) {
                        float dx = startX - event.getX();
                        float dy = startY - event.getY();
                        if (dx * dx + dy * dy >= squareSize * squareSize) {
                            fling = true;
                        }
                        if (fling) {
                            boardHolder.onFling(selected);
                        } else {
                            if (boardHolder.onSquareClick(selected)) {
                                BoardView.this.selected = selected;
                            }
                        }
                    }
                    selected = null;
                    return true;

                default:
                    selected = null;
                    return true;

            }
        }
    }
}