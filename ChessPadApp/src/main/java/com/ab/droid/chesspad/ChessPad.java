package com.ab.droid.chesspad;

import android.annotation.SuppressLint;
import android.content.ComponentCallbacks2;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.pm.PackageInfoCompat;

import com.ab.droid.engine.stockfish.StockFishEngine;
import com.ab.pgn.BitStream;
import com.ab.pgn.Board;
import com.ab.pgn.Book;
import com.ab.pgn.Config;
import com.ab.pgn.CpEventObserver;
import com.ab.pgn.CpFile;
import com.ab.pgn.Move;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.Setup;
import com.ab.pgn.Square;
import com.ab.pgn.Util;
import com.ab.pgn.dgtboard.DgtBoardPad;
import com.ab.pgn.fics.FicsPad;
import com.ab.pgn.fics.chat.InboundMessage;
import com.ab.pgn.lichess.LichessPad;

import org.petero.droidfish.engine.UCIEngine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * main activity
 * Created by Alexander Bootman on 8/20/16.
 */
public class ChessPad extends AppCompatActivity implements BoardHolder, ComponentCallbacks2 {
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_DGT_BOARD = false;
    private static final boolean SAVE_UPDATED_PUZZLES = false;

    private static final boolean NEW_FEATURE_LICHESS = false;  // Lichess code is not fully tested, need to fix crash report from 4/11/2020 21:45
    private static final boolean NEW_FEATURE_FICS = false;     // fics software is buggy and seems not too popular
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    private static final String
        STATUS_FILE_NAME = ".ChessPad.state",
        MOVELINE_FILE_NAME = ".ChessPad.moveline",
        CURRENT_PGN_NAME = ".ChessPad.current" + CpFile.EXT_PGN,
        DEFAULT_DIRECTORY = "ChessPad",
        PUZZLE_INFO_EXT = ".info",
        PUZZLE_UPDATE_FILENAMEPUZZLE_UPDATE_FILENAME = "-upd" + CpFile.EXT_PGN,
        PUZZLE_TAG = "Puzzle",
        BOOK_ASSET_NAME = "book/combined.book",
        str_dummy = null;

    private static final int
        MAX_ENGINE_HINTS = 2,       // todo: prefs
        MAX_BOOK_HINTS = 5,         // todo: prefs
        MIN_PUZZLE_TEXT_LENGTH = 40,    // or slightly less? [FEN "8/8/8/8/K7/8/8/7k w - - 0 1"]
        PUZZLE_MOVE_DELAY_MSEC = 500,
        int_dummy = 0;

    private static int i = -1;

    enum Command {
        None(++i),
        Start(++i),
        PrevVar(++i),
        Prev(++i),
        Stop(++i),
        Next(++i),
        NextVar(++i),
        End(++i),
        Flip(++i),
        Delete(++i),    // last in GameView.imageButtons
        Menu(++i),
        ShowGlyphs(++i),
        ShowFiles(++i),
        Append(++i),
        Merge(++i),
        EditTags(++i),
        FlipBoard(++i),

        Send2Fics(++i),
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
        LichessPuzzle(++j),

        FicsConnection(++j),
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
        Analysis,
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
        LichessPuzzle,
        LichessLogin,
        LichessLogout,

        ConnectToFics,
        CloseFicsConnection,
        FicsMate,
        FicsTactics,
        FicsStudy,
        FicsEndgame,
    }

    private PgnGraph pgnGraph;
    private int animationTimeout = 1000;      // config?
    CpFile currentPath = new CpFile.Dir(null, "/");
    private Popups popups;
    Mode mode = Mode.Game;
    private Mode nextMode;
    Setup setup;
    DgtBoardPad dgtBoardPad;
    private CpFile.Item nextPgnFile;
    private boolean flipped, oldFlipped;
    Square selectedSquare;
    private int selectedPiece;
    private int versionCode;

    transient public String versionName;
    transient private final int timeoutDelta = animationTimeout / 4;
    transient private AnimationHandler animationHandler;
    transient private boolean unserializing = false;
    transient private boolean merging = false;
    transient ChessPadView chessPadView;
    transient private String[] setupErrs;
    transient private boolean freshStart = true;
    transient private boolean pgngraphModified = false;     // compared to status

    private DgtBoardInterface dgtBoardInterface;
    private Handler bgMessageHandler;

    private boolean dgtBoardOpen = false;
    private boolean dgtBoardAccessible = DEBUG_DGT_BOARD;

    FicsPad ficsPad;
    private final FicsPad.FicsSettings ficsSettings = new FicsPad.FicsSettings();
    LichessPad lichessPad;
    LichessPad.LichessSettings lichessSettings = new LichessPad.LichessSettings();

    transient private AlertDialog msgPopup;
    private String ficsCommand;
    private MediaPlayer dingPlayer;

    private boolean savePuzzle = false;
    private PuzzleData puzzleData = new PuzzleData();

    transient private String intentFileName = null;
    transient private boolean isDestroying = false;
    private static String defaultDirectory;
    transient private UCIEngine uciEngine;
    transient private UCIEngine.IncomingInfoMessage uciengineInfoMessage;
    boolean doAnalysis;
    transient private Book openingBook;

    private static ChessPad instance;
    public static ChessPad getInstance() {
        return instance;
    }
    public static Context getContext() {
        return instance;
    }

    private RelativeLayout mainRelativeLayout;

    public RelativeLayout getMainRelativeLayout() {
        return mainRelativeLayout;
    }

    // Always followed by onStart()
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        Log.d(DEBUG_TAG, "onCreate()");
        mainRelativeLayout = new RelativeLayout(this);
        RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        mainRelativeLayout.setBackgroundColor(Color.BLACK);
        this.setContentView(mainRelativeLayout, rlp);

        popups = new Popups(this);
        // let root be /
        File root = Environment.getExternalStorageDirectory();
        CpFile.setRoot(root);
        File dir = new File(getDefaultDirectory());
        dir.mkdirs();
        currentPath = new CpFile.Dir(DEFAULT_DIRECTORY);

        try {
            PackageInfo pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pinfo.versionName;
            versionCode = (int)PackageInfoCompat.getLongVersionCode(pinfo);
        } catch (PackageManager.NameNotFoundException nnfe) {
            versionName = "0.0";
        }
        setupErrs = getResources().getStringArray(R.array.setup_errs);
        init();
        freshStart = true;
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
                if(dgtBoardAccessible) {
                    try {
                        dgtBoardInterface.open();
                    } catch (IOException e) {
                        // happens on API 21 whith pre-connected device
                        e.printStackTrace();
                        Toast.makeText(ChessPad.this, e.getMessage() + "\n" + "Try to reconnect device", Toast.LENGTH_LONG).show();
                        cancelSetup();
                    }
                } else {
                    if(!isDestroying) {
                        cancelSetup();
                    }
                }
            }
        });
        dgtBoardPad = new DgtBoardPad(dgtBoardInterface, getDefaultDirectory(), getCpEventObserver());
        if(NEW_FEATURE_FICS) {
            // problem with androidx.appcompat:appcompat:1.0.2
            dingPlayer = MediaPlayer.create(this, R.raw.ding);
        }

        try (InputStream is = this.getAssets().open(BOOK_ASSET_NAME)) {
            int length = is.available();
            openingBook = new Book(is, length);
        } catch (IOException | Config.PGNException e) {
            e.printStackTrace();
            Toast.makeText(ChessPad.this, e.getMessage() + "\n" + "Opening book failed to open", Toast.LENGTH_LONG).show();
        }

        final File engineWorkDir = new File("");
        try {
            uciEngine = new StockFishEngine(this, new UCIEngine.EngineWatcher() {
                @Override
                public String getEngineWorkDirectory() {
                    return engineWorkDir.getAbsolutePath();
                }

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
                    uciEngine.setOption("Hash", 64);
                    // do we need this?
//        uciEngine.setOption("SyzygyPath", "/storage/emulated/0/DroidFish/rtb");
//        uciEngine.setOption("SyzygyPath", "/storage/emulated/0/DroidFish/rtb");
                }

                @Override
                public void acceptAnalysis(UCIEngine.IncomingInfoMessage uciengineInfoMessage) {
                    Log.d(DEBUG_TAG, uciengineInfoMessage.toString());
                    if (ChessPad.this.uciengineInfoMessage != null &&
                            uciengineInfoMessage.getMoves() != null &&
                            ChessPad.this.uciengineInfoMessage.getMoves() != null &&
                            ChessPad.this.uciengineInfoMessage.getMoves().startsWith(uciengineInfoMessage.getMoves())) {
                        return;
                    }
                    ChessPad.this.uciengineInfoMessage = uciengineInfoMessage;
                    sendMessage(Config.MSG_REFRESH_SCREEN, null);
                }

                @Override
                public void reportError(String message) {
                    Log.e(DEBUG_TAG, message);
                }
            });
            uciEngine.launch();
        } catch (IOException e) {
            Log.e(DEBUG_TAG, e.getMessage(), e);
        }
        Log.d(DEBUG_TAG, "engine launched");
        try {
            lichessPad = new LichessPad(new LichessPad.LichessMessageConsumer() {
                @Override
                public void consume(LichessPad.LichessMessage message) {
                    if (message instanceof LichessPad.LichessMessageLoginOk) {
                        sendMessage("Login OK");
                    } else if (message instanceof LichessPad.LichessMessagePuzzle) {
                        try {
                            setPgnGraph(null);
                        } catch (Config.PGNException e) {
                            Log.e(DEBUG_TAG, e.getLocalizedMessage());
                            e.printStackTrace();
                        }
                    } else {
                        // todo:
                    }
                }

                @Override
                public void error(Exception e) {
                    sendMessage(e.getLocalizedMessage());
                }

                private void sendMessage(String message) {
                    Message msg = bgMessageHandler.obtainMessage();
                    msg.arg1 = Config.MSG_NOTIFICATION;
                    msg.obj = message;
                    bgMessageHandler.sendMessage(msg);
                }
            });
        } catch (Config.PGNException e) {
            Log.e(DEBUG_TAG, e.getMessage(), e);
        }
    }

    // called after onStop()
    // Always followed by onStart()
    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(DEBUG_TAG, "onRestart()");
        freshStart = false;
    }

    // called after onCreate() or onRestart()
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(DEBUG_TAG, "onStart()");
        if(freshStart) {
            mode = Mode.Game;
            unserializing = true;
            if(unserializeUI()) {
                chessPadView.enableCommentEdit(false);
                unserializePgnGraph();
            } else {
                unserializing = false;
            }
            intentFileName =  getIntentFileName();
            if(intentFileName != null) {
                currentPath = CpFile.fromFile(new File(intentFileName));
                popups.dialogType = Popups.DialogType.Load;
            }
            popups.afterUnserialize();
        }
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
                if("application/zip".equals(type)) {
                    ext = CpFile.EXT_ZIP;
                } else {
                    ext = CpFile.EXT_PGN;
                }
                intentFileName = CpFile.getRootPath() + File.separator + DEFAULT_DIRECTORY + File.separator + "download" + ext;
                ContentResolver resolver = getContentResolver();
                try (InputStream in = resolver.openInputStream(data);
                        OutputStream os = new FileOutputStream(intentFileName)) {
                    CpFile.copy(in, os);
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
        if(!dgtBoardAccessible) {
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
        uciEngine.doAnalysis(doAnalysis);
        if(intentFileName == null) {
            chessPadView.invalidate();
        }
        intentFileName = null;
    }

//    // another activity comes to foreground
//    // followed by onResume() if the activity comes to the foreground, or onStop() if it becomes hidden.
//    @Override
//    public void onPause() {
//        super.onPause();
//        Log.d(DEBUG_TAG, "onPause()");
//    }

    // Followed by either onRestart() if this activity is coming back to interact with the user, or onDestroy() if this activity is going away
    // becomes killable
    @Override
    protected void onStop() {
        super.onStop();
        Log.d(DEBUG_TAG, "onStop()");
        if (merging) {
            return;     // skip serialization
        }
        uciEngine.doAnalysis(false);    // no BG analysis
        serializeUI();
        serializePgnGraph();
    }

    // The final call you receive before your activity is destroyed
    // becomes killable
    @Override
    protected void onDestroy() {
        Log.d(DEBUG_TAG, "onDestroy()");
        uciEngine.shutDown();
        isDestroying = true;
        popups.dismissDlg();
        dgtBoardInterface.close();
        dgtBoardPad.close();
        super.onDestroy();
    }

//    // This callback is called only when there is a saved instance that is previously saved by using
//    // onSaveInstanceState(). We restore some state in onCreate(), while we can optionally restore
//    // other state here, possibly usable after onStart() has completed.
//    // The savedInstanceState Bundle is same as the one used in onCreate().
//    @Override
//    protected void onRestoreInstanceState(Bundle savedInstanceState) {
//        super.onRestoreInstanceState(savedInstanceState);
//        Log.d(DEBUG_TAG, "onRestoreInstanceState()");
//    }

//    // invoked when the activity may be temporarily destroyed, save the instance state here
//    @Override
//    protected void onSaveInstanceState(Bundle outState) {
//        // call superclass to save any view hierarchy
//        super.onSaveInstanceState(outState);
//        Log.d(DEBUG_TAG, "onSaveInstanceState()");
//    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(DEBUG_TAG, "onConfigurationChanged()");
        chessPadView.redraw();
        popups.relaunchDialog();
    }

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     * This is in theory. In practice the method is being called only with ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN.
     * Even when the program consumes huge amts of memory, it crashes with OOM because of fragmentation, never calling onTrimMemory.
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

    private void init() {
        try {
            setup = null;
            pgnGraph = new PgnGraph(new Board());
            chessPadView = new ChessPadView(this);
            sample();       // initially create a sample pgn
        } catch (Throwable t) {
            Log.e(DEBUG_TAG, t.getLocalizedMessage(), t);
        }
    }

    public static String getDefaultDirectory() {
        if(defaultDirectory == null) {
            CpFile cpFile = new CpFile.Dir(DEFAULT_DIRECTORY);
            defaultDirectory = cpFile.getAbsolutePath();
        }
        return defaultDirectory;
    }

    private CpEventObserver getCpEventObserver() {
        return (msgId) -> {
            Message msg = bgMessageHandler.obtainMessage();
            msg.arg1 = msgId & 0x0ff;
            bgMessageHandler.sendMessage(msg);
        };
    }

    boolean isSaveable() {
        return pgnGraph.isModified() && pgnGraph.getPgn().getIndex() != -1;
    }

    public void setComment(String newComment) {
        if (!isUiFrozen()) {
            pgnGraph.setComment(newComment);
        }
    }

    List<MenuItem> getMenuItems() {
        if (mode == Mode.FicsConnection) {
            return getFicsMenuItems();
        }

        List<MenuItem> menuItems = new LinkedList<>();
        boolean enabled = true;
        if (mode == Mode.DgtGame) {
            enabled = false;
        }
        menuItems.add(new MenuItem(MenuCommand.Load, getResources().getString(R.string.menu_load), enabled));
        if(mode == Mode.Game) {
            if(doAnalysis) {
                menuItems.add(new MenuItem(MenuCommand.Analysis, getResources().getString(R.string.menu_stop_analysis), enabled));
            } else {
                menuItems.add(new MenuItem(MenuCommand.Analysis, getResources().getString(R.string.menu_analysis), enabled));
            }
        }
        menuItems.add(new MenuItem(MenuCommand.Puzzle, getResources().getString(R.string.menu_puzzle), enabled));
        boolean enableMerge = enabled && mode == Mode.Game && pgnGraph.getInitBoard().equals(new Board());
        menuItems.add(new MenuItem(MenuCommand.Merge, getResources().getString(R.string.menu_merge), enableMerge));
        menuItems.add(new MenuItem(MenuCommand.Save, getResources().getString(R.string.menu_save), enabled && isSaveable()));
        menuItems.add(new MenuItem(MenuCommand.Append, getResources().getString(R.string.menu_append),
                enabled && (mode == Mode.Game || mode == Mode.Puzzle || mode == Mode.Game.LichessPuzzle)));
        if (mode == Mode.Game) {
            menuItems.add(new MenuItem(MenuCommand.AnyMove, getResources().getString(R.string.menu_any_move), pgnGraph.isNullMoveValid()));
            menuItems.add(new MenuItem(MenuCommand.Setup, getResources().getString(R.string.menu_setup), true));
        } else if (mode == Mode.Puzzle) {
            menuItems.add(new MenuItem(MenuCommand.Setup, getResources().getString(R.string.menu_setup), true));
        } else if (mode == Mode.Setup) {
            menuItems.add(new MenuItem(MenuCommand.CancelSetup, getResources().getString(R.string.menu_cancel_setup), true));
        }
        if (NEW_FEATURE_LICHESS) {
            menuItems.add(new MenuItem(MenuCommand.ConnectToFics, getResources().getString(R.string.menu_connect_to_fics), enabled));
        }
        if (NEW_FEATURE_LICHESS) {
            menuItems.add(new MenuItem(MenuCommand.LichessPuzzle, getResources().getString(R.string.menu_lichess_puzzle), enabled));
            if (lichessPad.isUserLoggedIn()) {
                menuItems.add(new MenuItem(MenuCommand.LichessLogout, getResources().getString(R.string.menu_lichess_logout), enabled));
            } else {
                menuItems.add(new MenuItem(MenuCommand.LichessLogin, getResources().getString(R.string.menu_lichess_login), enabled));
            }
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

    private List<MenuItem> getFicsMenuItems() {
        List<MenuItem> menuItems = new LinkedList<>();

        menuItems.add(new MenuItem(MenuCommand.FicsMate, getResources().getString(R.string.menu_fics_mate)));
        menuItems.add(new MenuItem(MenuCommand.FicsTactics, getResources().getString(R.string.menu_tactics)));
        menuItems.add(new MenuItem(MenuCommand.FicsStudy, getResources().getString(R.string.menu_study)));
        menuItems.add(new MenuItem(MenuCommand.FicsEndgame, getResources().getString(R.string.menu_endgame)));

        menuItems.add(new MenuItem(MenuCommand.CloseFicsConnection, getResources().getString(R.string.menu_close_fics_connection), true));
        menuItems.add(new MenuItem(MenuCommand.About, getResources().getString(R.string.menu_about)));
        return menuItems;
    }

    private void sample() {
        new Sample().createPgnTest();
    }

    // serialize graph and moveline
    private void serializePgnGraph() {
        Log.d(DEBUG_TAG, String.format("serializePgnGraph() pgngraphModified=%s", pgngraphModified));
        File outDir = new File(getDefaultDirectory());
        if(pgngraphModified || pgnGraph.isModified()) {
            final CpFile.Pgn _pgn = new CpFile.Pgn(new File(outDir.getAbsolutePath(), CURRENT_PGN_NAME).getAbsolutePath());
            final CpFile.Item _item = new CpFile.Item(_pgn);
            _item.setIndex(0);
            CpFile.Item original = pgnGraph.getPgn();
            _item.setTags(original.getTags());
            _item.setFen(original.getFen());
            pgnGraph.setPgn(_item);
            boolean modified = pgnGraph.isModified();
            try {
                pgnGraph.save(true, null);
            } catch (Config.PGNException e) {
                Log.e(DEBUG_TAG, "serializePgnGraph", e);
            }
            pgnGraph.setPgn(original);
            pgnGraph.setModified(modified);
            pgngraphModified = false;
        }

        try (FileOutputStream fos = new FileOutputStream(new File(outDir.getAbsolutePath(), MOVELINE_FILE_NAME));
                BitStream.Writer writer = new BitStream.Writer(fos) ) {
            CpFile parent = pgnGraph.getPgn().getParent();
            writer.writeString(parent.getAbsolutePath());
            writer.write(parent.getTotalChildren(), 24);
            writer.write(pgnGraph.getPgn().getIndex(), 24);
            if(pgnGraph.isModified()) {
                writer.write(1, 1);
            } else {
                writer.write(0, 1);
            }
            pgnGraph.serializeMoveLine(writer, versionCode);
        } catch (Config.PGNException | IOException e) {
            Log.e(DEBUG_TAG, "serializePgnGraph - moveline", e);
        }
    }

    // unserialize graph and moveline
    private void unserializePgnGraph() {
        final File inDir = new File(getDefaultDirectory());
        CpFile.Item item;

        try (BufferedReader br = new BufferedReader(new FileReader(new File(inDir.getAbsolutePath(), CURRENT_PGN_NAME)))) {
            final List<CpFile> items = new LinkedList<>();
            CpFile.parsePgnFiles(null, br, new CpFile.EntryHandler() {
                @Override
                public boolean handle(CpFile entry, BufferedReader bufferedReader) {
                    items.add(entry);
                    return true;
                }

                @Override
                public boolean getMoveText(CpFile entry) {
                    return true;
                }

                @Override
                public boolean addOffset(int length, int totalLength) {
                    return false;
                }

                @Override
                public boolean skip(CpFile entry) {
                    return false;
                }
            });
            item = (CpFile.Item)items.get(0);
        } catch (IOException | Config.PGNException e) {
            Log.w(DEBUG_TAG, "unserializePgnGraph()", e);
            try {
                pgnGraph = new PgnGraph(new Board());
            } catch (Config.PGNException e1) {
                Log.e(DEBUG_TAG, "unserializePgnGraph()", e1);
            }
            chessPadView.invalidate();
            unserializing = false;
            chessPadView.enableCommentEdit(true);
            return;
        }

        try (FileInputStream fis = new FileInputStream(new File(inDir.getAbsolutePath(), MOVELINE_FILE_NAME));
                BitStream.Reader reader = new BitStream.Reader(fis)) {
            String currentItemPath = reader.readString();
            int totalChildren = reader.read(24);
            if(totalChildren == 0x0ffffff) {
                totalChildren = -1;
            }
            int currentItemIndex = reader.read(24);
            if(currentItemIndex == 0x0ffffff) {
                currentItemIndex = -1;
            }
            boolean currentItemModified = reader.read(1) == 1;
            CpFile.Pgn parent = (CpFile.Pgn)CpFile.fromFile(new File(currentItemPath));
            parent.setTotalChildren(totalChildren);
            item.setParent(parent);
            item.setIndex(currentItemIndex);
            pgnGraph = new PgnGraph(item, null);
            pgnGraph.setModified(currentItemModified);
            pgnGraph.unserializeMoveLine(reader, versionCode);
        } catch (IOException|Config.PGNException e) {
            Log.w(DEBUG_TAG, "unserializePgnGraph()", e);
        }
        notifyUci();
        chessPadView.invalidate();
        unserializing = false;
        chessPadView.enableCommentEdit(true);
    }

    private void serializeUI() {
        Log.d(DEBUG_TAG, "serializeUI()");
        try (BitStream.Writer writer = new BitStream.Writer(openFileOutput(STATUS_FILE_NAME, Context.MODE_PRIVATE))) {
            writer.write(versionCode, 8);
            currentPath.serialize(writer);
            writer.write(mode.getValue(), 2);
            if(mode == Mode.Setup) {
                setup.serialize(writer);
            }
            dgtBoardPad.serialize(writer);
            if (nextPgnFile == null) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                nextPgnFile.serialize(writer);
            }
            writer.write(flipped ? 1 : 0, 1);
            writer.write(oldFlipped ? 1 : 0, 1);
            if (selectedSquare == null) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                selectedSquare.serialize(writer);
            }
            if(ficsPad != null) {
                writer.write(1, 1);
                ficsPad.serialize(writer, versionCode);
                writer.writeString(ficsCommand);
            } else {
                writer.write(0, 1);
            }
            ficsSettings.serialize(writer);
            lichessPad.serialize(writer);
            lichessSettings.serialize(writer);
            writer.write(doAnalysis ? 1 : 0, 1);
            writer.write(savePuzzle ? 1 : 0, 1);
            puzzleData.serialize(writer);
            popups.serialize(writer);
        } catch (Config.PGNException | IOException e) {
            Log.d(DEBUG_TAG, "serializeUI", e);
        }
    }

    private boolean unserializeUI() {
        boolean res = false;
        try (BitStream.Reader reader = new BitStream.Reader(openFileInput(STATUS_FILE_NAME))) {
            int oldVersionCode = reader.read(8);
            if (versionCode == oldVersionCode) {
                currentPath = CpFile.unserialize(reader);
                mode = Mode.value(reader.read(2));
                if(mode == Mode.Setup) {
                    setup = new Setup(reader);
                } else if(mode == Mode.DgtGame) {
                    mode = Mode.Game;   // do not launch DGT board connection automatically
                }
                if(NEW_FEATURE_FICS) {
                    if(mode == Mode.FicsConnection) {
                        mode = Mode.Game;
                    }
                }
                dgtBoardPad.unserialize(reader);
                if (reader.read(1) == 1) {
                    nextPgnFile = (CpFile.Item) CpFile.unserialize(reader);
                }
                flipped = reader.read(1) == 1;
                oldFlipped = reader.read(1) == 1;
                if (reader.read(1) == 1) {
                    selectedSquare = new Square(reader);
                }
                if (reader.read(1) == 1) {
                    ficsPad = new FicsPad(reader, versionCode);
                    ficsCommand = reader.readString();
                }
                ficsSettings.unserialize(reader);
                lichessPad.unserialize(reader);
                lichessSettings.unserialize(reader);
                doAnalysis = reader.read(1) == 1;
                savePuzzle = reader.read(1) == 1;
                puzzleData.unserialize(reader);
                popups.unserialize(reader);
                if(selectedSquare != null) {
                    selectedPiece = getBoard().getPiece(selectedSquare);
                }
                res = true;
            } else {
                Log.w(DEBUG_TAG, String.format("Old serialization %d ignored", oldVersionCode));
            }
        } catch(Throwable t){
            Log.e(DEBUG_TAG, t.getLocalizedMessage(), t);
        }
        chessPadView.redraw();
        return res;
    }

    private boolean isUiFrozen() {
        return unserializing || merging || animationHandler != null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)  {
        if ( keyCode == KeyEvent.KEYCODE_MENU ) {
            onButtonClick(Command.Menu);
            // return 'true' to prevent further propagation of the key event
            return true;
        }
        // let the system handle all other key events
        return super.onKeyDown(keyCode, event);
    }

    public void onButtonClick(Command command) {
        Board board = getBoard();
        if(DEBUG) {
            if(board != null) {
                Log.d(DEBUG_TAG, String.format("click %s\n%s", command.toString(), board.toString()));
            }
        }
        switch (command) {
            case Start:
                selectedSquare = null;
                if (!isUiFrozen()) {
                    if(pgnGraph.isInit()) {
                        Log.d(DEBUG_TAG, "to prev game");
                        CpFile.Item currentItem = pgnGraph.getPgn();
                        toNextGame(currentItem.getParent(), currentItem.getIndex() - 1);
                    } else {
                        pgnGraph.toInit();
                        notifyUci();
                        chessPadView.invalidate();
                    }
                } else if(animationHandler != null) {
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
                    chessPadView.invalidate();
                }
                break;

            case PrevVar:
                if (!isUiFrozen()) {
                    selectedSquare = null;
                    pgnGraph.toPrevVar();
                    notifyUci();
                    chessPadView.invalidate();
                }
                break;

            case Next:
                if (!isUiFrozen()) {
                    puzzleData.failed = true;
                    selectedSquare = null;
                    List<Move> variations = pgnGraph.getVariations();
                    if (variations == null) {
                        pgnGraph.toNext();
                        notifyUci();
                        chessPadView.invalidate();
                    } else {
                        Log.d(DEBUG_TAG, "variation");
                        popups.launchDialog(Popups.DialogType.Variations);
                    }
                }
                break;

            case Stop:
                selectedSquare = null;
                animationHandler.stop();
                chessPadView.setButtonEnabled(ChessPad.Command.Stop.getValue(), false);
                break;

            case NextVar:
                if (!isUiFrozen()) {
                    puzzleData.failed = true;
                    selectedSquare = null;
                    if (pgnGraph.getVariations() == null) {
                        animationHandler = new AnimationHandler(animationTimeout, new TimeoutObserver() {
                            @Override
                            public boolean handle() {
                                pgnGraph.toNext();
                                notifyUci();
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
                }
                break;

            case End:
                selectedSquare = null;
                if (!isUiFrozen()) {
                    CpFile.Item currentItem = pgnGraph.getPgn();
                    if (mode == Mode.Puzzle) {
                        toNextGame(currentItem.getParent(), puzzleData.getNextIndex());
//                        Toast.makeText(this, puzzleData.toString(), Toast.LENGTH_SHORT).show();
                    } else {    //  if (mode == Mode.Game) {
                        if(pgnGraph.isEnd()) {
                            Log.d(DEBUG_TAG, "to next game");
                            toNextGame(currentItem.getParent(), currentItem.getIndex() + 1);
                        } else {
                            animationHandler = new AnimationHandler(animationTimeout, new TimeoutObserver() {
                                @Override
                                public boolean handle() {
                                    pgnGraph.toNext();
                                    notifyUci();
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
                        }
                    }
                } else if(animationHandler != null) {
                    animationTimeout -= timeoutDelta;
                    if (animationTimeout <= 0) {
                        animationTimeout = 1;
                    }
                    animationHandler.setTimeout(animationTimeout);
                }
                break;

            case Flip:
                flipped = !flipped;
                chessPadView.invalidate();
                break;

            case FlipBoard:
                dgtBoardPad.turnBoard();
                chessPadView.invalidate();
                break;

            case ShowGlyphs:
                if (!isUiFrozen()) {
                    boolean okToSetGlyph = false;
                    if(mode == Mode.Game || mode == Mode.Puzzle) {
                        okToSetGlyph = pgnGraph.okToSetGlyph();
                    } else if(mode == ChessPad.Mode.DgtGame) {
                        okToSetGlyph = dgtBoardPad.getPgnGraph().okToSetGlyph();
                    }
                    if(okToSetGlyph) {
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
                    if(getPgnGraph().isEnd() && !getPgnGraph().isInit()) {
                        getPgnGraph().delCurrentMove();
                        notifyUci();
                        chessPadView.invalidate();
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

            case Send2Fics:
                if (ficsPad != null) {
                    ficsPad.send();
                }
                chessPadView.invalidate();
                break;

        }
    }

    void delete() {
        if(isFirstMove()) {
            pgnGraph.getPgn().setMoveText(null);
            savePgnGraph(false, () -> {
                pgnGraph = new PgnGraph(new Board());
                pgngraphModified = true;
                currentPath = currentPath.getParent();
                notifyUci();
                chessPadView.invalidate();
            });
        } else {
            pgnGraph.delCurrentMove();
            notifyUci();
            chessPadView.invalidate();
        }
    }

    public void completePromotion(Move promotionMove) {
        getPgnGraph().validateUserMove(promotionMove);  // validate check
        addMove(promotionMove);
        if(mode == Mode.FicsConnection) {
            ficsPad.send(promotionMove.toString());
        }
    }

    public List<Pair<String, String>> getTags() {
        switch (mode) {
            case Game:
            case Puzzle:
                return pgnGraph.getPgn().getTags();

            case Setup:
                return setup.getTags();

            case DgtGame:
                return dgtBoardPad.getTags();

            case FicsConnection:
                return ficsPad.getPgnGraph().getPgn().getTags();
        }
        return null;    // should never happen
    }

    public void setTags(List<Pair<String, String>> tags) {
        switch (mode) {
            case Game:
            case Puzzle:
                pgnGraph.setTags(tags);
                return;

            case Setup:
                setup.setTags(tags);
                return;

            case DgtGame:
                dgtBoardPad.setTags(tags);
        }
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

    int lastItemIndex() {
        return pgnGraph.getPgn().getParent().getTotalChildren() - 1;
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

    void executeMenuCommand(MenuCommand menuCommand) {
        Log.d(DEBUG_TAG, String.format("menu %s", menuCommand.toString()));
        switch (menuCommand) {
            case Load:
                popups.launchDialog(Popups.DialogType.Load);
                break;

            case Analysis:
                if(doAnalysis) {
                    uciengineInfoMessage = null;
                }
                doAnalysis = !doAnalysis;
                uciEngine.doAnalysis(doAnalysis);
                notifyUci();
                break;

            case Puzzle:
                popups.launchDialog(Popups.DialogType.Puzzle);
                break;

            case About:
                popups.launchDialog(Popups.DialogType.ShowMessage);
                break;

            case Save:
                savePgnGraph(true, () -> setPgnGraph(null));
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

            case LichessLogin:
                popups.launchDialog(Popups.DialogType.LichessLogin);
                break;

            case LichessPuzzle:
                mode = Mode.LichessPuzzle;
                try {
                    lichessPad.loadPuzzle();
                } catch (Config.PGNException e) {
                    Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
                }
                break;

            case ConnectToFics:
                try {
                    nextMode = Mode.FicsConnection;
                    setPgnGraph(null);
                } catch (Config.PGNException e) {
                    Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
                }
                break;

            case CloseFicsConnection:
                closeFicsConnection();
                break;

            case FicsMate:
                selectPuzzlebotOptions(FicsPad.COMMAND_GET_MATE);
                break;

            case FicsTactics:
                selectPuzzlebotOptions(FicsPad.COMMAND_TACTICS);
                break;

            case FicsStudy:
                selectPuzzlebotOptions(FicsPad.COMMAND_STUDY);
                break;

            case FicsEndgame:
                this.ficsCommand = FicsPad.COMMAND_ENDGAME;
                popups.launchDialog(Popups.DialogType.SelectEndgamebotOptions);
                break;
        }

    }

    private void switchToSetup() {
        setup = new Setup(pgnGraph);
        mode = Mode.Setup;
        stopAnalysis();
        chessPadView.redraw();
    }

    private void cancelSetup() {
        Log.d(DEBUG_TAG, "cancelSetup(), to Mode.Game");
        setup = null;
        if(mode != Mode.Puzzle) {
            mode = Mode.Game;
        }
        chessPadView.redraw();
        postErrorMsg();
    }

    private void postErrorMsg() {
        if(pgnGraph.getParsingErrorNum() != 0) {
            String error = pgnGraph.getParsingError();
            if(error == null) {
                error = String.format("%s, %s", getSetupErr(pgnGraph.getParsingErrorNum()), getResources().getString(R.string.err_moves_ignored));
            }
            Toast.makeText(ChessPad.this, error, Toast.LENGTH_LONG).show();
        }
    }

    private void startDgtMode() {
        nextMode = Mode.DgtGame;
        if(pgnGraph.isModified()) {
            popups.launchDialog(Popups.DialogType.SaveModified);
        } else {
            openDgtMode();
        }
    }

    private void openDgtMode() {
        Log.d(DEBUG_TAG, "openDgtMode()");
        mode = Mode.DgtGame;
        oldFlipped = flipped;
        dgtBoardPad.resume();
        chessPadView.redraw();
    }

    private void stopDgtMode() {
        mode = Mode.Game;
        flipped = oldFlipped;
        dgtBoardPad.stop();
        chessPadView.redraw();
    }

    void lichessLogin(String username, String password) {
        Log.d(DEBUG_TAG, "lichessLogin()");
        lichessSettings.setUsername(username);
        lichessSettings.setPassword(password);
        try {
            lichessPad.login(lichessSettings);
        } catch (Config.PGNException e) {
            Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
        }
    }

    LichessPad.LichessSettings getLichessSettings() {
        return lichessSettings;
    }

    public FicsPad.FicsSettings getFicsSettings() {
        return ficsSettings;
    }

    void openFicsConnection() {
        Log.d(DEBUG_TAG, "openFicsConnection()");
        ficsPad = new FicsPad(ficsSettings, (inboundMessage) -> {
            Log.d(DEBUG_TAG, inboundMessage.toString());
            if(inboundMessage.getMessageType() == InboundMessage.MessageType.Ready) {
                mode = Mode.FicsConnection;
                oldFlipped = flipped;
                if(msgPopup != null) {
                    msgPopup.dismiss();
                    msgPopup = null;
                }
            }
            Message msg = bgMessageHandler.obtainMessage();
            msg.arg1 = inboundMessage.getMessageType().getValue();
            bgMessageHandler.sendMessage(msg);
        });
        ficsLoginLaunched();
    }

    private void launchFicsMenu() {
        onButtonClick(Command.Menu);
    }

    private void closeFicsConnection() {
        Log.d(DEBUG_TAG, "closeFicsConnection()");
        mode = Mode.Game;
        flipped = oldFlipped;
        if(ficsPad != null && ficsPad.isConnected()) {
            ficsPad.close();
        }
        ficsPad = null;
        chessPadView.redraw();
    }

    private void ficsLoginLaunched() {
        msgPopup = new AlertDialog.Builder(this).create();
        msgPopup.setMessage(getResources().getString(R.string.msg_connecting));
        msgPopup.show();
    }

    private void selectPuzzlebotOptions(String ficsCommand) {
        Log.d(DEBUG_TAG, "selectMate()");
        this.ficsCommand = ficsCommand;
        popups.launchDialog(Popups.DialogType.SelectPuzzlebotOptions);
    }

    // puzzlebot
    void setCommandParam(int selected) {
        ficsPad.send(FicsPad.COMMAND_UNEXAMINE);    // in case we are examining a game
        String param = "";
        if(selected > 0) {
            param = "" + selected;
        }
        ficsPad.send(ficsCommand, param);
    }

    // endgamebot
    void setCommandParam(Pair<String, String> option) {
        ficsPad.send(FicsPad.COMMAND_UNEXAMINE);    // in case we are examining a game
        this.ficsCommand = FicsPad.COMMAND_ENDGAME;
        ficsPad.send(ficsCommand, option.first);
    }

    // after loading a new item or ending setup
    // in Setup on 'setup ok' (null)
    // after ending Append (null)
    // after SaveModified?, savePgnGraph, (null)
    // after SaveModified, negative, (null)
    // after load, new item
    public void setPgnGraph(CpFile.Item item) throws Config.PGNException {
        if (savePuzzle) {
            Log.d(DEBUG_TAG, String.format(Locale.US, "save puzzleData for %s", pgnGraph.getPgn().getAbsolutePath()));
            if (pgnGraph.isModified()) {
                savePuzzle();
            }
            CpFile.Pgn parent = null;
            if (item != null) {
                parent = (CpFile.Pgn)item.getParent();
                if (mode != Mode.Puzzle) {
                    parent = null;
                }
            }
            puzzleData.setPgn(parent);
        }
        if(pgnGraph.isModified()) {
            Log.d(DEBUG_TAG, String.format(Locale.US, "setPgnGraph %s, old is modified", item));
            nextPgnFile = item;
            popups.launchDialog(Popups.DialogType.SaveModified);
        } else {
            savePuzzle = mode == Mode.Puzzle;
            if(nextMode == Mode.FicsConnection) {
                popups.launchDialog(Popups.DialogType.FicsLogin);
                nextMode = null;
                return;
            }

            if (item == null) {
                if (mode == Mode.Game || mode == Mode.Puzzle) {
                    item = nextPgnFile;
                } else if (mode == Mode.LichessPuzzle) {
                    item = lichessPad.getPuzzle();
                }
            }

            if (item == null && setup != null) {
                pgnGraph = setup.toPgnGraph();
                pgngraphModified = true;
            } else if(item != null) {
                Log.d(DEBUG_TAG, String.format("setPgnGraph loadPgnGraph %s", item));
                pgnGraph = new PgnGraph(item, null);
                pgngraphModified = true;
            }
            if(nextMode == Mode.DgtGame) {
                openDgtMode();
            } else {
                if (mode == Mode.Puzzle) {
                    pgnGraph.toInit();
                    Log.d(DEBUG_TAG, String.format("pgnGraph.toInit(), moveline %s", pgnGraph.moveLine.size()));
                    flipped = (pgnGraph.getBoard().getFlags() & Config.BLACK) != 0;
                }
                nextPgnFile = null;
                selectedSquare = null;
                uciengineInfoMessage = null;
                popups.promotionMove = null;
                cancelSetup();
            }
        }
        popups.editTags = null;
        if(mode == Mode.Game) {
            notifyUci();
        } else {
            stopAnalysis();
        }
    }

    void notifyUci() {
        Log.d(DEBUG_TAG, String.format("notifyUci() %s", getPgnGraph().getBoard().toFEN()));
        uciEngine.abortCurrentAnalisys();
        if(doAnalysis) {
            UCIEngine.IncomingInfoMessage msg = fromBook();
            if (msg == null) {
                uciEngine.resumeAnalisys();
            } else {
                uciengineInfoMessage = msg;
            }
        }
    }

    private UCIEngine.IncomingInfoMessage fromBook() {
        List<Move> bookMoves;
        if ((bookMoves = openingBook.getMoves(getBoard())) == null) {
            return null;
        }

        UCIEngine.IncomingInfoMessage infoMessage = new UCIEngine.IncomingInfoMessage();
        if (pgnGraph.moveLine.size() > 1) {
            // find current opening name:
            Move prevMove = pgnGraph.moveLine.get(pgnGraph.moveLine.size() - 2);
            Board prevBoard = openingBook.getBoard(prevMove);
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
        doAnalysis = false;
        uciEngine.doAnalysis(doAnalysis);
        uciengineInfoMessage = null;
    }

    public String getAnalysisMessage()  {
        if (uciengineInfoMessage == null) {
            return "";
        }
        return uciengineInfoMessage.toString();
    }

    public String getHints()  {
        if (uciengineInfoMessage == null || uciengineInfoMessage.getMoves() == null) {
            return null;
        }
        String[] tokens = uciengineInfoMessage.getMoves().split("\\s+");

        int totalHints = MAX_ENGINE_HINTS;
        if (uciengineInfoMessage.info != null) {
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
        puzzleData.setPgn((CpFile.Pgn) currentPath);
        savePuzzle = mode == Mode.Puzzle;
        mode = Mode.Puzzle;
        toNextGame(currentPath, puzzleData.getNextIndex());
    }

    private void toNextGame(final CpFile pgn, final int nextIndex) {
        CpFile.Item item = new CpFile.Item(pgn);
        item.setIndex(nextIndex);

        new CPAsyncTask(chessPadView, new CPExecutor() {
            @Override
            public void onPostExecute() throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("toNextGame onPostExecute, thread %s", Thread.currentThread().getName()));
                int itemIndex = item.getIndex();
                if(nextIndex == itemIndex) {
                    setPgnGraph(item);
                } else {
                    // means that nextIndex is too large, item.getIndex() is the last one
                    pgn.setTotalChildren(itemIndex + 1);
                    puzzleData.setTotalPuzzles();
                    toNextGame(pgn, puzzleData.getNextIndex());
                }
            }

            @Override
            public void onExecuteException(Config.PGNException e) {
                Log.e(DEBUG_TAG, "toNextGame, onExecuteException, thread " + Thread.currentThread().getName(), e);
                popups.crashAlert(R.string.crash_cannot_load);
            }

            @Override
            public void doInBackground(final ProgressPublisher progressPublisher) throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("toNextGame start, thread %s", Thread.currentThread().getName()));

                CpFile.getPgnFile(item, (progress) -> {
                    if (DEBUG) {
                        Log.d(DEBUG_TAG, String.format("toNextGame, Offset %d%%", progress));
                    }
                    progressPublisher.publishProgress(progress);
                    return false;
                });
            }
        }).execute();
    }

    private void savePuzzle() {
        if (SAVE_UPDATED_PUZZLES) {
            Log.d(DEBUG_TAG, String.format("Save puzzle for %s", pgnGraph.getPgn().getParent().getAbsolutePath()));
            File file = puzzleData.getPuzzleUpdateFile();
            Log.d(DEBUG_TAG, String.format("Save puzzle to %s", file.getAbsolutePath()));

            final CpFile.Pgn _pgn = new CpFile.Pgn(file.getAbsolutePath());
            final CpFile.Item _item = new CpFile.Item(_pgn);
            CpFile.Item original = pgnGraph.getPgn();
            _item.setTags(original.getTags());
            _item.setTag(PUZZLE_TAG, "" + (original.getIndex() + 1));
            _item.setFen(original.getFen());
            pgnGraph.setPgn(_item);
            try {
                pgnGraph.save(true, null);
            } catch (Config.PGNException e) {
                Log.e(DEBUG_TAG, "serializePgnGraph", e);
            }
            pgnGraph.setPgn(original);
        }
        pgnGraph.setModified(false);
    }

    public void mergePgnGraph(final Popups.MergeData mergeData, final CPPostExecutor cpPostExecutor) {
        merging = true;
        chessPadView.enableCommentEdit(false);
        new CPAsyncTask(chessPadView, new CPExecutor() {
            @Override
            public void onPostExecute() throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("mergePgnGraph onPostExecute, thread %s", Thread.currentThread().getName()));
                if(cpPostExecutor != null) {
                    cpPostExecutor.onPostExecute();
                }
                merging = false;
                chessPadView.enableCommentEdit(true);
                pgngraphModified = true;
                String msg = String.format(getResources().getString(R.string.msg_merge_count), mergeData.merged);
                Toast.makeText(ChessPad.this, msg, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onExecuteException(Config.PGNException e) {
                merging = false;
                chessPadView.enableCommentEdit(true);
                Log.e(DEBUG_TAG, "mergePgnGraph, onExecuteException, thread " + Thread.currentThread().getName(), e);
                popups.crashAlert(R.string.crash_cannot_save);
            }

            @Override
            public void doInBackground(final ProgressPublisher progressPublisher) {
                Log.d(DEBUG_TAG, String.format("mergePgnGraph start, thread %s", Thread.currentThread().getName()));
                try {
                    pgnGraph.merge(mergeData, (progress) -> {
                        if(DEBUG) {
                            Log.d(DEBUG_TAG, String.format("Offset %d%%, thread %s", progress, Thread.currentThread().getName()));
                        }
                        progressPublisher.publishProgress(progress);
                        return false;
                    });
                } catch (Config.PGNException e) {
                    e.printStackTrace();
                }
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
        new CPAsyncTask(chessPadView, new CPExecutor() {
            @Override
            public void onPostExecute() throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("savePgnGraph onPostExecute, thread %s", Thread.currentThread().getName()));
                if(cpPostExecutor != null) {
                    cpPostExecutor.onPostExecute();
                }
            }

            @Override
            public void onExecuteException(Config.PGNException e) {
                Log.e(DEBUG_TAG, "savePgnGraph, onExecuteException, thread " + Thread.currentThread().getName(), e);
                popups.crashAlert(R.string.crash_cannot_save);
            }

            @Override
            public void doInBackground(final ProgressPublisher progressPublisher) throws Config.PGNException {
                long start = new Date().getTime();
                if(DEBUG) {
                    Log.d(DEBUG_TAG, String.format("savePgnGraph start, thread %s", Thread.currentThread().getName()));
                }
                pgnGraph.save(updateMoves, (progress) -> {
                    if(true) {
                        Log.d(DEBUG_TAG, String.format("Offset %d%%, thread %s", progress, Thread.currentThread().getName()));
                    }
                    progressPublisher.publishProgress(progress);
                    return false;
                });
                if(true) {
                    long end = new Date().getTime();
                    long duration = end - start;
                    Log.d(DEBUG_TAG, String.format("savePgnGraph end %s, thread %s", duration, Thread.currentThread().getName()));
                }
            }
        }).execute();
    }

    public PgnGraph getPgnGraph() {
        PgnGraph pgnGraph;
        switch(mode) {
            case LichessPuzzle:
                // todo:
            case Game:
            case Setup:
            case Puzzle:
                pgnGraph = this.pgnGraph;
                break;

            case FicsConnection:
                pgnGraph = this.ficsPad.getPgnGraph();
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
        if(DEBUG) {
            Log.d(DEBUG_TAG, String.format("board onSquareClick (%s)\n%s", clicked.toString(), pgnGraph.getBoard().toString()));
        }
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
                    Log.d(DEBUG_TAG, String.format("2nd click, piece changed %d != %d", piece, selectedPiece));
                    return false;
                }
                Move newMove = new Move(pgnGraph.getBoard(), selectedSquare, clicked);
                if (!pgnGraph.validateUserMove(newMove)) {
                    return false;
                }
                selectedSquare = null;
                selectedPiece = 0;
                if(newMove.isPromotion()) {
                    popups.promotionMove = newMove;
                    popups.launchDialog(Popups.DialogType.Promotion);
                } else {
                    addMove(newMove);
                    if (mode != Mode.Puzzle) {
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
        chessPadView.invalidate();
        return true;
    }

    private void addMove(Move newMove) {
        try {
            if (mode == Mode.Puzzle) {
                puzzleData.started = true;
                boolean ok = newMove.isSameAs(pgnGraph.getBoard().getMove());
                if (ok) {
                    pgnGraph.toNext();
                    chessPadView.invalidate();
                    Move lastMove = pgnGraph.getBoard().getMove();
                    if (!puzzleData.failed) {
                        if (lastMove == null) {
                            puzzleData.solved = true;
                            Toast.makeText(this, R.string.msg_puzzle_success, Toast.LENGTH_SHORT).show();
                        }
                        if (lastMove != null) {
                            sendMessage(Config.MSG_PUZZLE_ADVANCE, null);
                        }
                    }
                } else {
                    puzzleData.failed = true;
                    pgnGraph.addUserMove(newMove);
                    chessPadView.invalidate();
                    sendMessage(Config.MSG_PUZZLE_TAKE_BACK, null);
                    Toast.makeText(this, R.string.err_not_solution, Toast.LENGTH_SHORT).show();
                }
            } else {
                pgnGraph.addUserMove(newMove);
                notifyUci();
            }
        } catch (Config.PGNException e) {
            Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
        }
    }

    @Override
    public void onFling(Square clicked) {
        if(DEBUG) {
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
            Log.d(activity.DEBUG_TAG, String.format("message 0x%s", Integer.toHexString(msgId)));
            if(msgId == Config.MSG_OOM || msgId == Config.MSG_NOTIFICATION) {
                Toast.makeText(activity, msg.obj.toString(), Toast.LENGTH_LONG).show();
            } else if (msgId == Config.MSG_PUZZLE_ADVANCE) {
                try {
                    Thread.sleep(PUZZLE_MOVE_DELAY_MSEC);
                } catch (InterruptedException e) {
                    // ignore
                }
                activity.pgnGraph.toNext();
                activity.chessPadView.invalidate();
            } else if (msgId == Config.MSG_PUZZLE_TAKE_BACK) {
                try {
                    Thread.sleep(PUZZLE_MOVE_DELAY_MSEC);
                } catch (InterruptedException e) {
                    // ignore
                }
                activity.pgnGraph.delCurrentMove();
                activity.chessPadView.invalidate();
            } else if(msgId == Config.MSG_DGT_BOARD_SETUP_MESS || msgId == Config.MSG_DGT_BOARD_GAME) {
                activity.chessPadView.redraw();
            } else if( msgId <= Config.MSG_DGT_BOARD_LAST ) {
                activity.chessPadView.invalidate();
            } else if( msgId == InboundMessage.MessageType.Ready.getValue() ) {
                activity.chessPadView.redraw();
                activity.launchFicsMenu();
            } else if( msgId == InboundMessage.MessageType.Closed.getValue() ) {
                if (activity.mode == Mode.FicsConnection) {
                    activity.closeFicsConnection();
                    Toast.makeText(activity, R.string.msg_connection_lost, Toast.LENGTH_LONG).show();
                }
            } else if( msgId == InboundMessage.MessageType.G1Game.getValue() ) {
                activity.flipped = (activity.ficsPad.getPgnGraph().getInitBoard().getFlags() & Config.FLAGS_BLACK_MOVE) != 0;
                if(activity.ficsPad.heMoved()) {
                    activity.dingPlayer.start();
                }
                activity.chessPadView.invalidate();
            } else if( msgId <= InboundMessage.MessageType.Info.getValue() ) {
                // todo for fics
                activity.chessPadView.invalidate();
            } else {
                activity.chessPadView.invalidate();
            }
        }
    }

    private class PuzzleData {
        transient private final Random random = new Random(System.currentTimeMillis());
//        transient private final Random random = new Random(1);    // debug
        CpFile.Pgn pgn;
        private int totalPuzzles = Integer.MAX_VALUE;
        private int totalOpened;
        private int totalSolved;
        private int totalFailed;
        private int currentIndex = Integer.MAX_VALUE;
        boolean solved;
        boolean failed;
        boolean started;

        public void setPgn(CpFile.Pgn pgn) {
            init(pgn);
        }

        private void init(CpFile.Pgn pgn) {
            boolean doSave = pgn != null && !pgn.equals(this.pgn) || pgn == null && this.pgn != null;
            if (!doSave || started) {
                updateTotals();
            }
            if (doSave) {
                save();
                this.pgn = pgn;
                load();
            }
            solved = failed = started = false;
        }

        public void setTotalPuzzles() {
            totalPuzzles = pgn.getTotalChildren();
        }

        public  int getNextIndex() {
            int totalPuzzles = this.totalPuzzles;
            if (totalPuzzles == Integer.MAX_VALUE) {
                totalPuzzles = pgn.getLength() / MIN_PUZZLE_TEXT_LENGTH;
            }
            if( totalPuzzles <= 0) {
                totalPuzzles = 1;
            }

            int newIndex = random.nextInt(totalPuzzles);
            if(totalPuzzles > 1) {
                while (newIndex == currentIndex) {
                    newIndex = random.nextInt(totalPuzzles);
                }
            }
            currentIndex = newIndex;
            return currentIndex;
        }

        private void load() {
            totalPuzzles = Integer.MAX_VALUE;
            if (pgn != null) {
                File puzzleInfoFile = getPuzzleInfoFile(pgn);
                long puzzleInfoTimestamp = puzzleInfoFile.lastModified();
                long pgnTimestamp = pgn.lastModified();
                if (puzzleInfoTimestamp > pgnTimestamp) {
                    try (FileInputStream fis = new FileInputStream(puzzleInfoFile)) {
                        totalPuzzles = Util.readInt(fis);
                        Log.d(DEBUG_TAG, String.format("from file totalPuzzles=%s", totalPuzzles));
                        totalOpened = Util.readInt(fis);
                        totalSolved = Util.readInt(fis);
                        totalFailed = Util.readInt(fis);
                        currentIndex = Util.readInt(fis);
                        pgn.setTotalChildren(totalPuzzles);     // check on -1?
                    } catch (IOException e) {
                        Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
                    }
                }
            }

            if (totalPuzzles == Integer.MAX_VALUE) {
                clear();
            }
        }

        @SuppressLint("StringFormatMatches")
        @Override
        public String toString() {
            if (totalOpened <= 0) {
                return "";
            }
            String form = getResources().getString(R.string.msg_puzzle_statistics);
            return String.format(Locale.getDefault(), form, totalOpened, totalSolved, (float)totalSolved * 100 / totalOpened);
        }

        public void save() {
            if (pgn == null) {
                return;
            }
            Toast.makeText(ChessPad.this, toString(), Toast.LENGTH_LONG).show();
            File puzzleInfoFile = getPuzzleInfoFile(pgn);
            try (FileOutputStream fos = new FileOutputStream(puzzleInfoFile)) {
                Util.writeInt(fos, totalPuzzles);
                Util.writeInt(fos, totalOpened);
                Util.writeInt(fos, totalSolved);
                Util.writeInt(fos, totalFailed);
                Util.writeInt(fos, currentIndex);
            } catch (IOException e) {
                Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
            }
            clear();
        }

        private File getPuzzleInfoFile(CpFile pgn) {
            String path = pgn.getAbsolutePath().substring(CpFile.getRootPath().length() + 1);
            path = path.replaceAll("/", "_");
            int i = path.lastIndexOf(".");
            String puzzleInfoPath = (path.substring(0, i) + PUZZLE_INFO_EXT);
            return new File(CpFile.getRootPath() + File.separator + DEFAULT_DIRECTORY, puzzleInfoPath);
        }

        public File getPuzzleUpdateFile() {
            String path = pgn.getAbsolutePath();
            int i = path.lastIndexOf(".");
            String puzzleUpdPath = (path.substring(0, i) + PUZZLE_UPDATE_FILENAMEPUZZLE_UPDATE_FILENAME);
            return new File(puzzleUpdPath);
        }

        private void clear() {
            totalPuzzles = Integer.MAX_VALUE;
            currentIndex = Integer.MAX_VALUE;
            totalOpened =
            totalSolved =
            totalFailed = 0;
        }

        private void updateTotals() {
            ++totalOpened;
            if (solved) {
                ++totalSolved;
            } else {
                ++totalFailed;
            }
        }

        void unserialize(BitStream.Reader reader) throws Config.PGNException {
            try {
                if (reader.read(1) == 1) {
                    String path = reader.readString();
                    pgn = (CpFile.Pgn)CpFile.fromFile(new File(path));
                }
                totalPuzzles = reader.read(32);
                totalOpened = reader.read(32);
                totalSolved = reader.read(32);
                totalFailed = reader.read(32);
                currentIndex = reader.read(32);
                solved = reader.read(1) == 1;
                failed = reader.read(1) == 1;
                started = reader.read(1) == 1;
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        void serialize(BitStream.Writer writer) throws Config.PGNException {
            try {
                if(pgn == null) {
                    writer.write(0, 1);
                } else {
                    writer.write(1, 1);
                    writer.writeString(pgn.getAbsolutePath());
                }
                writer.write(totalPuzzles, 32);
                writer.write(totalOpened, 32);
                writer.write(totalSolved, 32);
                writer.write(totalFailed, 32);
                writer.write(currentIndex, 32);
                writer.write(solved ? 1 : 0, 1);
                writer.write(failed ? 1 : 0, 1);
                writer.write(started ? 1 : 0, 1);
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }
    }
}