package com.ab.droid.chesspad;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.ab.pgn.BitStream;
import com.ab.pgn.Config;
import com.ab.pgn.Move;
import com.ab.pgn.Pair;
import com.ab.pgn.PgnItem;
import com.ab.pgn.Square;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * various dialogs for ChessPad
 * Created by alex on 10/30/16.
 */
public class Popups {
    protected final String DEBUG_TAG = this.getClass().getName();

    private static int j = -1;

    public enum DialogType {
        None(++j),
        Glyphs(++j),
        Variations(++j),
        DeleteYesNo(++j),
        Promotion(++j),
        Menu(++j),
        About(++j),
        Load(++j),
        Append(++j),
        Headers(++j),
        SaveModified(++j),
//        Delete(++j),
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

    public static final String ADD_HEADER_LABEL = "            ";
    private ChessPad chessPad;
    private final int[] promotionListW = {R.drawable.qw, R.drawable.bw, R.drawable.nw, R.drawable.rw};
    private final int[] promotionListB = {R.drawable.qb, R.drawable.bb, R.drawable.nb, R.drawable.rb};
    private List<String> glyphs;

    private boolean fileListShown = false;
    protected Square selected;
    protected Move promotionMove;
    private Dialog currentAlertDialog;

    private DialogType dialogType = DialogType.None;
    //    private int dialogYesNoMsgRes;
    private String dialogMsg = null;
    private List<Pair<String, String>> editHeaders;
    private int topVisibleRow = 0;

    public Popups(ChessPad chessPad) {
        this.chessPad = chessPad;
        glyphs = Arrays.asList(getResources().getStringArray(R.array.glyphs));
    }

    public void serialize(BitStream.Writer writer) throws IOException {
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
        if (selected == null) {
            writer.write(0, 1);
        } else {
            writer.write(1, 1);
            selected.serialize(writer);
        }
        writer.write(dialogType.getValue(), 4);
        writer.writeString(dialogMsg);
        PgnItem.serialize(writer, editHeaders);
        writer.write(topVisibleRow, 12);
    }

    public void unserialize(BitStream.Reader reader) throws IOException {
        fileListShown = reader.read(1) == 1;
        if (reader.read(1) == 1) {
            promotionMove = new Move(reader, chessPad.pgnTree.getBoard());
            promotionMove.snapshot = null;
        }
        if (reader.read(1) == 1) {
            selected = new Square(reader);
        }
        this.dialogType = DialogType.value(reader.read(4));
        dialogMsg = reader.readString();
        if (dialogMsg == null || dialogMsg.isEmpty()) {
            dialogMsg = null;
        }
        editHeaders = PgnItem.unserializeHeaders(reader);
        topVisibleRow = reader.read(12);
        afterUnserialize();
    }

    private void afterUnserialize() {
        if (selected != null) {
            chessPad.chessPadView.setSelected(selected);
        }
        try {
            launchDialog(this.dialogType);
        } catch (IOException e) {
            Log.e(DEBUG_TAG, "onResume() 4", e);
        }
    }

    private Resources getResources() {
        return chessPad.getResources();
    }

    public void dismiss() {
        dismissDlg();
    }

    public void launchDialog(DialogType dialogType) throws IOException {
        PgnItem pgnItem;
        switch (dialogType) {
            case Glyphs:
                launchDialog(dialogType, new CPArrayAdapter(glyphs, chessPad.pgnTree.getGlyph()));
                break;

            case Variations:
                List<Move> variations = chessPad.pgnTree.getVariations();
                launchDialog(dialogType, new CPArrayAdapter(variations, 0));       // 1st one is main line
                break;

            case Promotion:
                if ((this.promotionMove.moveFlags & Config.BLACK) == 0) {
                    launchDialog(dialogType, new CPArrayAdapter(promotionListW, -1));
                } else {
                    launchDialog(dialogType, new CPArrayAdapter(promotionListB, -1));
                }
                break;

            case Load:
                int selectedPgnItem = -1;
                pgnItem = chessPad.pgnTree.getPgn();
                if (pgnItem != null) {
                    selectedPgnItem = pgnItem.parentIndex(chessPad.currentPath);
                }
                launchDialog(dialogType, new CPPgnItemListAdapter(chessPad.currentPath, selectedPgnItem));
                break;

            case Menu:
                launchDialog(dialogType, new CPArrayAdapter(chessPad.getMenuItems(), -1));
                break;

/*
            case DeleteYesNo:
                dlgMessage(dialogType, getResources().getString(R.string.msg_del_move), DialogButton.YesNo);
                break;
*/

            case About:
                dlgMessage(dialogType, String.format(getResources().getString(R.string.about), chessPad.versionName), 0, DialogButton.Ok);
                break;

            case Append:
                dlgAppend();
                break;

            case Headers:
                if(chessPad.mode == ChessPad.Mode.Game) {
                    pgnItem = chessPad.pgnTree.getPgn();
                    if (pgnItem != null) {
                        CPHeaderListAdapter adapter;
                        if (editHeaders == null) {
                            adapter = new CPHeaderListAdapter((PgnItem.Item) pgnItem);
                        } else {
                            adapter = new CPHeaderListAdapter(editHeaders);
                        }
                        launchDialog(dialogType, null, 0, adapter, DialogButton.OkCancel);
                    }
                } else {
                    if (editHeaders == null) {
                        editHeaders = PgnItem.cloneHeaders(chessPad.setup.getHeaders());
                    }
                    CPHeaderListAdapter adapter = new CPHeaderListAdapter(editHeaders);
                    launchDialog(dialogType, null, 0, adapter, DialogButton.OkCancel);
                }
                break;

            case DeleteYesNo:
                if (chessPad.isFirstMove()) {
                    dlgMessage(Popups.DialogType.DeleteYesNo, getResources().getString(R.string.msg_del_game), R.drawable.exclamation, Popups.DialogButton.YesNo);
                } else if (chessPad.pgnTree.isEnd()) {
                    chessPad.pgnTree.delCurrentMove();
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

    private void returnFromDiaqlog(DialogType dialogType, Object selectedValue, int selected) throws IOException {
        this.dialogType = DialogType.None;
        if(dialogType == DialogType.SaveModified) {
            switch (selected) {
                case DialogInterface.BUTTON_POSITIVE:
                    try {
                        if(chessPad.isSaveable()) {
                            chessPad.pgnTree.save();
                            chessPad.setPgnTree(chessPad.nextPgnItem);
                        } else {
                            dismissDlg();
                            launchDialog(DialogType.Append);
                            return;
                        }
                    } catch (IOException e) {
                        Log.e(DEBUG_TAG, "dlgMessage()", e);
                    }
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    try {
                        chessPad.pgnTree.setModified(false);
                        chessPad.setPgnTree(chessPad.nextPgnItem);
                    } catch (IOException e) {
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
            case About:
                dismissDlg();
                break;

            case Glyphs:
                dismissDlg();
                chessPad.pgnTree.setGlyph(selected);
                break;

            case Variations:
                dismissDlg();
                Move variation = (Move) selectedValue;
                chessPad.pgnTree.toVariation(variation);
                break;

            case DeleteYesNo:
                dismissDlg();
                chessPad.delete();
                break;

            case Promotion:
                dismissDlg();
                promotionMove.piecePromoted = (selected + 2) | (promotionMove.moveFlags & Config.BLACK);
                promotionMove.snapshot = null;
                chessPad.pgnTree.addUserMove(promotionMove);
                break;

            case Menu:
                dismissDlg();
                chessPad.executeMenuCommand(((ChessPad.MenuItem) selectedValue).getCommand());
                break;

            case Load:
                if (selectedValue instanceof PgnItem.Item) {
                    Log.d(DEBUG_TAG, String.format("selected %s", selectedValue.toString()));
                    dismissDlg();
                    PgnItem.Item actualItem = (PgnItem.Item) selectedValue;
                    try {
                        PgnItem.getPgnItem(actualItem);
                        chessPad.setPgnTree(actualItem);
                    } catch (IOException e) {
                        Log.e(DEBUG_TAG, String.format("selected %s", selectedValue.toString()), e);
                    }
                } else {
                    chessPad.currentPath = (PgnItem) selectedValue;
                    dismissDlg();
                    launchDialog(DialogType.Load);
                }
                break;

            case Append:
                PgnItem.Pgn pgn = new PgnItem.Pgn(selectedValue.toString());
                chessPad.pgnTree.getPgn().setParent(pgn);
                chessPad.pgnTree.getPgn().setIndex(-1);
                chessPad.pgnTree.save();
                if(chessPad.nextPgnItem != null) {
                    chessPad.setPgnTree(chessPad.nextPgnItem);
                }
                break;

            case Headers:
                if (selected == DialogInterface.BUTTON_POSITIVE) {
                    if(chessPad.getMode() == ChessPad.Mode.Game) {
                        editHeaders.remove(editHeaders.size() - 1);     // remove 'add new' row
                        chessPad.pgnTree.setHeaders(editHeaders);
                    } else {
                        chessPad.setup.setHeaders(editHeaders);
                    }
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

    void dlgMessage(final DialogType dialogType, String msg, int icon, DialogButton button) {
        launchDialog(dialogType, msg, icon, null, button);
    }

    private void launchDialog(final DialogType dialogType, final CPArrayAdapter arrayAdapter) {
        launchDialog(dialogType, null, 0, arrayAdapter, DialogButton.None);
    }

    // todo: fix sizes
    private void launchDialog(final DialogType dialogType, String msg, int icon, final CPArrayAdapter arrayAdapter, DialogButton button) {
        EditText editText;  // fictitious view to enable keyboard display for CPHeadersAdapter
        if (currentAlertDialog != null) {
            return;
        }
        dialogMsg = msg;
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    returnFromDiaqlog(dialogType, null, which);
                } catch (IOException e) {
                    Log.e(DEBUG_TAG, "dlgMessage()", e);
                }
            }
        };
        editText = new EditText(chessPad);
        editText.setVisibility(View.GONE);

        AlertDialog.Builder builder = new AlertDialog.Builder(chessPad);

        if (msg != null) {
//            ImageView imageView = new ImageView(chessPad);
//            imageView.setImageResource(R.drawable.exclamation);
//            builder.setView(imageView);
//            builder.setMessage(msg);
            TextView textView = new TextView(chessPad);
            textView.setText(msg);
            textView.setTextSize(16);
            textView.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
            builder.setView(textView);

//            builder.setTitle("Question");
//            builder.setMessage(msg).setIcon(R.drawable.exclamation);
        }
        if (arrayAdapter != null) {
            builder.setSingleChoiceItems(
                    arrayAdapter,
                    arrayAdapter.getInitSelection(),
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
                    .setView(editText)
            ;
        }
        if (button == DialogButton.YesNoCancel) {
            builder = builder.setNegativeButton(R.string.no, dialogClickListener);
            builder = builder.setPositiveButton(R.string.yes, dialogClickListener);
            builder = builder.setNeutralButton(R.string.cancel, dialogClickListener);
        } else {
            if (button == DialogButton.YesNo) {
                builder = builder.setNegativeButton(R.string.no, dialogClickListener);
                builder = builder.setPositiveButton(R.string.yes, dialogClickListener);
            }
            if (button == DialogButton.OkCancel) {
                builder = builder.setNegativeButton(R.string.cancel, dialogClickListener);
            }
            if (button == DialogButton.Ok || button == DialogButton.OkCancel) {
                builder = builder.setPositiveButton(R.string.ok, dialogClickListener);
            }
        }
        currentAlertDialog = builder.create();
        currentAlertDialog.show();
    }

    private String getTruncatedPath(PgnItem pgnItem) {
        String path = pgnItem.getAbsolutePath();
        if (path.startsWith(chessPad.root.getAbsolutePath())) {
            path = path.substring(chessPad.root.getAbsolutePath().length());
        }
        return path;
    }

    private void dlgAppend() {
        final Dialog mDialog = new Dialog(chessPad);
        mDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mDialog.setContentView(R.layout.dlg_append);
        final Button btnOk = (Button) mDialog.findViewById(R.id.ok_button);
        btnOk.setEnabled(false);
        final TextView textView = ((TextView) mDialog.findViewById(R.id.file_name));
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
        final ListView listView = (ListView) mDialog.findViewById(R.id.file_list);
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
                        File appendToFile = new File(chessPad.root, fileName);
                        returnFromDiaqlog(DialogType.Append, appendToFile.getAbsoluteFile(), 0);
                    } catch (IOException e) {
                        Log.e(DEBUG_TAG, String.format("onClick: %s", DialogType.Append.toString()), e);
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
                PgnItem pgnItem = chessPad.pgnTree.getPgn();
                if (pgnItem != null) {
                    try {
                        int selectedIndex = pgnItem.parentIndex(chessPad.currentPath);
                        final CPPgnItemListAdapter mAdapter = new CPPgnItemListAdapter(chessPad.currentPath, selectedIndex);
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
                                        mAdapter.refresh(chessPad.currentPath, clicked);
                                    } catch (IOException e) {
                                        Log.e(DEBUG_TAG, String.format("onClick 2: %s", DialogType.Append.toString()), e);
                                    }
                                }
                            }
                        });
                    } catch (IOException e) {
                        Log.e(DEBUG_TAG, String.format("onClick 1: %s", DialogType.Append.toString()), e);
                    }
                }
            }
        });

        currentAlertDialog = mDialog;
        mDialog.show();
    }

    private class CPArrayAdapter extends ArrayAdapter<Object> {
        protected List<?> values;
        protected int[] resources;
        protected int initSelection;
        protected LayoutInflater layoutInflater;

        protected CPArrayAdapter() {
            super(chessPad, 0);
        }

        public CPArrayAdapter(int[] resources, int initSelection) {
            super(chessPad, 0);
            init(null, resources, initSelection);
        }

        public CPArrayAdapter(List<?> values, int initSelection) {
            super(chessPad, 0);
            init(values, null, initSelection);
        }

        protected void init(List<?> values, int[] resources, int initSelection) {
            this.values = values;
            this.resources = resources;
            this.initSelection = initSelection;
            layoutInflater = LayoutInflater.from(chessPad);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = layoutInflater.inflate(R.layout.list_view, null);
            }
            TextView textView = (TextView) convertView.findViewById(R.id.listViewRow);
            textView.setVisibility(View.VISIBLE);
            if (position == initSelection) {
                textView.setBackgroundColor(Color.CYAN);
            } else {
                textView.setBackgroundColor(Color.WHITE);
            }

            if (values != null) {
                Object value = values.get(position);
                textView.setText(value.toString());
                if (value instanceof ChessPad.MenuItem) {
                    if (((ChessPad.MenuItem) value).isEnabled()) {
                        textView.setTextColor(Color.BLACK);
                    } else {
                        textView.setTextColor(Color.LTGRAY);
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

        public int getInitSelection() {
            return initSelection;
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

        public CPPgnItemListAdapter(PgnItem parentItem, int initSelection) throws IOException {
            refresh(parentItem, initSelection);
        }

        public void refresh(PgnItem parentItem, int initSelection) throws IOException {
            this.parentItem = parentItem;
            if (parentItem != null && parentItem.getParent() != null && initSelection >= 0) {
                ++initSelection;
            }
            init(null, null, initSelection);
            if (parentItem != null) {
                pgnItemList = parentItem.getChildrenNames();
                Log.d(DEBUG_TAG, String.format("Child list id %s items long", pgnItemList.size()));
                if (parentItem.getParent() != null) {
                    pgnItemList.add(0, parentItem.getParent());
                }
                notifyDataSetChanged();
            }
        }

        @Override
        public int getCount() {
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
            TextView textView = (TextView) convertView.findViewById(R.id.listViewRow);
            textView.setVisibility(View.VISIBLE);
            textView.setSingleLine();
            if (position == initSelection) {
                textView.setBackgroundColor(Color.CYAN);
            } else {
                textView.setBackgroundColor(Color.WHITE);
            }
            String text;
            int res = 0;
            if (position == 0 && parentItem.getParent() != null) {
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
//                    res = R.drawable.pw;        // todo
                }
            }
            textView.setText(text);
            textView.setCompoundDrawablesWithIntrinsicBounds(res, 0, 0, 0);
            return convertView;
        }
    }

    private class CPHeaderListAdapter extends CPArrayAdapter {
        CPHeaderListAdapter(PgnItem.Item thisItem) throws IOException {
            editHeaders = thisItem.cloneHeaders(Config.HEADER_FEN);     // skip FEN
            editHeaders.add(new Pair<>(ADD_HEADER_LABEL, ""));
            refresh();
        }

        CPHeaderListAdapter(List<Pair<String, String>> headers) throws IOException {
            editHeaders = headers;
            refresh();
        }

        void refresh() throws IOException {
            init(null, null, -1);
            notifyDataSetChanged();
        }

        @Override
        public Object getItem(int position) {
            return editHeaders.get(position);
        }

        @Override
        public int getCount() {
            if(editHeaders == null) {
                Log.e(DEBUG_TAG, "getCount, editHeaders == null");
                return 0;
            }
            return editHeaders.size();
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            RowViewHolder rowViewHolder;
            if (convertView == null) {
                convertView = layoutInflater.inflate(R.layout.list_view, null);
                rowViewHolder = new RowViewHolder();
                convertView.setTag(rowViewHolder);
                LinearLayout layout = (LinearLayout) convertView.findViewById(R.id.headerRowLayout);
                layout.setVisibility(View.VISIBLE);
                rowViewHolder.labelView = (TextView) convertView.findViewById(R.id.headerLabel);
                rowViewHolder.valueView = (TextView) convertView.findViewById(R.id.headerValue);
                rowViewHolder.actionButton = (ImageButton) convertView.findViewById(R.id.headerActionButton);
            } else {
                rowViewHolder = (RowViewHolder)convertView.getTag();
            }

            rowViewHolder.labelView.setTag(position);
            rowViewHolder.valueView.setTag(position);
            rowViewHolder.actionButton.setTag(position);
            Pair<String, String> header = editHeaders.get(position);
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
                        } catch (IOException e) {
                            Log.e(DEBUG_TAG, "onClick", e);
                        }
                    }
                });
            }

            if (position == editHeaders.size() - 1) {
                rowViewHolder.labelView.setEnabled(true);
                rowViewHolder.actionButton.setImageResource(android.R.drawable.ic_input_add);
            } else {
                rowViewHolder.labelView.setEnabled(false);
            }

            rowViewHolder.labelView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    Log.d(DEBUG_TAG, String.format("header label %d, focus %b", position, hasFocus));
                    if (!hasFocus) {
                        int position = Integer.valueOf(v.getTag().toString());
                        String text = ((TextView) v).getText().toString();
                        String value = editHeaders.get(position).second;
                        Log.d(DEBUG_TAG, String.format("%s, label %s -> %s>", position, editHeaders.get(position).first, text));
                        Pair<String, String> newHeader = new Pair<>(text, value);
                        editHeaders.set(position, newHeader);
                    }
                }
            });
            rowViewHolder.valueView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    Log.d(DEBUG_TAG, String.format("header value %d, focus %b", position, hasFocus));
                    if (!hasFocus) {
                        int position = Integer.valueOf(v.getTag().toString());
                        String label = editHeaders.get(position).first;
                        String text = ((TextView) v).getText().toString();
                        Log.d(DEBUG_TAG, String.format("%s, %s: %s -> %s>", position, label, editHeaders.get(position).second, text));
                        Pair<String, String> newHeader = new Pair<>(label, text);
                        editHeaders.set(position, newHeader);
                    }
                }
            });
            return convertView;
        }

        private class RowViewHolder {
            TextView labelView;
            TextView valueView;
            ImageButton actionButton;

            @Override
            public String toString() {
                return String.format("tag %s=%s, %b)", labelView.getText().toString(), valueView.getText().toString(), actionButton.isEnabled());
            }
        }
    }
}