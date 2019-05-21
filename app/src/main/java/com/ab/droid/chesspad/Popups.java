package com.ab.droid.chesspad;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
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
        FicsLogin(++j),
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

    private static final String ADD_HEADER_LABEL = "";
    private ChessPad chessPad;

    private static final List<Integer> wPromotionList = Arrays.asList(R.drawable.qw, R.drawable.rw, R.drawable.bw, R.drawable.nw);
    private static final List<Integer> bPromotionList = Arrays.asList(R.drawable.qb, R.drawable.rb, R.drawable.bb, R.drawable.nb);
    private static final List<Integer> promotionPieces = Arrays.asList(Config.QUEEN, Config.ROOK, Config.BISHOP, Config.KNIGHT);

    private List<String> glyphs;

    private boolean fileListShown = false;
    protected Move promotionMove;
    private Dialog currentAlertDialog;
    private MergeData mergeData, appendData;

    private DialogType dialogType = DialogType.None;
    private String dialogMsg = null;
    private List<Pair<String, String>> editHeaders;
    PgnItemListAdapter pgnItemListAdapter;
    private PgnItemListAdapter mergePgnItemListAdapter;     // I need it here to refer it from within its creation. Any more graceful way to do it?

    public Popups(ChessPad chessPad) {
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
            PgnItem.serialize(writer, editHeaders);
            appendData.serialize(writer);
            mergeData.serialize(writer);
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
            appendData = new MergeData(reader);
            mergeData = new MergeData(reader);
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

    public void launchDialog(final DialogType dialogType) {
        PgnItem pgnItem;
        switch (dialogType) {
            case Append:
                dlgMergeOrAppend(dialogType);
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

            case FicsLogin:
                ficsLogin();
                break;

            case Glyphs:
                launchDialog(dialogType, new TextArrayAdapter(glyphs, chessPad.pgnGraph.getGlyph()));
                break;

            case Headers:
                if (editHeaders == null) {
                    editHeaders = PgnItem.cloneHeaders(chessPad.getHeaders());
                    editHeaders.add(new Pair<>(ADD_HEADER_LABEL, ""));
                }
                launchDialog(dialogType, null, 0, new HeaderListAdapter(editHeaders), DialogButton.OkCancel);
                break;

            case Load:
                int selectedPgnItem = -1;
                pgnItem = chessPad.pgnGraph.getPgn();
                if (pgnItem != null) {
                    selectedPgnItem = pgnItem.parentIndex(chessPad.currentPath);
                }
                launchDialog(dialogType, getPgnItemListAdapter(chessPad.currentPath, selectedPgnItem));
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


            case Merge:
                dlgMergeOrAppend(dialogType);
                break;


            case Promotion:
                List<Integer> _promotionList = null;
                int index;
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

            case SaveModified:
                dlgMessage(Popups.DialogType.SaveModified, getResources().getString(R.string.msg_save), R.drawable.exclamation, Popups.DialogButton.YesNoCancel);
                break;

            case ShowMessage:
                dlgMessage(dialogType, String.format(getResources().getString(R.string.about), chessPad.versionName), 0, DialogButton.Ok);
                break;

            case Variations:
                launchDialog(dialogType, new TextArrayAdapter(chessPad.pgnGraph.getVariations(), 0));       // 1st one is main line
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

    private void returnFromDialog(DialogType dialogType, Object selectedValue, int selected) {
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
                promotionMove.setPiecePromoted(promotionPieces.get(selected) | (promotionMove.moveFlags & Config.BLACK));
                chessPad.pgnGraph.validateUserMove(promotionMove);  // validate check
                try {
                    chessPad.pgnGraph.addUserMove(promotionMove);
                } catch (Config.PGNException e) {
                    Log.e(DEBUG_TAG, e.getMessage(), e);
                }
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
                        Pair<String, String> lastPair = editHeaders.get(editHeaders.size() - 1);
                        String label = lastPair.first.trim();
                        if (label.isEmpty()) {
                            Toast.makeText(chessPad, R.string.err_empty_new_tag_name, Toast.LENGTH_LONG).show();
                            break;
                        } else {
                            for(Pair<String, String> header : editHeaders) {
                                if(lastPair == header) {
                                    continue;
                                }
                                if(label.equals(header.first)) {
                                    Toast.makeText(chessPad, R.string.err_tag_name_exists, Toast.LENGTH_LONG).show();
                                    return;
                                }
                            }
                            editHeaders.add(new Pair<>(ADD_HEADER_LABEL, ""));      // new 'add header' row
                        }
                    } else {
                        editHeaders.remove(selected);
                    }
                    dismissDlg();
                    launchDialog(DialogType.Headers);
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
        if(chessPad.mode == ChessPad.Mode.FicsConnection) {
            if(this.dialogType == DialogType.FicsLogin) {
                chessPad.mode = ChessPad.Mode.Game;
            }
        }
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
                returnFromDialog(dialogType, null, which);
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
            builder.setAdapter(arrayAdapter, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    try {
                        arrayAdapter.onConvertViewClick(which);
                    } catch (Throwable t) {
                        Log.e(DEBUG_TAG, "onClick", t);
                    }
                }
            });

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
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
        if (arrayAdapter != null) {
            alertDialog.getListView().setSelection(arrayAdapter.getSelectedIndex());
        }
        currentAlertDialog = alertDialog;
    }

    static class MergeData extends PgnGraph.MergeData {
        ChessPadView.StringKeeper startStringWrapper;
        ChessPadView.StringKeeper endStringWrapper;
        ChessPadView.StringKeeper maxPlysPathWrapper;
        ChessPadView.StringKeeper pgnPathWrapper;
        ChessPadView.ChangeObserver changeObserver;

        public MergeData() {
            super();
            init();
        }

        public MergeData(BitStream.Reader reader) throws Config.PGNException {
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

        public void setChangeObserver(ChessPadView.ChangeObserver changeObserver) {
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
                if (stringKeeper == null) {
                    return;
                }
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
        final Dialog mDialog = new Dialog(chessPad);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setContentView(R.layout.dlg_merge);
        final Button btnOk = mDialog.findViewById(R.id.ok_button);
        final TextView textViewFilePath = mDialog.findViewById(R.id.file_name);

        MergeData _mergeData;

        if(dialogType == DialogType.Append) {
            _mergeData = this.appendData;
        } else {  // if(dialogType == DialogType.Merge)
            textViewFilePath.setEnabled(false);
            _mergeData = this.mergeData;
            View mergeControls = mDialog.findViewById(R.id.merge_controls_pane);
            mergeControls.setVisibility(View.VISIBLE);
        }
        final MergeData mergeData = _mergeData;
        if(mergeData.pgnPath == null) {
            PgnItem path = chessPad.currentPath;
            mergeData.pgnPath = PgnItem.getRelativePath(path);
            while ((path instanceof PgnItem.Item) || (path instanceof PgnItem.Pgn)) {
                mergeData.pgnPath = PgnItem.getRelativePath(path);
                path = path.getParent();
            }
        }

        mergeData.setChangeObserver(new ChessPadView.ChangeObserver() {
            @Override
            public void onValueChanged(Object value) {
                btnOk.setEnabled(((MergeData)value).isMergeSetupOk());
            }
        });

        textViewFilePath.addTextChangedListener(new TextWatcher() {
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
        textViewFilePath.setText(mergeData.pgnPath);

        final TextView fileNameTextView = mDialog.findViewById(R.id.file_name);
        fileNameTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                mergeData.pgnPathWrapper.setValue(s.toString());
            }
        });
        fileNameTextView.setText(mergeData.pgnPath);

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
                    String fileName = fileNameTextView.getText().toString();
                    Log.d(DEBUG_TAG, String.format("onClick: %s", DialogType.Merge.toString()));
                    File appendToFile = new File(PgnItem.getRoot(), fileName);
                    returnFromDialog(dialogType, appendToFile.getAbsoluteFile(), 0);
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
                    PgnItem path = chessPad.currentPath;
                    fileNameTextView.setText(PgnItem.getRelativePath(path));
                    while ((path instanceof PgnItem.Item) || (path instanceof PgnItem.Pgn)) {
                        fileNameTextView.setText(PgnItem.getRelativePath(path));
                        path = path.getParent();
                    }

                    int selectedIndex = pgnItem.parentIndex(path);
                    mergePgnItemListAdapter = new PgnItemListAdapter(path, selectedIndex) {
                        @Override
                        protected void onConvertViewClick(int position) {
                            Log.d(DEBUG_TAG, "PgnItemListAdapter.subclass.onConvertViewClick " + position);
                            PgnItem newPath = (PgnItem) mergePgnItemListAdapter.getItem(position);
                            fileNameTextView.setText(PgnItem.getRelativePath(newPath));
                            if (newPath instanceof PgnItem.Pgn) {
                                fileListShown = false;
                                fileListView.setVisibility(View.GONE);
                                fileListView.setAdapter(null);
                            } else if (newPath instanceof PgnItem.Item) {
                                Log.e(DEBUG_TAG, String.format("Invalid selection %s", newPath.toString()));
                            } else {
                                chessPad.currentPath = newPath;
                                int selectedIndex = pgnItem.parentIndex(newPath);
                                mergePgnItemListAdapter.refresh(chessPad.currentPath, selectedIndex);
                            }

                        }
                    };
                    fileListView.setAdapter(mergePgnItemListAdapter);
                    fileListView.setFastScrollEnabled(true);
                }
            }
        });

        // for merge only:
        final TextView textViewAnnotate = mDialog.findViewById(R.id.annotate);
        setCheckMark(textViewAnnotate, mergeData);
        textViewAnnotate.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    mergeData.annotate = !mergeData.annotate;
                    setCheckMark(textViewAnnotate, mergeData);
                }
                // true if the event was handled and should not be given further down to other views.
                return true;
            }
        });
        attachStringKeeper((EditText)mDialog.findViewById(R.id.merge_start), mergeData.startStringWrapper);
        attachStringKeeper((EditText)mDialog.findViewById(R.id.merge_end), mergeData.endStringWrapper);
        attachStringKeeper((EditText)mDialog.findViewById(R.id.merge_max_plys), mergeData.maxPlysPathWrapper);

        currentAlertDialog = mDialog;
        mDialog.show();
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
                .setPositiveButton(R.string.login, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dismissDlg();
                        chessPad.getFicsSettings().setUsername(usernameEditText.getText().toString());
                        chessPad.getFicsSettings().setPassword(passwordEditText.getText().toString());
                        chessPad.openFicsConnection();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int id) {
                        dismissDlg();
                    }
                });
        ;

        currentAlertDialog = builder.create();

        boolean ficsLoginAsGuest = chessPad.getFicsSettings().isLoginAsGuest();
        usernameEditText.setEnabled(!ficsLoginAsGuest);
        usernameEditText.setText(chessPad.getFicsSettings().getUsername());
        passwordEditText.setEnabled(!ficsLoginAsGuest);
        passwordEditText.setText(chessPad.getFicsSettings().getUsername());
        CheckBox checkBox = view.findViewById(R.id.login_as_guest);
        checkBox.setChecked(ficsLoginAsGuest);
        checkBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean ficsLoginAsGuest = !chessPad.getFicsSettings().isLoginAsGuest();
                chessPad.getFicsSettings().setLoginAsGuest(ficsLoginAsGuest);
                usernameEditText.setEnabled(!ficsLoginAsGuest);
                passwordEditText.setEnabled(!ficsLoginAsGuest);
            }
        });
        currentAlertDialog.show();
    }

    private PgnItemListAdapter getPgnItemListAdapter(PgnItem parentItem, int initSelection) {
        if(pgnItemListAdapter == null || pgnItemListAdapter.isChanged(parentItem)
                   || pgnItemListAdapter.getCount() == 0) {  // kludgy way to fix storage permission change problem
            pgnItemListAdapter = new PgnItemListAdapter(parentItem, initSelection);
        }
        pgnItemListAdapter.setSelectedIndex(initSelection);
        return pgnItemListAdapter;
    }

    private void onLoadParentCrash() {
        dismissDlg();
        crashAlert(R.string.crash_cannot_list);
        pgnItemListAdapter = null;
        chessPad.currentPath = PgnItem.getRootDir();
    }

    /////////////// array adapters /////////////////////////

    private class TextArrayAdapter extends CPArrayAdapter {
        public TextArrayAdapter(List<?> values, int selectedIndex) {
            super(values, selectedIndex);
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        protected void setRowViewHolder(RowViewHolder rowViewHolder, final int position) {
            rowViewHolder.valueView = rowViewHolder.convertView.findViewById(R.id.listViewRow);
            rowViewHolder.valueView.setVisibility(View.VISIBLE);
            rowViewHolder.valueView.setEnabled(false);
            rowViewHolder.valueView.setText(getValues().get(position).toString());
            rowViewHolder.valueView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        onConvertViewClick(position);
                    }
                    // true if the event was handled and should not be given further down to other views.
                    return true;
                }
            });
        }

        @Override
        protected void onConvertViewClick(int position) {
            Log.d(DEBUG_TAG, "TextArrayAdapter.onConvertViewClick " + position);
            returnFromDialog(dialogType, getValues().get(position), position);
        }
    }

    private class HeaderListAdapter extends CPArrayAdapter {
        // last item is an extra header to add a new line
        private List<Pair<String, String>> values;
        RowTextWatcher[] rowTextWatchers;

        HeaderListAdapter(List<Pair<String, String>> values) {
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
            View layout = rowViewHolder.convertView.findViewById(R.id.list_row_layout);
            layout.setVisibility(View.VISIBLE);
            rowViewHolder.labelView = rowViewHolder.convertView.findViewById(R.id.list_row_label);
            rowViewHolder.valueView = rowViewHolder.convertView.findViewById(R.id.list_row_value);
            rowViewHolder.actionButton = rowViewHolder.convertView.findViewById(R.id.list_row_action_button);
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

            Pair<String, String> header = (Pair<String, String>) values.get(position);
            String labelText = header.first;
            rowViewHolder.labelView.setText(labelText);
            rowViewHolder.valueView.setText(header.second);

            rowViewHolder.labelView.addTextChangedListener(rowTextWatcher);
            rowViewHolder.valueView.addTextChangedListener(rowTextWatcher);

            if (Config.STR.contains(labelText)) {
                rowViewHolder.actionButton.setEnabled(false);
                rowViewHolder.actionButton.setImageResource(android.R.drawable.ic_input_delete);
            } else {
                rowViewHolder.actionButton.setEnabled(true);
                rowViewHolder.actionButton.setImageResource(android.R.drawable.ic_delete);
                rowViewHolder.actionButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(DEBUG_TAG, String.format("onClick actionButton %s", v.getTag().toString()));
                        int position = Integer.valueOf(v.getTag().toString());
                        returnFromDialog(DialogType.Headers, HeaderListAdapter.this, position);
                    }
                });
            }

            if (position == values.size() - 1) {
                rowViewHolder.labelView.setEnabled(true);
                rowViewHolder.labelView.setHint(R.string.hint_header_label);
                rowViewHolder.valueView.setHint(R.string.hint_header_value);
                rowViewHolder.actionButton.setImageResource(android.R.drawable.ic_input_add);
            } else {
                rowViewHolder.labelView.setEnabled(false);
            }
        }

        @Override
        protected void onConvertViewClick(int position) {
            Log.d(DEBUG_TAG, "HeaderListAdapter.onConvertViewClick " + position);
//            returnFromDialog(dialogType, values.get(position), position);
        }

        private class RowTextWatcher implements TextWatcher {
            // needed to keep editHeaders in sync with EditText
            private RowViewHolder rowViewHolder;

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
                Pair<String, String> header = values.get(index);
                String label = header.first;
                String text = header.second;
                String newLabel = rowViewHolder.labelView.getText().toString();
                String newText = rowViewHolder.valueView.getText().toString();
                if(label.equals(newLabel) && text.equals(newText)) {
                    return;
                }
                if (DEBUG) {
                    Log.d(DEBUG_TAG, String.format("header %s, %s -> %s : %s -> %s>", index, label, text, newLabel, newText));
                }
                values.set(index, new Pair<>(newLabel, newText));
            }
        };
    }

    private class PgnItemListAdapter extends CPArrayAdapter {
        protected PgnItem parentItem;
        private List<PgnItem> pgnItemList;  // values
        boolean addParentLink;

        public PgnItemListAdapter(PgnItem parentItem, int initSelection) {
            super();
            refresh(parentItem, initSelection);
        }

        public void refresh(final PgnItem parentItem, int initSelection) {
            Log.d(DEBUG_TAG, "PgnItemListAdapter.refresh, thread " + Thread.currentThread().getName());
            this.parentItem = parentItem;
            init(null, initSelection);
            if (parentItem == null) {
                return;
            }

            addParentLink = false;
            if (!parentItem.getAbsolutePath().equals(PgnItem.getRoot().getAbsolutePath())) {
                addParentLink = true;
                ++selectedIndex;
            }

            new CPAsyncTask(chessPad.chessPadView, new CPExecutor() {
                @Override
                public void onPostExecute() {
                    Log.d(DEBUG_TAG, String.format("Child list %d items long, thread %s", pgnItemList.size(), Thread.currentThread().getName()));
                    if (addParentLink) {
                        pgnItemList.add(0, parentItem.getParent());
                    }
                    notifyDataSetChanged();
                }

                @Override
                public void onExecuteException(Config.PGNException e) {
                    Log.e(DEBUG_TAG, "PgnItemListAdapter.onExecuteException, thread " + Thread.currentThread().getName(), e);
                    onLoadParentCrash();
                }

                @Override
                public void doInBackground(final ProgressPublisher progressPublisher) throws Config.PGNException {
                    Log.d(DEBUG_TAG, String.format("PgnItemListAdapter.getChildrenNames start, thread %s", Thread.currentThread().getName()));
                    pgnItemList = parentItem.getChildrenNames(new PgnItem.ProgressObserver() {
                        @Override
                        public void setProgress(int progress) {
                            Log.d(DEBUG_TAG, String.format("Offset %d%%, thread %s", progress, Thread.currentThread().getName()));
                            progressPublisher.publishProgress(progress);
                        }
                    });
                    Log.d(DEBUG_TAG, String.format("getChildrenNames list %d items long, thread %s", pgnItemList.size(), Thread.currentThread().getName()));
                }
            }).execute();
        }

        @Override
        protected List<?> getValues() {
            return pgnItemList;
        }

        @Override
        public void setSelectedIndex(int selectedIndex) {
            if(addParentLink) {
                ++selectedIndex;
            }
            this.selectedIndex = selectedIndex;
        }

        public boolean isChanged(PgnItem parentItem) {
            return !this.parentItem.equals(parentItem);
        }

        @Override
        protected void onConvertViewClick(int position) {
            Log.d(DEBUG_TAG, "PgnItemListAdapter.onConvertViewClick " + position);
            returnFromDialog(dialogType, getValues().get(position), position);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        protected void setRowViewHolder(RowViewHolder rowViewHolder, int position) {
            String text;
            int res = 0;
            if (position == 0 && addParentLink) {
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
            rowViewHolder.valueView = rowViewHolder.convertView.findViewById(R.id.listViewRow);
            rowViewHolder.valueView.setVisibility(View.VISIBLE);
            rowViewHolder.valueView.setSingleLine();
            rowViewHolder.valueView.setText(text);
            rowViewHolder.valueView.setCompoundDrawablesWithIntrinsicBounds(res, 0, 0, 0);
        }
    }

}