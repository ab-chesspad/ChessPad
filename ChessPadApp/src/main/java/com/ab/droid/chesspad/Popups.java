package com.ab.droid.chesspad;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.ab.pgn.BitStream;
import com.ab.pgn.Config;
import com.ab.pgn.CpFile;
import com.ab.pgn.Move;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnGraph;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * various dialogs for ChessPad
 * Created by Alexander Bootman on 10/30/16.
 */
public class Popups {
    private static final boolean DEBUG = false;
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    private static int j = -1;

    public enum DialogType {
        None(++j),
        Glyphs(++j),
        Variations(++j),
        DeleteYesNo(++j),
        Promotion(++j),
        Menu(++j),
        ShowMessage(++j),
        Load(++j),
        Puzzle(++j),
        Append(++j),
        Merge(++j),
        Tags(++j),
        SaveModified(++j),
        FicsLogin(++j),
        SelectPuzzlebotOptions(++j),
        SelectEndgamebotOptions(++j),
        ;

        private final int value;
        private static final DialogType[] values = DialogType.values();

        DialogType(int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }

        static DialogType value(int v) {
            return values[v];
        }

        public static int total() {
            return values.length;
        }
    }

    public enum DialogButton {
        None,
        YesNoCancel,
        YesNo,
        Ok,
        OkCancel,
    }

    private static final String ADD_TAG_LABEL = "";
    private final ChessPad chessPad;

    private static final List<Integer> wPromotionList = Arrays.asList(R.drawable.qw, R.drawable.rw, R.drawable.bw, R.drawable.nw);
    private static final List<Integer> bPromotionList = Arrays.asList(R.drawable.qb, R.drawable.rb, R.drawable.bb, R.drawable.nb);
    private static final List<Integer> promotionPieces = Arrays.asList(Config.QUEEN, Config.ROOK, Config.BISHOP, Config.KNIGHT);
    private static final String endgamebotOptionsStringSep = " - ";

    private final List<String> glyphs;
    private final List<String> puzzlebotOptions;
    private final List<Pair<String, String>> endgamebotOptions;

    private boolean fileListShown = false;
    Move promotionMove;
    private Dialog currentAlertDialog;
    private MergeData mergeData, appendData;

    DialogType dialogType = DialogType.None;
    private String dialogMsg = null;
    private List<Pair<String, String>> editTags;
    private PgnFileListAdapter pgnFileListAdapter;
    private PgnFileListAdapter mergePgnFileListAdapter;     // I need it here to refer it from within its creation. Any more graceful way to do it?

    Popups(ChessPad chessPad) {
        this.chessPad = chessPad;
        appendData = new MergeData();
        mergeData = new MergeData();
        glyphs = Arrays.asList(getResources().getStringArray(R.array.glyphs));
        puzzlebotOptions = Arrays.asList(getResources().getStringArray(R.array.puzzlebot_options));
        endgamebotOptions = new ArrayList<>();
        String[] options = getResources().getStringArray(R.array.endgamebot_options);
        for(String option : options) {
            String[] parts = option.split(endgamebotOptionsStringSep);
            endgamebotOptions.add(new Pair<>(parts[0], parts[1]));
        }
    }

    public void serialize(BitStream.Writer writer) throws Config.PGNException {
        try {
            if (fileListShown) {
                writer.write(1, 1);
            } else {
                writer.write(0, 1);
            }
            if (promotionMove == null) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                promotionMove.serialize(writer);
            }
            writer.write(dialogType.getValue(), 4);
            writer.writeString(dialogMsg);
            CpFile.serializeTagList(writer, editTags);
            appendData.serialize(writer);
            mergeData.serialize(writer);
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    void unserialize(BitStream.Reader reader) throws Config.PGNException {
        try {
            fileListShown = reader.read(1) == 1;
            if (reader.read(1) == 1) {
                promotionMove = new Move(reader, chessPad.getBoard());
            }
            this.dialogType = DialogType.value(reader.read(4));
            dialogMsg = reader.readString();
            if (dialogMsg == null || dialogMsg.isEmpty()) {
                dialogMsg = null;
            }
            editTags = CpFile.unserializeTagList(reader);
            appendData = new MergeData(reader);
            mergeData = new MergeData(reader);
            afterUnserialize();
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    void afterUnserialize() {
        // let UI be redrawn, otherwise ProgressBar will not show up
        new CPAsyncTask(new CPExecutor() {
            @Override
            public void onExecuteException(Config.PGNException e) {
                Log.e(DEBUG_TAG, "sleep", e);
            }

            @Override
            public void doInBackground(ProgressPublisher progressPublisher) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.e(DEBUG_TAG, "sleep", e);
                }
            }

            @Override
            public void onPostExecute() {
                launchDialog(Popups.this.dialogType);
            }
        }).execute();
    }

    private Resources getResources() {
        return chessPad.getResources();
    }

    void crashAlert(int iMsg) {
        String msg = getResources().getString(iMsg);
        crashAlert(msg);
    }

    private void crashAlert(String msg) {
        dlgMessage(Popups.DialogType.ShowMessage, msg, R.drawable.exclamation, Popups.DialogButton.Ok);
    }

    void relaunchDialog() {
        if (currentAlertDialog == null) {
            return;
        }
        DialogType dialogType = this.dialogType;
        dismissDlg();
        launchDialog(dialogType);
    }


    void launchDialog(final DialogType dialogType) {
        CpFile cpFile;
        switch (dialogType) {
            case Append:
            case Merge:
            case Puzzle:
                dlgMergeOrAppend(dialogType);
                break;

            case DeleteYesNo:
                if (chessPad.isFirstMove()) {
                    dlgMessage(Popups.DialogType.DeleteYesNo, getResources().getString(R.string.msg_del_game), R.drawable.exclamation, Popups.DialogButton.YesNo);
                } else {
                    dlgMessage(Popups.DialogType.DeleteYesNo, getResources().getString(R.string.msg_del_move), 0, Popups.DialogButton.YesNo);
                }
                break;

            case FicsLogin:
                ficsLogin();
                break;

            case Glyphs:
                launchDialog(dialogType, new TextArrayAdapter(glyphs, chessPad.getPgnGraph().getGlyph()));
                break;

            case Tags:
                if (editTags == null) {
                    editTags = chessPad.getTags();
                    editTags.add(new Pair<>(ADD_TAG_LABEL, ""));
                }
                launchTagEditor();
                break;

            case Load:
                int selectedPgnFile = -1;
                cpFile = chessPad.getPgnGraph().getPgn();
                if (cpFile != null) {
                    try {
                        selectedPgnFile = cpFile.parentIndex(chessPad.currentPath);
                    } catch (Config.PGNException e) {
                        Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
                    }
                }
                launchDialog(dialogType, getPgnFileListAdapter(chessPad.currentPath, selectedPgnFile));
                break;

            case Menu:
                final List<ChessPad.MenuItem> menuItems = chessPad.getMenuItems();
                launchDialog(dialogType, new TextArrayAdapter(menuItems, -1) {
                    @Override
                    protected void setRowViewHolder(RowViewHolder rowViewHolder, int position) {
                        super.setRowViewHolder(rowViewHolder, position);
                        if (menuItems.get(position).isEnabled()) {
                            rowViewHolder.valueView.setTextColor(Color.BLACK);
                        } else {
                            rowViewHolder.valueView.setTextColor(Color.LTGRAY);
                        }
                    }

                    @Override
                    protected void onConvertViewClick(int position) {
                        Log.d(DEBUG_TAG, "TextArrayAdapter.subclass.onConvertViewClick " + position);
                        if( menuItems.get(position).isEnabled()) {
                            super.onConvertViewClick(position);
                        }
                    }
                });
                break;


            case Promotion:
                List<Integer> _promotionList;
                if ((this.promotionMove.moveFlags & Config.BLACK) == 0) {
                    _promotionList = wPromotionList;
                } else {
                    _promotionList = bPromotionList;
                }
                final List<Integer> promotionList = _promotionList;
                launchDialog(dialogType, new TextArrayAdapter(promotionList, -1) {
                    @Override
                    protected void setRowViewHolder(RowViewHolder rowViewHolder, int position) {
                        super.setRowViewHolder(rowViewHolder, position);
                        rowViewHolder.valueView.setText("");
                        rowViewHolder.valueView.setCompoundDrawablesWithIntrinsicBounds(promotionList.get(position), 0, 0, 0);
                    }
                });
                break;

            case SelectPuzzlebotOptions:
                launchDialog(dialogType, new TextArrayAdapter(puzzlebotOptions, 0) {
                    @Override
                    protected void setRowViewHolder(RowViewHolder rowViewHolder, int position) {
                        super.setRowViewHolder(rowViewHolder, position);
                    }
                });
                break;

            case SelectEndgamebotOptions:
                launchDialog(dialogType, new EndgameOptionsAdapter(endgamebotOptions) {
                    @Override
                    protected void setRowViewHolder(RowViewHolder rowViewHolder, int position) {
                        super.setRowViewHolder(rowViewHolder, position);
                    }
                });
                break;

            case SaveModified:
                dlgMessage(Popups.DialogType.SaveModified, getResources().getString(R.string.msg_save), R.drawable.exclamation, Popups.DialogButton.YesNoCancel);
                break;

            case ShowMessage:
                aboutDialog();
                break;

            case Variations:
                launchDialog(dialogType, new TextArrayAdapter(chessPad.getPgnGraph().getVariations(), 0) {
                    @Override
                    protected void setRowViewHolder(RowViewHolder rowViewHolder, final int position) {
                        super.setRowViewHolder(rowViewHolder, position);
                        rowViewHolder.valueView.setText(((Move)getValues().get(position)).toCommentedString());
                    }
                });       // 1st one is main line
                break;

        }

        if(currentAlertDialog != null) {
            this.dialogType = dialogType;
            currentAlertDialog.setOnCancelListener((dialog) -> {
                // When user touches outside of dialog bounds,
                // the dialog gets canceled and this method executes.
                Log.d(DEBUG_TAG, "dialog cancelled");
                dismissDlg();
            });
        }
    }

    private void returnFromDialog(DialogType dialogType, Object selectedValue, int selected) {
        this.dialogType = DialogType.None;
        if(dialogType == DialogType.SaveModified) {
            switch (selected) {
                case DialogInterface.BUTTON_POSITIVE:
                    if(chessPad.isSaveable()) {
                        chessPad.savePgnGraph(true, () -> chessPad.setPgnGraph(null));
                    } else {
                        dismissDlg();
                        launchDialog(DialogType.Append);
                        return;
                    }
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    try {
                        chessPad.getPgnGraph().setModified(false);
                        dismissDlg();
                        chessPad.setPgnGraph(null);
                        return;
                    } catch (Config.PGNException e) {
                        Log.e(DEBUG_TAG, "dlgMessage()", e);
                    }
                    break;

                case DialogInterface.BUTTON_NEUTRAL:
                    Log.e(DEBUG_TAG, "SaveModified - return to opened game or setup");
                    break;

            }
            dismissDlg();
            return;
        }

        if (selected == DialogInterface.BUTTON_NEGATIVE) {
            dismissDlg();
            chessPad.chessPadView.invalidate();
            return;
        }
        switch (dialogType) {
            case ShowMessage:
                dismissDlg();
                break;

            case Glyphs:
                dismissDlg();
                if(chessPad.mode == ChessPad.Mode.Game || chessPad.mode == ChessPad.Mode.Puzzle) {
                    chessPad.getPgnGraph().setGlyph(selected);
                } else if(chessPad.mode == ChessPad.Mode.DgtGame) {
                    chessPad.dgtBoardPad.getPgnGraph().getCurrentMove().setGlyph(selected);
                }
                break;

            case Variations:
                dismissDlg();
                Move variation = (Move) selectedValue;
                chessPad.getPgnGraph().toVariation(variation);
                chessPad.notifyUci();
                break;

            case DeleteYesNo:
                dismissDlg();
                chessPad.delete();
                break;

            case Promotion:
                dismissDlg();
                promotionMove.setPiecePromoted(promotionPieces.get(selected) | (promotionMove.moveFlags & Config.BLACK));
                chessPad.completePromotion(promotionMove);
                break;

            case Menu:
                dismissDlg();
                chessPad.executeMenuCommand(((ChessPad.MenuItem) selectedValue).getCommand());
                break;

            case SelectPuzzlebotOptions:
                dismissDlg();
                chessPad.setCommandParam(selected);
                break;

            case SelectEndgamebotOptions:
                dismissDlg();
                chessPad.setCommandParam(endgamebotOptions.get(selected));
                break;

            case Load:
                if (selectedValue instanceof CpFile.Item) {
                    Log.d(DEBUG_TAG, String.format("selectedSquare %s", selectedValue.toString()));
                    dismissDlg();
                    final CpFile.Item actualItem = (CpFile.Item) selectedValue;
                    new CPAsyncTask(chessPad.chessPadView, new CPExecutor() {
                        @Override
                        public void onPostExecute() {
                            Log.d(DEBUG_TAG, String.format("gePgnFile end, thread %s", Thread.currentThread().getName()));
                            try {
                                chessPad.mode = ChessPad.Mode.Game;
                                chessPad.setPgnGraph(actualItem);
                            } catch (Config.PGNException e) {
                                Log.e(DEBUG_TAG, String.format("actualItem %s", actualItem.toString()), e);
                            }
                        }

                        @Override
                        public void onExecuteException(Config.PGNException e) {
                            Log.e(DEBUG_TAG, "load, onExecuteException, thread " + Thread.currentThread().getName(), e);
                            crashAlert(R.string.crash_cannot_load);
                        }

                        @Override
                        public void doInBackground(final ProgressPublisher progressPublisher) throws Config.PGNException {
                            Log.d(DEBUG_TAG, String.format("getPgnFile start, thread %s", Thread.currentThread().getName()));
                            CpFile.getPgnFile(actualItem, (progress) -> {
                                Log.d(DEBUG_TAG, String.format("loading, Offset %d%%, thread %s", progress, Thread.currentThread().getName()));
                                return false;
                            });
                        }
                    }).execute();
                } else {
                    chessPad.currentPath = (CpFile) selectedValue;
                    dismissDlg();
                    launchDialog(DialogType.Load);
                }
                break;

            case Puzzle:
                chessPad.currentPath = (CpFile) selectedValue;
                chessPad.setPuzzles();
                break;

            case Append:
                final CpFile.Pgn pgn = new CpFile.Pgn(selectedValue.toString());
                chessPad.getPgnGraph().getPgn().setParent(pgn);
                chessPad.getPgnGraph().getPgn().setIndex(-1);
                chessPad.savePgnGraph(true, () -> chessPad.setPgnGraph(null));
                break;

            case Merge:
                chessPad.mergePgnGraph(mergeData, () -> chessPad.setPgnGraph(null));
                break;

            case Tags:
                if (selected == DialogInterface.BUTTON_POSITIVE) {
                    editTags.remove(editTags.size() - 1);     // remove 'add new' row
                    chessPad.setTags(editTags);
                    dismissDlg();
                } else {
                    if (selected == editTags.size() - 1) {
                        Pair<String, String> lastPair = editTags.get(editTags.size() - 1);
                        String label = lastPair.first.trim();
                        if (label.isEmpty()) {
                            Toast.makeText(chessPad, R.string.err_empty_new_tag_name, Toast.LENGTH_LONG).show();
                            break;
                        } else {
                            for(Pair<String, String> tag : editTags) {
                                if(lastPair == tag) {
                                    continue;
                                }
                                if(label.equals(tag.first)) {
                                    Toast.makeText(chessPad, R.string.err_tag_name_exists, Toast.LENGTH_LONG).show();
                                    return;
                                }
                            }
                            editTags.add(new Pair<>(ADD_TAG_LABEL, ""));      // new 'add tag' row
                        }
                    } else {
                        editTags.remove(selected);
                    }
                    dismissDlg();
                    launchTagEditor();
                }
                break;
        }
        if (currentAlertDialog == null) {
            chessPad.chessPadView.invalidate();
        }
    }

    void dismissDlg() {
        if (currentAlertDialog != null) {
            currentAlertDialog.dismiss();
        }
        currentAlertDialog = null;
        if(chessPad.mode == ChessPad.Mode.FicsConnection) {
            if(this.dialogType == DialogType.FicsLogin) {
                chessPad.mode = ChessPad.Mode.Game;
            }
        }
        this.dialogType = DialogType.None;
        editTags = null;
    }

    private void dlgMessage(final DialogType dialogType, String msg, int icon, DialogButton button) {
        launchDialog(dialogType, msg, icon, null, button);
    }

    private void launchDialog(final DialogType dialogType, final CPArrayAdapter arrayAdapter) {
        launchDialog(dialogType, null, 0, arrayAdapter, DialogButton.None);
    }

    // todo: fix sizes
    private void launchDialog(final DialogType dialogType, String msg, int icon, final CPArrayAdapter arrayAdapter, DialogButton button) {
        if (currentAlertDialog != null) {
            return;
        }
        dialogMsg = msg;
        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> returnFromDialog(dialogType, null, which);

        AlertDialog.Builder builder = new AlertDialog.Builder(chessPad);

        if (msg != null) {
            TextView textView = new TextView(chessPad);
            textView.setText(msg);
            textView.setTextSize(20);
            textView.setTypeface(Typeface.MONOSPACE);
            textView.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
            builder.setView(textView);
        }

        if (arrayAdapter != null) {
            builder.setAdapter(arrayAdapter, (dialog, which) -> {
                try {
                    arrayAdapter.onConvertViewClick(which);
                } catch (Throwable t) {
                    Log.e(DEBUG_TAG, "onClick", t);
                }
            });

            EditText dummy = new EditText(chessPad);  // fictitious view to enable keyboard display for CPTagsAdapter
            dummy.setVisibility(View.GONE);
            builder.setView(dummy);
        }

        if (button == DialogButton.YesNoCancel) {
            builder.setNegativeButton(R.string.no, dialogClickListener);
            builder.setPositiveButton(R.string.yes, dialogClickListener);
            builder.setNeutralButton(R.string.cancel, dialogClickListener);
        } else if (button == DialogButton.YesNo) {
            builder.setNegativeButton(R.string.no, dialogClickListener);
            builder.setPositiveButton(R.string.yes, dialogClickListener);
        }
        if (button == DialogButton.OkCancel) {
            builder.setNegativeButton(R.string.cancel, dialogClickListener);
        }
        if (button == DialogButton.Ok || button == DialogButton.OkCancel) {
            builder.setPositiveButton(R.string.ok, dialogClickListener);
        }

        AlertDialog dialog = builder.create();
        dialog.show();
        if (arrayAdapter != null) {
            dialog.getListView().setSelection(arrayAdapter.getSelectedIndex());
        }
        currentAlertDialog = dialog;
    }

    @SuppressLint("ResourceType")
    private void aboutDialog() {
        if (currentAlertDialog != null) {
            return;
        }
        dialogType = DialogType.ShowMessage;
        String title = chessPad.getString(R.string.app_name);
        title += " " + chessPad.versionName;
        AlertDialog.Builder builder = new AlertDialog.Builder(chessPad);
        // problem with androidx.appcompat:appcompat:1.1.0
        WebView webView = new WebView(chessPad);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);

        builder.setView(webView);
        builder.setTitle(title);
        Dialog dialog = builder.create();
        webView.loadUrl("file:///android_asset/chesspad-about.html");
        dialog.show();
        currentAlertDialog = dialog;
    }

    private void adjustSize(Dialog dialog, double w, double h) {
        int dialogWindowWidth = (int) (Metrics.screenWidth * w);
        int dialogWindowHeight = (int) (Metrics.screenHeight * h);

        // Set the width and height for the layout parameters
        // This will be the width and height of alert dialog
        // Initialize a new window manager layout parameters
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();

        // Copy the alert dialog window attributes to new layout parameter instance
        layoutParams.copyFrom(dialog.getWindow().getAttributes());
        layoutParams.width = dialogWindowWidth;
        layoutParams.height = dialogWindowHeight;

        // Apply the newly created layout parameters to the alert dialog window
        dialog.getWindow().setAttributes(layoutParams);
    }

    private void launchTagEditor() {
        if (currentAlertDialog != null) {
            return;
        }
        dialogType = DialogType.Tags;

        final Dialog dialog = new Dialog(chessPad);
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.tag_editor);
        dialog.setCancelable(true);

        // Set alert dialog width equal to screen width 90%
        adjustSize(dialog, .9, .9);

        ListView listView = dialog.findViewById(R.id.list_view);
        listView.setAdapter(new TagListAdapter(editTags));

        dialog.findViewById(R.id.btn_cancel).setOnClickListener((view) -> dismissDlg());
        dialog.findViewById(R.id.btn_done).setOnClickListener((view) -> returnFromDialog(dialogType, null, DialogInterface.BUTTON_POSITIVE));

        dialog.show();
        currentAlertDialog = dialog;
        currentAlertDialog.setOnCancelListener((d) -> {
            // When user touches outside of dialog bounds,
            // the dialog gets canceled and this method executes.
            Log.d(DEBUG_TAG, "tag editor cancelled");
            dismissDlg();
            editTags = null;
        });
    }

    static class MergeData extends PgnGraph.MergeData {
        ChessPadView.StringKeeper startStringWrapper;
        ChessPadView.StringKeeper endStringWrapper;
        ChessPadView.StringKeeper maxPlysPathWrapper;
        ChessPadView.StringKeeper pgnPathWrapper;
        ChessPadView.ChangeObserver changeObserver;

        MergeData() {
            super();
            init();
        }

        MergeData(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
            init();
        }

        private void init() {
            pgnPathWrapper = new ChessPadView.StringKeeper() {
                @Override
                public void setValue(String str) {
                    pgnPath = str;
                    if(MergeData.this.changeObserver != null) {
                        MergeData.this.changeObserver.onValueChanged(MergeData.this);
                    }
                }

                @Override
                public String getValue() {
                    return pgnPath;
                }
            };

            startStringWrapper = new ChessPadView.StringKeeper() {
                @Override
                public void setValue(String value) {
                    if( value.isEmpty()) {
                        start = -1;
                    } else {
                        start = getNumericValue(value);
                    }
                    if(MergeData.this.changeObserver != null) {
                        MergeData.this.changeObserver.onValueChanged(MergeData.this);
                    }
                }

                @Override
                public String getValue() {
                    if(start == -1) {
                        return "";
                    }
                    return "" + start;
                }
            };

            endStringWrapper = new ChessPadView.StringKeeper() {
                @Override
                public void setValue(String value) {
                    if( value.isEmpty()) {
                        end = -1;
                    } else {
                        end = getNumericValue(value);
                    }
                    if(MergeData.this.changeObserver != null) {
                        MergeData.this.changeObserver.onValueChanged(MergeData.this);
                    }
                }

                @Override
                public String getValue() {
                    if(end == -1) {
                        return "";
                    }
                    return "" + end;
                }
            };

            maxPlysPathWrapper = new ChessPadView.StringKeeper() {
                @Override
                public void setValue(String value) {
                    if( value.isEmpty()) {
                        maxPlys = -1;
                    } else {
                        maxPlys = getNumericValue(value);
                    }
                    if(MergeData.this.changeObserver != null) {
                        MergeData.this.changeObserver.onValueChanged(MergeData.this);
                    }
                }

                @Override
                public String getValue() {
                    if(maxPlys == -1) {
                        return "";
                    }
                    return "" + maxPlys;
                }
            };
        }

        void setChangeObserver(ChessPadView.ChangeObserver changeObserver) {
            this.changeObserver = changeObserver;
        }
    }

    private void setCheckMark(TextView textViewAnnotate, MergeData mergeData) {
        int res = 0;
        if(mergeData.annotate) {
            res = R.drawable.check;
        }
        textViewAnnotate.setCompoundDrawablesWithIntrinsicBounds(res, 0, 0, 0);
    }

    private void attachStringKeeper(final EditText editText, final ChessPadView.StringKeeper stringKeeper) {
        if(stringKeeper == null) {
            return;
        }
        editText.setText(stringKeeper.getValue());
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().toLowerCase();
                String oldText = stringKeeper.getValue();
                if(!text.equals(oldText)) {
                    stringKeeper.setValue(text);
                }
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void dlgMergeOrAppend(final DialogType dialogType) {
        if (currentAlertDialog != null) {
            return;
        }
        final Dialog dialog = new Dialog(chessPad);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dlg_merge);
        dialog.findViewById(R.id.btn_cancel).setOnClickListener((view) -> dismissDlg());
        final Button btnOk = dialog.findViewById(R.id.btn_done);
        final TextView filePathTextView = dialog.findViewById(R.id.file_name);
        final ListView fileListView = dialog.findViewById(R.id.file_list);

        MergeData _mergeData;

        if(dialogType == DialogType.Puzzle) {
            adjustSize(dialog, .9, .9);
            _mergeData = new MergeData();
            dialog.findViewById(R.id.append_controls_pane).setVisibility(View.GONE);
            dialog.findViewById(R.id.append_path_title).setVisibility(View.GONE);
            dialog.findViewById(R.id.end_dialog_buttons_pane).setVisibility(View.GONE);
            fileListShown = true;

            final CpFile cpFile = chessPad.getPgnGraph().getPgn();
            if (cpFile != null) {
                CpFile path = chessPad.currentPath;
                filePathTextView.setText(CpFile.getRelativePath(path));
                while ((path instanceof CpFile.Item) || (path instanceof CpFile.Pgn)) {
                    filePathTextView.setText(CpFile.getRelativePath(path));
                    path = path.getParent();
                }
                int selectedIndex = 0;
                try {
                    selectedIndex = cpFile.parentIndex(path);
                } catch (Config.PGNException e) {
                    Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
                }
                mergePgnFileListAdapter = new PgnFileListAdapter(path, selectedIndex) {
                    @Override
                    protected void onConvertViewClick(int position) {
                        Log.d(DEBUG_TAG, "Open Puzzles onConvertViewClick " + position);
                        CpFile newPath = (CpFile) mergePgnFileListAdapter.getItem(position);
                        filePathTextView.setText(CpFile.getRelativePath(newPath));
                        if (newPath instanceof CpFile.Pgn) {
                            returnFromDialog(dialogType, newPath, 0);
                            dismissDlg();
                        } else if (newPath instanceof CpFile.Item) {
                            // sanity check
                            Log.e(DEBUG_TAG, String.format("Invalid selection %s", newPath.toString()));
                        } else {
                            chessPad.currentPath = newPath;
                            int selectedIndex = 0;
                            try {
                                selectedIndex = cpFile.parentIndex(newPath);
                            } catch (Config.PGNException e) {
                                Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
                            }
                            mergePgnFileListAdapter.refresh(chessPad.currentPath, selectedIndex);
                        }

                    }
                };
                fileListView.setAdapter(mergePgnFileListAdapter);
            }
        } else if(dialogType == DialogType.Append) {
            _mergeData = this.appendData;
        } else {  // if(dialogType == DialogType.Merge)
            filePathTextView.setEnabled(false);
            _mergeData = this.mergeData;
            View mergeControls = dialog.findViewById(R.id.merge_controls_pane);
            mergeControls.setVisibility(View.VISIBLE);
        }
        final MergeData mergeData = _mergeData;
        if(mergeData.pgnPath == null) {
            CpFile path = chessPad.currentPath;
            mergeData.pgnPath = CpFile.getRelativePath(path);
            while ((path instanceof CpFile.Item) || (path instanceof CpFile.Pgn)) {
                mergeData.pgnPath = CpFile.getRelativePath(path);
                path = path.getParent();
            }
        }

        mergeData.setChangeObserver((value) -> btnOk.setEnabled(((MergeData)value).isMergeSetupOk()));

        filePathTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                mergeData.pgnPathWrapper.setValue(s.toString());
            }
        });
        filePathTextView.setText(mergeData.pgnPath);

        if (fileListShown) {
            fileListView.setVisibility(View.VISIBLE);
        } else {
            fileListView.setVisibility(View.GONE);
        }
        btnOk.setOnClickListener((v) -> {
            if (fileListShown) {
                fileListShown = false;
                fileListView.setVisibility(View.GONE);
            } else {
                String fileName = filePathTextView.getText().toString();
                Log.d(DEBUG_TAG, String.format("onClick: %s", DialogType.Merge.toString()));
                File appendToFile = new File(CpFile.getRootPath(), fileName);
                returnFromDialog(dialogType, appendToFile.getAbsoluteFile(), 0);
                dismissDlg();
            }
        });
        dialog.findViewById(R.id.lookup_button).setOnClickListener((v) -> {
            fileListShown = true;
            fileListView.setVisibility(View.VISIBLE);
            final CpFile cpFile = chessPad.getPgnGraph().getPgn();
            if (cpFile != null) {
                CpFile path = chessPad.currentPath;
                filePathTextView.setText(CpFile.getRelativePath(path));
                while ((path instanceof CpFile.Item) || (path instanceof CpFile.Pgn)) {
                    filePathTextView.setText(CpFile.getRelativePath(path));
                    path = path.getParent();
                }

                int selectedIndex = 0;
                try {
                    selectedIndex = cpFile.parentIndex(path);
                } catch (Config.PGNException e) {
                    Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
                }
                mergePgnFileListAdapter = new PgnFileListAdapter(path, selectedIndex) {
                    @Override
                    protected void onConvertViewClick(int position) {
                        Log.d(DEBUG_TAG, "PgnFileListAdapter.subclass.onConvertViewClick " + position);
                        CpFile newPath = (CpFile) mergePgnFileListAdapter.getItem(position);
                        filePathTextView.setText(CpFile.getRelativePath(newPath));
                        if (newPath instanceof CpFile.Pgn) {
                            fileListShown = false;
                            fileListView.setVisibility(View.GONE);
                            fileListView.setAdapter(null);
                        } else if (newPath instanceof CpFile.Item) {
                            Log.e(DEBUG_TAG, String.format("Invalid selection %s", newPath.toString()));
                        } else {
                            chessPad.currentPath = newPath;
                            int selectedIndex = 0;
                            try {
                                selectedIndex = cpFile.parentIndex(newPath);
                            } catch (Config.PGNException e) {
                                Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
                            }
                            mergePgnFileListAdapter.refresh(chessPad.currentPath, selectedIndex);
                        }

                    }
                };
                fileListView.setAdapter(mergePgnFileListAdapter);
                fileListView.setFastScrollEnabled(true);
                fileListView.setFastScrollAlwaysVisible(true);
            }
        });

        // for merge only:
        final TextView textViewAnnotate = dialog.findViewById(R.id.annotate);
        setCheckMark(textViewAnnotate, mergeData);
        textViewAnnotate.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                mergeData.annotate = !mergeData.annotate;
                setCheckMark(textViewAnnotate, mergeData);
            }
            // true if the event was handled and should not be given further down to other views.
            return true;
        });
        attachStringKeeper(dialog.findViewById(R.id.merge_start), mergeData.startStringWrapper);
        attachStringKeeper(dialog.findViewById(R.id.merge_end), mergeData.endStringWrapper);
        attachStringKeeper(dialog.findViewById(R.id.merge_max_plys), mergeData.maxPlysPathWrapper);

        currentAlertDialog = dialog;
        dialog.show();
    }

    private void ficsLogin() {
        if (currentAlertDialog != null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(ChessPad.getContext());
        LayoutInflater inflater = ChessPad.getInstance().getLayoutInflater();
        View view = inflater.inflate(R.layout.fics_login, null);
        final EditText usernameEditText = view.findViewById(R.id.username);
        final EditText passwordEditText = view.findViewById(R.id.password);

        builder.setView(view)
                .setPositiveButton(R.string.login, (dialog, id) -> {
                    dismissDlg();
                    chessPad.getFicsSettings().setUsername(usernameEditText.getText().toString());
                    chessPad.getFicsSettings().setPassword(passwordEditText.getText().toString());
                    chessPad.openFicsConnection();
                })
                .setNegativeButton(R.string.cancel, (dialogInterface, id) -> dismissDlg());

        currentAlertDialog = builder.create();

        boolean ficsLoginAsGuest = chessPad.getFicsSettings().isLoginAsGuest();
        usernameEditText.setEnabled(!ficsLoginAsGuest);
        usernameEditText.setText(chessPad.getFicsSettings().getUsername());
        passwordEditText.setEnabled(!ficsLoginAsGuest);
        passwordEditText.setText(chessPad.getFicsSettings().getUsername());
        CheckBox checkBox = view.findViewById(R.id.login_as_guest);
        checkBox.setChecked(ficsLoginAsGuest);
        checkBox.setOnClickListener((v) -> {
            boolean _ficsLoginAsGuest = !chessPad.getFicsSettings().isLoginAsGuest();
            chessPad.getFicsSettings().setLoginAsGuest(_ficsLoginAsGuest);
            usernameEditText.setEnabled(!_ficsLoginAsGuest);
            passwordEditText.setEnabled(!_ficsLoginAsGuest);
        });
        currentAlertDialog.show();
    }

    private PgnFileListAdapter getPgnFileListAdapter(CpFile parentItem, int initSelection) {
        if(pgnFileListAdapter == null || pgnFileListAdapter.isChanged(parentItem)
                   || pgnFileListAdapter.getCount() == 0) {  // kludgy way to fix storage permission change problem
            pgnFileListAdapter = new PgnFileListAdapter(parentItem, initSelection);
        }
        pgnFileListAdapter.setSelectedIndex(initSelection);
        return pgnFileListAdapter;
    }

    private void onLoadParentCrash() {
        dismissDlg();
        crashAlert(R.string.crash_cannot_list);
        pgnFileListAdapter = null;
        chessPad.currentPath = CpFile.getRootDir();
    }

    /////////////// array adapters /////////////////////////

    private class TextArrayAdapter extends CPArrayAdapter {
        TextArrayAdapter(List<?> values, int selectedIndex) {
            super(values, selectedIndex);
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        protected void setRowViewHolder(RowViewHolder rowViewHolder, final int position) {
            rowViewHolder.valueView = rowViewHolder.convertView.findViewById(R.id.view_row);
            rowViewHolder.valueView.setVisibility(View.VISIBLE);
            rowViewHolder.valueView.setEnabled(false);
            rowViewHolder.valueView.setText(getValues().get(position).toString());
            rowViewHolder.valueView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    onConvertViewClick(position);
                }
                // true if the event was handled and should not be given further down to other views.
                return true;
            });
        }

        @Override
        protected void onConvertViewClick(int position) {
            Log.d(DEBUG_TAG, "TextArrayAdapter.onConvertViewClick " + position);
            returnFromDialog(dialogType, getValues().get(position), position);
        }
    }

    private class EndgameOptionsAdapter extends TextArrayAdapter {
        EndgameOptionsAdapter(List<Pair<String, String>> values) {
            super(values, -1);
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void setRowViewHolder(RowViewHolder rowViewHolder, final int position) {
            super.setRowViewHolder(rowViewHolder, position);
            Pair<String, String> value = (Pair)getValues().get(position);
            rowViewHolder.valueView.setText(value.second);
        }
    }

    private class TagListAdapter extends CPArrayAdapter {
        // last item is an extra tag to add a new line
        private final List<Pair<String, String>> values;
        final RowTextWatcher[] rowTextWatchers;

        TagListAdapter(List<Pair<String, String>> values) {
            super();
            this.values = values;
            rowTextWatchers = new RowTextWatcher[values.size()];
        }

        @Override
        protected List<?> getValues() {
            return values;
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        protected void setRowViewHolder(RowViewHolder rowViewHolder, final int position) {
            View layout = rowViewHolder.convertView.findViewById(R.id.row_layout);
            layout.setVisibility(View.VISIBLE);
            rowViewHolder.labelView = rowViewHolder.convertView.findViewById(R.id.row_label);
            rowViewHolder.valueView = rowViewHolder.convertView.findViewById(R.id.row_value);
            rowViewHolder.actionButton = rowViewHolder.convertView.findViewById(R.id.row_action_button);
            rowViewHolder.valueView.setTag(position);
            rowViewHolder.labelView.setTag(position);
            rowViewHolder.actionButton.setTag(position);

            RowTextWatcher rowTextWatcher = rowTextWatchers[position];
            if(rowTextWatcher != null) {
                rowViewHolder.labelView.removeTextChangedListener(rowTextWatcher);
                rowViewHolder.valueView.removeTextChangedListener(rowTextWatcher);
            }
            rowTextWatcher = new RowTextWatcher(rowViewHolder);
            rowTextWatchers[position] = rowTextWatcher;

            Pair<String, String> tag = values.get(position);
            String labelText = tag.first;
            rowViewHolder.labelView.setText(labelText);
            rowViewHolder.valueView.setText(tag.second);

            rowViewHolder.labelView.addTextChangedListener(rowTextWatcher);
            rowViewHolder.valueView.addTextChangedListener(rowTextWatcher);

            int actionButtonRes;
            if (Config.STR.contains(labelText)) {
                rowViewHolder.actionButton.setEnabled(false);
                actionButtonRes = R.drawable.delete_disabled;
            } else {
                rowViewHolder.actionButton.setEnabled(true);
                actionButtonRes = R.drawable.delete;
                rowViewHolder.actionButton.setOnClickListener((v) -> {
                    Log.d(DEBUG_TAG, String.format("onClick actionButton %s", v.getTag().toString()));
                    int pos = Integer.valueOf(v.getTag().toString());
                    returnFromDialog(DialogType.Tags, TagListAdapter.this, pos);
                });
            }

            if (position == values.size() - 1) {
                rowViewHolder.labelView.setEnabled(true);
                actionButtonRes = R.drawable.add;
            } else {
                rowViewHolder.labelView.setEnabled(false);
            }
            rowViewHolder.actionButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, actionButtonRes, 0);
        }

        @Override
        protected void onConvertViewClick(int position) {
            Log.d(DEBUG_TAG, "TagListAdapter.onConvertViewClick " + position);
        }

        private class RowTextWatcher implements TextWatcher {
            // needed to keep editTags in sync with EditText
            private final RowViewHolder rowViewHolder;

            RowTextWatcher(RowViewHolder rowViewHolder) {
                this.rowViewHolder = rowViewHolder;
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                int index = rowViewHolder.index;
                Pair<String, String> tag = values.get(index);
                String label = tag.first;
                String text = tag.second;
                String newLabel = rowViewHolder.labelView.getText().toString();
                String newText = rowViewHolder.valueView.getText().toString();
                if(label.equals(newLabel) && text.equals(newText)) {
                    return;
                }
                if (DEBUG) {
                    Log.d(DEBUG_TAG, String.format("tag %s, %s -> %s : %s -> %s>", index, label, text, newLabel, newText));
                }
                values.set(index, new Pair<>(newLabel, newText));
            }
        }
    }

    private int dp2Pixels(int dp) {
        final float scale = chessPad.getResources().getDisplayMetrics().density;
        return (int) (30 * scale + 0.5f);
    }

    private void checkMemory(int progress) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long freeMemory = runtime.freeMemory();
        double percentAvail = (double)freeMemory / (double)maxMemory * 100.0;
        Log.d(DEBUG_TAG, String.format("Offset %d%%, free %s, %.1f%%", progress, freeMemory, percentAvail));
    }

    private class PgnFileListAdapter extends CPArrayAdapter {
        private CpFile parentItem;
        private List<CpFile> cpFileList;  // values
        private boolean addParentLink;

        PgnFileListAdapter(CpFile parentItem, int initSelection) {
            super();
            refresh(parentItem, initSelection);
        }

        void refresh(final CpFile parentItem, int initSelection) {
            if (DEBUG) {
                Log.d(DEBUG_TAG, "PgnFileListAdapter.refresh, thread " + Thread.currentThread().getName());
            }
            this.parentItem = parentItem;
            init(null, initSelection);
            if (parentItem == null) {
                return;
            }

            // With large files program crashes because of fragmentation.
            // On https://stackoverflow.com/questions/59959384/how-to-figure-out-when-android-memory-is-too-fragmented?noredirect=1#comment106055443_59959384
            // greeble31 suggested to allocate certain amount of memory and free it on OOM and process abort. Then Android will still have memory to continue.
            // For some reason SoftReference does not work with API 22, releases the buffer on the 1st GC
            final byte[][] oomReserve = new byte[1][];
            if(parentItem instanceof CpFile.Pgn) {
                oomReserve[0] = new byte[5 * 1024 * 1024];
            }

            addParentLink = false;
            if (!parentItem.getAbsolutePath().equals(CpFile.getRootDir().getAbsolutePath())) {
                addParentLink = true;
                ++selectedIndex;
            }

            new CPAsyncTask(chessPad.chessPadView, new CPExecutor() {
                @Override
                public void onPostExecute() {
                    oomReserve[0] = null;
                    System.gc();    // ??
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Log.e(DEBUG_TAG, "sleep", e);
                    }
                    parentItem.setTotalChildren(cpFileList.size());
                    Log.d(DEBUG_TAG, String.format("Child list %d items long, thread %s", cpFileList.size(), Thread.currentThread().getName()));
                    if (addParentLink) {
                        cpFileList.add(0, parentItem.getParent());
                    }
                    notifyDataSetChanged();
                }

                @Override
                public void onExecuteException(Config.PGNException e) {
                    Log.e(DEBUG_TAG, "PgnFileListAdapter.onExecuteException, thread " + Thread.currentThread().getName(), e);
                    onLoadParentCrash();
                }

                @Override
                public void doInBackground(final ProgressPublisher progressPublisher) {
                    final int[] oldProgress = {0};
                    Log.d(DEBUG_TAG, String.format("PgnFileListAdapter.getChildrenNames start, thread %s", Thread.currentThread().getName()));
                    try {
                        cpFileList = parentItem.getChildrenNames((progress) -> {
                            if(progress < 0) {
                                // on OOM additional diagnostics require more memory and crash
//                                String message = "ERROR, list truncated to " + (-progress);
                                chessPad.sendMessage(Config.MSG_OOM, "Operation aborted");
                                return true;
                            }
                            if(DEBUG) {
                                if (oldProgress[0] != progress) {
                                    checkMemory(progress);
                                    oldProgress[0] = progress;
                                }
                            }
                            progressPublisher.publishProgress(progress);
                            return false;
                        });
                        Log.d(DEBUG_TAG, String.format("getChildrenNames list %d items long, thread %s", cpFileList.size(), Thread.currentThread().getName()));
                    } catch (Config.PGNException e) {
                        Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
                    }
                }
            }).execute();
        }

        @Override
        List<?> getValues() {
            return cpFileList;
        }

        @Override
        public void setSelectedIndex(int selectedIndex) {
            if(addParentLink) {
                ++selectedIndex;
            }
            this.selectedIndex = selectedIndex;
        }

        boolean isChanged(CpFile parentItem) {
            return !this.parentItem.equals(parentItem);
        }

        @Override
        protected void onConvertViewClick(int position) {
            Log.d(DEBUG_TAG, "PgnFileListAdapter.onConvertViewClick " + position);
            returnFromDialog(dialogType, getValues().get(position), position);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        protected void setRowViewHolder(RowViewHolder rowViewHolder, final int position) {
            String text;
            String displayLength = "";
            int res = 0;
            if (position == 0 && addParentLink) {
                text = "..";
                res = R.drawable.go_up;
            } else {
                CpFile cpFile = cpFileList.get(position);
                displayLength = cpFile.getDisplayLength();
                text = cpFile.toString();
                if (cpFile instanceof CpFile.Zip) {
                    res = R.drawable.zip;
                } else if (cpFile instanceof CpFile.Dir) {
                    res = R.drawable.folder;
                } else if (cpFile instanceof CpFile.Pgn) {
                    res = R.drawable.pw;
//                } else {
//                    res = R.drawable.pw;        // to do ?
                }
            }

            View layout = rowViewHolder.convertView.findViewById(R.id.row_layout);
            layout.setVisibility(View.VISIBLE);
            rowViewHolder.labelView = rowViewHolder.convertView.findViewById(R.id.row_label);
            rowViewHolder.labelView.setVisibility(View.GONE);
            rowViewHolder.valueView = rowViewHolder.convertView.findViewById(R.id.row_value);
            rowViewHolder.actionButton = rowViewHolder.convertView.findViewById(R.id.row_action_button);

            rowViewHolder.valueView.setText(text);
            rowViewHolder.valueView.setCompoundDrawablesWithIntrinsicBounds(res, 0, 0, 0);

            rowViewHolder.valueView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    onConvertViewClick(position);
                }
                // true if the event was handled and should not be given further down to other views.
                return true;
            });
            if(displayLength.length() > 0) {
                rowViewHolder.actionButton.setLayoutParams(new LinearLayout.LayoutParams(
                        0,
                        dp2Pixels(20),
                        3.0f
                ));
                rowViewHolder.actionButton.setText(displayLength);
            } else {
                rowViewHolder.actionButton.setLayoutParams(new LinearLayout.LayoutParams(
                        0,
                        dp2Pixels(20),
                        0
                ));
            }
        }
    }

}