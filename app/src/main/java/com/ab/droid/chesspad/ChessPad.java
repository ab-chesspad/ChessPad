package com.ab.droid.chesspad;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Toast;

import com.ab.pgn.BitStream;
import com.ab.pgn.Board;
import com.ab.pgn.Config;
import com.ab.pgn.Move;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnItem;
import com.ab.pgn.PgnTree;
import com.ab.pgn.Square;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;

/**
 * main activity
 * Created by Alexander Bootman on 8/20/16.
 */
public class ChessPad extends AppCompatActivity {
    protected final String DEBUG_TAG = this.getClass().getName();

    static final String
        STATUS_FILE_NAME = "ChessPad.status",
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
    protected PgnTree pgnTree;
    protected int animationTimeout = 1000;      // config?
    protected PgnItem currentPath = new PgnItem.Dir(null, "/");
    private Popups popups;
    protected Mode mode = Mode.Game;
    protected Setup setup;
    protected PgnItem.Item nextPgnItem;

    transient public String versionName;
    transient public int versionCode;         // igor - 7
    transient public Resources resources;
    transient public int timeoutDelta = animationTimeout / 4;
    transient private AnimationHandler animationHandler;
    transient protected ChessPadView chessPadView;
    transient private GestureDetectorCompat gestureDetector;
    transient protected File root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(DEBUG_TAG, "onCreate()");

        popups = new Popups(this);
        root = Environment.getExternalStorageDirectory();
        currentPath = new PgnItem.Dir(null, root.getAbsolutePath());
        PgnItem.setRoot(root);

        try {
            PackageInfo pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pinfo.versionName;
            versionCode = pinfo.versionCode;
        } catch (PackageManager.NameNotFoundException nnfe) {
            versionName = "0.0";
        }
        resources = getResources();
//        sample();       // initially create a sample pgn
    }

    private void init() {
        try {
            setup = null;
            chessPadView = new ChessPadView(this);
            sample();       // initially create a sample pgn
        } catch (Throwable t) {
//dlgMessage(DialogType.About, t.toString(), DIALOG_BUTTON_OK);
            Log.e(DEBUG_TAG, t.toString(), t);
        }
    }

    protected boolean isSaveable() {
        return pgnTree.isModified() && pgnTree.getPgn().getIndex() != -1;
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
        new Sample(this).createPgnTest();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(DEBUG_TAG, "onResume() 0");
        FileInputStream fis = null;

        try {
            init();

            try {
                fis = openFileInput(STATUS_FILE_NAME);
            } catch (FileNotFoundException e) {
                Log.d(DEBUG_TAG, "onResume() 1", e);
            }

            if (fis != null) {
                BitStream.Reader reader = new BitStream.Reader(fis);
                try {
                    unserialize(reader);
                } catch( IOException e) {
                    Log.w(DEBUG_TAG, e.getMessage());
                    fis = null;
                }
            }
        } catch (Throwable t) {
            Log.e(DEBUG_TAG, "onResume()", t);
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            popups.dlgMessage(Popups.DialogType.About, sw.toString(), R.drawable.exclamation, Popups.DialogButton.Ok);
            // start from scratch
            fis = null;
/*
            mode = Mode.Game;
            try {
                pgnTree = new PgnTree(new Board());
            } catch (IOException e) {
                Log.e(DEBUG_TAG, "onResume() 4", e);
            }
*/
        }

        if (fis == null) {
            try {
                mode = Mode.Game;
                pgnTree = new PgnTree(new Board());
            } catch (IOException e) {
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
        } catch (IOException e) {
            Log.d(DEBUG_TAG, "onPause() 1", e);
        }
        popups.dismiss();
    }

    public Mode getMode() {
        return mode;
    }

    private void serialize(BitStream.Writer writer) throws IOException {
        writer.write(versionCode, 4);
        writer.write(mode.getValue(), 2);
        currentPath.serialize(writer);
        pgnTree.serialize(writer);
        if(mode == Mode.Game) {
            writer.write(0, 1);
        } else {
            writer.write(1, 1);
            setup.serialize(writer);
        }
        popups.serialize(writer);
        writer.close();
    }

    private void unserialize(BitStream.Reader reader) throws IOException {
        if(versionCode != reader.read(4)) {
            throw new IOException("Old serialization ignored");
        }
        mode = Mode.value(reader.read(2));
        currentPath = PgnItem.unserialize(reader);
        pgnTree = new PgnTree(reader);
        if(reader.read(1) == 1) {
            setup = new Setup(reader);
        }
        popups.unserialize(reader);
    }

/*
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        chessPadView.redraw();
    }
*/

    @Override
    public boolean onTouchEvent(MotionEvent event){
        if(gestureDetector != null) {
            gestureDetector.onTouchEvent(event);
        }
        return super.onTouchEvent(event);
    }
    public void setGestureDetector(GestureDetectorCompat gestureDetector) {
        this.gestureDetector = gestureDetector;
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
        try {
            switch (command) {
                case Start:
                    if (animationHandler == null) {
                        pgnTree.toInit();
                        chessPadView.invalidate();
                    } else {
//                    animationHandler.increaseTimeout();
                        animationTimeout += timeoutDelta;
                        animationHandler.setTimeout(animationTimeout);
                    }
                    break;

                case Prev:
                    pgnTree.toPrev();
                    chessPadView.invalidate();
                    break;

                case PrevVar:
                    pgnTree.toPrevVar();
                    chessPadView.invalidate();
                    break;

                case Next:
                    List<Move> variations = pgnTree.getVariations();
                    if (variations == null) {
                        pgnTree.toNext();
                        chessPadView.invalidate();
                    } else {
                        Log.d(DEBUG_TAG, "variation");
                        popups.launchDialog(Popups.DialogType.Variations);
                    }
                    break;

                case Stop:
                    animationHandler.stop();
                    chessPadView.setButtonEnabled(ChessPad.Command.Stop.getValue(), false);
                    break;

                case NextVar:
                    if (pgnTree.getVariations() == null) {
                        animationHandler = new AnimationHandler(animationTimeout, new TimeoutObserver() {
                            @Override
                            public boolean handle() {
                                pgnTree.toNext();
                                chessPadView.invalidate();
                                return !pgnTree.isEnd() && pgnTree.getVariations() == null;
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
                    if (animationHandler == null) {
                        animationHandler = new AnimationHandler(animationTimeout, new TimeoutObserver() {
                            @Override
                            public boolean handle() {
                                pgnTree.toNext();
                                chessPadView.invalidate();
                                return !pgnTree.isEnd();
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
                    chessPadView.reverseBoard();
                    break;

                case ShowGlyphs:
                    popups.launchDialog(Popups.DialogType.Glyphs);
                    break;

                case Menu:
                    popups.launchDialog(Popups.DialogType.Menu);
                    break;

                case Delete:
                    popups.launchDialog(Popups.DialogType.DeleteYesNo);
                    break;

                case Append:
                    Log.d(DEBUG_TAG, String.format("Append %s", param.toString()));
                    break;

                case EditHeaders:
                    popups.launchDialog(Popups.DialogType.Headers);
                    break;

            }
        } catch (IOException e) {
            Log.e(DEBUG_TAG, "ChessPad.onButtonClick", e);
        }
    }

    void delete() throws IOException {
        if(isFirstMove()) {
            pgnTree.getPgn().setMoveText(null);
            pgnTree.getPgn().save();
            pgnTree = new PgnTree(new Board());
            currentPath = currentPath.getParent();
        } else {
            pgnTree.delCurrentMove();
        }
        chessPadView.invalidate();
    }

    public boolean onSquareClick(Square clicked) {
        Log.d(DEBUG_TAG, String.format("board onSquareClick (%s)", clicked.toString()));
        int piece = pgnTree.getBoard().getPiece(clicked);
        if(popups.selected == null) {
            if(piece == Config.EMPTY || (pgnTree.getFlags() & Config.PIECE_COLOR) != (piece & Config.PIECE_COLOR) ) {
                return false;
            }
            popups.selected = clicked;
        } else {
            if((piece != Config.EMPTY && (pgnTree.getFlags() & Config.PIECE_COLOR) == (piece & Config.PIECE_COLOR)) ) {
                popups.selected = clicked;
            } else {
                Move newMove = new Move(pgnTree.getBoard().getPiece(popups.selected), popups.selected, clicked);
                if (!pgnTree.validateUserMove(newMove)) {
                    return false;
                }
                popups.selected = null;
                if((newMove.moveFlags & Config.FLAGS_PROMOTION) != 0 ) {
                    popups.promotionMove = newMove;
                    try {
                        popups.launchDialog(Popups.DialogType.Promotion);
                    } catch (IOException e) {
                        Log.e(DEBUG_TAG, e.toString(), e);
                    }
                } else {
                    try {
                        pgnTree.addUserMove(newMove);
                    } catch (IOException e) {
                        Log.e(DEBUG_TAG, e.toString(), e);
                    }
                    if((newMove.snapshot.flags & Config.FLAGS_REPETITION) != 0) {
                        Toast.makeText(this, R.string.msg_3_fold_repetition, Toast.LENGTH_LONG).show();
                        if(newMove.comment == null) {
                            newMove.comment = getResources().getString(R.string.msg_3_fold_repetition);
                        }
                    } else if(newMove.snapshot.reversiblePlyNum == 100) {
                        Toast.makeText(this, R.string.msg_50_reversible_moves, Toast.LENGTH_LONG).show();
                        if(newMove.comment == null) {
                            newMove.comment = getResources().getString(R.string.msg_50_reversible_moves);
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
        return pgnTree.getPgn().getHeaders();
    }

    public String getTitleText() {
        return null;
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
    }

    private void afterAnimationEnd() {
        animationHandler = null;
        chessPadView.setButtonEnabled(ChessPad.Command.Stop.getValue(), false);
        chessPadView.enableNavigation(true);
        chessPadView.invalidate();
    }

    boolean isFirstMove() {
        return pgnTree.isInit();
    }

    boolean isLastMove() {
        return pgnTree.isEnd();
    }

    protected void executeMenuCommand(MenuCommand menuCommand) throws IOException {
        Log.d(DEBUG_TAG, String.format("menu %s", menuCommand.toString()));
        switch (menuCommand) {
            case Load:
                // todo: background
//                Toast.makeText(this, R.string.alert_msg_wait, Toast.LENGTH_LONG).show();
                popups.launchDialog(Popups.DialogType.Load);
                break;

            case About:
                popups.launchDialog(Popups.DialogType.About);
                break;

            case Save:
                pgnTree.save();
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

    private void switchToSetup() throws IOException {
        setup = new Setup(pgnTree, chessPadView);
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
    public void setPgnTree(PgnItem.Item item ) throws IOException {
        if(pgnTree.isModified()) {
            Log.d(DEBUG_TAG, String.format("setPgnTree %s, old is modified", item));
            nextPgnItem = item;
            popups.launchDialog(Popups.DialogType.SaveModified);
        } else {
            if (mode == Mode.Setup) {
                pgnTree = setup.toPgnTree();
                cancelSetup();
            } else {
                pgnTree = new PgnTree(item);
                chessPadView.redraw();
            }
        }
    }

    public Board getBoard() {
        return pgnTree.getBoard();
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