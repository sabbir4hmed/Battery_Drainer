package com.sabbir.batterydrainer;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.apache.poi.sl.draw.geom.Context;

import java.util.List;

public class DataPointAdapter extends ArrayAdapter<String> {
    public DataPointAdapter(android.content.Context context, List<String> data) {
        super(context, android.R.layout.simple_list_item_1, data);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        TextView text = view.findViewById(android.R.id.text1);
        text.setTextSize(12); // Adjust text size if needed
        return view;
    }
}
