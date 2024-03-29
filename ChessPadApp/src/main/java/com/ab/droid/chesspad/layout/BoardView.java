/*
     Copyright (C) 2021-2022	Alexander Bootman, alexbootman@gmail.com

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

 * visual board
 * Created by Alexander Bootman on 8/20/16.
 */
package com.ab.droid.chesspad.layout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.ab.droid.chesspad.BoardHolder;
import com.ab.droid.chesspad.ChessPad;
import com.ab.droid.chesspad.MainActivity;
import com.ab.droid.chesspad.R;
import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Pair;
import com.ab.pgn.Square;

import java.util.ArrayList;
import java.util.List;

public class BoardView extends View {
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();
    private final static boolean DEBUG = false;    // todo: config

    private final static String[] colorStrings = {"#FFFF0000", "#80B00000"};
    private final static int[] colors = new int[colorStrings.length];
    static {
        for (int i = 0; i < colors.length; ++i) {
            colors[i] = Color.parseColor(colorStrings[i]);
        }
    }

    private Bitmap[] pieces;
    private Bitmap[] bgBitmaps;
    private int squareSize;
    private final int margin = 0;
    private Square selected = new Square();
    private BoardHolder boardHolder;
    private final List<Pair<Square, Square>> hints = new ArrayList<>();

    @SuppressLint("ClickableViewAccessibility")
    public BoardView(Context context, BoardHolder boardHolder) {
        super(context);
        this.boardHolder = boardHolder;
        this.setBackgroundColor(Color.CYAN);
        initBitmaps();
        initBitmaps(boardHolder.getBGResources());
        this.setOnTouchListener(new BoardOnTouchListener());
    }

    public BoardView(ChessPad chessPad) {
        this(MainActivity.getContext(), chessPad);
    }

    private void initBitmaps(int... bgResources) {
        Resources r = getContext().getResources();

        bgBitmaps = new Bitmap[bgResources.length];
        for (int i = 0; i < bgResources.length; ++i) {
            bgBitmaps[i] = BitmapFactory.decodeResource(r, bgResources[i]);
        }
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
    }

    public void draw(BoardHolder boardHolder) {
        this.boardHolder = boardHolder;
        draw();
    }

    public void draw() {
        int size = 8;
        Board board = boardHolder.getBoard();
        if (board != null) {
            int xSize = boardHolder.getBoard().getXSize();
            int ySize = boardHolder.getBoard().getYSize();
            size = Math.max(xSize, ySize);
        }
        squareSize = boardHolder.getBoardViewSize() / size;
        recalcBitmapSizes(bgBitmaps);
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
        if (selected == null) {
            selected = new Square();
        }
        this.selected = selected;
    }

    public void setHints(String pv) {
        hints.clear();
        if (pv == null) {
            return;
        }
        String[] tokens = pv.split("\\s+");
        for (String token : tokens) {
            Square from = new Square(token.substring(0, 2));
            Square to = new Square(token.substring(2, 4));
            hints.add(new Pair<>(from, to));
        }
    }

    // translate board coords to local screen coords
    private Point board2screen(int x, int y) {
        Point res = new Point();
        Board board = boardHolder.getBoard();
        if (boardHolder.isFlipped()) {
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
    private Square screen2board(int x, int y) {
        Square res = new Square();
        Board board = boardHolder.getBoard();
        if (boardHolder.isFlipped()) {
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
        super.onDraw(canvas);
        drawBoard(canvas);
        drawHints(canvas);
    }

    final String vertNotation = "12345678";
    final String horizNotation = "abcdefgh";

    private void drawBoard(Canvas canvas) {
        if (DEBUG) {
            Log.d(DEBUG_TAG, "BoardView.drawBoard()");
        }
        Board board = boardHolder.getBoard();
        boolean drawNotation = board.getXSize() == Config.BOARD_SIZE && board.getYSize() == Config.BOARD_SIZE;
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        int textSize = squareSize / 2;
        int textWidth = 2 * textSize / 3;
        paint.setTextSize(textSize);

        for (int x = 0; x < board.getXSize(); ++x) {
            for (int y = 0; y < board.getYSize(); ++y) {
                int i = (x + y) % bgBitmaps.length;
                Point where = board2screen(x, y);
                canvas.drawBitmap(bgBitmaps[i], where.x, where.y, null);
                if (drawNotation) {
                    float xT, yT;
                    if (x == 0) {
                        if (boardHolder.isFlipped()) {
                            xT = where.x + squareSize - textWidth;
                            yT = where.y + textSize;
                        } else {
                            xT = where.x;
                            yT = where.y + textSize;
                        }
                        canvas.drawText(vertNotation.substring(y, y + 1), xT, yT, paint);
                    } else if (x == board.getXSize() - 1) {
                        if (boardHolder.isFlipped()) {
                            xT = where.x;
                            yT = where.y + textSize;
                        } else {
                            xT = where.x + squareSize - textWidth;
                            yT = where.y + textSize;
                        }
                        canvas.drawText(vertNotation.substring(y, y + 1), xT, yT, paint);
                    }
                    if (y == 0) {
                        if (boardHolder.isFlipped()) {
                            xT = where.x + squareSize / 3;
                            yT = where.y + squareSize / 3;
                        } else {
                            xT = where.x + squareSize / 3;
                            yT = where.y + squareSize - 2;
                        }
                        canvas.drawText(horizNotation.substring(x, x + 1), xT, yT, paint);
                    } else if (y == board.getYSize() - 1) {
                        if (boardHolder.isFlipped()) {
                            xT = where.x + squareSize / 3;
                            yT = where.y + squareSize - 2;
                        } else {
                            xT = where.x + squareSize / 3;
                            yT = where.y + squareSize / 3;
                        }
                        canvas.drawText(horizNotation.substring(x, x + 1), xT, yT, paint);
                    }
                }

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

    /**
     * Algorithm copied from DroidFish ChessBoard.java
     */
    private void drawHints(Canvas canvas) {
        Paint p = new Paint();
        p.setStyle(Paint.Style.FILL);
        p.setAntiAlias(true);

        float h = (float)(squareSize / 2.0);
        float d = (float)(squareSize / 8.0);
        double v = 35 * Math.PI / 180;
        double cosv = Math.cos(v);
        double sinv = Math.sin(v);
        double tanv = Math.tan(v);

        for (int i = 0; i < hints.size(); ++i) {
            Pair<Square, Square> hint = hints.get(i);
            Point from = board2screen(hint.first.getX(), hint.first.getY());
            Point to = board2screen(hint.second.getX(), hint.second.getY());
            float x0 = from.x + h;
            float y0 = from.y + h;
            float x1 = to.x + h;
            float y1 = to.y + h;

            float x2 = (float)(Math.hypot(x1 - x0, y1 - y0) + d);
            float y2 = 0;
            float x3 = (float)(x2 - h * cosv);
            float y3 = (float)(y2 - h * sinv);
            float x4 = (float)(x3 - d * sinv);
            float y4 = (float)(y3 + d * cosv);
            float x5 = (float)(x4 + (-d/2 - y4) / tanv);
            float y5 = -d / 2;
            float x6 = 0;
            float y6 = y5 / 2;
            Path path = new Path();
            path.moveTo(x2, y2);
            path.lineTo(x3, y3);
            path.lineTo(x5, y5);
            path.lineTo(x6, y6);
            path.lineTo(x6, -y6);
            path.lineTo(x5, -y5);
            path.lineTo(x3, -y3);
            path.close();

            Matrix mtx = new Matrix();
            mtx.postRotate((float)(Math.atan2(y1 - y0, x1 - x0) * 180 / Math.PI));
            mtx.postTranslate(x0, y0);
            path.transform(mtx);
            int j = i;
            if (i >= colors.length) {
                j = colors.length - 1;
            }
            p.setColor(colors[j]);
            canvas.drawPath(path, p);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private class BoardOnTouchListener implements OnTouchListener {
        Square selected = null;
        float startX, startY;
        boolean fling;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Square clicked = screen2board((int) event.getX(), (int) event.getY());
            if (DEBUG) {
                Log.d(DEBUG_TAG, String.format("BoardView (%s) %s", clicked, event));
            }
            Board board = boardHolder.getBoard();

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
