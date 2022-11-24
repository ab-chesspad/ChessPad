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

 * convenient ArrayAdapter subclass
 * Created by Alexander Bootman on 8/20/16.
*/
package com.ab.droid.chesspad;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;

import com.ab.pgn.Config;

import java.util.List;

abstract class CPArrayAdapter<T> extends ArrayAdapter<T> {
    private final String DEBUG_TAG = Config.DEBUG_TAG + this.getClass().getSimpleName();

    protected List<T> values;
    protected int selectedIndex;

    protected abstract void rowSetup(RowViewHolder rowViewHolder);
    protected abstract void onRowViewClick(int position);

    CPArrayAdapter(List<T> values, int selectedIndex) {
        super(MainActivity.getContext(), R.layout.list_view_row);
        init(values, selectedIndex);
    }

    void init(List<T> values, int initSelection) {
        this.values = values;
        this.selectedIndex = initSelection;
    }

    List<T> getValues() {
        return values;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        RowViewHolder rowViewHolder;
        if (convertView == null) {
            convertView = LayoutInflater.from(MainActivity.getContext()).inflate(R.layout.list_view_row, null);
            convertView.setVisibility(View.VISIBLE);
            rowViewHolder = new RowViewHolder();
            convertView.setTag(rowViewHolder);
        } else {
            rowViewHolder = (RowViewHolder)convertView.getTag();
        }

        rowViewHolder.convertView = convertView;
        rowViewHolder.position = position;

        convertView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
                onRowViewClick(position);
            }
            // true if the event was handled and should not be given further down to other views.
            return true;
        });


        rowViewHolder.rowLabel = convertView.findViewById(R.id.row_label);
        rowViewHolder.rowLabel.setTag(rowViewHolder);
        rowViewHolder.rowLabel.setVisibility(View.VISIBLE);
//        rowViewHolder.rowLabel.setEnabled(false);
        if (rowViewHolder.rowLabel != null) {
            rowViewHolder.rowLabel.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    onRowViewClick(position);
                }
                // true if the event was handled and should not be given further down to other views.
                return true;
            });
        }

        rowViewHolder.rowValue = convertView.findViewById(R.id.row_value);
        rowViewHolder.rowValue.setTag(rowViewHolder);
        rowViewHolder.rowValue.setVisibility(View.VISIBLE);
//        rowViewHolder.rowValue.setEnabled(false);
        if (rowViewHolder.rowValue != null) {
            rowViewHolder.rowValue.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    onRowViewClick(position);
                }
                // true if the event was handled and should not be given further down to other views.
                return true;
            });
        }

        rowViewHolder.actionButton = rowViewHolder.convertView.findViewById(R.id.row_action_button);
        rowViewHolder.actionButton.setTag(rowViewHolder);

        rowSetup(rowViewHolder);
        if (position == selectedIndex) {
            rowViewHolder.rowValue.setBackgroundColor(Color.CYAN);
        } else {
            rowViewHolder.rowValue.setBackgroundColor(Color.WHITE);
        }
        return convertView;
    }

    int getSelectedIndex() {
        return selectedIndex;
    }

    public void setSelectedIndex(int selectedIndex) {
        this.selectedIndex = selectedIndex;
    }

    @Override
    public int getCount() {
        List<T> values = getValues();
        if (values == null) {
            return 0;       // happens in PgnFileListAdapter
        }
        return values.size();
    }

    public T getItem(int position) {
        T item = null;
        List<T> values = getValues();
        if (values != null) {
            item = values.get(position);
        }
        return item;
    }

    static class RowViewHolder {
        View convertView;
        int position;
        TextView rowLabel;
        TextView rowValue;
        Button actionButton;

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (rowLabel != null) {
                sb.append("tag ").append(rowLabel.getText().toString()).append("=");
            }
            sb.append(rowValue.getText().toString());
            if (actionButton != null) {
                sb.append(", ").append(actionButton.isEnabled());
            }
            return new String(sb);
        }
    }
}
