package com.ab.droid.chesspad;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.List;

public abstract class CPArrayAdapter extends ArrayAdapter {
    private List<?> values;
    private int layoutResource;
    protected LayoutInflater layoutInflater;
    protected int selectedIndex;

    protected abstract void setRowViewHolder(RowViewHolder rowViewHolder, final int position);
    protected abstract void onConvertViewClick(int position);

    public CPArrayAdapter() {
        super(ChessPad.getContext(), R.layout.list_view);
        init(null, -1);
    }

    public CPArrayAdapter(List<?> values, int selectedIndex) {
        super(ChessPad.getContext(), R.layout.list_view);
        init(values, selectedIndex);
    }

    protected void init(List<?> values, int initSelection) {
        this.values = values;
        this.selectedIndex = initSelection;
        layoutResource = R.layout.list_view;
        layoutInflater = LayoutInflater.from(ChessPad.getContext());
    }

    protected List<?> getValues() {
        return values;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        RowViewHolder rowViewHolder;
        if (convertView == null) {
            convertView = layoutInflater.inflate(layoutResource, null);
            convertView.setVisibility(View.VISIBLE);
            rowViewHolder = new RowViewHolder();
            convertView.setTag(rowViewHolder);

        } else {
            rowViewHolder = (RowViewHolder) convertView.getTag();
        }

        rowViewHolder.convertView = convertView;
        rowViewHolder.index = position;

        convertView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    onConvertViewClick(position);
                }
                // true if the event was handled and should not be given further down to other views.
                return true;
            }
        });
        setRowViewHolder(rowViewHolder, position);
        if (position == selectedIndex) {
            rowViewHolder.valueView.setBackgroundColor(Color.CYAN);
        } else {
            rowViewHolder.valueView.setBackgroundColor(Color.WHITE);
        }
        return convertView;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int selectedIndex) {
        this.selectedIndex = selectedIndex;
    }

    @Override
    public int getCount() {
        List<?> values = getValues();
        if(values == null) {
            return 0;       // happens in PgnItemListAdapter
        }
        return getValues().size();
    }

    public Object getItem(int position) {
        Object item = null;
        List<?> values = getValues();
        if (values != null) {
            item = values.get(position);
        }
        return item;
    }

    protected class RowViewHolder {
        int index;
        TextView labelView;
        TextView valueView;
        ImageButton actionButton;
        View convertView;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if(labelView != null) {
                sb.append("tag ").append(labelView.getText().toString()).append("=");
            }
            sb.append(valueView.getText().toString());
            if(actionButton != null) {
                sb.append(", ").append(actionButton.isEnabled());
            }
            return new String(sb);
        }
    }
}
