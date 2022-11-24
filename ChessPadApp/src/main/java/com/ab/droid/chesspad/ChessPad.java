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

 * works with main activity
 * Created by Alexander Bootman on 8/20/16.
 */
package com.ab.droid.chesspad;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.core.content.pm.PackageInfoCompat;
import androidx.documentfile.provider.DocumentFile;

import com.ab.droid.chesspad.io.DocFilAx;
import com.ab.droid.chesspad.layout.ChessPadLayout;
import com.ab.droid.chesspad.layout.Metrics;
import com.ab.droid.chesspad.uci.Stockfish;
import com.ab.pgn.BitStream;
import com.ab.pgn.Board;
import com.ab.pgn.Book;
import com.ab.pgn.Config;
import com.ab.pgn.dgtboard.DgtBoardEventObserver;
import com.ab.pgn.Move;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.Setup;
import com.ab.pgn.Square;
import com.ab.pgn.dgtboard.DgtBoardPad;
import com.ab.pgn.io.CpFile;
import com.ab.pgn.uci.UCI;
import com.ab.pgn.io.FilAx;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class ChessPad implements Serializable, BoardHolder {
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_DGT_BOARD = false;

    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    private static final String
        STATUS_FILE_NAME = "status",
        CURRENT_PGN_NAME = "current-pgn",
        MOVES_NAME = "moves",
        DEFAULT_DIRECTORY = "/",
        DGT_BOARD_OUTPUT_DIRECTORY = "dgt_board",
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
        Statistics,
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

    private static final int OPEN_DIRECTORY_REQUEST_CODE = 1;

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
    private CpFile currentPath = new CpFile.Dir(null, "/");
    private final Popups popups;
    Mode nextMode;
    private CpFile.PgnItem nextPgnFile;
    private boolean flipped, oldFlipped;
    private int selectedPiece;
    private int versionCode;

    transient private final int timeoutDelta = animationTimeout / 4;
    transient private AnimationHandler animationHandler;
    transient private boolean unserializing = false;
    transient private boolean merging = false;
    transient final private String[] setupErrs;
    transient private boolean pgngraphModified = false;     // compared to status
    transient private boolean navigationEnabled = true;
    transient private DocFilAx rootFilAx;

    private DgtBoardInterface dgtBoardInterface;
    private final Handler bgMessageHandler;

    private boolean dgtBoardOpen = false;
    private boolean dgtBoardAccessible = DEBUG_DGT_BOARD;

    //    private MediaPlayer dingPlayer;
    private final PuzzleData puzzleData;

    transient private boolean isDestroying = false;
    transient private UCI uci;
    transient private UCI.IncomingInfoMessage incomingInfoMessage;
    transient private Book openingBook;

    static transient final byte[][] oomReserve = new byte[1][];

    private final RelativeLayout mainLayout;

    public RelativeLayout getMainLayout() {
        return mainLayout;
    }

    public ChessPad() {
        mainLayout = new RelativeLayout(MainActivity.getContext());
        RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT);
        mainLayout.setBackgroundColor(Color.BLACK);
        MainActivity.getInstance().setContentView(mainLayout, rlp);
        chessPadLayout = new ChessPadLayout(this);
        CPAsyncTask.setProgressBarHolder(chessPadLayout);

        puzzleData = new PuzzleData(this);
        popups = new Popups(this);

        try {
            PackageInfo pinfo = MainActivity.getContext().getPackageManager().getPackageInfo(MainActivity.getContext().getPackageName(), 0);
            versionName = pinfo.versionName;
            versionCode = (int) PackageInfoCompat.getLongVersionCode(pinfo);
        } catch (PackageManager.NameNotFoundException nnfe) {
            versionName = "0.0";
        }
        setupErrs = MainActivity.getContext().getResources().getStringArray(R.array.setup_errs);

        CpFile.setFilAxProvider(new FilAx.FilAxProvider() {
            @Override
            public FilAx newFilAx(String path) {
                return new DocFilAx(path);
            }

            @Override
            public FilAx newFilAx(FilAx parent, String name) {
                return new DocFilAx(parent, name);
            }

            @Override
            public String getRootPath() {
                return DocFilAx.getRootUri();
            }
        });

        bgMessageHandler = new CpHandler(this);

        try {
            setup = null;
            pgnGraph = new PgnGraph(new Board());
        } catch (Throwable t) {
            Log.e(DEBUG_TAG, t.getLocalizedMessage(), t);
        }
        restoreData();
    }

    void onCreateContinue() {
        unserializing = false;

        try (InputStream is = MainActivity.getContext().getAssets().open(BOOK_ASSET_NAME)) {
            int length = is.available();
            openingBook = new Book(is, length);
        } catch (IOException | Config.PGNException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.getContext(), e.getMessage() + "\n" + "Opening book failed to open", Toast.LENGTH_LONG).show();
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
//                    uci.setOption("Hash", 64);
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
            }, new Stockfish(MainActivity.getContext()));
        } catch (IOException e) {
            Log.e(DEBUG_TAG, e.getMessage(), e);
        }
        Log.d(DEBUG_TAG, "engine launched");
        chessPadLayout.redraw();

        chessPadLayout.enableCommentEdit(true);
        chessPadLayout.invalidate();
        notifyUci();
        launch();
    }

    // Called when the activity will start interacting with the user
    // after onStart() or onPause()
    // @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB_MR1)
    void launch() {
        Log.d(DEBUG_TAG, "onResume(), rootFilAx=" + rootFilAx);
        if (unserializing) {
            return;
        }
        if (rootFilAx == null) {
            popups.launchDialog(Popups.DialogType.Welcome);
            return;
        }

        sample();

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

        CpFile intentFile;
        if ((intentFile = handleIntentView()) != null) {
            currentPath = intentFile;
            popups.dialogType = Popups.DialogType.Load;
        }
        popups.afterUnserialize();
    }

    void returnFromWelcome() {
        Log.d(DEBUG_TAG, "returnFromWelcome");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
//                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        MainActivity.getInstance().startActivityForResult(intent, OPEN_DIRECTORY_REQUEST_CODE);
    }

    void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == OPEN_DIRECTORY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            MainActivity.getContext().getContentResolver().takePersistableUriPermission(
                    intent.getData(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
            );
            try {
                DocFilAx.setRoot(MainActivity.getContext(), intent.getDataString());
                rootFilAx = new DocFilAx((String)null);
                launch();
            } catch (Config.PGNException e) {
                popups.crashAlert(e.getMessage());
            }
        }
    }

    protected void onDestroy() {
        Log.d(DEBUG_TAG, "onDestroy()");
        if (uci != null) {
            uci.shutDown();
        }
        isDestroying = true;
        popups.dismissDlg();
        dgtBoardInterface.close();
        dgtBoardPad.close();
    }

    public void onConfigurationChanged() {
        Log.d(DEBUG_TAG, "onConfigurationChanged()");
        chessPadLayout.redraw();
        popups.relaunchDialog();
    }

    // copy intent file into default directory
    private CpFile handleIntentView() {
        CpFile intentFile = null;
        Intent intent = MainActivity.getInstance().getIntent();
        String action = intent.getAction();
        if (!Intent.ACTION_VIEW.equals(action)) {
            return intentFile;
        }
        Uri uri = intent.getData();
        DocumentFile documentFile = DocumentFile.fromSingleUri(MainActivity.getContext(), uri);
        String intentFileName = documentFile.getName();
        if (intentFileName == null) {
            return null;
        }

        // check if file exists and create a unique name?
        intentFileName = DEFAULT_DIRECTORY + FilAx.SLASH + intentFileName;
        try (InputStream is = MainActivity.getContext().getContentResolver().openInputStream(uri);
            OutputStream os = CpFile.newFile(intentFileName).getOutputStream()) {
            CpFile.copy(is, os);
            intentFile = CpFile.fromPath(intentFileName);
        } catch (IOException e) {
            Log.e(DEBUG_TAG, e.getMessage(), e);
        }
        return intentFile;
    }

    public Mode getMode() {
        return mode;
    }

    public CpFile getCurrentPath() {
        return currentPath;
    }

    public void setCurrentPath(CpFile currentPath) {
        this.currentPath = currentPath;
    }

    private DgtBoardEventObserver getCpEventObserver() {
        return new DgtBoardEventObserver() {
            @Override
            public void update(byte msgId) {
                Message msg = bgMessageHandler.obtainMessage();
                msg.arg1 = msgId & 0x0ff;
                bgMessageHandler.sendMessage(msg);
            }

            @Override
            public OutputStream getOutputStream() throws FileNotFoundException {
                String fname = String.format("%s/rec-%s.pgn", DGT_BOARD_OUTPUT_DIRECTORY, new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date()));
                FilAx f = new DocFilAx(fname);
                return f.getOutputStream();
            }
        };
    }

    boolean isSaveable() {
        return pgnGraph.isModified() && pgnGraph.getPgnItem().getIndex() != -1;
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
        boolean enabled = mode != Mode.DgtGame;
        Context context = MainActivity.getContext();
        Resources resources = context.getResources();
        menuItems.add(new MenuItem(MenuCommand.Load, resources.getString(R.string.menu_load), enabled));
        menuItems.add(new MenuItem(MenuCommand.Puzzle, resources.getString(R.string.menu_puzzle), enabled));
        if (mode == Mode.Puzzle) {
            menuItems.add(new MenuItem(MenuCommand.Statistics, resources.getString(R.string.menu_statistics), enabled));
        }
        boolean enableMerge = enabled && mode == Mode.Game && pgnGraph.getInitBoard().equals(new Board());
        menuItems.add(new MenuItem(MenuCommand.Merge, resources.getString(R.string.menu_merge), enableMerge));
        menuItems.add(new MenuItem(MenuCommand.Save, resources.getString(R.string.menu_save), enabled && isSaveable()));
        menuItems.add(new MenuItem(MenuCommand.Append, resources.getString(R.string.menu_append),
                enabled && (mode == Mode.Game || isPuzzleMode())));
        if (mode == Mode.Game) {
            menuItems.add(new MenuItem(MenuCommand.AnyMove, resources.getString(R.string.menu_any_move), pgnGraph.isNullMoveValid()));
            menuItems.add(new MenuItem(MenuCommand.Setup, resources.getString(R.string.menu_setup), true));
        } else if (mode == Mode.Puzzle) {
            menuItems.add(new MenuItem(MenuCommand.Setup, resources.getString(R.string.menu_setup), true));
        } else if (mode == Mode.Setup) {
            menuItems.add(new MenuItem(MenuCommand.CancelSetup, resources.getString(R.string.menu_cancel_setup), true));
        }
        if (dgtBoardAccessible) {
            if (mode == Mode.DgtGame) {
                menuItems.add(new MenuItem(MenuCommand.StopDgtGame, resources.getString(R.string.menu_stop_dgt_game), true));
            } else {
                menuItems.add(new MenuItem(MenuCommand.StartDgtGame, resources.getString(R.string.menu_start_dgt_game), enabled));
            }
        }

        menuItems.add(new MenuItem(MenuCommand.About, resources.getString(R.string.menu_about)));
        return menuItems;
    }

    private void sample() {
        new Sample().createSample("sample.pgn");
    }

    void serialize() {
        Log.d(DEBUG_TAG, "onStop()");
        if (merging) {
            return;     // skip serialization
        }
        if (uci != null) {
            uci.doAnalysis(false);    // no BG analysis
        }
        if (currentPath != null) {
            serializeUI();
            serializePgnGraph();
        }
    }

    private void serializePgnGraph() {
        Log.d(DEBUG_TAG, String.format("serializePgnGraph() pgngraphModified=%s", pgngraphModified));
        if (pgngraphModified || pgnGraph.isModified()) {
            try (BitStream.Writer writer = new BitStream.Writer(MainActivity.getContext().openFileOutput(CURRENT_PGN_NAME, Context.MODE_PRIVATE))) {
                pgnGraph.serializeGraph(writer, versionCode);
            } catch (Config.PGNException | IOException e) {
                Log.e(DEBUG_TAG, "serializePgnGraph", e);
            }
            Log.d(DEBUG_TAG, "serializePgnGraph() done");
        }
        try (BitStream.Writer writer = new BitStream.Writer(MainActivity.getContext().openFileOutput(MOVES_NAME, Context.MODE_PRIVATE))) {
            pgnGraph.serializeMoveLine(writer, versionCode);
        } catch (Config.PGNException | IOException e) {
            Log.e(DEBUG_TAG, "serializeMoveLine", e);
        }
        Log.d(DEBUG_TAG, "serializeMoveLine() done");
    }

    private boolean unserializePgnGraph() {
        boolean res = false;
        Log.d(DEBUG_TAG, "unserializePgnGraph() start");
        try (BitStream.Reader reader = new BitStream.Reader(MainActivity.getContext().openFileInput(CURRENT_PGN_NAME))) {
            pgnGraph = new PgnGraph(reader, versionCode);
            res = true;
        } catch (Config.PGNException | IOException e) {
            Log.w(DEBUG_TAG, "unserializePgnGraph()", e);
        }
        try (BitStream.Reader reader = new BitStream.Reader(MainActivity.getContext().openFileInput(MOVES_NAME))) {
            pgnGraph.unserializeMoveLine(reader, versionCode);
        } catch (Config.PGNException | IOException e) {
            Log.w(DEBUG_TAG, "unserializeMoveLine()", e);
        }
        Log.d(DEBUG_TAG, "unserializePgnGraph() done");
        return res;
    }

    private void serializeUI() {
        Log.d(DEBUG_TAG, "serializeUI()");
        try (BitStream.Writer writer = new BitStream.Writer(MainActivity.getContext().openFileOutput(STATUS_FILE_NAME, Context.MODE_PRIVATE))) {
            writer.write(versionCode, 8);
            if (rootFilAx == null) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                rootFilAx.serialize(writer);
            }
            currentPath.serialize(writer);
            writer.write(mode.getValue(), 3);
            if (mode == Mode.Setup) {
                setup.serialize(writer);
            }
            if (dgtBoardPad == null) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                dgtBoardPad.serialize(writer);
            }
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
            writer.write(doAnalysis ? 1 : 0, 1);
            puzzleData.serialize(writer);
            popups.serialize(writer);
        } catch (Config.PGNException | IOException e) {
            Log.d(DEBUG_TAG, "serializeUI", e);
        }
    }

    // return false on failure
    private void unserializeUI() throws Config.PGNException {
        try (BitStream.Reader reader = new BitStream.Reader(MainActivity.getContext().openFileInput(STATUS_FILE_NAME))) {
            int oldVersionCode = reader.read(8);
            if (versionCode != oldVersionCode) {
                throw new Config.PGNException(String.format("Old serialization %d ignored", oldVersionCode));
            }
            if (reader.read(1) == 1) {
                rootFilAx = new DocFilAx(reader);
            }
            currentPath = CpFile.unserialize(reader);
            if (currentPath == null) {
                currentPath = CpFile.fromPath(DEFAULT_DIRECTORY);
            }
            mode = Mode.value(reader.read(3));
            if (mode == Mode.Setup) {
                setup = new Setup(reader);
            } else if (mode == Mode.DgtGame) {
                mode = Mode.Game;   // do not launch DGT board connection automatically
            }
            if (reader.read(1) == 1) {
                dgtBoardPad.unserialize(reader);
            }
            if (reader.read(1) == 1) {
                nextPgnFile = (CpFile.PgnItem) CpFile.unserialize(reader);
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
        } catch (Config.PGNException e) {
            throw e;
        } catch(Throwable t){
            Log.e(DEBUG_TAG, t.getLocalizedMessage(), t);
        }
        chessPadLayout.redraw();
    }

    private void restoreData() {
        // unserialize data asynchronously, show progress bar
        // can be quite long, e.g. for SicilianGranPrix-merged.pgn
        if (dgtBoardInterface == null) {
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
                            Toast.makeText(MainActivity.getContext(), e.getMessage() + "\n" + "Try to reconnect device", Toast.LENGTH_LONG).show();
                            cancelSetup();
                        }
                    } else {
                        if (!isDestroying) {
                            cancelSetup();
                        }
                    }
                }
            });
            dgtBoardPad = new DgtBoardPad(dgtBoardInterface, getCpEventObserver());
        }

        unserializing = true;
        chessPadLayout.enableCommentEdit(false);
        try {
            unserializeUI();

            new CPAsyncTask(new CPExecutor() {
                @Override
                public void doInBackground(final CpFile.ProgressObserver progressObserver) {
                    reserveOOMBuffer();
                    Log.d(DEBUG_TAG, String.format("restoreData start, thread %s", Thread.currentThread().getName()));
                    unserializePgnGraph();
                }

                @Override
                public void onPostExecute() {
                    freeOOMBuffer();
                    Log.d(DEBUG_TAG, String.format("restoreData onPostExecute, thread %s", Thread.currentThread().getName()));
                    onCreateContinue();
                }

                @Override
                public void onExecuteException(Throwable e) {
                    freeOOMBuffer();
                    Log.e(DEBUG_TAG, "restoreData, onExecuteException, thread " + Thread.currentThread().getName(), e);
                    if (e instanceof OutOfMemoryError) {
                        popups.crashAlert(R.string.crash_oom);
                    } else {
                        popups.crashAlert(R.string.crash_cannot_load);
                    }
                }

            }).execute();
        } catch (Config.PGNException e) {
            Log.w(DEBUG_TAG, e);
            onCreateContinue();
        }
    }

    private boolean isUiFrozen() {
        return unserializing || merging || animationHandler != null;
    }

    public void onButtonClick(Command command) {
        Board board = getBoard();
        if (DEBUG) {
            if (board != null) {
                Log.d(DEBUG_TAG, String.format("click %s\n%s", command.toString(), board));
            }
        }
        switch (command) {
            case Start:
                selectedSquare = null;
                if (!isUiFrozen()) {
                    if (pgnGraph.isInit()) {
                        Log.d(DEBUG_TAG, "to prev game");
                        CpFile.PgnItem currentPgnItem = pgnGraph.getPgnItem();
                        toNextGame((CpFile.PgnFile)currentPgnItem.getParent(), currentPgnItem.getIndex() - 1);
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
                    if (isPuzzleMode() &&
                            !puzzleData.isDone() && puzzleData.solvedMoves <= pgnGraph.moveLine.size() - 1) {
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
                    CpFile.PgnItem currentPgnItem = pgnGraph.getPgnItem();
                    if (isPuzzleMode()) {
                        if (puzzleData.started && puzzleData.isUnsolved()) {
                            puzzleData.setFailed();
                        }
                        stopAnalysis();
                        if (mode == Mode.Puzzle) {
                            toNextGame((CpFile.PgnFile)currentPgnItem.getParent(), puzzleData.getNextIndex());
                        }
                    } else {    //  if (mode == Mode.Game) {
                        if (pgnGraph.isEnd()) {
                            Log.d(DEBUG_TAG, "to next game");
                            toNextGame((CpFile.PgnFile)currentPgnItem.getParent(), currentPgnItem.getIndex() + 1);
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
                } else if (mode == Mode.Setup) {
                    setup.setFlipped(!setup.isFlipped());
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
                currentPath = CpFile.fromPath(DocFilAx.getParentPath(currentPath.getAbsolutePath()));
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
                return pgnGraph.getPgnItem().getTags();

            case Setup:
                return setup.getTags();

            case DgtGame:
                return dgtBoardPad.getTags();

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

            case Statistics:
                String msg = puzzleData.statisticsToString(R.string.msg_puzzle_statistics);
                popups.dlgMessage(Popups.DialogType.ShowMessage, msg, 0, Popups.DialogButton.Ok);
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
                error = String.format("%s, %s", getSetupErr(pgnGraph.getParsingErrorNum()), MainActivity.getContext().getResources().getString(R.string.err_moves_ignored));
            }
            Toast.makeText(MainActivity.getContext(), error, Toast.LENGTH_LONG).show();
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

    // after loading a new pgnItem or ending setup
    // in Setup on 'setup ok' (null)
    // after ending Append (null)
    // after SaveModified?, savePgnGraph, (null)
    // after SaveModified, negative, (null)
    // after load, new pgnItem
    public void setPgnGraph(CpFile.PgnItem pgnItem) throws Config.PGNException {
        if (pgnGraph.isModified()) {
            Log.d(DEBUG_TAG, String.format(Locale.US, "setPgnGraph %s, old is modified", pgnItem));
            nextPgnFile = pgnItem;
            popups.launchDialog(Popups.DialogType.SaveModified);
        } else {
            if (pgnItem == null) {
                if (mode == Mode.Game || mode == Mode.Puzzle) {
                    pgnItem = nextPgnFile;
                }
            }

            if (pgnItem == null && setup != null) {
                pgnGraph = setup.toPgnGraph();
                pgngraphModified = true;
                cancelSetup();
            } else if (pgnItem != null) {
                if (this.nextMode != null) {
                    this.mode = this.nextMode;
                    this.nextMode = null;
                }
                Log.d(DEBUG_TAG, String.format("setPgnGraph loadPgnGraph %s", pgnItem));
                loadPgnGraph(pgnItem);
            }
            if (nextMode == Mode.DgtGame) {
                openDgtMode();
            }
        }
        popups.editTags = null;
        if (mode == Mode.Game) {
            notifyUci();
        } else {
            stopAnalysis();
        }
    }

    private void refreshScreenWithNewGraph() {
        pgngraphModified = true;
        chessPadLayout.showProgressBar(false);
        if (isPuzzleMode()) {
            pgnGraph.setPuzzleMode();
            pgnGraph.toInit();
            puzzleData.newPuzzle();
            Log.d(DEBUG_TAG, String.format("pgnGraph.toInit(), moveline %s", pgnGraph.moveLine.size()));
            flipped = (pgnGraph.getBoard().getFlags() & Config.BLACK) != 0;
        }
        nextPgnFile = null;
        selectedSquare = null;
        incomingInfoMessage = null;
        popups.promotionMove = null;
        cancelSetup();

        popups.editTags = null;
        if (mode == Mode.Game) {
            notifyUci();
        } else {
            stopAnalysis();
        }
    }

    private void loadPgnGraph(CpFile.PgnItem pgnItem) {
        new CPAsyncTask(new CPExecutor() {
            @Override
            public void doInBackground(final CpFile.ProgressObserver progressObserver) throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("loadPgnGraph start, thread %s", Thread.currentThread().getName()));
                reserveOOMBuffer();
                pgnGraph = new PgnGraph(pgnItem);
            }

            @Override
            public void onPostExecute() {
                freeOOMBuffer();
                Log.d(DEBUG_TAG, String.format("loadPgnGraph onPostExecute, thread %s", Thread.currentThread().getName()));
                refreshScreenWithNewGraph();
            }

            @Override
            public void onExecuteException(Throwable e) {
                freeOOMBuffer();
                Log.e(DEBUG_TAG, "loadPgnGraph, onExecuteException, thread " + Thread.currentThread().getName(), e);
                chessPadLayout.showProgressBar(false);
                chessPadLayout.invalidate();
                if (e instanceof OutOfMemoryError) {
                    popups.crashAlert(R.string.crash_oom);
                } else {
                    popups.crashAlert(R.string.crash_cannot_load);
                }
            }
        }).execute();
    }

    void notifyUci() {
        if (uci != null) {
            if (getPgnGraph() != null && getPgnGraph().getBoard() != null) {
                Log.d(DEBUG_TAG, String.format("notifyUci() %s", getPgnGraph().getBoard().toFEN()));
            }
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
        } while ( i < tokens.length && i < totalHints);
        return new String(sb);
    }

    void setPuzzles() {
        Log.d(DEBUG_TAG, String.format("set for puzzles %s, totalChildren=%s", currentPath.getAbsolutePath(), currentPath.getTotalChildren()));
        puzzleData.reset((CpFile.PgnFile)currentPath);
        mode = Mode.Puzzle;
        doAnalysis = false;
        toNextGame((CpFile.PgnFile)currentPath, puzzleData.getNextIndex());
    }

    void toNextGame(final CpFile.PgnFile pgnFile, final int nextIndex) {
        CpFile.PgnItem[] pgnItems = {null};

        new CPAsyncTask(new CPExecutor() {
            @Override
            public void doInBackground(final CpFile.ProgressObserver progressObserver) throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("toNextGame start, thread %s", Thread.currentThread().getName()));
                reserveOOMBuffer();
                pgnItems[0] = pgnFile.getPgnItem(nextIndex);
            }

            @Override
            public void onPostExecute() throws Config.PGNException {
                freeOOMBuffer();
                ChessPad.this.showMemory("toNextGame onPostExecute");
                if (nextIndex >= pgnFile.getTotalChildren()) {
                    // That means that items[0] is lastEntry. pgnFile.getPgnItem brings back all info only for the real requested index, so retry.
                    puzzleData.setTotalPuzzles(pgnFile.getTotalChildren());
                    toNextGame(pgnFile, puzzleData.getNextIndex());
                } else {
                    setPgnGraph(pgnItems[0]);
                }
            }

            @Override
            public void onExecuteException(Throwable e) {
                freeOOMBuffer();
                if (e.getCause() != null) {
                    e = e.getCause();
                }
                Log.e(DEBUG_TAG, "toNextGame, onExecuteException, thread " + Thread.currentThread().getName(), e);
                if (e instanceof OutOfMemoryError) {
                    popups.crashAlert(R.string.crash_oom);
                } else {
                    popups.crashAlert(R.string.crash_cannot_load);
                }
            }
        }).execute();
    }

    public void mergePgnGraph(final Popups.MergeData mergeData, final CPPostExecutor cpPostExecutor) {
        merging = true;
        chessPadLayout.enableCommentEdit(false);
        new CPAsyncTask(new CPExecutor() {
            @Override
            public void doInBackground(final CpFile.ProgressObserver progressObserver) {
                Log.d(DEBUG_TAG, String.format("mergePgnGraph start, thread %s", Thread.currentThread().getName()));
                reserveOOMBuffer();
                try {
                    pgnGraph.merge(mergeData);
                } catch (Config.PGNException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onPostExecute() throws Config.PGNException {
                freeOOMBuffer();
                Log.d(DEBUG_TAG, String.format("mergePgnGraph onPostExecute, thread %s", Thread.currentThread().getName()));
                if (cpPostExecutor != null) {
                    cpPostExecutor.onPostExecute();
                }
                merging = false;
                chessPadLayout.enableCommentEdit(true);
                pgngraphModified = true;
                String msg = String.format(MainActivity.getContext().getResources().getString(R.string.msg_merge_count), mergeData.merged);
                Toast.makeText(MainActivity.getContext(), msg, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onExecuteException(Throwable e) {
                freeOOMBuffer();
                merging = false;
                chessPadLayout.enableCommentEdit(true);
                Log.e(DEBUG_TAG, "mergePgnGraph, onExecuteException, thread " + Thread.currentThread().getName(), e);
                if (e instanceof OutOfMemoryError) {
                    // rollback, memory fragmented, no good
                    PgnGraph pgnGraph = getPgnGraph();
                    Move lastMove = pgnGraph.getCurrentMove();
                    pgnGraph.delCurrentMove();
                    try {
                        pgnGraph.addMove(lastMove);  // restore
                    } catch (Config.PGNException ex) {
                        ex.printStackTrace();
                    }
                    popups.crashAlert(R.string.crash_oom);
                } else {
                    popups.crashAlert(R.string.crash_cannot_merge);
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

    // when deletion no need to updateMoves
    public void savePgnGraph(final boolean updateMoves, final CPPostExecutor cpPostExecutor) {
        new CPAsyncTask(new CPExecutor() {
            @Override
            public void doInBackground(final CpFile.ProgressObserver progressObserver) throws Config.PGNException {
                long start = new Date().getTime();
                if (DEBUG) {
                    Log.d(DEBUG_TAG, String.format("savePgnGraph start, thread %s", Thread.currentThread().getName()));
                }
                reserveOOMBuffer();
                pgnGraph.save(updateMoves);
                if (true) {
                    long end = new Date().getTime();
                    long duration = end - start;
                    Log.d(DEBUG_TAG, String.format("savePgnGraph end %s, thread %s", duration, Thread.currentThread().getName()));
                }
            }

            @Override
            public void onPostExecute() throws Config.PGNException {
                freeOOMBuffer();
                Log.d(DEBUG_TAG, String.format("savePgnGraph onPostExecute, thread %s", Thread.currentThread().getName()));
                if (cpPostExecutor != null) {
                    cpPostExecutor.onPostExecute();
                }
            }

            @Override
            public void onExecuteException(Throwable e) {
                ChessPad.freeOOMBuffer();
                if (e.getCause() != null) {
                    e = e.getCause();
                }
                Log.e(DEBUG_TAG, "savePgnGraph, onExecuteException, thread " + Thread.currentThread().getName(), e);
                if (e instanceof OutOfMemoryError) {
                    popups.crashAlert(R.string.crash_oom);
                } else {
                    popups.crashAlert(R.string.crash_cannot_save);
                }
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
                                Toast.makeText(MainActivity.getContext(), R.string.msg_3_fold_repetition, Toast.LENGTH_LONG).show();
                                if (newMove.comment == null) {
                                    newMove.comment = MainActivity.getContext().getResources().getString(R.string.msg_3_fold_repetition);
                                }
                            } else if (pgnGraph.getBoard(newMove).getReversiblePlyNum() == 100) {
                                Toast.makeText(MainActivity.getContext(), R.string.msg_50_reversible_moves, Toast.LENGTH_LONG).show();
                                if (newMove.comment == null) {
                                    newMove.comment = MainActivity.getContext().getResources().getString(R.string.msg_50_reversible_moves);
                                }
                            } else if ((newMove.moveFlags & Config.FLAGS_STALEMATE) != 0) {
                                Toast.makeText(MainActivity.getContext(), R.string.msg_stalemate, Toast.LENGTH_LONG).show();
                                if (newMove.comment == null) {
                                    newMove.comment = MainActivity.getContext().getResources().getString(R.string.msg_stalemate);
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
                            Toast.makeText(MainActivity.getContext(), R.string.msg_puzzle_keep_solving, Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(MainActivity.getContext(), R.string.err_not_solution, Toast.LENGTH_SHORT).show();
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

    // With large files the program crashes because of fragmentation.
    // On https://stackoverflow.com/questions/59959384/how-to-figure-out-when-android-memory-is-too-fragmented?noredirect=1#comment106055443_59959384
    // greeble31 suggested to allocate certain amount of memory and free it on OOM and process abort. Then Android will still have memory to continue.
    // For some reason SoftReference does not work with API 22, releases the buffer on the 1st GC
    public static void reserveOOMBuffer() {
        freeOOMBuffer();
        oomReserve[0] = new byte[10 * 1024 * 1024];
    }

    public static void freeOOMBuffer() {
        oomReserve[0] = null;
        System.gc();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    public void showMemory(String msg) {
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = Runtime.getRuntime().totalMemory() - freeMemory;
        Log.d(DEBUG_TAG, String.format("%s, used %s, free %s", msg,
                NumberFormat.getInstance().format(usedMemory),
                NumberFormat.getInstance().format(freeMemory)));
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
                Toast.makeText(MainActivity.getContext(), msg.obj.toString(), Toast.LENGTH_LONG).show();
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