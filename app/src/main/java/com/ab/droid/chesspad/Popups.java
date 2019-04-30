package com.ab.droid.chesspad;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ab.pgn.BitStream;
import com.ab.pgn.Config;
import com.ab.pgn.Move;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnGraph;
import com.ab.pgn.PgnItem;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * various dialogs for ChessPad
 * Created by Alexander Bootman on 10/30/16.
 */
public class Popups {
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();
    public boolean DEBUG = false;

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
        Append(++j),
        Merge(++j),
        Headers(++j),
        SaveModified(++j),
        ;

        private final int value;
        private static DialogType[] values = DialogType.values();

        DialogType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static DialogType value(int v) {
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

    private static final String ADD_HEADER_LABEL = "            ";
    private ChessPad chessPad;
    private final int[][] promotionList = {
            {R.drawable.qw, R.drawable.rw, R.drawable.bw, R.drawable.nw},
            {R.drawable.qb, R.drawable.rb, R.drawable.bb, R.drawable.nb},
            {Config.QUEEN, Config.ROOK, Config.BISHOP, Config.KNIGHT},
    };
    private List<String> glyphs;

    private boolean fileListShown = false;
    protected Move promotionMove;
    private Dialog currentAlertDialog;
    private MergeData mergeData;

    private DialogType dialogType = DialogType.None;
    private String dialogMsg = null;
    private List<Pair<String, String>> editHeaders;
    CPPgnItemListAdapter cpPgnItemListAdapter;

    public Popups(ChessPad chessPad) {
        this.chessPad = chessPad;
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
            PgnItem.serialize(writer, editHeaders);
            if(mergeData == null) {
                writer.write(0, 1);
            } else {
                writer.write(1, 1);
                mergeData.serialize(writer);
            }
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    public void unserialize(BitStream.Reader reader) throws Config.PGNException {
        try {
            fileListShown = reader.read(1) == 1;
            if (reader.read(1) == 1) {
                promotionMove = new Move(reader, chessPad.pgnGraph.getBoard());
            }
            this.dialogType = DialogType.value(reader.read(4));
            dialogMsg = reader.readString();
            if (dialogMsg == null || dialogMsg.isEmpty()) {
                dialogMsg = null;
            }
            editHeaders = PgnItem.unserializeHeaders(reader);
            if (reader.read(1) == 1) {
                mergeData = new MergeData(reader);
            }
            afterUnserialize();
        } catch (IOException e) {
            throw new Config.PGNException(e);
        }
    }

    protected void afterUnserialize() {
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

    public void dismiss() {
        dismissDlg();
    }

    public void crashAlert(int iMsg) {
        String msg = getResources().getString(iMsg);
        crashAlert(msg);
    }

    public void crashAlert(String msg) {
        dlgMessage(Popups.DialogType.ShowMessage, msg, R.drawable.exclamation, Popups.DialogButton.Ok);
    }

    public void launchDialog(DialogType dialogType) {
        PgnItem pgnItem;
        switch (dialogType) {
            case Glyphs:
                launchDialog(dialogType, new CPArrayAdapter(glyphs, chessPad.pgnGraph.getGlyph()));
                break;

            case Variations:
                List<Move> variations = chessPad.pgnGraph.getVariations();
                launchDialog(dialogType, new CPArrayAdapter(variations, 0));       // 1st one is main line
                break;

            case Promotion:
                int index;
                if ((this.promotionMove.moveFlags & Config.BLACK) == 0) {
                    index = 0;
                } else {
                    index = 1;
                }
                launchDialog(dialogType, new CPArrayAdapter(promotionList[index], -1));
                break;

            case Load:
                int selectedPgnItem = -1;
                pgnItem = chessPad.pgnGraph.getPgn();
                if (pgnItem != null) {
                    try {
                        selectedPgnItem = pgnItem.parentIndex(chessPad.currentPath);
                    } catch (Config.PGNException e) {
                        Log.e(DEBUG_TAG, e.getMessage(), e);
                    }
                }
                launchDialog(dialogType, getPgnItemListAdapter(chessPad.currentPath, selectedPgnItem));
                break;

            case Menu:
                launchDialog(dialogType, new CPArrayAdapter(chessPad.getMenuItems(), -1));
                break;

            case ShowMessage:
                dlgMessage(dialogType, String.format(getResources().getString(R.string.about), chessPad.versionName), 0, DialogButton.Ok);
                break;

            case Append:
                dlgAppend();
                break;

            case Merge:
                dlgMerge();
                break;

            case Headers:
                if (editHeaders == null) {
                    editHeaders = PgnItem.cloneHeaders(chessPad.getHeaders());
                    editHeaders.add(new Pair<>(ADD_HEADER_LABEL, ""));
                }
                CPHeaderListAdapter adapter = new CPHeaderListAdapter(editHeaders);
                launchDialog(dialogType, null, 0, adapter, DialogButton.OkCancel);
                break;

            case DeleteYesNo:
                if (chessPad.isFirstMove()) {
                    dlgMessage(Popups.DialogType.DeleteYesNo, getResources().getString(R.string.msg_del_game), R.drawable.exclamation, Popups.DialogButton.YesNo);
                } else if (chessPad.pgnGraph.isEnd()) {
                    chessPad.pgnGraph.delCurrentMove();
                    chessPad.chessPadView.invalidate();
                } else {
                    dlgMessage(Popups.DialogType.DeleteYesNo, getResources().getString(R.string.msg_del_move), 0, Popups.DialogButton.YesNo);
                }
                break;

            case SaveModified:
                dlgMessage(Popups.DialogType.SaveModified, getResources().getString(R.string.msg_save), R.drawable.exclamation, Popups.DialogButton.YesNoCancel);
                break;
        }

        if(currentAlertDialog != null) {
            this.dialogType = dialogType;
            currentAlertDialog.setOnCancelListener(
                    new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            // When user touches outside of dialog bounds,
                            // the dialog gets canceled and this method executes.
                            Log.d(DEBUG_TAG, "dialog cancelled");
                            dismissDlg();
                        }
                    }
            );
        }
    }

    private void returnFromDiaqlog(DialogType dialogType, Object selectedValue, int selected) throws Config.PGNException {
        this.dialogType = DialogType.None;
        if(dialogType == DialogType.SaveModified) {
            switch (selected) {
                case DialogInterface.BUTTON_POSITIVE:
                    if(chessPad.isSaveable()) {
                        chessPad.savePgnGraph(true, new CPPostExecutor() {
                            @Override
                            public void onPostExecute() throws Config.PGNException {
                                chessPad.setPgnGraph(null);
                            }
                        });
                    } else {
                        dismissDlg();
                        launchDialog(DialogType.Append);
                        return;
                    }
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    try {
                        chessPad.pgnGraph.setModified(false);
                        chessPad.setPgnGraph(null);
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
            editHeaders = null;
            chessPad.chessPadView.invalidate();
            return;
        }
        switch (dialogType) {
            case ShowMessage:
                dismissDlg();
                break;

            case Glyphs:
                dismissDlg();
                if(chessPad.mode == ChessPad.Mode.Game) {
                    chessPad.pgnGraph.setGlyph(selected);
                } else if(chessPad.mode == ChessPad.Mode.DgtGame) {
                    chessPad.dgtBoardPad.getPgnGraph().getCurrentMove().setGlyph(selected);
                }
                break;

            case Variations:
                dismissDlg();
                Move variation = (Move) selectedValue;
                chessPad.pgnGraph.toVariation(variation);
                break;

            case DeleteYesNo:
                dismissDlg();
                chessPad.delete();
                break;

            case Promotion:
                dismissDlg();
                promotionMove.setPiecePromoted(promotionList[2][selected] | (promotionMove.moveFlags & Config.BLACK));
                chessPad.pgnGraph.validateUserMove(promotionMove);  // validate check
                chessPad.pgnGraph.addUserMove(promotionMove);
                break;

            case Menu:
                dismissDlg();
                chessPad.executeMenuCommand(((ChessPad.MenuItem) selectedValue).getCommand());
                break;

            case Load:
                if (selectedValue instanceof PgnItem.Item) {
                    Log.d(DEBUG_TAG, String.format("selectedSquare %s", selectedValue.toString()));
                    dismissDlg();
                    final PgnItem.Item actualItem = (PgnItem.Item) selectedValue;
                    new CPAsyncTask(chessPad.chessPadView, new CPExecutor() {
                        @Override
                        public void onPostExecute() {
                            Log.d(DEBUG_TAG, String.format("getPgnItem end, thread %s", Thread.currentThread().getName()));
                            try {
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
                            Log.d(DEBUG_TAG, String.format("getPgnItem start, thread %s", Thread.currentThread().getName()));
                            PgnItem.getPgnItem(actualItem, new PgnItem.ProgressObserver() {
                                @Override
                                public void setProgress(int progress) {
                                    Log.d(DEBUG_TAG, String.format("Offset %d%%, thread %s", progress, Thread.currentThread().getName()));
                                    progressPublisher.publishProgress(progress);
                                }
                            });
                        }
                    }).execute();
                } else {
                    chessPad.currentPath = (PgnItem) selectedValue;
                    dismissDlg();
                    launchDialog(DialogType.Load);
                }
                break;

            case Append:
                final PgnItem.Pgn pgn = new PgnItem.Pgn(selectedValue.toString());
                chessPad.pgnGraph.getPgn().setParent(pgn);
                chessPad.pgnGraph.getPgn().setIndex(-1);
                chessPad.savePgnGraph(true, new CPPostExecutor() {
                    @Override
                    public void onPostExecute() throws Config.PGNException {
                        chessPad.setPgnGraph(null);
                    }
                });
                break;

            case Merge:
                chessPad.mergePgnGraph(mergeData, new CPPostExecutor() {
                    @Override
                    public void onPostExecute() throws Config.PGNException {
                        chessPad.setPgnGraph(null);
                    }
                });
                break;

            case Headers:
                if (selected == DialogInterface.BUTTON_POSITIVE) {
                    editHeaders.remove(editHeaders.size() - 1);     // remove 'add new' row
                    chessPad.setHeaders(editHeaders);
                    dismissDlg();
                    editHeaders = null;
                } else {
                    if (selected == editHeaders.size() - 1) {
                        String label = editHeaders.get(editHeaders.size() - 1).first.trim();
                        if (label.isEmpty()) {
                            Toast.makeText(chessPad, R.string.err_empty_new_tag_name, Toast.LENGTH_LONG).show();
                            break;
                        } else {
                            for(Pair<String, String> header : editHeaders) {
                                if(label.equals(header.first)) {
                                    Toast.makeText(chessPad, R.string.err_tag_name_exists, Toast.LENGTH_LONG).show();
                                    return;
                                }
                            }
                            String value = editHeaders.get(editHeaders.size() - 1).second;
                            editHeaders.set(editHeaders.size() - 1, new Pair<>(label, value));
                            editHeaders.add(new Pair<>(ADD_HEADER_LABEL, ""));      // new 'add header' row
                        }
                    } else {
                        editHeaders.remove(selected);
                    }
                    ((CPHeaderListAdapter) selectedValue).refresh();
                }
                break;
        }
        if (currentAlertDialog == null) {
            chessPad.chessPadView.invalidate();
        }
    }

    private void dismissDlg() {
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
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    returnFromDiaqlog(dialogType, null, which);
                } catch (Config.PGNException e) {
                    Log.e(DEBUG_TAG, "dlgMessage()", e);
                }
            }
        };

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
            builder.setSingleChoiceItems(
                    arrayAdapter,
                    arrayAdapter.getSelectedIndex(),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                Object selectedItem = arrayAdapter.getItem(which);
                                if (selectedItem instanceof ChessPad.MenuItem) {
                                    if (!((ChessPad.MenuItem) selectedItem).isEnabled()) {
                                        return; // ignore
                                    }
                                }
                                returnFromDiaqlog(dialogType, selectedItem, which);
                            } catch (Throwable t) {
                                Log.e(DEBUG_TAG, "onClick", t);
                            }
                        }
                    })
            ;
            EditText editText = new EditText(chessPad);  // fictitious view to enable keyboard display for CPHeadersAdapter
            editText.setVisibility(View.GONE);
            builder.setView(editText);
        }
        if (button == DialogButton.YesNoCancel) {
            builder.setNegativeButton(R.string.no, dialogClickListener);
            builder.setPositiveButton(R.string.yes, dialogClickListener);
            builder.setNeutralButton(R.string.cancel, dialogClickListener);
        } else {
            if (button == DialogButton.YesNo) {
                builder.setNegativeButton(R.string.no, dialogClickListener);
                builder.setPositiveButton(R.string.yes, dialogClickListener);
            }
            if (button == DialogButton.OkCancel) {
                builder.setNegativeButton(R.string.cancel, dialogClickListener);
            }
            if (button == DialogButton.Ok || button == DialogButton.OkCancel) {
                builder.setPositiveButton(R.string.ok, dialogClickListener);
            }
        }
        currentAlertDialog = builder.create();
        currentAlertDialog.show();
    }

    private String getTruncatedPath(PgnItem pgnItem) {
        String path = pgnItem.getAbsolutePath();
        String rootPath = PgnItem.getRoot().getAbsolutePath();
        if(path.equals(rootPath)) {
            return "";
        }
        if (path.startsWith(rootPath)) {
            path = path.substring(rootPath.length() + 1);    // remove "/"
        }
        if(pgnItem.getClass().isAssignableFrom(PgnItem.Dir.class)) {
            path += "/";
        }
        return path;
    }

    private void dlgAppend() {
        final Dialog mDialog = new Dialog(chessPad);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setContentView(R.layout.dlg_append);
        final Button btnOk = mDialog.findViewById(R.id.ok_button);
        btnOk.setEnabled(false);
        final TextView textView = mDialog.findViewById(R.id.file_name);
        textView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String text = s.toString().toLowerCase();
                if (text.endsWith(PgnItem.EXT_PGN)) {
                    btnOk.setEnabled(true);
                } else {
                    btnOk.setEnabled(false);
                }
            }
        });
        PgnItem path = chessPad.currentPath;
        textView.setText(getTruncatedPath(path));
        while ((path instanceof PgnItem.Item) || (path instanceof PgnItem.Pgn)) {
            textView.setText(getTruncatedPath(path));
            path = path.getParent();
        }
        final ListView listView = mDialog.findViewById(R.id.file_list);
        if (fileListShown) {
            listView.setVisibility(View.VISIBLE);
        } else {
            listView.setVisibility(View.GONE);
        }
        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (fileListShown) {
                    fileListShown = false;
                    listView.setVisibility(View.GONE);
                } else {
                    String fileName = textView.getText().toString();
                    Log.d(DEBUG_TAG, String.format("onClick: %s", DialogType.Append.toString()));
                    try {
                        File appendToFile = new File(PgnItem.getRoot(), fileName);
                        returnFromDiaqlog(DialogType.Append, appendToFile.getAbsoluteFile(), 0);
                    } catch (Config.PGNException e) {
                        Log.e(DEBUG_TAG, String.format("onClick: %s", DialogType.Append.toString()), e);
                        Toast.makeText(chessPad, R.string.toast_err_file, Toast.LENGTH_LONG).show();
                    }
                    mDialog.cancel();
                }
            }
        });
        mDialog.findViewById(R.id.lookup_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fileListShown = true;
                listView.setVisibility(View.VISIBLE);
                final PgnItem pgnItem = chessPad.pgnGraph.getPgn();
                if (pgnItem != null) {
                    try {

                        PgnItem path = chessPad.currentPath;
                        textView.setText(getTruncatedPath(path));
                        while ((path instanceof PgnItem.Item) || (path instanceof PgnItem.Pgn)) {
                            textView.setText(getTruncatedPath(path));
                            path = path.getParent();
                        }

                        int selectedIndex = pgnItem.parentIndex(path);
                        final CPPgnItemListAdapter mAdapter = getPgnItemListAdapter(path, selectedIndex);
                        listView.setAdapter(mAdapter);
                        listView.setFastScrollEnabled(true);
                        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> parent, View view, int clicked, long id) {
                                PgnItem newPath = (PgnItem) mAdapter.getItem(clicked);
                                textView.setText(getTruncatedPath(newPath));
                                if (newPath instanceof PgnItem.Pgn) {
                                    fileListShown = false;
                                    listView.setVisibility(View.GONE);
                                } else if (newPath instanceof PgnItem.Item) {
                                    Log.e(DEBUG_TAG, String.format("Invalid selection %s", newPath.toString()));
                                } else {
                                    chessPad.currentPath = newPath;
                                    try {
                                        int selectedIndex = pgnItem.parentIndex(newPath);
                                        mAdapter.refresh(chessPad.currentPath, selectedIndex);
                                    } catch (Config.PGNException e) {
                                        Log.e(DEBUG_TAG, String.format("onClick 2: %s", DialogType.Append.toString()), e);
                                    }
                                }
                            }
                        });
                    } catch (Config.PGNException e) {
                        Log.e(DEBUG_TAG, String.format("onClick 1: %s", DialogType.Append.toString()), e);
                    }
                }
            }
        });

        currentAlertDialog = mDialog;
        mDialog.show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createMergeParamPane(RelativeLayout rlPane) {
        int height = (2 * Metrics.squareSize - 2 * Metrics.ySpacing) / 3;
        int x = Metrics.xSpacing;
        int y = Metrics.ySpacing;
        int w1 = Metrics.moveLabelWidth / 2;
        int w2 = w1;
        ChessPadView.CpEditText startEditText = ChessPadView.createLabeledEditText(chessPad, rlPane, x, y,
                w1, w2, height, R.string.alert_merge_start_label, mergeData.startStringWrapper);

        x += w1 + w2 + 8 * Metrics.xSpacing;
        ChessPadView.CpEditText endEditText = ChessPadView.createLabeledEditText(chessPad, rlPane, x, y,
                w1, w2, height, R.string.alert_merge_end_label, mergeData.endStringWrapper);

        x += w1 + w2 + 8 * Metrics.xSpacing;
        w1 = Metrics.cpScreenWidth - x - 20;
        final TextView includeHeaders = new TextView(chessPad);
        includeHeaders.setSingleLine();
        includeHeaders.setBackgroundColor(Color.GREEN);
        includeHeaders.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        includeHeaders.setText(R.string.alert_annotate_label);
        ChessPadView.addTextView(rlPane, includeHeaders, x, y, w1, height);
        int res = 0;
        if(mergeData.annotate) {
            res = R.drawable.check;
        }
        includeHeaders.setCompoundDrawablesWithIntrinsicBounds(res, 0, 0, 0);
        includeHeaders.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    mergeData.annotate = !mergeData.annotate;
                    int res = 0;
                    if(mergeData.annotate) {
                        res = R.drawable.check;
                    }
                    includeHeaders.setCompoundDrawablesWithIntrinsicBounds(res, 0, 0, 0);
                }
                // true if the event was handled and should not be given further down to other views.
                return true;
            }
        });
    }

    private void dlgMerge() {
        PgnItem.Item pgnItem = chessPad.pgnGraph.getPgn();
        if(mergeData == null) {
            mergeData = new MergeData(pgnItem);
        }
        final Dialog mDialog = new Dialog(chessPad);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setContentView(R.layout.dlg_merge);
        RelativeLayout rlHeaders = mDialog.findViewById(R.id.controls_pane_merge);
        createMergeParamPane(rlHeaders);

        final Button btnOk = mDialog.findViewById(R.id.ok_button);
        btnOk.setEnabled(mergeData.isMergeSetupOk());
        mergeData.setChangeObserver(new ChessPadView.ChangeObserver() {
            @Override
            public void onValueChanged(Object value) {
                btnOk.setEnabled(((MergeData)value).isMergeSetupOk());
            }
        });

        final TextView textView = mDialog.findViewById(R.id.file_name);
        textView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                mergeData.pgnPathWrapper.setValue(textView, s.toString());
            }
        });
        PgnItem path = chessPad.currentPath;
        textView.setText(getTruncatedPath(path));
        while ((path instanceof PgnItem.Item) || (path instanceof PgnItem.Pgn)) {
            textView.setText(getTruncatedPath(path));
            path = path.getParent();
        }
        final ListView fileListView = mDialog.findViewById(R.id.file_list);
        if (fileListShown) {
            fileListView.setVisibility(View.VISIBLE);
        } else {
            fileListView.setVisibility(View.GONE);
        }
        btnOk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (fileListShown) {
                    fileListShown = false;
                    fileListView.setVisibility(View.GONE);
                } else {
                    String fileName = textView.getText().toString();
                    Log.d(DEBUG_TAG, String.format("onClick: %s", DialogType.Merge.toString()));
                    try {
                        File appendToFile = new File(PgnItem.getRoot(), fileName);
                        returnFromDiaqlog(DialogType.Merge, appendToFile.getAbsoluteFile(), 0);
                    } catch (Config.PGNException e) {
                        Log.e(DEBUG_TAG, String.format("onClick: %s", DialogType.Merge.toString()), e);
                        Toast.makeText(chessPad, R.string.toast_err_file, Toast.LENGTH_LONG).show();
                    }
                    mDialog.cancel();
                }
            }
        });
        mDialog.findViewById(R.id.lookup_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fileListShown = true;
                fileListView.setVisibility(View.VISIBLE);
                final PgnItem pgnItem = chessPad.pgnGraph.getPgn();
                if (pgnItem != null) {
                    try {
                        PgnItem path = chessPad.currentPath;
                        textView.setText(getTruncatedPath(path));
                        while ((path instanceof PgnItem.Item) || (path instanceof PgnItem.Pgn)) {
                            textView.setText(getTruncatedPath(path));
                            path = path.getParent();
                        }

                        int selectedIndex = pgnItem.parentIndex(path);
                        final CPPgnItemListAdapter mAdapter = getPgnItemListAdapter(path, selectedIndex);
                        fileListView.setAdapter(mAdapter);
                        fileListView.setFastScrollEnabled(true);
                        fileListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                            @Override
                            public void onItemClick(AdapterView<?> parent, View view, int clicked, long id) {
                                PgnItem newPath = (PgnItem) mAdapter.getItem(clicked);
                                textView.setText(getTruncatedPath(newPath));
                                if (newPath instanceof PgnItem.Pgn) {
                                    fileListShown = false;
                                    fileListView.setVisibility(View.GONE);
                                } else if (newPath instanceof PgnItem.Item) {
                                    Log.e(DEBUG_TAG, String.format("Invalid selection %s", newPath.toString()));
                                } else {
                                    chessPad.currentPath = newPath;
                                    try {
                                        int selectedIndex = pgnItem.parentIndex(newPath);
                                        mAdapter.refresh(chessPad.currentPath, selectedIndex);
                                    } catch (Config.PGNException e) {
                                        Log.e(DEBUG_TAG, String.format("onClick 2: %s", DialogType.Merge.toString()), e);
                                    }
                                }
                            }
                        });
                    } catch (Config.PGNException e) {
                        Log.e(DEBUG_TAG, String.format("onClick 1: %s", DialogType.Merge.toString()), e);
                    }
                }
            }
        });

        currentAlertDialog = mDialog;
        mDialog.show();
    }

    private CPPgnItemListAdapter getPgnItemListAdapter(PgnItem parentItem, int initSelection) {
        if(cpPgnItemListAdapter == null || cpPgnItemListAdapter.isChanged(parentItem)
                   || cpPgnItemListAdapter.getCount() == 0) {  // kludgy way to fix storage permission change problem
            cpPgnItemListAdapter = new CPPgnItemListAdapter(parentItem, initSelection);
        }
        cpPgnItemListAdapter.setSelectedIndex(initSelection);
        return cpPgnItemListAdapter;
    }

    private void onLoadParentCrash() {
        dismissDlg();
        crashAlert(R.string.crash_cannot_list);
        cpPgnItemListAdapter = null;
        chessPad.currentPath = PgnItem.getRootDir();
    }

    private class CPArrayAdapter extends ArrayAdapter<Object> {
        protected List<?> values;
        protected int[] resources;
        protected int selectedIndex;
        protected int selectedIndexAdjustment = 0;
        protected LayoutInflater layoutInflater;

        protected CPArrayAdapter() {
            super(chessPad, 0);
        }

        public CPArrayAdapter(int[] resources, int selectedIndex) {
            super(chessPad, 0);
            init(null, resources, selectedIndex);
        }

        public CPArrayAdapter(List<?> values, int selectedIndex) {
            super(chessPad, 0);
            init(values, null, selectedIndex);
        }

        protected void init(List<?> values, int[] resources, int initSelection) {
            this.values = values;
            this.resources = resources;
            this.selectedIndex = initSelection;
            layoutInflater = LayoutInflater.from(chessPad);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = layoutInflater.inflate(R.layout.list_view, null);
            }
            TextView textView = convertView.findViewById(R.id.listViewRow);
            textView.setVisibility(View.VISIBLE);
            if (position == selectedIndex) {
                textView.setBackgroundColor(Color.CYAN);
            } else {
                textView.setBackgroundColor(Color.WHITE);
            }

            if (values != null) {
                Object value = values.get(position);
                if (value instanceof Move) {
                    Move move = (Move)value;
                    String text = move.toString().trim();
                    if(move.getGlyph() != 0) {
                        String[] glyphs = getResources().getStringArray(R.array.glyphs);
                        String glyph = glyphs[move.getGlyph()].split("\\s+")[0];
                        text += " " + glyph;
                    }
                    if(move.comment != null) {
                        int len = 40;
                        if(len > move.comment.length()) {
                            len = move.comment.length();
                        }
                        text += ", " + move.comment.substring(0, len);
                    }
                    textView.setText(text);
                } else {
                    textView.setText(value.toString());
                    if (value instanceof ChessPad.MenuItem) {
                        if (((ChessPad.MenuItem) value).isEnabled()) {
                            textView.setTextColor(Color.BLACK);
                        } else {
                            textView.setTextColor(Color.LTGRAY);
                        }
                    }
                }
            } else {
                textView.setText("");
            }
            if (resources != null) {
                textView.setCompoundDrawablesWithIntrinsicBounds(resources[position], 0, 0, 0);
            }
            return convertView;
        }

        @Override
        public int getCount() {
            if (values != null) {
                return values.size();
            } else if (resources != null) {
                return resources.length;
            }
            return 0;
        }

        public int getSelectedIndex() {
            return selectedIndex;
        }

        public void setSelectedIndex(int selectedIndex) {
            this.selectedIndex = selectedIndex;
        }

        public Object getItem(int position) {
            Object item = null;
            if (values != null) {
                item = values.get(position);
            } else if (resources != null) {
                return resources.length;
            }
            return item;
        }
    }

    private class CPPgnItemListAdapter extends CPArrayAdapter {
        private PgnItem parentItem;
        protected List<PgnItem> pgnItemList;

        public CPPgnItemListAdapter(PgnItem parentItem, int initSelection) {
            refresh(parentItem, initSelection);
        }

        public void refresh(final PgnItem parentItem, int initSelection) {
//            Log.d(DEBUG_TAG, "CPPgnItemListAdapter.refresh, thread " + Thread.currentThread().getName());
            this.parentItem = parentItem;
            if (parentItem != null && !parentItem.getAbsolutePath().equals(PgnItem.getRoot().getAbsolutePath())) {
                selectedIndexAdjustment = 1;
            }
            init(null, null, initSelection);
            if (parentItem != null) {
                new CPAsyncTask(chessPad.chessPadView, new CPExecutor() {
                    @Override
                    public void onPostExecute() {
                        Log.d(DEBUG_TAG, String.format("Child list %d items long, thread %s", pgnItemList.size(), Thread.currentThread().getName()));
                        if (selectedIndexAdjustment > 0) {
                            pgnItemList.add(0, parentItem.getParent());
                        }
                        notifyDataSetChanged();
                    }

                    @Override
                    public void onExecuteException(Config.PGNException e) {
                        Log.e(DEBUG_TAG, "CPPgnItemListAdapter.onExecuteException, thread " + Thread.currentThread().getName(), e);
                        onLoadParentCrash();
                    }

                    @Override
                    public void doInBackground(final ProgressPublisher progressPublisher) throws Config.PGNException {
//                        Log.d(DEBUG_TAG, String.format("parentItem.getChildrenNames start, thread %s", Thread.currentThread().getName()));
                        pgnItemList = parentItem.getChildrenNames(new PgnItem.ProgressObserver() {
                            @Override
                            public void setProgress(int progress) {
                                Log.d(DEBUG_TAG, String.format("Offset %d%%, thread %s", progress, Thread.currentThread().getName()));
                                progressPublisher.publishProgress(progress);
                            }
                        });
                    }
                }).execute();
            }
        }

        @Override
        public int getCount() {
//            Log.d(DEBUG_TAG, "CPPgnItemListAdapter.getCount, thread " + Thread.currentThread().getName());
            if (pgnItemList != null) {
                return pgnItemList.size();
            }
            return 0;
        }

        @Override
        public Object getItem(int position) {
            return pgnItemList.get(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = layoutInflater.inflate(R.layout.list_view, null);
            }
            TextView textView = convertView.findViewById(R.id.listViewRow);
            textView.setVisibility(View.VISIBLE);
            textView.setSingleLine();
            if (position == selectedIndex + selectedIndexAdjustment) {
                textView.setBackgroundColor(Color.CYAN);
            } else {
                textView.setBackgroundColor(Color.WHITE);
            }
            String text;
            int res = 0;
            if (position == 0 && selectedIndexAdjustment > 0) {
                text = "..";
                res = R.drawable.go_up;
            } else {
                PgnItem pgnItem = pgnItemList.get(position);
                text = pgnItem.toString();
                if (pgnItem instanceof PgnItem.Zip) {
                    res = R.drawable.zip;
                } else if (pgnItem instanceof PgnItem.Dir) {
                    res = R.drawable.folder;
                } else if (pgnItem instanceof PgnItem.Pgn) {
                    res = R.drawable.pw;
                } else {
//                    res = R.drawable.pw;        // todo ?
                }
            }
            textView.setText(text);
            textView.setCompoundDrawablesWithIntrinsicBounds(res, 0, 0, 0);
            return convertView;
        }

        public boolean isChanged(PgnItem parentItem) {
            return !this.parentItem.equals(parentItem);
        }
    }

    private class CPHeaderListAdapter extends CPArrayAdapter {
        List<Pair<String, String>> headerList;

        CPHeaderListAdapter(List<Pair<String, String>> headers) {
            headerList = headers;
            refresh();
        }

        void refresh() {
            init(null, null, -1);
            notifyDataSetChanged();
        }

        @Override
        public Object getItem(int position) {
            return headerList.get(position);
        }

        @Override
        public int getCount() {
            return headerList.size();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            RowViewHolder _rowViewHolder;
            if (convertView == null) {
                convertView = layoutInflater.inflate(R.layout.list_view, null);
                _rowViewHolder = new RowViewHolder();
                convertView.setTag(_rowViewHolder);
                LinearLayout layout = convertView.findViewById(R.id.headerRowLayout);
                layout.setVisibility(View.VISIBLE);
                _rowViewHolder.labelView = convertView.findViewById(R.id.headerLabel);
                _rowViewHolder.valueView = convertView.findViewById(R.id.headerValue);
                _rowViewHolder.actionButton = convertView.findViewById(R.id.headerActionButton);
            } else {
                _rowViewHolder = (RowViewHolder)convertView.getTag();
            }

            final RowViewHolder rowViewHolder = _rowViewHolder;
            rowViewHolder.labelView.setTag(position);
            rowViewHolder.valueView.setTag(position);
            rowViewHolder.actionButton.setTag(position);
            Pair<String, String> header = headerList.get(position);
            String labelText = header.first;
            rowViewHolder.labelView.setText(labelText);
            rowViewHolder.valueView.setText(header.second);
            rowViewHolder.valueView.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            if (Config.STR.contains(labelText)) {
                rowViewHolder.actionButton.setEnabled(false);
                rowViewHolder.actionButton.setImageResource(android.R.drawable.ic_input_delete);
            } else {
                rowViewHolder.actionButton.setEnabled(true);
                rowViewHolder.actionButton.setImageResource(android.R.drawable.ic_delete);
                rowViewHolder.actionButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            Log.d(DEBUG_TAG, String.format("onClick actionButton %s", v.getTag().toString()));
                            int position = Integer.valueOf(v.getTag().toString());
                            returnFromDiaqlog(DialogType.Headers, CPHeaderListAdapter.this, position);
                        } catch (Config.PGNException e) {
                            Log.e(DEBUG_TAG, "onClick", e);
                        }
                    }
                });
            }

            if (position == headerList.size() - 1) {
                rowViewHolder.labelView.setEnabled(true);
                rowViewHolder.labelView.addTextChangedListener(new RowTextWatcher(rowViewHolder.labelView, 1));
                rowViewHolder.actionButton.setImageResource(android.R.drawable.ic_input_add);
            } else {
                rowViewHolder.labelView.setEnabled(false);
            }

            rowViewHolder.valueView.addTextChangedListener(new RowTextWatcher(rowViewHolder.valueView, 2));
            return convertView;
        }

        private class RowViewHolder {
            int index;
            EditText labelView;
            EditText valueView;
            ImageButton actionButton;

            @Override
            public String toString() {
                return String.format("tag %s=%s, %b)", labelView.getText().toString(), valueView.getText().toString(), actionButton.isEnabled());
            }
        }

        private class RowTextWatcher implements TextWatcher {
            private View view;
            private int pairIndex;

            public RowTextWatcher(View view, int pairIndex) {
                this.view = view;
                this.pairIndex = pairIndex;
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                int index = Integer.valueOf(view.getTag().toString());
                Pair<String, String> header = headerList.get(index);
                String label = header.first;
                String text = header.second;
                if(pairIndex == 1) {
                    label = s.toString();
                } else {
                    text = s.toString();
                }
                if (DEBUG) {
                    Log.d(DEBUG_TAG, String.format("header %s, %s: %s -> %s>", index, label, header.second, text));
                }
                Pair<String, String> newHeader = new Pair<>(label, text);
                headerList.set(index, newHeader);
            }
        };

    }

    static class MergeData extends PgnGraph.MergeData {
        ChessPadView.StringKeeper startStringWrapper;
        ChessPadView.StringKeeper endStringWrapper;
        ChessPadView.StringKeeper pgnPathWrapper;
        ChessPadView.ChangeObserver changeObserver;

        public MergeData(PgnItem.Item target) {
            super(target);
            init();
        }

        public MergeData(BitStream.Reader reader) throws Config.PGNException {
            super(reader);
            init();
        }

        private void init() {
            pgnPathWrapper = new ChessPadView.StringKeeper() {
                @Override
                public void setValue(TextView v, String str) {
                    pgnPath = str;
                }

                @Override
                public String getValue(TextView v) {
                    return pgnPath;
                }
            };

            startStringWrapper = new ChessPadView.StringKeeper() {
                @Override
                public void setValue(TextView v, String value) {
                    if( value.isEmpty()) {
                        start = -1;
                    } else {
                        start = getNumericValue(value);
                    }
                }

                @Override
                public String getValue(TextView v) {
                    if(start == -1) {
                        return "";
                    }
                    return "" + start;
                }
            };

            endStringWrapper = new ChessPadView.StringKeeper() {
                @Override
                public void setValue(TextView v, String value) {
                    if( value.isEmpty()) {
                        end = -1;
                    } else {
                        end = getNumericValue(value);
                    }
                }

                @Override
                public String getValue(TextView v) {
                    if(end == -1) {
                        return "";
                    }
                    return "" + end;
                }
            };
        }

        public void setChangeObserver(ChessPadView.ChangeObserver changeObserver) {
            this.changeObserver = changeObserver;
        }

    }
}