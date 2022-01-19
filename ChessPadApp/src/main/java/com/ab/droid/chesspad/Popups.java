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

 * various dialogs for ChessPad
 * Created by Alexander Bootman on 10/30/16.
*/
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

    private static final String DIR_GO_UP_TEXT = "..";
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
            CpFile.PgnItem.serializeTagList(writer, editTags);
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
            editTags = CpFile.PgnItem.unserializeTagList(reader);
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
        switch (dialogType) {
            case Append:
            case Merge:
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
                launchDialog(dialogType, new TextArrayAdapter(glyphs, chessPad.getPgnGraph().getGlyph()));
                break;

            case Tags:
                if (editTags == null) {
                    editTags = chessPad.getTags();
                    editTags.add(new Pair<>(Config.ADD_TAG_LABEL, ""));
                }
                launchTagEditor();
                break;

            case Load:
            case Puzzle:
                dlgSelectPgnItem(dialogType);
                break;

            case Menu:
                final List<ChessPad.MenuItem> menuItems = chessPad.getMenuItems();
                launchDialog(dialogType, new TextArrayAdapter(menuItems, -1) {
                    @Override
                    protected void setRowViewHolder(RowViewHolder rowViewHolder, int position) {
                        super.setRowViewHolder(rowViewHolder, position);
                        if (menuItems.get(position).isEnabled()) {
                            rowViewHolder.rowValue.setTextColor(Color.BLACK);
                        } else {
                            rowViewHolder.rowValue.setTextColor(Color.LTGRAY);
                        }
                    }

                    @Override
                    protected void onConvertViewClick(int position) {
                        Log.d(DEBUG_TAG, "TextArrayAdapter.subclass.onConvertViewClick " + position);
                        if ( menuItems.get(position).isEnabled()) {
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
                        rowViewHolder.rowValue.setText("");
                        rowViewHolder.rowValue.setCompoundDrawablesWithIntrinsicBounds(promotionList.get(position), 0, 0, 0);
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
                        rowViewHolder.rowValue.setText(((Move)getValues().get(position)).toCommentedString());
                    }
                });       // 1st one is main line
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
        this.dialogType = DialogType.None;
        if (dialogType == DialogType.SaveModified) {
            switch (selected) {
                case DialogInterface.BUTTON_POSITIVE:
                    if (chessPad.isSaveable()) {
                        chessPad.savePgnGraph(true, () -> chessPad.setPgnGraph(-1, null));
                    } else {
                        dismissDlg();
                        launchDialog(dialogType);
                        return;
                    }
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    try {
                        chessPad.getPgnGraph().setModified(false);
                        dismissDlg();
                        editTags = null;
                        chessPad.setPgnGraph(-1,null);
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
                if (selectedValue instanceof CpFile.PgnItemName) {
                    Log.d(DEBUG_TAG, String.format("selectedSquare %s", selectedValue.toString()));
                    dismissDlg();
                    final CpFile.PgnItem[] actualItem = {null};
                    new CPAsyncTask(chessPad.chessPadLayout, new CPExecutor() {
                        @Override
                        public void doInBackground(final ProgressPublisher progressPublisher) throws Config.PGNException {
                            Log.d(DEBUG_TAG, String.format("getPgnItem start, thread %s", Thread.currentThread().getName()));
                            actualItem[0] = ((CpFile.PgnFile)chessPad.getCurrentPath()).getPgnItem(selected - 1, (progress) -> {
                                Log.d(DEBUG_TAG, String.format("loading, Offset %d%%, thread %s", progress, Thread.currentThread().getName()));
                                progressPublisher.publishProgress(progress);
                                return false;
                            });
                        }

                        @Override
                        public void onPostExecute() {
                            Log.d(DEBUG_TAG, String.format("getPgnItem end, thread %s", Thread.currentThread().getName()));
                            try {
                                chessPad.mode = ChessPad.Mode.Game;
                                chessPad.setPgnGraph(selected - 1, actualItem[0]);
                            } catch (Config.PGNException e) {
                                Log.e(DEBUG_TAG, String.format("actualItem %s", actualItem[0].toString()), e);
                            }
                        }

                        @Override
                        public void onExecuteException(Config.PGNException e) {
                            Log.e(DEBUG_TAG, "load, onExecuteException, thread " + Thread.currentThread().getName(), e);
                            crashAlert(R.string.crash_cannot_load);
                        }
                    }).execute();
                } else {
                    if (pgnFileListAdapter.parentItem == selectedValue) {
                        chessPad.setCurrentPath(((CpFile.CpParent)selectedValue).getParent());
                    } else {
                        chessPad.setCurrentPath((CpFile.CpParent)selectedValue);
                    }
                    // refresh dialog without blink
                    pgnFileListAdapter.refresh(chessPad.getCurrentPath(), selected);
                    this.dialogType = dialogType;   // restore
                }
                break;

            case Puzzle:
                dismissDlg();
                if (selectedValue instanceof CpFile.PgnFile) {
                    chessPad.setCurrentPath((CpFile.CpParent)selectedValue);
                    chessPad.setPuzzles();
                } else {
                    if (pgnFileListAdapter.parentItem == selectedValue) {
                        chessPad.setCurrentPath(((CpFile.CpParent)selectedValue).getParent());
                    } else {
                        chessPad.setCurrentPath((CpFile.CpParent)selectedValue);
                    }
                    launchDialog(dialogType);
                }
                break;

            case Append:
                chessPad.mode = ChessPad.Mode.Game;
                chessPad.getPgnGraph().getPgnItem().setParent((CpFile.PgnFile)selectedValue);
                chessPad.savePgnGraph(true, () -> chessPad.setPgnGraph(-1, null));
                break;

            case Merge:
                chessPad.mergePgnGraph(mergeData, () -> {
                    PgnGraph pgnGraph = chessPad.getPgnGraph();
                    pgnGraph.setModified(false);
                    chessPad.setPgnGraph(pgnGraph.getPgnItemIndex(), null);
                    pgnGraph.setModified(true);
                });
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
                            Toast.makeText(chessPad, R.string.err_empty_new_tag_name, Toast.LENGTH_LONG).show();
                            break;
                        } else {
                            for (Pair<String, String> tag : editTags) {
                                if (lastPair == tag) {
                                    continue;
                                }
                                if (label.equals(tag.first)) {
                                    Toast.makeText(chessPad, R.string.err_tag_name_exists, Toast.LENGTH_LONG).show();
                                    return;
                                }
                            }
                            editTags.add(new Pair<>(Config.ADD_TAG_LABEL, ""));      // new 'add tag' row
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
        webView.loadUrl("file:///android_asset/about/chesspad.html");
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

        dialog.findViewById(R.id.btn_cancel).setOnClickListener((view) -> {
            dismissDlg();
            editTags = null;
        });
        dialog.findViewById(R.id.btn_done).setOnClickListener((view) -> returnFromDialog(dialogType, null, DialogInterface.BUTTON_POSITIVE));

        dialog.show();
        currentAlertDialog = dialog;
        currentAlertDialog.setOnCancelListener((d) -> {
            // When user touches outside of dialog bounds,
            // the dialog gets cancelled and this method executes.
            Log.d(DEBUG_TAG, "tag editor cancelled");
            dismissDlg();
            editTags = null;
        });
    }

    // for Load and Puzzle
    private void dlgSelectPgnItem(final DialogType dialogType) {
        int selectedLine = -1;
        PgnFileListAdapter getPgnFileListAdapter = null;
        CpFile.CpParent currentPath = chessPad.getCurrentPath();
        CpFile.PgnItem pgnItem = chessPad.getPgnGraph().getPgnItem();

        if (dialogType == DialogType.Puzzle) {
            if (currentPath instanceof CpFile.PgnFile) {
                currentPath = currentPath.getParent();
            }
        } else {
            if (pgnItem != null) {
                // set highlighted line
                if (currentPath.differs(pgnItem.getParent())) {
                    String currentAbsPath = currentPath.getAbsolutePath();
                    String parentPath = pgnItem.getParent().getAbsolutePath();
                    if (parentPath.startsWith(currentAbsPath)) {
                        CpFile.CpParent parent = pgnItem.getParent();
                        while (currentPath.differs(parent.getParent())) {
                            parent = parent.getParent();
                        }
                        String relPath = parent.getRelativePath();
                        getPgnFileListAdapter = getPgnFileListAdapter(currentPath, relPath);
                    }
                } else {
                    selectedLine = chessPad.getPgnGraph().getPgnItemIndex();
                }
            }
        }
        if (getPgnFileListAdapter == null) {
            getPgnFileListAdapter = getPgnFileListAdapter(currentPath, selectedLine);
        }
        launchDialog(dialogType, getPgnFileListAdapter);
        adjustSize(currentAlertDialog, .9, .9);
    }

    // for Append and Merge
    @SuppressLint("ClickableViewAccessibility")
    private void dlgSelectPgnFile(final DialogType dialogType) {
        if (currentAlertDialog != null) {
            return;
        }
        final Dialog dialog = new Dialog(chessPad);
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
        if (dialogType == DialogType.Append) {
            _mergeData = this.appendData;
        } else {  // if (dialogType == DialogType.Merge)
            filePathTextView.setEnabled(false);
            _mergeData = this.mergeData;
            View mergeControls = dialog.findViewById(R.id.merge_controls_pane);
            mergeControls.setVisibility(View.VISIBLE);
        }
        final MergeData mergeData = _mergeData;
        CpFile.CpParent currentPath = chessPad.getCurrentPath();
        while ((currentPath instanceof CpFile.PgnFile)) {
            if (mergeData.pgnFile == null) {
                mergeData.pgnFile = (CpFile.PgnFile)currentPath;
            }
            currentPath = currentPath.getParent();
        }
        if (mergeData.pgnFile == null) {
            // should not be here!
            mergeData.pgnFile = (CpFile.PgnFile)chessPad.getPgnGraph().getPgnItem().getParent();
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
        filePathTextView.setText(mergeData.pgnFile.getAbsolutePath());

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
                    chessPad.getPgnGraph().setPgnItemIndex(-1);     // to append
                }
                dismissDlg();
                returnFromDialog(dialogType, CpFile.CpParent.fromPath(fileName), 0);
            }
        });
        // search button:
        dialog.findViewById(R.id.lookup_button).setOnClickListener((v) -> {
            if (fileListShown) {
                return;
            }
            fileListShown = true;
            fileListView.setVisibility(View.VISIBLE);
            CpFile.CpParent parent = null;
            CpFile.PgnItem pgnItem = chessPad.getPgnGraph().getPgnItem();
            if (pgnItem != null) {
                parent = pgnItem.getParent();
            }
            if (parent == null) {
                parent = chessPad.getCurrentPath();
            }

            filePathTextView.setText(parent.getAbsolutePath());
            while (parent instanceof CpFile.PgnFile) {
                filePathTextView.setText(parent.getAbsolutePath());
                parent = parent.getParent();
            }

            int selectedIndex = -1;
            mergePgnFileListAdapter = new PgnFileListAdapter(parent, selectedIndex) {
                @Override
                protected void onConvertViewClick(int position) {
                    Log.d(DEBUG_TAG, "PgnFileListAdapter.subclass.onConvertViewClick " + position);
                    CpFile.CpParent newPath = (CpFile.CpParent)mergePgnFileListAdapter.getItem(position);
                    if (newPath instanceof CpFile.PgnFile) {
                        fileListShown = false;
                        fileListView.setVisibility(View.GONE);
                        fileListView.setAdapter(null);
                        filePathTextView.setText(newPath.getAbsolutePath());
                    } else {
                        if (position == 0) {
                            chessPad.setCurrentPath(newPath.getParent());
                        } else {
                            chessPad.setCurrentPath(newPath);
                        }
                        filePathTextView.setText(chessPad.getCurrentPath().getAbsolutePath());
                        mergePgnFileListAdapter.refresh(chessPad.getCurrentPath(), position);
                    }

                }
            };
            fileListView.setAdapter(mergePgnFileListAdapter);
            fileListView.setFastScrollEnabled(true);
            fileListView.setFastScrollAlwaysVisible(true);
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

        adjustSize(dialog, .9, .9);
        currentAlertDialog = dialog;
        dialog.show();
    }

    static class MergeData extends PgnGraph.MergeData {
        ChessPadLayout.StringKeeper startStringWrapper;
        ChessPadLayout.StringKeeper endStringWrapper;
        ChessPadLayout.StringKeeper maxPlysPathWrapper;
        ChessPadLayout.StringKeeper pgnPathWrapper;
        ChessPadLayout.ChangeObserver changeObserver;
        String path;

        MergeData() {
            super();
            init();
        }

        private void init() {
            pgnPathWrapper = new ChessPadLayout.StringKeeper() {
                @Override
                public void setValue(String str) {
                    path = str;
                    if (MergeData.this.changeObserver != null) {
                        MergeData.this.changeObserver.onValueChanged(MergeData.this);
                    }
                }

                @Override
                public String getValue() {
                    if (path == null) {
                        return "";
                    }
                    return path;
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
                path = reader.readString();
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
            init();
        }

        @Override
        public void serialize(BitStream.Writer writer) throws Config.PGNException {
            super.serialize(writer);
            try {
                writer.writeString(path);
            } catch (IOException e) {
                throw new Config.PGNException(e);
            }
        }

        public boolean isMergeSetupOk() {
            if (path == null) {
                return false;
            }
            if (!CpFile.isPgnOk(path)) {
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

    private PgnFileListAdapter getPgnFileListAdapter(CpFile.CpParent parentItem, String selectedChild) {
        PgnFileListAdapter pgnFileListAdapter = getPgnFileListAdapter(parentItem, -1);
        pgnFileListAdapter.setSelectedChild(selectedChild);
        return pgnFileListAdapter;
    }

    private PgnFileListAdapter getPgnFileListAdapter(CpFile.CpParent parentItem, int initSelection) {
        if (pgnFileListAdapter == null || pgnFileListAdapter.isChanged(parentItem)
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
    }

    /////////////// array adapters /////////////////////////

    // objects can be images, texts
    private class TextArrayAdapter<T> extends CPArrayAdapter<T> {
        TextArrayAdapter(List<T> values, int selectedIndex) {
            super(values, selectedIndex);
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        protected void setRowViewHolder(RowViewHolder rowViewHolder, final int position) {
            rowViewHolder.rowValue = rowViewHolder.convertView.findViewById(R.id.view_row);
            rowViewHolder.rowValue.setVisibility(View.VISIBLE);
            rowViewHolder.rowValue.setEnabled(false);
            rowViewHolder.rowValue.setText(getValues().get(position).toString());
            rowViewHolder.rowValue.setOnTouchListener((v, event) -> {
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

    private class TagListAdapter extends CPArrayAdapter<Pair<String, String>> {
        // last item is an extra tag to add a new line
//        private final List<Pair<String, String>> values;
        final RowTextWatcher[] rowTextWatchers;

        TagListAdapter(List<Pair<String, String>> values) {
            super();
            this.values = values;
            rowTextWatchers = new RowTextWatcher[values.size()];
        }

//        @Override
//        protected List<?> getValues() {
//            return values;
//        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        protected void setRowViewHolder(RowViewHolder rowViewHolder, final int position) {
            View layout = rowViewHolder.convertView.findViewById(R.id.row_layout);
            layout.setVisibility(View.VISIBLE);
            rowViewHolder.rowLabel = rowViewHolder.convertView.findViewById(R.id.row_label);
            rowViewHolder.rowValue = rowViewHolder.convertView.findViewById(R.id.row_value);
            rowViewHolder.actionButton = rowViewHolder.convertView.findViewById(R.id.row_action_button);
            rowViewHolder.rowValue.setTag(position);
            rowViewHolder.rowLabel.setTag(position);
            rowViewHolder.actionButton.setTag(position);

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
                    Log.d(DEBUG_TAG, String.format("onClick actionButton %s", v.getTag().toString()));
                    int pos = Integer.valueOf(v.getTag().toString());
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
        protected void onConvertViewClick(int position) {
            // do nothing
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

    private int dp2Pixels() {
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

    private class PgnFileListAdapter extends CPArrayAdapter<CpFile> {
        private CpFile.CpParent parentItem;
        private boolean addParentLink;
        private String selectedChild;

        PgnFileListAdapter(CpFile.CpParent parentItem, int initSelection) {
            super();
            refresh(parentItem, initSelection);
        }

        void refresh(final CpFile.CpParent parentItem, int initSelection) {
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
            if (parentItem instanceof CpFile.PgnFile) {
                oomReserve[0] = new byte[5 * 1024 * 1024];
            }

            addParentLink = false;
            if (!parentItem.isRoot()) {
                addParentLink = true;
                ++selectedIndex;
            }

            new CPAsyncTask(chessPad.chessPadLayout, new CPExecutor() {
                private List<CpFile> cpFileList = new ArrayList<>();     // fix crash report from 4/11/2020 21:45

                @Override
                public void doInBackground(final ProgressPublisher progressPublisher) {
                    final int[] oldProgress = {0};
                    Log.d(DEBUG_TAG, String.format("PgnFileListAdapter.getChildrenNames start, thread %s", Thread.currentThread().getName()));
                    try {
                        cpFileList = parentItem.getChildrenNames((progress) -> {
                            if (progress < 0) {
                                // on OOM additional diagnostics require more memory and crash
//                                String message = "ERROR, list truncated to " + (-progress);
                                chessPad.sendMessage(Config.MSG_NOTIFICATION, "Operation aborted");
                                return true;
                            }
                            if (DEBUG) {
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

                @Override
                public void onPostExecute() {
                    oomReserve[0] = null;
                    System.gc();    // ??
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Log.e(DEBUG_TAG, "sleep", e);
                    }
                    Log.d(DEBUG_TAG, String.format("Child list %d items long, thread %s", cpFileList.size(), Thread.currentThread().getName()));
                    if (addParentLink) {
                        cpFileList.add(0, parentItem);
                    }
                    values = cpFileList;
                    notifyDataSetChanged();
                }

                @Override
                public void onExecuteException(Config.PGNException e) {
                    Log.e(DEBUG_TAG, "PgnFileListAdapter.onExecuteException, thread " + Thread.currentThread().getName(), e);
                    onLoadParentCrash();
                }
            }).execute();
        }

        @Override
        public void setSelectedIndex(int selectedIndex) {
            if (addParentLink) {
                ++selectedIndex;
            }
            super.setSelectedIndex(selectedIndex);
        }

        public void setSelectedChild(String selectedChild) {
            this.selectedChild = selectedChild;
        }

        boolean isChanged(CpFile.CpParent parentItem) {
            return this.parentItem.differs(parentItem);
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
            boolean goBackRow = false;
            CpFile cpFile = (CpFile)values.get(position);
            if (position == 0 && addParentLink) {
                text = DIR_GO_UP_TEXT;
                res = R.drawable.go_up;
                goBackRow = true;
            } else {
                text = cpFile.getRelativePath();
                displayLength = cpFile.getDisplayLength();
                if (cpFile instanceof CpFile.Zip) {
                    res = R.drawable.zip;
                } else if (cpFile instanceof CpFile.Dir) {
                    res = R.drawable.folder;
                } else if (cpFile instanceof CpFile.PgnFile) {
                    res = R.drawable.pw;
                }
            }

            if (selectedChild != null) {
                if (selectedChild.equals(text)) {
                    selectedIndex = position;
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
                    onConvertViewClick(position);
                }
                // true if the event was handled and should not be given further down to other views.
                return true;
            });
            rowViewHolder.rowValue.setFocusable(false);

            if (goBackRow || cpFile instanceof CpFile.PgnItemName) {
                rowViewHolder.actionButton.setVisibility(View.GONE);
            } else {
                rowViewHolder.actionButton.setVisibility(View.VISIBLE);
/* todo: implement delete
                rowViewHolder.actionButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.delete_small, 0);
//*/
                rowViewHolder.actionButton.setText(displayLength);
                rowViewHolder.actionButton.setLayoutParams(new LinearLayout.LayoutParams(
                        0,
                        dp2Pixels(),
                        3.0f
                ));
            }

/*
            if (displayLength.length() > 0) {
                rowViewHolder.actionButton.setLayoutParams(new LinearLayout.LayoutParams(
                        0,
                        dp2Pixels(),
                        3.0f
                ));
                rowViewHolder.actionButton.setText(displayLength);
            } else {
                rowViewHolder.actionButton.setLayoutParams(new LinearLayout.LayoutParams(
                        0,
                        dp2Pixels(),
                        0
                ));
            }
*/
        }
    }

}