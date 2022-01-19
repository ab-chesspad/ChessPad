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

 * main activity
 * Created by Alexander Bootman on 8/20/16.
 */
package com.ab.droid.chesspad;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentCallbacks2;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.documentfile.provider.DocumentFile;

import com.ab.droid.chesspad.layout.ChessPadLayout;
import com.ab.droid.chesspad.layout.Metrics;
import com.ab.droid.chesspad.uci.Stockfish;
import com.ab.pgn.BitStream;
import com.ab.pgn.Board;
import com.ab.pgn.Book;
import com.ab.pgn.Config;
import com.ab.pgn.CpEventObserver;
import com.ab.pgn.io.CpFile;
import com.ab.pgn.Move;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.Setup;
import com.ab.pgn.Square;
import com.ab.pgn.dgtboard.DgtBoardPad;
import com.ab.pgn.uci.UCI;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class ChessPad extends AppCompatActivity implements BoardHolder, ComponentCallbacks2 {
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_DGT_BOARD = false;
    private static final boolean DEBUG_RESOURCES = true;

    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    private static final String
        STATUS_FILE_NAME = "status",
        CURRENT_PGN_NAME = "current-pgn",
        DEFAULT_DIRECTORY = "ChessPad",
        BOOK_ASSET_NAME = "book/combined.book",
        str_dummy = null;

    private static final int
        MAX_ENGINE_HINTS = 2,       // todo: prefs
        MAX_BOOK_HINTS = 5,         // todo: prefs
        PUZZLE_MOVE_DELAY_MSEC = 500,
        int_dummy = 0;

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
        Flip(++i),
        Analysis(++i),
        Delete(++i),    // last in GameView.imageButtons
        Menu(++i),
        ShowGlyphs(++i),
        ShowFiles(++i),
        Append(++i),
        Merge(++i),
        EditTags(++i),
        FlipBoard(++i),
        ;

        private final int value;
        private static final Command[] values = Command.values();

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
        Game(++j),
        Puzzle(++j),
        Setup(++j),
        DgtGame(++j),
        ;

        private final int value;
        private static final Mode[] values = Mode.values();

        Mode(int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }

        static Mode value(int v) {
            return values[v];
        }

        public static int total() {
            return values.length;
        }
    }

    private enum MenuCommand {
        Load,
        Puzzle,
        Merge,
        Save,
        Append,
        Setup,
        AnyMove,
        CancelSetup,
        About,
        StartDgtGame,
        StopDgtGame,
    }

    private final static int OPEN_DIRECTORY_REQUEST_CODE = 1;

    private static String defaultDirectory;

    public Mode mode = Mode.Game;
    public Setup setup;
    public DgtBoardPad dgtBoardPad;
    public Square selectedSquare;
    transient public String versionName;
    transient public ChessPadLayout chessPadLayout;
    public boolean doAnalysis;

    private PgnGraph pgnGraph;
    private int animationTimeout = 1000;      // config?
    private CpFile.CpParent currentPath = new CpFile.Dir(null, "/");
    private Popups popups;
    private Mode nextMode;
    private CpFile.PgnItem nextPgnItem;
    private int nextPgnItemIndex;
    private boolean flipped, oldFlipped;
    private int selectedPiece;
    private int versionCode;

    transient private final int timeoutDelta = animationTimeout / 4;
    transient private AnimationHandler animationHandler;
    transient private boolean unserializing = false;
    transient private boolean merging = false;
    transient private String[] setupErrs;
    transient private boolean pgngraphModified = false;     // compared to status
    transient private boolean navigationEnabled = true;

    private DgtBoardInterface dgtBoardInterface;
    private Handler bgMessageHandler;

    private boolean dgtBoardOpen = false;
    private boolean dgtBoardAccessible = DEBUG_DGT_BOARD;

    //    private MediaPlayer dingPlayer;
    private PuzzleData puzzleData;

    transient private String intentFileName = null;
    transient private boolean isDestroying = false;
    transient private UCI uci;
    transient private UCI.IncomingInfoMessage incomingInfoMessage;
    transient private Book openingBook;

    private static ChessPad instance;

    public static ChessPad getInstance() {
        return instance;
    }

    public static Context getContext() {
        return instance;
    }

    private RelativeLayout mainLayout;

    public RelativeLayout getMainLayout() {
        return mainLayout;
    }

    // Always followed by onStart()
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        Log.d(DEBUG_TAG, "onCreate()");

        if (DEBUG_RESOURCES) {
            try {
                Class.forName("dalvik.system.CloseGuard")
                        .getMethod("setEnabled", boolean.class)
                        .invoke(null, true);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        mainLayout = new RelativeLayout(this);
        RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        mainLayout.setBackgroundColor(Color.BLACK);
        this.setContentView(mainLayout, rlp);
        chessPadLayout = new ChessPadLayout(this);

        puzzleData = new PuzzleData(this);
        popups = new Popups(this);
        setupDirs();

        try {
            PackageInfo pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pinfo.versionName;
            versionCode = (int) PackageInfoCompat.getLongVersionCode(pinfo);
//            int targetSdkVersion = pinfo.applicationInfo.targetSdkVersion;
        } catch (PackageManager.NameNotFoundException nnfe) {
            versionName = "0.0";
        }
        setupErrs = getResources().getStringArray(R.array.setup_errs);
        init();
        bgMessageHandler = new CpHandler(this);
        dgtBoardInterface = new DgtBoardInterface(new DgtBoardInterface.StatusObserver() {
            @Override
            public void isOpen(boolean open) {
                dgtBoardOpen = open;
                Log.d(DEBUG_TAG, String.format("isOpen(%b)", dgtBoardOpen));
            }

            @Override
            public void isAccessible(boolean accessible) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
                dgtBoardAccessible = accessible;
                Log.d(DEBUG_TAG, String.format("isAccessible(%b)", dgtBoardAccessible));
                if (dgtBoardAccessible) {
                    try {
                        dgtBoardInterface.open();
                    } catch (IOException e) {
                        // happens on API 21 whith pre-connected device
                        e.printStackTrace();
                        Toast.makeText(ChessPad.this, e.getMessage() + "\n" + "Try to reconnect device", Toast.LENGTH_LONG).show();
                        cancelSetup();
                    }
                } else {
                    if (!isDestroying) {
                        cancelSetup();
                    }
                }
            }
        });
        dgtBoardPad = new DgtBoardPad(dgtBoardInterface, getDefaultDirectory(), getCpEventObserver());

        try (InputStream is = this.getAssets().open(BOOK_ASSET_NAME)) {
            int length = is.available();
            openingBook = new Book(is, length);
        } catch (IOException | Config.PGNException e) {
            e.printStackTrace();
            Toast.makeText(ChessPad.this, e.getMessage() + "\n" + "Opening book failed to open", Toast.LENGTH_LONG).show();
        }

        try {
            uci = new UCI(new UCI.EngineWatcher() {
                @Override
                public String getCurrentFen() {
                    if (openingBook.getMoves(getBoard()) == null) {
                        return getPgnGraph().getBoard().toFEN();
                    }
                    return null;
                }

                @Override
                public void engineOk() {
                    Log.d(DEBUG_TAG, "engineOk()");
                    uci.setOption("Hash", 64);
                    // do we need this?
//        uciEngine.setOption("SyzygyPath", "/storage/emulated/0/DroidFish/rtb");
//        uciEngine.setOption("SyzygyPath", "/storage/emulated/0/DroidFish/rtb");
                }

                @Override
                public void acceptAnalysis(UCI.IncomingInfoMessage incomingInfoMessage) {
                    if (DEBUG) {
                        Log.d(DEBUG_TAG, String.format("from uci: %s", incomingInfoMessage.toString()));
                    }
                    if (ChessPad.this.incomingInfoMessage != null &&
                            incomingInfoMessage.getMoves() != null &&
                            ChessPad.this.incomingInfoMessage.getMoves() != null &&
                            ChessPad.this.incomingInfoMessage.getMoves().startsWith(incomingInfoMessage.getMoves())) {
                        return;
                    }
                    ChessPad.this.incomingInfoMessage = incomingInfoMessage;
                    sendMessage(Config.MSG_REFRESH_SCREEN, null);
                }

                @Override
                public void reportError(String message) {
                    Log.e(DEBUG_TAG, message);
                }
            }, new Stockfish(this));
        } catch (IOException e) {
            Log.e(DEBUG_TAG, e.getMessage(), e);
        }
        Log.d(DEBUG_TAG, "engine launched");

        mode = Mode.Game;
        chessPadLayout.redraw();
        chessPadLayout.invalidate();
        intentFileName = getIntentFileName();
        if (intentFileName != null) {
            currentPath = CpFile.CpParent.fromPath(intentFileName);
            popups.dialogType = Popups.DialogType.Load;
        } else {
            restoreData();
        }
        Log.d(DEBUG_TAG, "onStart() done");
    }

    // on fresh start only
    private String getIntentFileName() {
        String intentFileName = null;
        Intent intent = getIntent();
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            Log.d(DEBUG_TAG, String.format("action %s", action));
        }
        Log.d(DEBUG_TAG, String.format("action %s", action));
        Uri data = intent.getData();
        if (data == null) {
            intentFileName = intent.getStringExtra(Intent.EXTRA_TEXT);
        } else {
            String scheme = data.getScheme();
            if ("file".equals(scheme)) {
                intentFileName = data.getEncodedPath();
                if (intentFileName != null)
                    intentFileName = Uri.decode(intentFileName);
            }
            if ((intentFileName == null) &&
                    ("content".equals(scheme) || "file".equals(scheme))) {
                String type = intent.getType();
                String ext;
                if ("application/zip".equals(type)) {
                    ext = CpFile.EXT_ZIP;
                } else {
                    ext = CpFile.EXT_PGN;
                }
                intentFileName = CpFile.getRootPath() + File.separator + DEFAULT_DIRECTORY + File.separator + "download" + ext;
                ContentResolver resolver = getContentResolver();
                try (InputStream is = resolver.openInputStream(data);
                     OutputStream os = new FileOutputStream(intentFileName)) {
                    CpFile.copy(is, os);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        Log.d(DEBUG_TAG, String.format("intentFileName %s", intentFileName));
        return intentFileName;
    }


    // Called when the activity will start interacting with the user
    // after onStart() or onPause()
    // @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB_MR1)
    @Override
    public void onResume() {
        super.onResume();
        Log.d(DEBUG_TAG, "onResume()");
        if (!dgtBoardAccessible) {
            try {
                dgtBoardInterface.checkPermissionAndOpen();
            } catch (IOException e) {
                Log.i(DEBUG_TAG, "Cannot open dgtBoardInterface");
                if (this.getMode() == Mode.DgtGame) {
                    cancelSetup();
                    return;
                }
            }
        }
        if (uci != null) {
            uci.doAnalysis(doAnalysis);
        }
        if (intentFileName == null) {
            chessPadLayout.invalidate();
        }
        intentFileName = null;
        popups.afterUnserialize();
        Log.d(DEBUG_TAG, "onResume() done");
    }

    // Followed by either onRestart() if this activity is coming back to interact with the user, or onDestroy() if this activity is going away
    // becomes killable
    @Override
    protected void onStop() {
        super.onStop();
        Log.d(DEBUG_TAG, "onStop()");
        if (merging) {
            return;     // skip serialization
        }
        if (uci != null) {
            uci.doAnalysis(false);    // no BG analysis
        }
        serializePgnGraph();
        serializeUI();
    }

    // The final call you receive before your activity is destroyed
    // becomes killable
    @Override
    protected void onDestroy() {
        Log.d(DEBUG_TAG, "onDestroy()");
        if (uci != null) {
            uci.shutDown();
        }
        isDestroying = true;
        popups.dismissDlg();
        dgtBoardInterface.close();
        dgtBoardPad.close();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(DEBUG_TAG, "onConfigurationChanged()");
        chessPadLayout.redraw();
        popups.relaunchDialog();
    }

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     * This is in theory. In practice the method is being called only with ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN.
     * Even when the program consumes huge amts of memory, it crashes with OOM because of fragmentation, never calling onTrimMemory.
     *
     * @param level the memory-related event that was raised.
     */
    @Override
    public void onTrimMemory(int level) {
        Log.d(DEBUG_TAG, "onTrimMemory() " + level);
        // Determine which lifecycle or system event was raised.
        switch (level) {
            case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:
                /*
                   Release any UI objects that currently hold memory.

                   "release your UI resources" is actually about things like caches.
                   You usually don't have to worry about managing views or UI components because the OS
                   already does that, and that's why there are all those callbacks for creating, starting,
                   pausing, stopping and destroying an activity.
                   The user interface has moved to the background.
                */
                break;

            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:

                /*
                   Release any memory that your app doesn't need to run.

                   The device is running low on memory while the app is running.
                   The event raised indicates the severity of the memory-related event.
                   If the event is TRIM_MEMORY_RUNNING_CRITICAL, then the system will
                   begin killing background processes.
                */

                break;

            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:

                /*
                   Release as much memory as the process can.
                   The app is on the LRU list and the system is running low on memory.
                   The event raised indicates where the app sits within the LRU list.
                   If the event is TRIM_MEMORY_COMPLETE, the process will be one of
                   the first to be terminated.
                */

                break;

            default:
                /*
                  Release any non-critical data structures.
                  The app received an unrecognized memory level value
                  from the system. Treat this as a generic low-memory message.
                */
                break;
        }
    }

    public Mode getMode() {
        return mode;
    }

    public CpFile.CpParent getCurrentPath() {
        return currentPath;
    }

    public void setCurrentPath(CpFile.CpParent currentPath) {
        this.currentPath = currentPath;
    }

    private void init() {
        try {
            setup = null;
            pgnGraph = new PgnGraph(new Board());
            sample();       // initially create a sample pgn
        } catch (Throwable t) {
            Log.e(DEBUG_TAG, t.getLocalizedMessage(), t);
        }
    }

    public static String getDefaultDirectory() {
        return DEFAULT_DIRECTORY;
    }

    private CpEventObserver getCpEventObserver() {
        return (msgId) -> {
            Message msg = bgMessageHandler.obtainMessage();
            msg.arg1 = msgId & 0x0ff;
            bgMessageHandler.sendMessage(msg);
        };
    }

    boolean isSaveable() {
        return pgnGraph.isModified() && pgnGraph.getPgnItemIndex() != -1;
    }

    public void setComment(String newComment) {
        if (!isUiFrozen()) {
            pgnGraph.setComment(newComment);
        }
    }

    public boolean isAnalysisOk() {
        return mode == ChessPad.Mode.Game || puzzleData.isDone();
    }

    public boolean isPuzzleMode() {
        return mode == Mode.Puzzle;
    }

    List<MenuItem> getMenuItems() {
        List<MenuItem> menuItems = new LinkedList<>();
        boolean enabled = true;
        if (mode == Mode.DgtGame) {
            enabled = false;
        }
        menuItems.add(new MenuItem(MenuCommand.Load, getResources().getString(R.string.menu_load), enabled));
        menuItems.add(new MenuItem(MenuCommand.Puzzle, getResources().getString(R.string.menu_puzzle), enabled));
        boolean enableMerge = enabled && mode == Mode.Game && pgnGraph.getInitBoard().equals(new Board());
        menuItems.add(new MenuItem(MenuCommand.Merge, getResources().getString(R.string.menu_merge), enableMerge));
        menuItems.add(new MenuItem(MenuCommand.Save, getResources().getString(R.string.menu_save), enabled && isSaveable()));
        menuItems.add(new MenuItem(MenuCommand.Append, getResources().getString(R.string.menu_append),
                enabled && (mode == Mode.Game || isPuzzleMode())));
        if (mode == Mode.Game) {
            menuItems.add(new MenuItem(MenuCommand.AnyMove, getResources().getString(R.string.menu_any_move), pgnGraph.isNullMoveValid()));
            menuItems.add(new MenuItem(MenuCommand.Setup, getResources().getString(R.string.menu_setup), true));
        } else if (mode == Mode.Puzzle) {
            menuItems.add(new MenuItem(MenuCommand.Setup, getResources().getString(R.string.menu_setup), true));
        } else if (mode == Mode.Setup) {
            menuItems.add(new MenuItem(MenuCommand.CancelSetup, getResources().getString(R.string.menu_cancel_setup), true));
        }
        if (dgtBoardAccessible) {
            if (mode == Mode.DgtGame) {
                menuItems.add(new MenuItem(MenuCommand.StopDgtGame, getResources().getString(R.string.menu_stop_dgt_game), true));
            } else {
                menuItems.add(new MenuItem(MenuCommand.StartDgtGame, getResources().getString(R.string.menu_start_dgt_game), enabled));
            }
        }

        menuItems.add(new MenuItem(MenuCommand.About, getResources().getString(R.string.menu_about)));
        return menuItems;
    }

    private void sample() {
        new Sample().createPgnTest();
    }

    private void serializePgnGraph() {
        Log.d(DEBUG_TAG, String.format("serializePgnGraph() pgngraphModified=%s", pgngraphModified));
        if (pgngraphModified || pgnGraph.isModified()) {
            try (BitStream.Writer writer = new BitStream.Writer(openFileOutput(CURRENT_PGN_NAME, Context.MODE_PRIVATE))) {
                pgnGraph.serializeGraph(writer, versionCode);
            } catch (Config.PGNException | IOException e) {
                Log.e(DEBUG_TAG, "serializePgnGraph", e);
            }
            Log.d(DEBUG_TAG, "serializePgnGraph() done");
        }
    }

    private boolean unserializePgnGraph(ProgressPublisher progressPublisher) {
        boolean res = false;
        Log.d(DEBUG_TAG, "unserializePgnGraph() start");
        try (BitStream.Reader reader = new BitStream.Reader(openFileInput(CURRENT_PGN_NAME))) {
            pgnGraph = new PgnGraph(reader, versionCode, new CpFile.ProgressObserver() {
                @Override
                public boolean setProgress(int progress) {
                    Log.d(DEBUG_TAG, String.format("loading, Offset %d%%, thread %s", progress, Thread.currentThread().getName()));
                    progressPublisher.publishProgress(progress);
                    return false;
                }
            });
            res = true;
        } catch (Config.PGNException | IOException e) {
            Log.w(DEBUG_TAG, "unserializePgnGraph()", e);
        }
        Log.d(DEBUG_TAG, "unserializePgnGraph() done");
        return res;
    }

    private void serializeUI() {
        Log.d(DEBUG_TAG, "serializeUI()");
        try (BitStream.Writer writer = new BitStream.Writer(openFileOutput(STATUS_FILE_NAME, Context.MODE_PRIVATE))) {
            writer.write(versionCode, 8);
            currentPath.serialize(writer);
            writer.write(mode.getValue(), 3);
            if (mode == Mode.Setup) {
                setup.serialize(writer);
            }
            dgtBoardPad.serialize(writer);
            if (nextPgnItem == null) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                nextPgnItem.serialize(writer);
                writer.write(nextPgnItemIndex, 24);
            }
            writer.write(flipped ? 1 : 0, 1);
            writer.write(oldFlipped ? 1 : 0, 1);
            if (selectedSquare == null) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                selectedSquare.serialize(writer);
            }
            writer.write(doAnalysis ? 1 : 0, 1);
            puzzleData.serialize(writer);
            popups.serialize(writer);
            pgnGraph.serializeMoveLine(writer, versionCode);
        } catch (Config.PGNException | IOException e) {
            Log.d(DEBUG_TAG, "serializeUI", e);
        }
    }

    private void unserializeUI() {
        Log.d(DEBUG_TAG, "unserializeUI() start");
        try (BitStream.Reader reader = new BitStream.Reader(openFileInput(STATUS_FILE_NAME))) {
            int oldVersionCode = reader.read(8);
            if (versionCode == oldVersionCode) {
                currentPath = (CpFile.CpParent)CpFile.unserialize(reader);
                mode = Mode.value(reader.read(3));
                if (mode == Mode.Setup) {
                    setup = new Setup(reader);
                } else if (mode == Mode.DgtGame) {
                    mode = Mode.Game;   // do not launch DGT board connection automatically
                }
                dgtBoardPad.unserialize(reader);
                if (reader.read(1) == 1) {
                    nextPgnItem = (CpFile.PgnItem)CpFile.unserialize(reader);
                    nextPgnItemIndex = reader.read(24);
                }
                flipped = reader.read(1) == 1;
                oldFlipped = reader.read(1) == 1;
                if (reader.read(1) == 1) {
                    selectedSquare = new Square(reader);
                }
                doAnalysis = reader.read(1) == 1;
                puzzleData.unserialize(reader);
                popups.unserialize(reader);
                if (selectedSquare != null) {
                    selectedPiece = getBoard().getPiece(selectedSquare);
                }
                pgnGraph.unserializeMoveLine(reader, versionCode);
            } else {
                Log.w(DEBUG_TAG, String.format("Old serialization %d ignored", oldVersionCode));
            }
        } catch (Throwable t) {
            Log.w(DEBUG_TAG, t.getLocalizedMessage(), t);
        }
        chessPadLayout.redraw();
        Log.d(DEBUG_TAG, "unserializeUI() done");
    }

    private void restoreData() {
        // unserialize data asynchronously, show progress bar
        // can be quite long, e.g. SicilianGranPrix-merged.pgn
        unserializing = true;
        chessPadLayout.enableCommentEdit(false);

        new CPAsyncTask(chessPadLayout, new CPExecutor() {
            @Override
            public void doInBackground(final ProgressPublisher progressPublisher) {
                Log.d(DEBUG_TAG, String.format("restoreData start, thread %s", Thread.currentThread().getName()));
                unserializePgnGraph(progressPublisher);
            }

            @Override
            public void onPostExecute() {
                Log.d(DEBUG_TAG, String.format("restoreData onPostExecute, thread %s", Thread.currentThread().getName()));
                unserializeUI();
                notifyUci();
                chessPadLayout.invalidate();
                unserializing = false;
                chessPadLayout.enableCommentEdit(true);
            }

            @Override
            public void onExecuteException(Config.PGNException e) {
                Log.e(DEBUG_TAG, "toNextGame, onExecuteException, thread " + Thread.currentThread().getName(), e);
                popups.crashAlert(R.string.crash_cannot_load);
            }

        }).execute();
    }

    private boolean isUiFrozen() {
        return unserializing || merging || animationHandler != null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            onButtonClick(Command.Menu);
            // return 'true' to prevent further propagation of the key event
            return true;
        }
        // let the system handle all other key events
        return super.onKeyDown(keyCode, event);
    }

    public void onButtonClick(Command command) {
        Board board = getBoard();
        if (DEBUG) {
            if (board != null) {
                Log.d(DEBUG_TAG, String.format("click %s\n%s", command.toString(), board.toString()));
            }
        }
        switch (command) {
            case Start:
                selectedSquare = null;
                if (!isUiFrozen()) {
                    if (pgnGraph.isInit()) {
                        Log.d(DEBUG_TAG, "to prev game");
                        toNextGame((CpFile.PgnFile) pgnGraph.getPgnItem().getParent(), pgnGraph.getPgnItemIndex() - 1);
                    } else {
                        pgnGraph.toInit();
                        notifyUci();
                        chessPadLayout.invalidate();
                    }
                } else if (animationHandler != null) {
//                    animationHandler.increaseTimeout();
                    animationTimeout += timeoutDelta;
                    animationHandler.setTimeout(animationTimeout);
                }
                break;

            case Prev:
                if (!isUiFrozen()) {
                    selectedSquare = null;
                    pgnGraph.toPrev();
                    notifyUci();
                    chessPadLayout.invalidate();
                }
                break;

            case PrevVar:
                if (!isUiFrozen()) {
                    selectedSquare = null;
                    pgnGraph.toPrevVar();
                    notifyUci();
                    chessPadLayout.invalidate();
                }
                break;

            case Next:
                if (!isUiFrozen()) {
                    if (!puzzleData.isDone() && puzzleData.solvedMoves <= pgnGraph.moveLine.size() - 1) {
                        puzzleData.setFailed();
                    }
                    selectedSquare = null;
                    List<Move> variations = pgnGraph.getVariations();
                    if (variations == null) {
                        pgnGraph.toNext();
                        notifyUci();
                        chessPadLayout.invalidate();
                    } else {
                        Log.d(DEBUG_TAG, "variation");
                        popups.launchDialog(Popups.DialogType.Variations);
                    }
                }
                break;

            case Stop:
                selectedSquare = null;
                animationHandler.stop();
                chessPadLayout.setButtonEnabled(ChessPad.Command.Stop, false);
                break;

            case NextVar:
                if (!isUiFrozen()) {
                    selectedSquare = null;
                    if (pgnGraph.getVariations() == null) {
                        animationHandler = new AnimationHandler(animationTimeout, new TimeoutObserver() {
                            @Override
                            public boolean handle() {
                                pgnGraph.toNext();
                                notifyUci();
                                chessPadLayout.invalidate();
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
                }
                break;

            case End:
                selectedSquare = null;
                if (!isUiFrozen()) {
                    CpFile.PgnItem currentItem = pgnGraph.getPgnItem();
                    if (isPuzzleMode()) {
                        if (puzzleData.started && puzzleData.isUnsolved()) {
                            puzzleData.setFailed();
                        }
                        stopAnalysis();
                        if (mode == Mode.Puzzle) {
                            toNextGame((CpFile.PgnFile) currentItem.getParent(), puzzleData.getNextIndex());
                        }
                    } else {    //  if (mode == Mode.Game) {
                        if (pgnGraph.isEnd()) {
                            Log.d(DEBUG_TAG, "to next game");
                            toNextGame((CpFile.PgnFile) currentItem.getParent(), pgnGraph.getPgnItemIndex() + 1);
                        } else {
                            animationHandler = new AnimationHandler(animationTimeout, new TimeoutObserver() {
                                @Override
                                public boolean handle() {
                                    pgnGraph.toNext();
                                    notifyUci();
                                    chessPadLayout.invalidate();
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
                        }
                    }
                } else if (animationHandler != null) {
                    animationTimeout -= timeoutDelta;
                    if (animationTimeout <= 0) {
                        animationTimeout = 1;
                    }
                    animationHandler.setTimeout(animationTimeout);
                }
                break;

            case Flip:
                if (mode == Mode.DgtGame) {
                    dgtBoardPad.setFlipped(!dgtBoardPad.isFlipped());
                } else {
                    flipped = !flipped;
                }
                chessPadLayout.invalidate();
                break;

            case FlipBoard:
                dgtBoardPad.turnBoard();
                chessPadLayout.invalidate();
                break;

            case ShowGlyphs:
                if (!isUiFrozen()) {
                    boolean okToSetGlyph = false;
                    if (mode == Mode.Game || isPuzzleMode()) {
                        okToSetGlyph = pgnGraph.okToSetGlyph();
                    } else if (mode == ChessPad.Mode.DgtGame) {
                        okToSetGlyph = dgtBoardPad.getPgnGraph().okToSetGlyph();
                    }
                    if (okToSetGlyph) {
                        popups.launchDialog(Popups.DialogType.Glyphs);
                    }
                }
                break;

            case Menu:
                if (!isUiFrozen()) {
                    popups.launchDialog(Popups.DialogType.Menu);
                }
                break;

            case Delete:
                if (!isUiFrozen()) {
                    if (getPgnGraph().isEnd() && !getPgnGraph().isInit()) {
                        getPgnGraph().delCurrentMove();
                        notifyUci();
                        chessPadLayout.invalidate();
                    } else {
                        popups.launchDialog(Popups.DialogType.DeleteYesNo);
                    }
                }
                break;

            case EditTags:
                if (!isUiFrozen()) {
                    popups.launchDialog(Popups.DialogType.Tags);
                }
                break;

            case Analysis:
                if (doAnalysis) {
                    incomingInfoMessage = null;
                }
                doAnalysis = !doAnalysis;
                uci.doAnalysis(doAnalysis);
                notifyUci();
                chessPadLayout.invalidate();
                break;

        }
    }

    void delete() {
        if (isFirstMove()) {
            pgnGraph.getPgnItem().setMoveText(null);
            savePgnGraph(false, () -> {
                pgnGraph = new PgnGraph(new Board());
                pgngraphModified = true;
                currentPath = currentPath.getParent();
                notifyUci();
                chessPadLayout.invalidate();
            });
        } else {
            pgnGraph.delCurrentMove();
            notifyUci();
            chessPadLayout.invalidate();
        }
    }

    public void completePromotion(Move promotionMove) {
        getPgnGraph().validateUserMove(promotionMove);  // validate check
        addMove(promotionMove);
    }

    public List<Pair<String, String>> getTags() {
        switch (mode) {
            case Game:
            case Puzzle:
                return pgnGraph.getPgnItem().cloneTags();

            case Setup:
                return setup.cloneTags();

            case DgtGame:
                return dgtBoardPad.cloneTags();

        }
        return null;    // should never happen
    }

    public void setTags(List<Pair<String, String>> tags) {
        switch (mode) {
            case Game:
            case Puzzle:
                pgnGraph.setTags(tags);
                break;

            case Setup:
                setup.setTags(tags);
                break;

            case DgtGame:
                dgtBoardPad.setTags(tags);
                break;
        }
    }

    public String getSetupErr(int errNum) {
        if (errNum >= 0 && errNum < setupErrs.length) {
            return setupErrs[errNum].replaceAll("^\\d+ ", "");
        }
        return "";
    }

    private void enableNavigation(boolean enable) {
        navigationEnabled = enable;
    }

    public boolean isNavigationEnabled() {
        return navigationEnabled;
    }

    private void beforeAnimationStart() {
        enableNavigation(false);
        chessPadLayout.setButtonEnabled(ChessPad.Command.Stop, true);
        chessPadLayout.setButtonEnabled(ChessPad.Command.Start, true);
        chessPadLayout.setButtonEnabled(ChessPad.Command.Prev, false);
        chessPadLayout.setButtonEnabled(ChessPad.Command.PrevVar, false);
        chessPadLayout.setButtonEnabled(ChessPad.Command.Next, false);
        chessPadLayout.setButtonEnabled(ChessPad.Command.NextVar, false);
        chessPadLayout.setButtonEnabled(ChessPad.Command.End, true);
        chessPadLayout.setButtonEnabled(ChessPad.Command.Delete, false);
        chessPadLayout.enableCommentEdit(false);
    }

    public int lastItemIndex() {
        return pgnGraph.getPgnItem().getParent().getTotalChildren() - 1;
    }

    private void afterAnimationEnd() {
        animationHandler = null;
        chessPadLayout.setButtonEnabled(ChessPad.Command.Stop, false);
        enableNavigation(true);
        chessPadLayout.enableCommentEdit(true);
        chessPadLayout.invalidate();
    }

    public boolean isFirstMove() {
        return pgnGraph.isInit();
    }

    public boolean isLastMove() {
        return pgnGraph.isEnd();
    }

    void executeMenuCommand(MenuCommand menuCommand) {
        Log.d(DEBUG_TAG, String.format("menu %s", menuCommand.toString()));
        switch (menuCommand) {
            case Load:
                popups.launchDialog(Popups.DialogType.Load);
                break;

            case Puzzle:
                popups.launchDialog(Popups.DialogType.Puzzle);
                break;

            case About:
                popups.launchDialog(Popups.DialogType.ShowMessage);
                break;

            case Save:
                savePgnGraph(true, () -> setPgnGraph(-1, null));
                break;

            case Append:
                popups.launchDialog(Popups.DialogType.Append);
                break;

            case AnyMove:
                Move anyMove = getBoard().newMove();
                anyMove.moveFlags |= Config.FLAGS_NULL_MOVE;
                try {
                    pgnGraph.addMove(anyMove);
                } catch (Config.PGNException e) {
                    Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
                }
                notifyUci();
                break;

            case Merge:
                popups.launchDialog(Popups.DialogType.Merge);
                break;

            case Setup:
                switchToSetup();
                break;

            case CancelSetup:
                cancelSetup();
                break;

            case StartDgtGame:
                startDgtMode();
                break;

            case StopDgtGame:
                stopDgtMode();
                break;

        }

    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void setupDirs() {
        // todo!!
/*
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, OPEN_DIRECTORY_REQUEST_CODE);
*/
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            int PERMISSION_REQUEST_CODE = 1;
/*
            if (ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            PERMISSION_REQUEST_CODE);
                }
            }
*/
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            PERMISSION_REQUEST_CODE);
                }
            }
        }

        File root = Environment.getExternalStorageDirectory();
//        File f = getContext().getFilesDir();
        File f1 = getContext().getExternalFilesDir(DEFAULT_DIRECTORY);
        boolean res = f1.mkdirs();  // false

/*
        File f2 = new File(getContext().getExternalFilesDir(null), "test");
        try {
            OutputStream os = new FileOutputStream(f2);
            byte[] data = "test string".getBytes(StandardCharsets.UTF_8);
            os.write(data);
            os.close();
        } catch (IOException e) {
            // Unable to create file, likely because external storage is
            // not currently mounted.
            Log.w("ExternalStorage", "Error writing " + f2, e);
        }
*/

        CpFile.setRoot(root.getAbsolutePath());
        currentPath = CpFile.CpParent.fromPath(DEFAULT_DIRECTORY);

//        if (!PermissionUtils.checkStoragePermission(this)) {
//            currentPath = null;
//            return;
//        }
//
//        ACTION_MANAGE_STORAGE

/*
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                );
        startActivityForResult(intent, OPEN_DIRECTORY_REQUEST_CODE);
*/

        File dir = CpFile.newFile(getDefaultDirectory());
        dir.mkdirs();
    }


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == OPEN_DIRECTORY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            this.getContentResolver().takePersistableUriPermission(
                    intent.getData(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
            loadDirectory(intent.getData());
        }
    }

    private void loadDirectory(Uri directoryUri) {
        DocumentFile documentsTree = DocumentFile.fromTreeUri(this, directoryUri);
        DocumentFile[] childDocuments = documentsTree.listFiles();
        for (DocumentFile childDocument : childDocuments) {
            Log.d(DEBUG_TAG, childDocument.toString());
        }

    }

    private void switchToSetup() {
        setup = new Setup(pgnGraph);
        mode = Mode.Setup;
        stopAnalysis();
        chessPadLayout.redraw();
    }

    private void cancelSetup() {
        Log.d(DEBUG_TAG, "cancelSetup(), to Mode.Game");
        setup = null;
        if (!isPuzzleMode()) {
            mode = Mode.Game;
        }
        chessPadLayout.redraw();
        postErrorMsg();
    }

    private void postErrorMsg() {
        if (pgnGraph.getParsingErrorNum() != 0) {
            String error = pgnGraph.getParsingError();
            if (error == null) {
                error = String.format("%s, %s", getSetupErr(pgnGraph.getParsingErrorNum()), getResources().getString(R.string.err_moves_ignored));
            }
            Toast.makeText(ChessPad.this, error, Toast.LENGTH_LONG).show();
        }
    }

    private void startDgtMode() {
        stopAnalysis();
        nextMode = Mode.DgtGame;
        if (pgnGraph.isModified()) {
            popups.launchDialog(Popups.DialogType.SaveModified);
        } else {
            openDgtMode();
        }
    }

    private void openDgtMode() {
        Log.d(DEBUG_TAG, "openDgtMode()");
        mode = Mode.DgtGame;
        nextMode = null;
        oldFlipped = flipped;
        dgtBoardPad.resume();
        chessPadLayout.redraw();
    }

    private void stopDgtMode() {
        mode = Mode.Game;
        flipped = oldFlipped;
        dgtBoardPad.stop();
        chessPadLayout.redraw();
    }

    // after loading a new item or ending setup
    // in Setup on 'setup ok' (null)
    // after ending Append (null)
    // after SaveModified?, savePgnGraph, (null)
    // after SaveModified, negative, (null)
    // after load, new item
    public void setPgnGraph(int index, CpFile.PgnItem item) throws Config.PGNException {
        if (pgnGraph.isModified()) {
            Log.d(DEBUG_TAG, String.format(Locale.US, "setPgnGraph %s, old is modified", item));
            nextPgnItem = item;
            nextPgnItemIndex = index;
            popups.launchDialog(Popups.DialogType.SaveModified);
        } else {
            if (item == null) {
                if (mode == Mode.Game || mode == Mode.Puzzle) {
                    item = nextPgnItem;
                    index = nextPgnItemIndex;
                }
            }

            boolean asyncLoad = false;
            if (item == null && setup != null) {
                pgnGraph = setup.toPgnGraph();
                pgngraphModified = true;
            } else if (item != null) {
                Log.d(DEBUG_TAG, String.format("setPgnGraph loadPgnGraph %d. %s", index, item));
                loadPgnGraph(index, item);
                asyncLoad = true;
            }

            if (nextMode == Mode.DgtGame) {
                openDgtMode();
            } else {
                if (!asyncLoad) {
                    puzzleData.setPgn(null);
                }
                nextPgnItem = null;
                selectedSquare = null;
                incomingInfoMessage = null;
                popups.promotionMove = null;
                cancelSetup();
            }
        }
        popups.editTags = null;
        if (mode == Mode.Game) {
            notifyUci();
        } else {
            stopAnalysis();
        }
    }

    private void loadPgnGraph(int index, CpFile.PgnItem pgnItem) {
        new CPAsyncTask(chessPadLayout, new CPExecutor() {
            @Override
            public void doInBackground(final ProgressPublisher progressPublisher) throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("loadPgnGraph start, thread %s", Thread.currentThread().getName()));
                pgnGraph = new PgnGraph(index, pgnItem, progress -> {
                    Log.d(DEBUG_TAG, String.format("loadPgnGraph %s, progress %d%%", pgnItem.toString(), progress));
                    progressPublisher.publishProgress(progress);
                    return false;
                });
            }

            @Override
            public void onPostExecute() {
                Log.d(DEBUG_TAG, String.format("loadPgnGraph onPostExecute, thread %s", Thread.currentThread().getName()));
                pgngraphModified = true;
                chessPadLayout.showProgressBar(false);
                chessPadLayout.invalidate();
                if (mode == Mode.Game) {
                    notifyUci();
                } else {
                    stopAnalysis();
                    pgnGraph.setPuzzleMode();
                    pgnGraph.toInit();
                    puzzleData.newPuzzle();
                    Log.d(DEBUG_TAG, String.format("pgnGraph.toInit(), moveline %s", pgnGraph.moveLine.size()));
                    flipped = (pgnGraph.getBoard().getFlags() & Config.BLACK) != 0;
                    chessPadLayout.invalidate();
                }
            }

            @Override
            public void onExecuteException(Config.PGNException e) {
                Log.e(DEBUG_TAG, "loadPgnGraph, onExecuteException, thread " + Thread.currentThread().getName(), e);
                popups.crashAlert(R.string.crash_cannot_load);
            }
        }).execute();
    }

    void notifyUci() {
        if (uci != null) {
//            Log.d(DEBUG_TAG, String.format("notifyUci() %s", getPgnGraph().getBoard().toFEN()));
            uci.abortCurrentAnalisys();
            if (doAnalysis) {
                UCI.IncomingInfoMessage msg = fromBook();
                if (msg == null) {
                    uci.doAnalysis(doAnalysis);
                } else {
                    incomingInfoMessage = msg;
                }
            }
        }
    }

    private UCI.IncomingInfoMessage fromBook() {
        List<Move> bookMoves;
        if ((bookMoves = openingBook.getMoves(getBoard())) == null) {
            return null;
        }

        UCI.IncomingInfoMessage infoMessage = new UCI.IncomingInfoMessage();
        if (pgnGraph.moveLine.size() > 1) {
            // find current opening name:
            Move prevMove = pgnGraph.moveLine.get(pgnGraph.moveLine.size() - 2);
            Board prevBoard = openingBook.getBoard(prevMove);
            if (prevBoard != null) {
                // prevBoard == null if we step out of the opening book and then get a book position
                Move lastMove = pgnGraph.getCurrentMove();
                Move bookMove = prevBoard.getMove();
                while (bookMove != null) {
                    if (lastMove.isSameAs(bookMove)) {
                        break;
                    }
                    bookMove = bookMove.variation;
                }
                if (bookMove != null) {     // sanity check
                    infoMessage.info = openingBook.getComment(bookMove.comment);
                }
            }
        }
        if (infoMessage.info == null) {
            infoMessage.info = "";
        }

        Collections.shuffle(bookMoves);
        StringBuilder sb = new StringBuilder();
        String sep = "";
        for (Move move : bookMoves) {
            sb.append(sep).append(move.getFrom().toString()).append(move.getTo().toString());
            sep = " ";
        }
        infoMessage.moves = new String(sb);
        return infoMessage;
    }

    private void stopAnalysis() {
        if (uci != null) {
            doAnalysis = false;
            uci.doAnalysis(doAnalysis);
            incomingInfoMessage = null;
        }
    }

    public String getAnalysisMessage()  {
        if (incomingInfoMessage == null) {
            return "";
        }
        return incomingInfoMessage.toString();
    }

    public String getHints()  {
        if (incomingInfoMessage == null || incomingInfoMessage.getMoves() == null) {
            return null;
        }
        String[] tokens = incomingInfoMessage.getMoves().split("\\s+");

        int totalHints = MAX_ENGINE_HINTS;
        if (incomingInfoMessage.info != null) {
            totalHints = MAX_BOOK_HINTS;
        }
        StringBuilder sb = new StringBuilder();
        String sep = "";
        i = 0;
        do {
            sb.append(sep).append(tokens[i++]);
            sep = " ";
        } while( i < tokens.length && i < totalHints);
        return new String(sb);
    }

    void setPuzzles() {
        Log.d(DEBUG_TAG, String.format("set for puzzles %s, totalChildren=%s", currentPath.getAbsolutePath(), currentPath.getTotalChildren()));
        puzzleData.setPgn((CpFile.PgnFile) currentPath);
        mode = Mode.Puzzle;
        doAnalysis = false;
        toNextGame((CpFile.PgnFile)currentPath, puzzleData.getNextIndex());
    }

    private void toNextGame(final CpFile.PgnFile pgnFile, final int nextIndex) {
        final CpFile.PgnItem[] items = {null};

        new CPAsyncTask(chessPadLayout, new CPExecutor() {
            @Override
            public void doInBackground(final ProgressPublisher progressPublisher) throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("toNextGame start, thread %s", Thread.currentThread().getName()));

                items[0] = pgnFile.getPgnItem(nextIndex, (progress) -> {
                    if (DEBUG) {
                        Log.d(DEBUG_TAG, String.format("toNextGame, Offset %d%%", progress));
                    }
                    progressPublisher.publishProgress(progress);
                    return false;
                });
            }

            @Override
            public void onPostExecute() throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("toNextGame onPostExecute, thread %s", Thread.currentThread().getName()));
                if (nextIndex >= pgnFile.getTotalChildren()) {
                    // That means that items[0] is lastEntry. pgnFile.getPgnItem brings back all info only for the real requested index, so retry.
                    puzzleData.setTotalPuzzles();
                    toNextGame(pgnFile, puzzleData.getNextIndex());
                } else {
                    setPgnGraph(nextIndex, items[0]);
                }
            }

            @Override
            public void onExecuteException(Config.PGNException e) {
                Log.e(DEBUG_TAG, "toNextGame, onExecuteException, thread " + Thread.currentThread().getName(), e);
                popups.crashAlert(R.string.crash_cannot_load);
            }

        }).execute();
    }

    public void mergePgnGraph(final Popups.MergeData mergeData, final CPPostExecutor cpPostExecutor) {
        merging = true;
        chessPadLayout.enableCommentEdit(false);
        new CPAsyncTask(chessPadLayout, new CPExecutor() {
            @Override
            public void doInBackground(final ProgressPublisher progressPublisher) {
                Log.d(DEBUG_TAG, String.format("mergePgnGraph start, thread %s", Thread.currentThread().getName()));
                try {
                    pgnGraph.merge(mergeData, (progress) -> {
                        if (DEBUG) {
                            Log.d(DEBUG_TAG, String.format("Offset %d%%, thread %s", progress, Thread.currentThread().getName()));
                        }
                        progressPublisher.publishProgress(progress);
                        return false;
                    });
                } catch (Config.PGNException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onPostExecute() throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("mergePgnGraph onPostExecute, thread %s", Thread.currentThread().getName()));
                if (cpPostExecutor != null) {
                    cpPostExecutor.onPostExecute();
                }
                merging = false;
                chessPadLayout.enableCommentEdit(true);
                pgngraphModified = true;
                String msg = String.format(getResources().getString(R.string.msg_merge_count), mergeData.merged);
                Toast.makeText(ChessPad.this, msg, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onExecuteException(Config.PGNException e) {
                merging = false;
                chessPadLayout.enableCommentEdit(true);
                Log.e(DEBUG_TAG, "mergePgnGraph, onExecuteException, thread " + Thread.currentThread().getName(), e);
                popups.crashAlert(R.string.crash_cannot_save);
            }
        }).execute();
    }

    void sendMessage(int msgId, String message) {
        Message msg = bgMessageHandler.obtainMessage();
        msg.arg1 = msgId;
        msg.obj = message;
        bgMessageHandler.sendMessage(msg);
    }

    public void savePgnGraph(final boolean updateMoves, final CPPostExecutor cpPostExecutor) {
        new CPAsyncTask(chessPadLayout, new CPExecutor() {
            @Override
            public void doInBackground(final ProgressPublisher progressPublisher) throws Config.PGNException {
                long start = new Date().getTime();
                if (DEBUG) {
                    Log.d(DEBUG_TAG, String.format("savePgnGraph start, thread %s", Thread.currentThread().getName()));
                }
                pgnGraph.save(updateMoves, (progress) -> {
                    if (true) {
                        Log.d(DEBUG_TAG, String.format("Offset %d%%, thread %s", progress, Thread.currentThread().getName()));
                    }
                    progressPublisher.publishProgress(progress);
                    return false;
                });
                if (true) {
                    long end = new Date().getTime();
                    long duration = end - start;
                    Log.d(DEBUG_TAG, String.format("savePgnGraph end %s, thread %s", duration, Thread.currentThread().getName()));
                }
            }

            @Override
            public void onPostExecute() throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("savePgnGraph onPostExecute, thread %s", Thread.currentThread().getName()));
                PgnGraph pgnGraph = ChessPad.this.getPgnGraph();
                if (pgnGraph.getPgnItemIndex() == -1) {
                    CpFile.PgnFile pgnFile = (CpFile.PgnFile) pgnGraph.getPgnItem().getParent();
                    int totalChildren;
                    if ((totalChildren = pgnFile.getTotalChildren()) > 0) {
                        pgnGraph.setPgnItemIndex(totalChildren - 1);
                    }
                }
                if (cpPostExecutor != null) {
                    cpPostExecutor.onPostExecute();
                }
            }

            @Override
            public void onExecuteException(Config.PGNException e) {
                Log.e(DEBUG_TAG, "savePgnGraph, onExecuteException, thread " + Thread.currentThread().getName(), e);
                popups.crashAlert(R.string.crash_cannot_save);
            }
        }).execute();
    }

    public PgnGraph getPgnGraph() {
        PgnGraph pgnGraph;
        switch(mode) {
            case Game:
            case Setup:
            case Puzzle:
                pgnGraph = this.pgnGraph;
                break;

            case DgtGame:
                pgnGraph = dgtBoardPad.getPgnGraph();
                break;

            default:
                throw  new RuntimeException("should not be here");
        }
        return pgnGraph;
    }

    // BoardHolder implementation:
    @Override
    public Board getBoard() {
        return pgnGraph.getBoard();
    }

    @Override
    public boolean onSquareClick(Square clicked) {
        if (isUiFrozen()) {
            return false;
        }
        PgnGraph pgnGraph = getPgnGraph();
        if (DEBUG) {
            Log.d(DEBUG_TAG, String.format("board onSquareClick (%s)\n%s", clicked.toString(), pgnGraph.getBoard().toString()));
        }
        int piece = pgnGraph.getBoard().getPiece(clicked);
        if (selectedSquare == null) {
            if (piece == Config.EMPTY || (pgnGraph.getFlags() & Config.PIECE_COLOR) != (piece & Config.PIECE_COLOR) ) {
                return false;
            }
            selectedSquare = clicked;
            selectedPiece = piece;
        } else {
            if ((piece != Config.EMPTY && (pgnGraph.getFlags() & Config.PIECE_COLOR) == (piece & Config.PIECE_COLOR)) ) {
                selectedSquare = clicked;
                selectedPiece = piece;
            } else {
                piece = pgnGraph.getBoard().getPiece(selectedSquare);
                if (piece != selectedPiece) {
                    Log.d(DEBUG_TAG, String.format("2nd click, piece changed %d != %d", piece, selectedPiece));
                    return false;
                }
                Move newMove = new Move(pgnGraph.getBoard(), selectedSquare, clicked);
                if (!pgnGraph.validateUserMove(newMove)) {
                    return false;
                }
                selectedSquare = null;
                selectedPiece = 0;
                if (newMove.isPromotion()) {
                    popups.promotionMove = newMove;
                    popups.launchDialog(Popups.DialogType.Promotion);
                } else {
                    addMove(newMove);
                    if (!isPuzzleMode()) {
                        if ((newMove.moveFlags & Config.FLAGS_CHECKMATE) == 0) {
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
                            } else if ((newMove.moveFlags & Config.FLAGS_STALEMATE) != 0) {
                                Toast.makeText(this, R.string.msg_stalemate, Toast.LENGTH_LONG).show();
                                if (newMove.comment == null) {
                                    newMove.comment = getResources().getString(R.string.msg_stalemate);
                                }
                            }
                        }
                    }
                }
            }
        }
        chessPadLayout.invalidate();
        return true;
    }

    private void addMove(Move newMove) {
        PgnGraph pgnGraph = getPgnGraph();
        try {
            if (isPuzzleMode()) {
                puzzleData.started = true;
                Move move = pgnGraph.getBoard().getMove();
                boolean ok = false;
                while (move != null) {
                    if ((ok = newMove.isSameAs(move))) {
                        break;
                    }
                    move = move.variation;
                }
                if (!ok) {
                    pgnGraph.addUserMove(newMove);
                    ok = (newMove.moveFlags & Config.FLAGS_CHECKMATE) != 0; // winning move can be missed in the puzzle
                }

                if (ok) {
                    pgnGraph.toVariation(move);
                    puzzleData.solvedMoves = pgnGraph.moveLine.size();  // this move and the opponent's move
                    chessPadLayout.invalidate();
                    Move lastMove = pgnGraph.getBoard().getMove();
                    if (!puzzleData.isDone()) {
                        if (lastMove == null) {
                            puzzleData.setSuccess();
                        } else if (puzzleData.isUnsolved()) {
                            Toast.makeText(this, R.string.msg_puzzle_keep_solving, Toast.LENGTH_SHORT).show();
                        }
                        if (lastMove != null) {
                            sendMessage(Config.MSG_PUZZLE_ADVANCE, null);
                        }
                    }
                } else {
                    if (!puzzleData.isDone()) {
                        puzzleData.setFailed();
                        chessPadLayout.invalidate();
                        sendMessage(Config.MSG_PUZZLE_TAKE_BACK, null);
                        Toast.makeText(this, R.string.err_not_solution, Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                pgnGraph.addUserMove(newMove);
            }
            notifyUci();
        } catch (Config.PGNException e) {
            Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
        }
    }

    @Override
    public void onFling(Square clicked) {
        if (DEBUG) {
            Log.d(DEBUG_TAG, String.format("board onFling(%s)", clicked.toString()));
        }
    }

    @Override
    public int[] getBGResources() {
        return new int[] {R.drawable.bsquare, R.drawable.wsquare};
    }

    @Override
    public int getBoardViewSize() {
        return Metrics.boardViewSize;
    }

    public boolean isFlipped() {
        return flipped;
    }

    // GUI access
    private static class AnimationHandler extends Handler {
        int timeout;
        final int timeoutDelta;
        final TimeoutObserver observer;
        boolean stopAnimation;

        AnimationHandler(int timeout, TimeoutObserver observer) {
            this.timeout = timeout;
            this.observer = observer;
            timeoutDelta = timeout / 4;
            observer.beforeAnimationStart();
            start();
        }

        void start() {
            stopAnimation = false;
            Message m = obtainMessage();
            sendMessage(m);
        }

        void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        void stop() {
            stopAnimation = true;
        }

        @Override
        public void handleMessage( Message m ) {
            if (!stopAnimation && observer.handle()) {
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
        final MenuCommand command;
        final String menuString;
        final boolean enabled;

        MenuItem(MenuCommand command, String menuString, boolean enabled) {
            this.command = command;
            this.menuString = menuString;
            this.enabled = enabled;
        }

        MenuItem(MenuCommand command, String menuString) {
            this(command, menuString, true);
        }

        @Override
        public String toString() {
            return menuString;
        }

        public MenuCommand getCommand() {
            return command;
        }

        boolean isEnabled() {
            return enabled;
        }
    }

    static class CpHandler extends Handler {
        private final WeakReference<ChessPad> mActivity;

        CpHandler(ChessPad activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            ChessPad activity = mActivity.get();
            if (activity == null) {
                return;
            }
            int msgId = msg.arg1;
            if (DEBUG) {
                Log.d(activity.DEBUG_TAG, String.format("message 0x%s", Integer.toHexString(msgId)));
            }
            if (msgId == Config.MSG_NOTIFICATION) {
                Toast.makeText(activity, msg.obj.toString(), Toast.LENGTH_LONG).show();
            } else if (msgId == Config.MSG_PUZZLE_ADVANCE) {
                try {
                    Thread.sleep(PUZZLE_MOVE_DELAY_MSEC);
                } catch (InterruptedException e) {
                    // ignore
                }
                activity.pgnGraph.toNext();
                activity.notifyUci();
                activity.chessPadLayout.invalidate();
            } else if (msgId == Config.MSG_PUZZLE_TAKE_BACK) {
                try {
                    Thread.sleep(PUZZLE_MOVE_DELAY_MSEC);
                } catch (InterruptedException e) {
                    // ignore
                }
                activity.pgnGraph.delCurrentMove();
                activity.notifyUci();
                activity.chessPadLayout.invalidate();
            } else if (msgId == Config.MSG_DGT_BOARD_SETUP_MESS || msgId == Config.MSG_DGT_BOARD_GAME) {
                activity.chessPadLayout.redraw();
            } else if ( msgId <= Config.MSG_DGT_BOARD_LAST ) {
                activity.chessPadLayout.invalidate();
            } else {
                activity.chessPadLayout.invalidate();
            }
        }
    }
}