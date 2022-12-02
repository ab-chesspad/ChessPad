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

 * various dialogs for ChessPad
 * Created by Alexander Bootman on 10/30/16.
*/
package com.ab.droid.chesspad;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.ab.droid.chesspad.layout.ChessPadLayout;
import com.ab.droid.chesspad.layout.Metrics;
import com.ab.pgn.BitStream;
import com.ab.pgn.Config;
import com.ab.pgn.io.CpFile;
import com.ab.pgn.Move;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.io.FilAx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        Welcome(++j),
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

    private final List<String> glyphs;

    private boolean fileListShown = false;
    Move promotionMove;
    private Dialog currentAlertDialog;
    private MergeData mergeData, appendData;

    DialogType dialogType = DialogType.None;
    private String dialogMsg = null;
    List<Pair<String, String>> editTags;
    private PgnFileListAdapter pgnFileListAdapter;
    private PgnFileListAdapter mergePgnFileListAdapter;     // I need it here to refer it from within its creation. Any more graceful way to do it?

    Popups(ChessPad chessPad) {
        this.chessPad = chessPad;
        appendData = new MergeData();
        mergeData = new MergeData();
        glyphs = Arrays.asList(getResources().getStringArray(R.array.glyphs));
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
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    void afterUnserialize() {
        // let UI be redrawn, otherwise ProgressBar will not show up
        new CPAsyncTask(new CPExecutor() {
            @Override
            public void onExecuteException(Throwable e) {
                Log.e(DEBUG_TAG, "sleep", e);
            }

            @Override
            public void doInBackground(CpFile.ProgressObserver progressObserver) {
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
        return MainActivity.getContext().getResources();
    }

    void crashAlert(int iMsg) {
        String msg = getResources().getString(iMsg);
        crashAlert(msg);
    }

    void crashAlert(String msg) {
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
        launchDialog(dialogType, -1);
    }

    void launchDialog(final DialogType dialogType, int selected) {
        switch (dialogType) {
            case Append:
            case Merge:
            case Puzzle:
                dlgSelectPgnFile(dialogType);
                break;

            case DeleteYesNo:
                if (chessPad.isFirstMove()) {
                    dlgMessage(Popups.DialogType.DeleteYesNo, getResources().getString(R.string.msg_del_game), R.drawable.exclamation, Popups.DialogButton.YesNo);
                } else {
                    dlgMessage(Popups.DialogType.DeleteYesNo, getResources().getString(R.string.msg_del_move), 0, Popups.DialogButton.YesNo);
                }
                break;

            case Glyphs:
                launchDialog(dialogType, new PopupArrayAdapter<String>(glyphs, chessPad.getPgnGraph().getGlyph()));
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
                CpFile currentPath = chessPad.getCurrentPath();
                if (chessPad.mode == ChessPad.Mode.Puzzle && selected == -1 && currentPath instanceof CpFile.PgnFile) {
                    currentPath = currentPath.getParent();
                    chessPad.setCurrentPath(currentPath);
                } else {
                    CpFile.PgnItem pgnItem = chessPad.getPgnGraph().getPgnItem();
                    if (pgnItem != null) {
                        if (pgnItem.getParent().differs(currentPath)) {
                            try {
                                selectedPgnFile = pgnItem.getParent().parentIndex(currentPath);
                            } catch (Config.PGNException e) {
                                Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
                            }
                        } else {
                            selectedPgnFile = pgnItem.getIndex();
                        }
                    }
                }
                launchDialog(dialogType, getPgnFileListAdapter(currentPath, selectedPgnFile));
                break;

            case Menu:
                launchDialog(dialogType, new PopupArrayAdapter<ChessPad.MenuItem>(chessPad.getMenuItems(), -1) {
                    @Override
                    protected void rowSetup(RowViewHolder rowViewHolder) {
                        super.rowSetup(rowViewHolder);
                        if (getValues().get(rowViewHolder.position).isEnabled()) {
                            rowViewHolder.rowValue.setTextColor(Color.BLACK);
                        } else {
                            rowViewHolder.rowValue.setTextColor(Color.LTGRAY);
                        }
                    }
                });
                break;

            case Promotion:
                List<Integer> promotionList;
                if ((this.promotionMove.moveFlags & Config.BLACK) == 0) {
                    promotionList = wPromotionList;
                } else {
                    promotionList = bPromotionList;
                }
                launchDialog(dialogType, new PopupArrayAdapter<Integer>(promotionList, -1) {
                    @Override
                    protected void rowSetup(RowViewHolder rowViewHolder) {
                        super.rowSetup(rowViewHolder);
                        rowViewHolder.rowValue.setText("");
                        rowViewHolder.rowValue.setCompoundDrawablesWithIntrinsicBounds(getValues().get(rowViewHolder.position), 0, 0, 0);
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
                launchDialog(dialogType, new PopupArrayAdapter<Move>(chessPad.getPgnGraph().getVariations(), 0) {
                    @Override
                    protected void rowSetup(RowViewHolder rowViewHolder) {
                        super.rowSetup(rowViewHolder);
                        rowViewHolder.rowValue.setText(getValues().get(rowViewHolder.position).toCommentedString());
                    }
                });       // 1st one is main line
                break;

            case Welcome:
                String msg = getResources().getString(R.string.alert_welcome);
                dlgMessage(DialogType.Welcome, msg, 0, Popups.DialogButton.Ok);
                break;
        }

        if (currentAlertDialog != null) {
            this.dialogType = dialogType;
            currentAlertDialog.setOnCancelListener((dialog) -> {
                // When user touches outside of dialog bounds,
                // the dialog gets canceled and this method executes.
                Log.d(DEBUG_TAG, "dialog cancelled");
                dismissDlg();
                editTags = null;
            });
        }
    }

    private void returnFromDialog(DialogType dialogType, Object selectedValue, int selected) {
        Context context = MainActivity.getContext();
        this.dialogType = DialogType.None;
        if (dialogType == DialogType.SaveModified) {
            switch (selected) {
                case DialogInterface.BUTTON_POSITIVE:
                    if (chessPad.isSaveable()) {
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
                        editTags = null;
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
            chessPad.chessPadLayout.invalidate();
            return;
        }
        switch (dialogType) {
            case ShowMessage:
                dismissDlg();
                break;

            case Glyphs:
                dismissDlg();
                if (chessPad.mode == ChessPad.Mode.Game || chessPad.isPuzzleMode()) {
                    chessPad.getPgnGraph().setGlyph(selected);
                } else if (chessPad.mode == ChessPad.Mode.DgtGame) {
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
                promotionMove = null;
                break;

            case Menu:
                dismissDlg();
                chessPad.executeMenuCommand(((ChessPad.MenuItem) selectedValue).getCommand());
                break;

            case Load:
                if (selectedValue instanceof CpFile.PgnItem) {
                    Log.d(DEBUG_TAG, String.format("selectedValue %s", selectedValue.toString()));
                    dismissDlg();
                    chessPad.nextMode = ChessPad.Mode.Game;
                    chessPad.toNextGame((CpFile.PgnFile)chessPad.getCurrentPath(), selected - 1);
                } else {
                    chessPad.setCurrentPath((CpFile)selectedValue);
                    dismissDlg();
                    launchDialog(DialogType.Load, selected);
                }
                break;

            case Puzzle:
                chessPad.setCurrentPath((CpFile) selectedValue);
                chessPad.setPuzzles();
                break;

            case Append:
                chessPad.mode = ChessPad.Mode.Game;
                chessPad.getPgnGraph().getPgnItem().setParent((CpFile.PgnFile)selectedValue);
                chessPad.getPgnGraph().getPgnItem().setIndex(-1);
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
                    editTags = null;
                } else {
                    if (selected == editTags.size() - 1) {
                        Pair<String, String> lastPair = editTags.get(editTags.size() - 1);
                        String label = lastPair.first.trim();
                        if (label.isEmpty()) {
                            Toast.makeText(context, R.string.err_empty_new_tag_name, Toast.LENGTH_LONG).show();
                            break;
                        } else {
                            for (Pair<String, String> tag : editTags) {
                                if (lastPair == tag) {
                                    continue;
                                }
                                if (label.equals(tag.first)) {
                                    Toast.makeText(context, R.string.err_tag_name_exists, Toast.LENGTH_LONG).show();
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

            case Welcome:
                dismissDlg();
                chessPad.returnFromWelcome();
                break;

        }
        if (currentAlertDialog == null) {
            chessPad.chessPadLayout.invalidate();
        }
    }

    void dismissDlg() {
        if (currentAlertDialog != null) {
            currentAlertDialog.dismiss();
        }
        currentAlertDialog = null;
        this.dialogType = DialogType.None;
    }

    void dlgMessage(final DialogType dialogType, String msg, int icon, DialogButton button) {
        launchDialog(dialogType, msg, icon, null, button);
    }

    private void launchDialog(final DialogType dialogType, final CPArrayAdapter<?> arrayAdapter) {
        launchDialog(dialogType, null, 0, arrayAdapter, DialogButton.None);
    }

    private void launchDialog(final DialogType dialogType, String msg, int icon, final CPArrayAdapter<?> arrayAdapter, DialogButton button) {
        if (currentAlertDialog != null) {
            return;
        }
        Context context = MainActivity.getContext();
        dialogMsg = msg;
        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> returnFromDialog(dialogType, null, which);

//* try to do it without androidx reference so it can work on earlier Androids
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        if (msg != null) {
            TextView textView = new TextView(context);
            textView.setPadding(10, 10, 10, 10);
            textView.setText(msg);
            textView.setTextSize(20);
            textView.setTypeface(Typeface.MONOSPACE);
            textView.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
            builder.setView(textView);
        }

        if (arrayAdapter != null) {
            builder.setAdapter(arrayAdapter, (dialog, which) -> {
                try {
                    arrayAdapter.onRowViewClick(which);
                } catch (Throwable t) {
                    Log.e(DEBUG_TAG, "onClick", t);
                }
            });

            EditText dummy = new EditText(context);  // fictitious view to enable keyboard display for CPTagsAdapter
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
//*/
    }

    @SuppressLint("ResourceType")
    private void aboutDialog() {
        if (currentAlertDialog != null) {
            return;
        }
        Context context = MainActivity.getContext();
        dialogType = DialogType.ShowMessage;
        String title = context.getString(R.string.app_name);
        title += " " + chessPad.versionName;
        Dialog dialog;
        // problem with androidx.appcompat:appcompat:1.1.0
        WebView webView = new WebView(context);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.loadUrl("file:///android_asset/about/chesspad.html");

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(webView);
        builder.setTitle(title);
        dialog = builder.create();
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
        Context context = MainActivity.getContext();
        dialogType = DialogType.Tags;

        final Dialog dialog = new Dialog(context);
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.tag_editor);
        dialog.setCancelable(true);

        // Set alert dialog width equal to screen width 90%
        adjustSize(dialog, .9, .9);

        ListView listView = dialog.findViewById(R.id.list_view);
        listView.setAdapter(new TagListAdapter(editTags));

        dialog.findViewById(R.id.btn_cancel).setOnClickListener((view) -> {
            dismissDlg();
            editTags = null;
        });
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
        ChessPadLayout.StringKeeper startStringWrapper;
        ChessPadLayout.StringKeeper endStringWrapper;
        ChessPadLayout.StringKeeper maxPlysPathWrapper;
        ChessPadLayout.StringKeeper pgnPathWrapper;
        ChessPadLayout.ChangeObserver changeObserver;
        public String pgnFilePath;

        MergeData() {
            super();
            init();
        }

        private void init() {
            pgnPathWrapper = new ChessPadLayout.StringKeeper() {
                @Override
                public void setValue(String str) {
                    pgnFilePath = str;
                    if (MergeData.this.changeObserver != null) {
                        MergeData.this.changeObserver.onValueChanged(MergeData.this);
                    }
                }

                @Override
                public String getValue() {
                    if (pgnFilePath == null) {
                        return "";
                    }
                    return pgnFilePath;
                }
            };

            startStringWrapper = new ChessPadLayout.StringKeeper() {
                @Override
                public void setValue(String value) {
                    if (value.isEmpty()) {
                        start = -1;
                    } else {
                        start = getNumericValue(value);
                    }
                    if (MergeData.this.changeObserver != null) {
                        MergeData.this.changeObserver.onValueChanged(MergeData.this);
                    }
                }

                @Override
                public String getValue() {
                    if (start == -1) {
                        return "";
                    }
                    return "" + start;
                }
            };

            endStringWrapper = new ChessPadLayout.StringKeeper() {
                @Override
                public void setValue(String value) {
                    if (value.isEmpty()) {
                        end = -1;
                    } else {
                        end = getNumericValue(value);
                    }
                    if (MergeData.this.changeObserver != null) {
                        MergeData.this.changeObserver.onValueChanged(MergeData.this);
                    }
                }

                @Override
                public String getValue() {
                    if (end == -1) {
                        return "";
                    }
                    return "" + end;
                }
            };

            maxPlysPathWrapper = new ChessPadLayout.StringKeeper() {
                @Override
                public void setValue(String value) {
                    if (value.isEmpty()) {
                        maxPlys = -1;
                    } else {
                        maxPlys = getNumericValue(value);
                    }
                    if (MergeData.this.changeObserver != null) {
                        MergeData.this.changeObserver.onValueChanged(MergeData.this);
                    }
                }

                @Override
                public String getValue() {
                    if (maxPlys == -1) {
                        return "";
                    }
                    return "" + maxPlys;
                }
            };
        }

        void setChangeObserver(ChessPadLayout.ChangeObserver changeObserver) {
            this.changeObserver = changeObserver;
        }

        public MergeData(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
            try {
                pgnFilePath = reader.readString();
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
            init();
        }

        @Override
        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            super.serialize(writer);
            try {
                writer.writeString(pgnFilePath);
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        public boolean isMergeSetupOk() {
            if (pgnFilePath == null) {
                return false;
            }
            if (!FilAx.isPgnOk(pgnFilePath)) {
                return false;
            }
            return end == -1 || start <= end;
        }
    }

    private void setCheckMark(TextView textViewAnnotate, MergeData mergeData) {
        int res = 0;
        if (mergeData.annotate) {
            res = R.drawable.check;
        }
        textViewAnnotate.setCompoundDrawablesWithIntrinsicBounds(res, 0, 0, 0);
    }

    private void attachStringKeeper(final EditText editText, final ChessPadLayout.StringKeeper stringKeeper) {
        if (stringKeeper == null) {
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
                if (!text.equals(oldText)) {
                    stringKeeper.setValue(text);
                }
            }
        });
    }

    // for Puzzle, Append and Merge
    @SuppressLint("ClickableViewAccessibility")
    private void dlgSelectPgnFile(final DialogType dialogType) {
        if (currentAlertDialog != null) {
            return;
        }
        final Dialog dialog = new Dialog(MainActivity.getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dlg_merge);
        dialog.findViewById(R.id.btn_cancel).setOnClickListener((view) -> {
            dismissDlg();
            editTags = null;
        });
        final Button btnOk = dialog.findViewById(R.id.btn_done);
        final TextView filePathTextView = dialog.findViewById(R.id.file_name);
        final ListView fileListView = dialog.findViewById(R.id.file_list);

        MergeData _mergeData;

        fileListShown = false;
        if (dialogType == DialogType.Puzzle) {
            adjustSize(dialog, .9, .9);
            _mergeData = new MergeData();
            dialog.findViewById(R.id.append_controls_pane).setVisibility(View.GONE);
            dialog.findViewById(R.id.append_path_title).setVisibility(View.GONE);
            dialog.findViewById(R.id.end_dialog_buttons_pane).setVisibility(View.GONE);
            fileListShown = true;

            final CpFile cpFile = chessPad.getPgnGraph().getPgnItem();
            if (cpFile != null) {
                CpFile path = chessPad.getCurrentPath();
                filePathTextView.setText(path.getAbsolutePath());
                while ((path instanceof CpFile.PgnItem) || (path instanceof CpFile.PgnFile)) {
                    filePathTextView.setText(path.getAbsolutePath());
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
                    protected void onRowViewClick(int position) {
                        Log.d(DEBUG_TAG, "Open Puzzles onConvertViewClick " + position);
                        CpFile newPath = (CpFile) mergePgnFileListAdapter.getItem(position);
                        filePathTextView.setText(newPath.getRelativePath());
                        if (newPath instanceof CpFile.PgnFile) {
                            returnFromDialog(dialogType, newPath, 0);
                            dismissDlg();
                        } else if (newPath instanceof CpFile.PgnItem) {
                            // sanity check
                            Log.e(DEBUG_TAG, String.format("Invalid selection %s", newPath.toString()));
                        } else {
                            chessPad.setCurrentPath(newPath);
                            int selectedIndex = 0;
                            try {
                                selectedIndex = cpFile.parentIndex(newPath);
                            } catch (Config.PGNException e) {
                                Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
                            }
                            mergePgnFileListAdapter.refresh(chessPad.getCurrentPath(), selectedIndex);
                        }

                    }
                };
                fileListView.setAdapter(mergePgnFileListAdapter);
            }
        } else if (dialogType == DialogType.Append) {
            _mergeData = this.appendData;
        } else {  // if (dialogType == DialogType.Merge)
            filePathTextView.setEnabled(false);
            _mergeData = this.mergeData;
            View mergeControls = dialog.findViewById(R.id.merge_controls_pane);
            mergeControls.setVisibility(View.VISIBLE);
        }
        final MergeData mergeData = _mergeData;
        if (mergeData.pgnFilePath == null) {
            CpFile path = chessPad.getCurrentPath();
            mergeData.pgnFilePath = path.getAbsolutePath();
            while ((path instanceof CpFile.PgnItem) || (path instanceof CpFile.PgnFile)) {
                mergeData.pgnFilePath = path.getAbsolutePath();
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
        filePathTextView.setText(mergeData.pgnFilePath);

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
                if (dialogType == DialogType.Append) {
                    chessPad.getPgnGraph().getPgnItem().setIndex(-1);     // to append
                }
                dismissDlg();
                mergeData.pgnFile = (CpFile.PgnFile)CpFile.fromPath(fileName);
                returnFromDialog(dialogType, mergeData.pgnFile, 0);
            }
        });
        dialog.findViewById(R.id.lookup_button).setOnClickListener((v) -> {
            fileListShown = true;
            fileListView.setVisibility(View.VISIBLE);
            final CpFile cpFile = chessPad.getPgnGraph().getPgnItem();
            if (cpFile != null) {
                CpFile path = chessPad.getCurrentPath();
                filePathTextView.setText(path.getAbsolutePath());
                while ((path instanceof CpFile.PgnItem) || (path instanceof CpFile.PgnFile)) {
                    filePathTextView.setText(path.getAbsolutePath());
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
                    protected void onRowViewClick(int position) {
                        Log.d(DEBUG_TAG, "Open Puzzles onConvertViewClick " + position);
                        CpFile newPath = (CpFile) mergePgnFileListAdapter.getItem(position);
                        filePathTextView.setText(newPath.getAbsolutePath());
                        if (newPath instanceof CpFile.PgnFile) {
                            fileListShown = false;
                            fileListView.setVisibility(View.GONE);
                            fileListView.setAdapter(null);
                        } else if (newPath instanceof CpFile.PgnItem) {
                            Log.e(DEBUG_TAG, String.format("Invalid selection %s", newPath.toString()));
                        } else {
                            chessPad.setCurrentPath(newPath);
                            int selectedIndex = 0;
                            try {
                                selectedIndex = cpFile.parentIndex(newPath);
                            } catch (Config.PGNException e) {
                                Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
                            }
                            mergePgnFileListAdapter.refresh(chessPad.getCurrentPath(), selectedIndex);
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

    private PgnFileListAdapter getPgnFileListAdapter(CpFile parentItem, int initSelection) {
        if (pgnFileListAdapter == null || pgnFileListAdapter.isChanged(parentItem)
                   || pgnFileListAdapter.getCount() == 0) {  // kludgy way to fix storage permission change problem
            pgnFileListAdapter = new PgnFileListAdapter(parentItem, initSelection);
        }
        pgnFileListAdapter.setSelectedIndex(initSelection);
        return pgnFileListAdapter;
    }

    private void onLoadParentCrash(int msg) {
        dismissDlg();
        crashAlert(msg);
        pgnFileListAdapter = null;
    }

    /////////////// array adapters /////////////////////////

    private class PopupArrayAdapter<T> extends CPArrayAdapter<T> {
        PopupArrayAdapter(List<T> values, int selectedIndex) {
            super(values, selectedIndex);
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        protected void rowSetup(RowViewHolder rowViewHolder) {
            rowViewHolder.rowValue = rowViewHolder.convertView.findViewById(R.id.view_row);
            rowViewHolder.rowValue.setVisibility(View.VISIBLE);
            rowViewHolder.rowValue.setText(getValues().get(rowViewHolder.position).toString());
        }

        @Override
        protected void onRowViewClick(int position) {
            Log.d(DEBUG_TAG, "PopupArrayAdapter.onConvertViewClick " + position);
            returnFromDialog(dialogType, getValues().get(position), position);
        }
    }

    private class TagListAdapter extends CPArrayAdapter<Pair<String, String>> {
        // last item is an extra line to add a new tag
        final RowTextWatcher[] rowTextWatchers;

        TagListAdapter(List<Pair<String, String>> values) {
            super(values, -1);
            rowTextWatchers = new RowTextWatcher[values.size()];
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        protected void rowSetup(RowViewHolder rowViewHolder) {
            final int position = rowViewHolder.position;
            View layout = rowViewHolder.convertView.findViewById(R.id.row_layout);
            layout.setVisibility(View.VISIBLE);

            rowViewHolder.rowLabel.setOnTouchListener(null);
            rowViewHolder.rowValue.setOnTouchListener(null);
            rowViewHolder.convertView.setOnTouchListener(null);

            rowViewHolder.rowValue.setEnabled(true);

            RowTextWatcher rowTextWatcher = rowTextWatchers[position];
            if (rowTextWatcher != null) {
                rowViewHolder.rowLabel.removeTextChangedListener(rowTextWatcher);
                rowViewHolder.rowValue.removeTextChangedListener(rowTextWatcher);
            }
            rowTextWatcher = new RowTextWatcher(rowViewHolder);
            rowTextWatchers[position] = rowTextWatcher;

            Pair<String, String> tag = values.get(position);
            String labelText = tag.first;
            rowViewHolder.rowLabel.setText(labelText);
            rowViewHolder.rowValue.setText(tag.second);

            rowViewHolder.rowLabel.addTextChangedListener(rowTextWatcher);
            rowViewHolder.rowValue.addTextChangedListener(rowTextWatcher);

            int actionButtonRes;
            if (Config.STR.contains(labelText)) {
                rowViewHolder.actionButton.setEnabled(false);
                actionButtonRes = R.drawable.delete_disabled;
            } else {
                rowViewHolder.actionButton.setEnabled(true);
                actionButtonRes = R.drawable.delete;
                rowViewHolder.actionButton.setOnClickListener((v) -> {
                    RowViewHolder _rowViewHolder = (RowViewHolder)v.getTag();
                    Log.d(DEBUG_TAG, String.format("onClick actionButton %s", v.getTag().toString()));
                    int pos = _rowViewHolder.position;
                    returnFromDialog(DialogType.Tags, TagListAdapter.this, pos);
                });
            }

            if (position == values.size() - 1) {
                rowViewHolder.rowLabel.setEnabled(true);
                actionButtonRes = R.drawable.add;
            } else {
                rowViewHolder.rowLabel.setEnabled(false);
            }
            rowViewHolder.actionButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, actionButtonRes, 0);
        }

        @Override
        protected void onRowViewClick(int position) {
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
                int index = rowViewHolder.position;
                Pair<String, String> tag = values.get(index);
                String label = tag.first;
                String text = tag.second;
                String newLabel = rowViewHolder.rowLabel.getText().toString();
                String newText = rowViewHolder.rowValue.getText().toString();
                if (label.equals(newLabel) && text.equals(newText)) {
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
        final float scale = MainActivity.getContext().getResources().getDisplayMetrics().density;
        return (int) (30 * scale + 0.5f);
    }

    private void checkMemory(int progress) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long freeMemory = runtime.freeMemory();
        double percentAvail = (double)freeMemory / (double)maxMemory * 100.0;
        Log.d(DEBUG_TAG, String.format("Offset %d%%, free %s, %.1f%%", progress, freeMemory, percentAvail));
    }

    private class PgnFileListAdapter extends CPArrayAdapter<CpFile> {
        private CpFile parentItem;
        private boolean addParentLink;

        PgnFileListAdapter(CpFile parentItem, int initSelection) {
            super(null, -1);
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

            addParentLink = false;
            if (!parentItem.isRoot()) {
                addParentLink = true;
                ++selectedIndex;
            }

            new CPAsyncTask(new CPExecutor() {
                @Override
                public void doInBackground(final CpFile.ProgressObserver progressObserver) {
                    ChessPad.reserveOOMBuffer();
                    final int[] oldProgress = {0};
                    Log.d(DEBUG_TAG, String.format("PgnFileListAdapter.getChildrenNames start, thread %s", Thread.currentThread().getName()));
                    try {
                        values = parentItem.getChildrenNames();
                        Log.d(DEBUG_TAG, String.format("getChildrenNames list %d items long, thread %s", values.size(), Thread.currentThread().getName()));
                        parentItem.setTotalChildren(values.size());
                        if (addParentLink) {
                            values.add(0, parentItem.getParent());
                        }
                    } catch (OutOfMemoryError e) {
                        ChessPad.freeOOMBuffer();
                        throw e;
                    } catch (Throwable e) {
                        ChessPad.freeOOMBuffer();
                        if (e.getCause() != null) {
                            e = e.getCause();
                        }
                        if (e instanceof OutOfMemoryError) {
                            throw (OutOfMemoryError)e;
                        } else {
                            Log.e(DEBUG_TAG, e.getLocalizedMessage(), e);
                            values = new ArrayList<>();     // fix crash report from 4/11/2020 21:45
                        }
                    }
                }

                @Override
                public void onPostExecute() {
                    ChessPad.freeOOMBuffer();
                    Log.d(DEBUG_TAG, String.format("Child list %d items long, thread %s", values.size(), Thread.currentThread().getName()));
                    chessPad.showMemory("getChildrenNames");
                    notifyDataSetChanged();
                }

                @Override
                public void onExecuteException(Throwable e) {
                    ChessPad.freeOOMBuffer();
                    Log.e(DEBUG_TAG, "PgnFileListAdapter.onExecuteException, thread " + Thread.currentThread().getName(), e);
                    if (e instanceof OutOfMemoryError) {
                        onLoadParentCrash(R.string.crash_oom);
                    } else {
                        onLoadParentCrash(R.string.crash_cannot_list);
                    }
                }
            }).execute();
        }

        @Override
        public void setSelectedIndex(int selectedIndex) {
            if (addParentLink) {
                ++selectedIndex;
            }
            this.selectedIndex = selectedIndex;
        }

        boolean isChanged(CpFile parentItem) {
            return this.parentItem.differs(parentItem);
        }

        @Override
        protected void onRowViewClick(int position) {
            Log.d(DEBUG_TAG, "PgnFileListAdapter.onConvertViewClick " + position);
            returnFromDialog(dialogType, getValues().get(position), position);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        protected void rowSetup(RowViewHolder rowViewHolder) {
            final int position = rowViewHolder.position;
            String text;
            String displayLength = "";
            int res = 0;
            if (position == 0 && addParentLink) {
                text = "..";
                res = R.drawable.go_up;
            } else {
                CpFile cpFile = values.get(position);
                displayLength = cpFile.getDisplayLength();
                text = cpFile.toString();
                if (cpFile instanceof CpFile.Zip) {
                    res = R.drawable.zip;
                } else if (cpFile instanceof CpFile.Dir) {
                    res = R.drawable.folder;
                } else if (cpFile instanceof CpFile.PgnFile) {
                    res = R.drawable.pw;
//                } else {
//                    res = R.drawable.pw;        // to do ?
                }
            }

            View layout = rowViewHolder.convertView.findViewById(R.id.row_layout);
            layout.setVisibility(View.VISIBLE);
            rowViewHolder.rowLabel = rowViewHolder.convertView.findViewById(R.id.row_label);
            rowViewHolder.rowLabel.setVisibility(View.GONE);
            rowViewHolder.rowValue = rowViewHolder.convertView.findViewById(R.id.row_value);
            rowViewHolder.actionButton = rowViewHolder.convertView.findViewById(R.id.row_action_button);

            rowViewHolder.rowValue.setText(text);
            rowViewHolder.rowValue.setCompoundDrawablesWithIntrinsicBounds(res, 0, 0, 0);

            rowViewHolder.rowValue.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (values != null && position < values.size()) {
                        // sanity check, happens, on a slow phone, quick clicks
                        // screen was not re-drawn yet, but values were updated already
                        onRowViewClick(position);
                    }
                }
                // true if the event was handled and should not be given further down to other views.
                return true;
            });
            if (displayLength.length() > 0) {
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