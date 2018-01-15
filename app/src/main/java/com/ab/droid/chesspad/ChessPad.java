package com.ab.droid.chesspad;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import com.ab.pgn.BitStream;
import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Move;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnItem;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.Square;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * main activity
 * Created by Alexander Bootman on 8/20/16.
 */
public class ChessPad extends AppCompatActivity {
    protected final String DEBUG_TAG = this.getClass().getName();
//* uncomment this line to replay crash providing the correct file name
    private final String CRASH_RESTORE = null;
/*/
    protected String CRASH_RESTORE = "cp-crash-2017-01-13_11-36-55";
//*/
    static final String
        STATUS_FILE_NAME = "ChessPad.state",
        DEFAULT_DIRECTORY = "ChessPad",
        str_dummy = null;

    private static int i = -1;

    public enum Command {
        None(++i),
        Start(++i),
        PrevVar(++i),
        Prev(++i),
        Stop(++i),
        Next(++i),
        NextVar(++i),
        End(++i),
        Reverse(++i),
        Delete(++i),
        Menu(++i),
        ShowGlyphs(++i),
        ShowFiles(++i),
        Append(++i),
        EditHeaders(++i),
        ;

        private final int value;
        private static Command[] values = Command.values();

        Command(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Command command(int v) {
            return values[v];
        }

        public static int total() {
            return values.length;
        }
    }

    private static int j = -1;

    public enum Mode {
        None(++j),
        Game(++j),
        Setup(++j),
        ;

        private final int value;
        private static Mode[] values = Mode.values();

        Mode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static Mode value(int v) {
            return values[v];
        }

        public static int total() {
            return values.length;
        }
    }

    public enum MenuCommand {
        Load,
        Save,
        Append,
        Setup,
        CancelSetup,
        About,
    }

    // restore in onResume()
    protected PgnGraph pgnGraph;
    protected int animationTimeout = 1000;      // config?
    protected PgnItem currentPath = new PgnItem.Dir(null, "/");
    private Popups popups;
    protected Mode mode = Mode.Game;
    protected Setup setup;
    private PgnItem.Item nextPgnItem;
    private boolean reversed;
    protected Square selectedSquare;
    private int selectedPiece;

    transient public String versionName;
    transient public int versionCode;
    transient public Resources resources;
    transient public int timeoutDelta = animationTimeout / 4;
    transient private AnimationHandler animationHandler;
    transient protected ChessPadView chessPadView;
    transient private String[] setupErrs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(DEBUG_TAG, "onCreate()");

        popups = new Popups(this);
        File root = Environment.getExternalStorageDirectory();
        PgnItem.setRoot(root);
        currentPath = new PgnItem.Dir(DEFAULT_DIRECTORY);
        File dir = new File(currentPath.getAbsolutePath());
        dir.mkdirs();

        try {
            PackageInfo pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pinfo.versionName;
            versionCode = pinfo.versionCode;
        } catch (PackageManager.NameNotFoundException nnfe) {
            versionName = "0.0";
        }
        resources = getResources();
        setupErrs = resources.getStringArray(R.array.setup_errs);
//        sample();       // initially create a sample pgn
    }

    private void init() {
        try {
            setup = null;
            chessPadView = new ChessPadView(this);
            sample();       // initially create a sample pgn
        } catch (Throwable t) {
            Log.e(DEBUG_TAG, t.toString(), t);
        }
    }

    protected boolean isSaveable() {
        return pgnGraph.isModified() && pgnGraph.getPgn().getIndex() != -1;
    }

    public void setComment(String newComment) {
        if(!isAnimationRunning()) {
            pgnGraph.setComment(newComment);
        }
    }

    protected List<MenuItem> getMenuItems() {
        List<MenuItem> menuItems = new LinkedList<>();
        menuItems.add(new MenuItem(MenuCommand.Load, getResources().getString(R.string.menu_load)));
        menuItems.add(new MenuItem(MenuCommand.Save, getResources().getString(R.string.menu_save), isSaveable()));
        menuItems.add(new MenuItem(MenuCommand.Append, getResources().getString(R.string.menu_append), mode == Mode.Game));
        if(mode == Mode.Game) {
            menuItems.add(new MenuItem(MenuCommand.Setup, getResources().getString(R.string.menu_setup), true));
        } else {
            menuItems.add(new MenuItem(MenuCommand.CancelSetup, getResources().getString(R.string.menu_cancel_setup), true));
        }
        menuItems.add(new MenuItem(MenuCommand.About, getResources().getString(R.string.menu_about)));
        return menuItems;
    }

    private void sample() {
        new Sample().createPgnTest();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(DEBUG_TAG, "onResume() 0");
        FileInputStream fis = null;

        try {
            init();

            try {
                if(CRASH_RESTORE != null) {
                    File inDir = new File(PgnItem.getRoot(), DEFAULT_DIRECTORY);
                    fis = new FileInputStream(new File(inDir.getAbsolutePath(), CRASH_RESTORE));
                } else {
                    fis = openFileInput(STATUS_FILE_NAME);
                }
            } catch (FileNotFoundException e) {
                Log.w(DEBUG_TAG, "onResume() 1", e);
            }

            if (fis != null) {
                BitStream.Reader reader = new BitStream.Reader(fis);
                if(!unserialize(reader)) {
                    fis = null;
                }
            }
        } catch (Throwable t) {
            Log.e(DEBUG_TAG, "onResume()", t);
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            popups.crashAlert(sw.toString());
            if (fis != null) {
                try {
                    File outDir = new File(PgnItem.getRoot(), DEFAULT_DIRECTORY);
                    String ts = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
                    FileOutputStream fos = new FileOutputStream(new File(outDir.getAbsolutePath(), "cp-crash-" + ts));
                    PgnItem.copy(fis, fos);
                } catch (IOException e) {
                    Log.e(DEBUG_TAG, e.getMessage(), e);
                }
            }
            // start from scratch
            fis = null;
        }

        if (fis == null) {
            try {
                mode = Mode.Game;
                pgnGraph = new PgnGraph(new Board());
            } catch (Config.PGNException e) {
                Log.e(DEBUG_TAG, "onResume() 3", e);
                // will crash anyway
            }
        }
        chessPadView.redraw();
        if(mode == Mode.Setup) {
            setup.setChessPadView(chessPadView);
            setup.onValueChanged(null);
        }
        chessPadView.invalidate();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(DEBUG_TAG, "onPause()");
        try {
            BitStream.Writer writer = new BitStream.Writer(openFileOutput(STATUS_FILE_NAME, Context.MODE_PRIVATE));
            this.serialize(writer);
        } catch (Config.PGNException | IOException e) {
            Log.d(DEBUG_TAG, "onPause() 1", e);
        }
        popups.dismiss();
    }

    public Mode getMode() {
        return mode;
    }

    private void serialize(BitStream.Writer writer) throws Config.PGNException {
        try {
            writer.write(versionCode, 4);
            writer.write(mode.getValue(), 2);
            currentPath.serialize(writer);
            pgnGraph.serialize(writer);
            if (mode == Mode.Game) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                setup.serialize(writer);
            }
            if (nextPgnItem == null) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                nextPgnItem.serialize(writer);
            }
            if (reversed) {
                writer.write(1, 1);
            } else {
                writer.write(0, 1);
            }
            if (selectedSquare == null) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                selectedSquare.serialize(writer);
            }
            popups.serialize(writer);
            writer.close();
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    private boolean unserialize(BitStream.Reader reader) throws Config.PGNException {
        try {
            if (versionCode != reader.read(4)) {
                return false;
//            throw new Config.PGNException("Old serialization ignored");
            }
            mode = Mode.value(reader.read(2));
            currentPath = PgnItem.unserialize(reader);
            pgnGraph = new PgnGraph(reader);
            if (reader.read(1) == 1) {
                setup = new Setup(reader);
            }
            if (reader.read(1) == 1) {
                nextPgnItem = (PgnItem.Item) PgnItem.unserialize(reader);
            }
            reversed = reader.read(1) == 1;
            if (reader.read(1) == 1) {
                selectedSquare = new Square(reader);
                selectedPiece = pgnGraph.getBoard().getPiece(selectedSquare);
            }
            popups.unserialize(reader);
            return true;
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }
    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }

    public boolean isReversed() {
        return reversed;
    }

    public boolean isAnimationRunning() {
        return animationHandler != null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if ( keyCode == KeyEvent.KEYCODE_MENU ) {
            onButtonClick(Command.Menu, null);
            // return 'true' to prevent further propagation of the key event
            return true;
        }

        // let the system handle all other key events
        return super.onKeyDown(keyCode, event);
    }

    public void onButtonClick(Command command) {
        onButtonClick(command, null);
    }

    public void onButtonClick(Command command, Object param) {
        Log.d(DEBUG_TAG, String.format("click %s\n%s", command.toString(), pgnGraph.getBoard().toString()));
        try {
            switch (command) {
                case Start:
                    selectedSquare = null;
                    if (!isAnimationRunning()) {
                        pgnGraph.toInit();
                        chessPadView.invalidate();
                    } else {
//                    animationHandler.increaseTimeout();
                        animationTimeout += timeoutDelta;
                        animationHandler.setTimeout(animationTimeout);
                    }
                    break;

                case Prev:
                    if (isAnimationRunning()) {
                        Log.e(DEBUG_TAG, String.format("with animation click %s\n%s", command.toString(), pgnGraph.getBoard().toString()));
                    }
                    selectedSquare = null;
                    pgnGraph.toPrev();
                    chessPadView.invalidate();
                    break;

                case PrevVar:
                    if (isAnimationRunning()) {
                        Log.e(DEBUG_TAG, String.format("with animation click %s\n%s", command.toString(), pgnGraph.getBoard().toString()));
                    }
                    selectedSquare = null;
                    pgnGraph.toPrevVar();
                    chessPadView.invalidate();
                    break;

                case Next:
                    if (isAnimationRunning()) {
                        Log.e(DEBUG_TAG, String.format("with animation click %s\n%s", command.toString(), pgnGraph.getBoard().toString()));
                    }
                    selectedSquare = null;
                    List<Move> variations = pgnGraph.getVariations();
                    if (variations == null) {
                        pgnGraph.toNext();
                        chessPadView.invalidate();
                    } else {
                        Log.d(DEBUG_TAG, "variation");
                        popups.launchDialog(Popups.DialogType.Variations);
                    }
                    break;

                case Stop:
                    selectedSquare = null;
                    animationHandler.stop();
                    chessPadView.setButtonEnabled(ChessPad.Command.Stop.getValue(), false);
                    break;

                case NextVar:
                    if (isAnimationRunning()) {
                        Log.e(DEBUG_TAG, String.format("with animation click %s\n%s", command.toString(), pgnGraph.getBoard().toString()));
                    }
                    selectedSquare = null;
                    if (pgnGraph.getVariations() == null) {
                        animationHandler = new AnimationHandler(animationTimeout, new TimeoutObserver() {
                            @Override
                            public boolean handle() {
                                pgnGraph.toNext();
                                chessPadView.invalidate();
                                return !pgnGraph.isEnd() && pgnGraph.getVariations() == null;
                            }

                            @Override
                            public void beforeAnimationStart() {
                                ChessPad.this.beforeAnimationStart();
                            }

                            @Override
                            public void afterAnimationEnd() {
                                ChessPad.this.afterAnimationEnd();
                            }
                        });
                    }
                    break;

                case End:
                    selectedSquare = null;
                    if (!isAnimationRunning()) {
                        animationHandler = new AnimationHandler(animationTimeout, new TimeoutObserver() {
                            @Override
                            public boolean handle() {
                                pgnGraph.toNext();
                                chessPadView.invalidate();
                                return !pgnGraph.isEnd();
                            }

                            @Override
                            public void beforeAnimationStart() {
                                ChessPad.this.beforeAnimationStart();
                            }

                            @Override
                            public void afterAnimationEnd() {
                                ChessPad.this.afterAnimationEnd();
                            }
                        });
                    } else {
                        animationTimeout -= timeoutDelta;
                        if (animationTimeout <= 0) {
                            animationTimeout = 1;
                        }
                        animationHandler.setTimeout(animationTimeout);
                    }
                    break;

                case Reverse:
                    reversed = !reversed;
                    chessPadView.invalidate();
                    break;

                case ShowGlyphs:
                    if (!isAnimationRunning()) {
                        if(pgnGraph.okToSetGlyph()) {
                            popups.launchDialog(Popups.DialogType.Glyphs);
                        }
                    }
                    break;

                case Menu:
                    if (!isAnimationRunning()) {
                        popups.launchDialog(Popups.DialogType.Menu);
                    }
                    break;

                case Delete:
                    if (!isAnimationRunning()) {
                        popups.launchDialog(Popups.DialogType.DeleteYesNo);
                    }
                    break;

                case EditHeaders:
                    if (!isAnimationRunning()) {
                        popups.launchDialog(Popups.DialogType.Headers);
                    }
                    break;

            }
        } catch (Config.PGNException e) {
            Log.e(DEBUG_TAG, "ChessPad.onButtonClick", e);
        }
    }

    void delete() throws Config.PGNException {
        if(isFirstMove()) {
            pgnGraph.getPgn().setMoveText(null);
            savePgnGraph(false, new CPPostExecutor() {
                @Override
                public void onPostExecute() throws Config.PGNException {
                    pgnGraph = new PgnGraph(new Board());
                    currentPath = currentPath.getParent();
                    chessPadView.invalidate();
                }

                @Override
                public void onExecuteException(Config.PGNException e) throws Config.PGNException {
                    Log.e(DEBUG_TAG, "delete, onExecuteException, thread " + Thread.currentThread().getName(), e);
                    popups.crashAlert(R.string.crash_cannot_delete);
                }
            });
        } else {
            pgnGraph.delCurrentMove();
            chessPadView.invalidate();
        }
    }

    public boolean onSquareClick(Square clicked) {
        if (isAnimationRunning()) {
            return false;
        }
        Log.d(DEBUG_TAG, String.format("board onSquareClick (%s)\n%s", clicked.toString(), pgnGraph.getBoard().toString()));
        int piece = pgnGraph.getBoard().getPiece(clicked);
        if(selectedSquare == null) {
            if(piece == Config.EMPTY || (pgnGraph.getFlags() & Config.PIECE_COLOR) != (piece & Config.PIECE_COLOR) ) {
                return false;
            }
            selectedSquare = clicked;
            selectedPiece = piece;
        } else {
            if((piece != Config.EMPTY && (pgnGraph.getFlags() & Config.PIECE_COLOR) == (piece & Config.PIECE_COLOR)) ) {
                selectedSquare = clicked;
                selectedPiece = piece;
            } else {
                piece = pgnGraph.getBoard().getPiece(selectedSquare);
                if(piece != selectedPiece) {
                    Log.e(DEBUG_TAG, String.format("2nd click, piece changed %d != %d", piece, selectedPiece));
                    return false;
                }
                Move newMove = new Move(pgnGraph.getBoard(), selectedSquare, clicked);
                if (!pgnGraph.validateUserMove(newMove)) {
                    return false;
                }
                selectedSquare = null;
                selectedPiece = 0;
                if((newMove.moveFlags & Config.FLAGS_PROMOTION) != 0 ) {
                    popups.promotionMove = newMove;
                    try {
                        popups.launchDialog(Popups.DialogType.Promotion);
                    } catch (Config.PGNException e) {
                        Log.e(DEBUG_TAG, e.toString(), e);
                    }
                } else {
                    try {
                        pgnGraph.addUserMove(newMove);
                    } catch (Config.PGNException e) {
                        Log.e(DEBUG_TAG, e.toString(), e);
                    }
                    if((newMove.moveFlags & Config.FLAGS_CHECKMATE) == 0) {
                        if ((newMove.moveFlags & Config.FLAGS_REPETITION) != 0) {
                            Toast.makeText(this, R.string.msg_3_fold_repetition, Toast.LENGTH_LONG).show();
                            if (newMove.comment == null) {
                                newMove.comment = getResources().getString(R.string.msg_3_fold_repetition);
                            }
                        } else if (pgnGraph.getBoard(newMove).getReversiblePlyNum() == 100) {
                            Toast.makeText(this, R.string.msg_50_reversible_moves, Toast.LENGTH_LONG).show();
                            if (newMove.comment == null) {
                                newMove.comment = getResources().getString(R.string.msg_50_reversible_moves);
                            }
                        }
                    }
                }
            }
        }
        chessPadView.invalidate();
        return true;
    }

    public boolean onFling(Square clicked) {
        Log.d(DEBUG_TAG, String.format("board onFling(%s)", clicked.toString()));
        return true;
    }

    public List<Pair<String, String>> getHeaders() {
        return pgnGraph.getPgn().getHeaders();
    }

    public String getTitleText() {
        return null;
    }

    public String getSetupErr(int errNum) {
        if(errNum >= 0 && errNum < setupErrs.length) {
            return setupErrs[errNum].replaceAll("^\\d+ ", "");
        }
        return "";
    }

    private void beforeAnimationStart() {
        chessPadView.enableNavigation(false);
        chessPadView.setButtonEnabled(ChessPad.Command.Stop.getValue(), true);
        chessPadView.setButtonEnabled(ChessPad.Command.Start.getValue(), true);
        chessPadView.setButtonEnabled(ChessPad.Command.Prev.getValue(), false);
        chessPadView.setButtonEnabled(ChessPad.Command.PrevVar.getValue(), false);
        chessPadView.setButtonEnabled(ChessPad.Command.Next.getValue(), false);
        chessPadView.setButtonEnabled(ChessPad.Command.NextVar.getValue(), false);
        chessPadView.setButtonEnabled(ChessPad.Command.End.getValue(), true);
        chessPadView.setButtonEnabled(ChessPad.Command.Delete.getValue(), false);
        chessPadView.enableCommentEdit(false);
    }

    private void afterAnimationEnd() {
        animationHandler = null;
        chessPadView.setButtonEnabled(ChessPad.Command.Stop.getValue(), false);
        chessPadView.enableNavigation(true);
        chessPadView.enableCommentEdit(true);
        chessPadView.invalidate();
    }

    boolean isFirstMove() {
        return pgnGraph.isInit();
    }

    boolean isLastMove() {
        return pgnGraph.isEnd();
    }

    protected void executeMenuCommand(MenuCommand menuCommand) throws Config.PGNException {
        Log.d(DEBUG_TAG, String.format("menu %s", menuCommand.toString()));
        switch (menuCommand) {
            case Load:
                popups.launchDialog(Popups.DialogType.Load);
                break;

            case About:
                popups.launchDialog(Popups.DialogType.ShowMessage);
                break;

            case Save:
                savePgnGraph(true, null);
                break;

            case Append:
                popups.launchDialog(Popups.DialogType.Append);
                break;

            case Setup:
                switchToSetup();
                break;

            case CancelSetup:
                cancelSetup();
                break;
        }

    }

    private void switchToSetup() throws Config.PGNException {
        setup = new Setup(pgnGraph, chessPadView);
        mode = Mode.Setup;
        chessPadView.redraw();
        setup.onValueChanged(null);     // refresh status
    }

    private void cancelSetup() {
        setup = null;
        mode = Mode.Game;
        chessPadView.redraw();
    }

    // after loading a new item or ending setup
    public void setPgnGraph(PgnItem.Item item ) throws Config.PGNException {
        if(pgnGraph.isModified()) {
            Log.d(DEBUG_TAG, String.format("setPgnGraph %s, old is modified", item));
            nextPgnItem = item;
            popups.launchDialog(Popups.DialogType.SaveModified);
        } else {
            if (mode == Mode.Setup) {
                pgnGraph = setup.toPgnGraph();
                cancelSetup();
            } else {
                if(item == null) {
                    item = nextPgnItem;
                }
                if(item != null) {
                    pgnGraph = new PgnGraph(item);
                    String parsingError = pgnGraph.getParsingError();
                    if(parsingError != null) {
                        popups.dlgMessage(Popups.DialogType.ShowMessage, parsingError, R.drawable.exclamation, Popups.DialogButton.Ok);
                    } else {
                        int parsingErrorNum = pgnGraph.getParsingErrorNum();
                        if(parsingErrorNum != 0) {
                            popups.dlgMessage(Popups.DialogType.ShowMessage, getSetupErr(parsingErrorNum), R.drawable.exclamation, Popups.DialogButton.Ok);
                        }
                    }
                }
                chessPadView.redraw();
            }
            nextPgnItem = null;
            selectedSquare = null;
            popups.promotionMove = null;
        }
    }

    public void savePgnGraph(final boolean updateMoves, final CPPostExecutor cpPostExecutor) throws Config.PGNException {
        new CPAsyncTask(chessPadView.cpProgressBar, new CPExecutor() {
            @Override
            public void onPostExecute() throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("savePgnGraph onPostExecute, thread %s", Thread.currentThread().getName()));
                if(cpPostExecutor != null) {
                    cpPostExecutor.onPostExecute();
                }
            }

            @Override
            public void onExecuteException(Config.PGNException e) throws Config.PGNException {
                Log.e(DEBUG_TAG, "savePgnGraph, onExecuteException, thread " + Thread.currentThread().getName(), e);
                popups.crashAlert(R.string.crash_cannot_save);
            }

            @Override
            public void doInBackground(final ProgressPublisher progressPublisher) throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("savePgnGraph start, thread %s", Thread.currentThread().getName()));
                pgnGraph.save(updateMoves, new PgnItem.OffsetHandler() {
                    @Override
                    public void setOffset(int offset) {
                        int totalLength = pgnGraph.getPgn().getParent().getLength();
                        Log.d(DEBUG_TAG, String.format("setOffset %d, total %d, thread %s", offset, totalLength, Thread.currentThread().getName()));
                        if(totalLength == 0) {
                            return;
                        }
                        int percent = offset * 100 / totalLength;
                        progressPublisher.publishProgress(percent);
                    }
                });
            }
        }).execute();
    }

    public Board getBoard() {
        return pgnGraph.getBoard();
    }

    private static class AnimationHandler extends Handler {
        int timeout;
        int timeoutDelta;
        TimeoutObserver observer;
        boolean stopAnimation;

        public AnimationHandler(int timeout, TimeoutObserver observer) {
            this.timeout = timeout;
            this.observer = observer;
            timeoutDelta = timeout / 4;
            observer.beforeAnimationStart();
            start();
        }

        public void start() {
            stopAnimation = false;
            Message m = obtainMessage();
            sendMessage(m);
        }

        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        public void stop() {
            stopAnimation = true;
        }

        @Override
        public void handleMessage( Message m ) {
                if(!stopAnimation && observer.handle()) {
                    m = obtainMessage();
                    sendMessageDelayed(m, timeout);
                } else {
                    observer.afterAnimationEnd();
                }
        }
    }

    interface TimeoutObserver {
        boolean handle();   // return true to continue
        void beforeAnimationStart();
        void afterAnimationEnd();
    }

    static class MenuItem {
        MenuCommand command;
        String menuString;
        boolean enabled;

        public MenuItem(MenuCommand command, String menuString, boolean enabled) {
            this.command = command;
            this.menuString = menuString;
            this.enabled = enabled;
        }

        public MenuItem(MenuCommand command, String menuString) {
            this(command, menuString, true);
        }

        @Override
        public String toString() {
            return menuString;
        }

        public MenuCommand getCommand() {
            return command;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}