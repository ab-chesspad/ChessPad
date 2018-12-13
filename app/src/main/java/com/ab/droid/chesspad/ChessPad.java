package com.ab.droid.chesspad;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
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
import com.ab.pgn.PgnGraph;
import com.ab.pgn.PgnItem;
import com.ab.pgn.Square;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

//import android.hardware.usb.UsbDevice;

/**
 * main activity
 * Created by Alexander Bootman on 8/20/16.
 */
public class ChessPad extends AppCompatActivity {
    public static boolean DEBUG = false;
    protected final String DEBUG_TAG = this.getClass().getName();
    //* uncomment this line to replay crash providing the correct file name
    private final String CRASH_RESTORE = null;
    /*/
        protected String CRASH_RESTORE = "cp-crash-2017-01-13_11-36-55";
    //*/
    static final String
        STATUS_FILE_NAME = ".ChessPad.state",
        CURRENT_FILE_NAME = ".ChessPad.pgngraph",
        MOVELINE_FILE_NAME = ".ChessPad.moveline",
        CURRENT_PGN_NAME = ".ChessPad.current.pgn",
        DEFAULT_DIRECTORY = "ChessPad",
        str_dummy = null;

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
        Reverse(++i),
        Delete(++i),
        Menu(++i),
        ShowGlyphs(++i),
        ShowFiles(++i),
        Append(++i),
        Merge(++i),
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

    private enum MenuCommand {
        Load,
        Merge,
        Save,
        Append,
        Setup,
        CancelSetup,
        About,
    }

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
    public int versionCode;

    transient public String versionName;
    transient public Resources resources;
    transient public int timeoutDelta = animationTimeout / 4;
    transient private AnimationHandler animationHandler;
    transient boolean unserializing = false;
    transient boolean merging = false;
    transient protected ChessPadView chessPadView;
    transient private String[] setupErrs;
    transient private boolean freshStart = true;
    transient private boolean pgngraphModified = false;

    // Always followed by onStart()
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
        init();
        freshStart = true;
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
            unserializeUI();
            chessPadView.enableCommentEdit(false);
            unserializePgnGraph();
        }
    }

    // Called when the activity will start interacting with the user
    // after onStart() or onPause()
    // @RequiresApi(api = Build.VERSION_CODES.HONEYCOMB_MR1)
    @Override
    public void onResume() {
        super.onResume();
        Log.d(DEBUG_TAG, "onResume()");
        chessPadView.invalidate();
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
        if(merging) {
            return;     // skip serialization
        }
        serializeUI();
        serializePgnGraph();
    }

//    // The final call you receive before your activity is destroyed
//    // becomes killable
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        Log.d(DEBUG_TAG, "onDestroy()");
//    }

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
            Log.e(DEBUG_TAG, t.toString(), t);
        }
    }

    protected boolean isSaveable() {
        return pgnGraph.isModified() && pgnGraph.getPgn().getIndex() != -1;
    }

    public void setComment(String newComment) {
        if (!isUiFrozen()) {
            pgnGraph.setComment(newComment);
        }
    }

    protected List<MenuItem> getMenuItems() {
        List<MenuItem> menuItems = new LinkedList<>();
        menuItems.add(new MenuItem(MenuCommand.Load, getResources().getString(R.string.menu_load)));
        boolean enableMerge = mode == Mode.Game && pgnGraph.getInitBoard().equals(new Board());
        menuItems.add(new MenuItem(MenuCommand.Merge, getResources().getString(R.string.menu_merge), enableMerge));
        menuItems.add(new MenuItem(MenuCommand.Save, getResources().getString(R.string.menu_save), isSaveable()));
        menuItems.add(new MenuItem(MenuCommand.Append, getResources().getString(R.string.menu_append), mode == Mode.Game));
        if (mode == Mode.Game) {
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

    // serialize graph and moveline
    private void serializePgnGraph() {
        Log.d(DEBUG_TAG, String.format("serializePgnGraph() pgngraphModified=%s", pgngraphModified));
        FileOutputStream fos = null;
        File outDir = new File(PgnItem.getRoot(), DEFAULT_DIRECTORY);
        if(pgngraphModified || pgnGraph.isModified()) {
            DataOutputStream dos = null;
            try {
                dos = new DataOutputStream(new FileOutputStream(new File(outDir.getAbsolutePath(), CURRENT_PGN_NAME).getAbsolutePath()));
                pgnGraph.getPgn().serialize(dos);
                dos.close();
            } catch (IOException|Config.PGNException e) {
                Log.e(DEBUG_TAG, "serializePgnGraph", e);
            }

            try {
                fos = new FileOutputStream(new File(outDir.getAbsolutePath(), CURRENT_FILE_NAME));
            } catch (FileNotFoundException e) {
                // should never happen
                Log.e(DEBUG_TAG, "serializePgnGraph()", e);
            }

            if (fos != null) {
                final PgnItem.Pgn _pgn = new PgnItem.Pgn(new File(outDir.getAbsolutePath(), CURRENT_FILE_NAME).getAbsolutePath());
                final PgnItem.Item _item = new PgnItem.Item(_pgn, "status");
                _item.setMoveText(pgnGraph.toPgn());
                try {
                    _item.save(null);
                } catch (Config.PGNException e) {
                    Log.e(DEBUG_TAG, "serializePgnGraph", e);
                }
                dos = new DataOutputStream(fos);
                try {
                    pgnGraph.serializeGraph(dos, versionCode);
                    dos.close();
                    fos.close();
                } catch (Config.PGNException | IOException e) {
                    Log.e(DEBUG_TAG, "serializePgnGraph", e);
                }
            }
            pgngraphModified = false;
        }

        try {
            fos = new FileOutputStream(new File(outDir.getAbsolutePath(), MOVELINE_FILE_NAME));
        } catch (FileNotFoundException e) {
            // should never happen
            Log.e(DEBUG_TAG, "serializePgnGraph() - moveline", e);
        }

        if (fos != null) {
            BitStream.Writer writer = new BitStream.Writer(fos);
            try {
                pgnGraph.serializeMoveLine(writer, versionCode);
                writer.close();
                fos.close();
            } catch (Config.PGNException | IOException e) {
                Log.e(DEBUG_TAG, "serializePgnGraph - moveline", e);
            }
        }
    }

    // unserialize graph and moveline
    private void unserializePgnGraph() {
        try {
            pgnGraph = new PgnGraph(new Board());
            FileInputStream fis = null;
            final File inDir = new File(PgnItem.getRoot(), DEFAULT_DIRECTORY);

            try {
                fis = new FileInputStream(new File(inDir.getAbsolutePath(), MOVELINE_FILE_NAME));
                BitStream.Reader reader = new BitStream.Reader(fis);
                pgnGraph.unserializeMoveLine(reader, versionCode);
            } catch (FileNotFoundException|Config.PGNException e) {
                Log.w(DEBUG_TAG, "unserializePgnGraph()", e);
                chessPadView.invalidate();
                unserializing = false;
                chessPadView.enableCommentEdit(true);
                return;
            }

            final LinkedList<Move> moveLine = pgnGraph.moveLine;
            PgnItem pgn = null;
            // headers:
            try {
                DataInputStream dis = new DataInputStream(new FileInputStream(new File(inDir.getAbsolutePath(), CURRENT_PGN_NAME)));
                pgn = PgnItem.unserialize(dis);
                dis.close();
            } catch (IOException|Config.PGNException e) {
                Log.e(DEBUG_TAG, "serializePgnGraph", e);
            }

            // pgn text:
            byte[] content = null;
            try {
                File f = new File(inDir.getAbsolutePath(), CURRENT_FILE_NAME);
                int length = (int)f.length();
                content = new byte[length];
                BufferedInputStream buf = new BufferedInputStream(new FileInputStream(f));
                int l = buf.read(content, 0, content.length);
                buf.close();
            } catch (IOException e) {
                Log.w(DEBUG_TAG, "unserializePgnGraph()", e);
                content = null;
            }
            if (content == null) {
                unserializing = false;
            } else {
                final PgnItem.Item finalPgn = (PgnItem.Item)pgn;
                finalPgn.setMoveText(new String(content));
                loadPgnGraph(finalPgn, new CPPostExecutor() {
                    @Override
                    public void onPostExecute() throws Config.PGNException {
                        Log.d(DEBUG_TAG, String.format("unserializePgnGraph onPostExecute, thread %s", Thread.currentThread().getName()));
                        pgnGraph.setPgn(finalPgn);
                        pgnGraph.moveLine = moveLine;
                        chessPadView.invalidate();
                        unserializing = false;
                        chessPadView.enableCommentEdit(true);
                    }
                });
            }
        } catch (Config.PGNException e) {
            // should not happen
            Log.e(DEBUG_TAG, "unserializePgnGraph()", e);
        }
    }

//    private void navigate(List<Move> moveLine) {
//        for(Move move : moveLine) {
//            Board board = pgnGraph.getBoard(move);
//            Move m = board.getMove();
//            while(m != null && !move.equals(m)) {
//                m = m.getVariation();
//            }
//            if( m == null) {
//                break;
//            }
//
//        }
//    }

    private void serializeUI() {
        Log.d(DEBUG_TAG, "serializeUI()");
        try {
            BitStream.Writer writer = new BitStream.Writer(openFileOutput(STATUS_FILE_NAME, Context.MODE_PRIVATE));
            this.serializeUI(writer, versionCode);
        } catch (Config.PGNException | IOException e) {
            Log.d(DEBUG_TAG, "serializeUI", e);
        }
    }

    private void unserializeUI() {
        Log.d(DEBUG_TAG, "unserializeUI()");
        FileInputStream fis = null;

        try {
            try {
                if(CRASH_RESTORE != null) {
                    File inDir = new File(PgnItem.getRoot(), DEFAULT_DIRECTORY);
                    fis = new FileInputStream(new File(inDir.getAbsolutePath(), CRASH_RESTORE));
                } else {
                    fis = openFileInput(STATUS_FILE_NAME);
                    if(DEBUG) {
                        File f = new File(new File(PgnItem.getRoot(), ChessPad.DEFAULT_DIRECTORY), STATUS_FILE_NAME);
                        FileOutputStream fos = new FileOutputStream(f);
                        byte[] buf = new byte[8192];
                        int len;
                        while( (len = fis.read(buf)) > 0) {
                            fos.write(buf, 0, len);
                        }
                        fos.close();
                        fis = openFileInput(STATUS_FILE_NAME);
                    }
                }
            } catch (FileNotFoundException e) {
                Log.w(DEBUG_TAG, "unserializeUI() 1", e);
            }

            if (fis != null) {
                BitStream.Reader reader = new BitStream.Reader(fis);
                unserializeUI(reader, versionCode);
                fis.close();
            }
        } catch (Throwable t) {
            Log.e(DEBUG_TAG, "unserializeUI()", t);
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
        }

        chessPadView.redraw();
        if(mode == Mode.Setup) {
            setup.setChessPadView(chessPadView);
            setup.onValueChanged(null);
        }
    }

    private void serializeUI(BitStream.Writer writer, int versionCode) throws Config.PGNException {
        try {
            writer.write(versionCode, 4);
            currentPath.serialize(writer);
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

    private boolean unserializeUI(BitStream.Reader reader, int versionCode) throws Config.PGNException {
        try {
            int oldVersionCode;
            if (versionCode != (oldVersionCode = reader.read(4))) {
                Log.w(DEBUG_TAG, String.format("Old serialization %d ignored", oldVersionCode));
                return false;
            }
            currentPath = PgnItem.unserialize(reader);
            if (reader.read(1) == 1) {
                mode = Mode.Setup;
                setup = new Setup(reader);
            } else {
                mode = Mode.Game;
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

    public boolean isUiFrozen() {
        return unserializing || merging || animationHandler != null;
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
        Board board = pgnGraph.getBoard();
        if(DEBUG) {
            if(board != null) {
                Log.d(DEBUG_TAG, String.format("click %s\n%s", command.toString(), board.toString()));
            }
        }
        try {
            switch (command) {
                case Start:
                    selectedSquare = null;
                    if (!isUiFrozen()) {
                        pgnGraph.toInit();
                        chessPadView.invalidate();
                    } else {
                        if(animationHandler != null) {
//                    animationHandler.increaseTimeout();
                            animationTimeout += timeoutDelta;
                            animationHandler.setTimeout(animationTimeout);
                        }
                    }
                    break;

                case Prev:
                    if (!isUiFrozen()) {
                        selectedSquare = null;
                        pgnGraph.toPrev();
                        chessPadView.invalidate();
                    }
                    break;

                case PrevVar:
                    if (!isUiFrozen()) {
                        selectedSquare = null;
                        pgnGraph.toPrevVar();
                        chessPadView.invalidate();
                    }
                    break;

                case Next:
                    if (!isUiFrozen()) {
                        selectedSquare = null;
                        List<Move> variations = pgnGraph.getVariations();
                        if (variations == null) {
                            pgnGraph.toNext();
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
                    }
                    break;

                case End:
                    selectedSquare = null;
                    if (!isUiFrozen()) {
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
                        if(animationHandler != null) {
                            animationTimeout -= timeoutDelta;
                            if (animationTimeout <= 0) {
                                animationTimeout = 1;
                            }
                            animationHandler.setTimeout(animationTimeout);
                        }
                    }
                    break;

                case Reverse:
                    reversed = !reversed;
                    chessPadView.invalidate();
                    break;

                case ShowGlyphs:
                    if (!isUiFrozen()) {
                        if(pgnGraph.okToSetGlyph()) {
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
                        popups.launchDialog(Popups.DialogType.DeleteYesNo);
                    }
                    break;

                case EditHeaders:
                    if (!isUiFrozen()) {
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
                    pgngraphModified = true;
                    currentPath = currentPath.getParent();
                    chessPadView.invalidate();
                }
            });
        } else {
            pgnGraph.delCurrentMove();
            chessPadView.invalidate();
        }
    }

    public boolean onSquareClick(Square clicked) {
        if (isUiFrozen()) {
            return false;
        }
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
        if(DEBUG) {
            Log.d(DEBUG_TAG, String.format("board onFling(%s)", clicked.toString()));
        }
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
                savePgnGraph(true, new CPPostExecutor() {
                    @Override
                    public void onPostExecute() throws Config.PGNException {
                        setPgnGraph(null);
                    }
                });
                break;

            case Append:
                popups.launchDialog(Popups.DialogType.Append);
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
    // in Setup on 'setup ok' (null)
    // after ending Append (null)
    // after SaveModified?, savePgnGraph, (null)
    // after SaveModified, negative, (null)
    // after load, new item
    public void setPgnGraph(PgnItem.Item item ) throws Config.PGNException {
        if(pgnGraph.isModified()) {
            Log.d(DEBUG_TAG, String.format("setPgnGraph %s, old is modified", item));
            nextPgnItem = item;
            popups.launchDialog(Popups.DialogType.SaveModified);
        } else {
            if (mode == Mode.Game && item == null) {
                item = nextPgnItem;
            }

            if (item == null && setup != null) {
                pgnGraph = setup.toPgnGraph();
                pgngraphModified = true;
            } else if(item != null){
                loadPgnGraph(item, new CPPostExecutor() {
                    @Override
                    public void onPostExecute() throws Config.PGNException {
                        chessPadView.invalidate();
                    }
                });
            }
            cancelSetup();
            nextPgnItem = null;
            selectedSquare = null;
            popups.promotionMove = null;
        }
    }

    public void loadPgnGraph(final PgnItem.Item item, final CPPostExecutor cpPostExecutor) throws Config.PGNException {
        new CPAsyncTask(chessPadView, new CPExecutor() {
            @Override
            public void onPostExecute() throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("savePgnGraph onPostExecute, thread %s", Thread.currentThread().getName()));
                if(cpPostExecutor != null) {
                    cpPostExecutor.onPostExecute();
                }
            }

            @Override
            public void onExecuteException(Config.PGNException e) throws Config.PGNException {
                Log.e(DEBUG_TAG, "loadPgnGraph, onExecuteException, thread " + Thread.currentThread().getName(), e);
                popups.crashAlert(R.string.crash_cannot_load);
            }

            @Override
            public void doInBackground(final ProgressPublisher progressPublisher) throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("loadPgnGraph start, thread %s", Thread.currentThread().getName()));
                pgnGraph = new PgnGraph(item, new PgnItem.ProgressObserver() {
                    @Override
                    public void setProgress(int progress) {
                        if(DEBUG) {
                            Log.d(DEBUG_TAG, String.format("Offset %d%%, thread %s", progress, Thread.currentThread().getName()));
                        }
                        progressPublisher.publishProgress(progress);

                    }
                });
                pgngraphModified = true;
            }
        }).execute();
    }

    public void mergePgnGraph(final Popups.MergeData mergeData, final CPPostExecutor cpPostExecutor) throws Config.PGNException {
        merging = true;
        chessPadView.enableCommentEdit(false);
        final PgnItem.Pgn _pgn = new PgnItem.Pgn(mergeData.pgnPath);
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
            public void onExecuteException(Config.PGNException e) throws Config.PGNException {
                merging = false;
                chessPadView.enableCommentEdit(true);
                Log.e(DEBUG_TAG, "mergePgnGraph, onExecuteException, thread " + Thread.currentThread().getName(), e);
                popups.crashAlert(R.string.crash_cannot_save);
            }

            @Override
            public void doInBackground(final ProgressPublisher progressPublisher) throws Config.PGNException {
                Log.d(DEBUG_TAG, String.format("mergePgnGraph start, thread %s", Thread.currentThread().getName()));
                pgnGraph.merge(mergeData,
                    new PgnItem.ProgressObserver() {
                        @Override
                        public void setProgress(int progress) {
                            if(DEBUG) {
                                Log.d(DEBUG_TAG, String.format("Offset %d%%, thread %s", progress, Thread.currentThread().getName()));
                            }
                            progressPublisher.publishProgress(progress);

                        }
                });
            }
        }).execute();
    }

    public void savePgnGraph(final boolean updateMoves, final CPPostExecutor cpPostExecutor) throws Config.PGNException {
        if(pgnGraph.getPgn().getIndex() == -1) {
            popups.cpPgnItemListAdapter = null;     // enforce reload
        }
        new CPAsyncTask(chessPadView, new CPExecutor() {
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
                if(DEBUG) {
                    Log.d(DEBUG_TAG, String.format("savePgnGraph start, thread %s", Thread.currentThread().getName()));
                }
                pgnGraph.save(updateMoves, new PgnItem.ProgressObserver() {
                    @Override
                    public void setProgress(int progress) {
                        if(DEBUG) {
                            Log.d(DEBUG_TAG, String.format("Offset %d%%, thread %s", progress, Thread.currentThread().getName()));
                        }
                        progressPublisher.publishProgress(progress);

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