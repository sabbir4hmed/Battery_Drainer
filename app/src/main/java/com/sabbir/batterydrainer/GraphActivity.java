package com.sabbir.batterydrainer;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GraphActivity extends AppCompatActivity {

    private LineChart chart;
    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        chart = findViewById(R.id.chart);
        Button saveChartButton = findViewById(R.id.saveChartButton);

        ArrayList<String> dataList = getIntent().getStringArrayListExtra("data");
        if (dataList != null && !dataList.isEmpty()) {
            setupChart(dataList);
        } else {
            Toast.makeText(this, "No data available for graph", Toast.LENGTH_SHORT).show();
        }

        saveChartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveChartToGallery();
                } else {
                    if (ContextCompat.checkSelfPermission(GraphActivity.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(GraphActivity.this,
                                new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
                    } else {
                        saveChartToGalleryLegacy();
                    }
                }
            }
        });
    }

    private void saveChartToGalleryLegacy() {
        Bitmap chartBitmap = chart.getChartBitmap();
        String fileName = "BatteryChart_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".png";
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), fileName);

        try (FileOutputStream out = new FileOutputStream(file)) {
            chartBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            Toast.makeText(this, "Chart saved to gallery: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save chart", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveChartToGallery() {
        Bitmap chartBitmap = chart.getChartBitmap();
        String fileName = "BatteryChart_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".png";

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

        ContentResolver resolver = getContentResolver();
        Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);

        try {
            if (imageUri != null) {
                try (OutputStream out = resolver.openOutputStream(imageUri)) {
                    chartBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                Toast.makeText(this, "Chart saved to gallery", Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to save chart", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveChartToGalleryLegacy();
            } else {
                Toast.makeText(this, "Permission denied. Cannot save chart.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupChart(ArrayList<String> dataList) {
        List<Entry> batteryLevelEntries = new ArrayList<>();
        List<Entry> batteryTempEntries = new ArrayList<>();
        List<Entry> voltageEntries = new ArrayList<>();
        List<String> xAxisLabels = new ArrayList<>();

        SimpleDateFormat inputFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault());
        SimpleDateFormat outputFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        for (int i = 0; i < dataList.size(); i++) {
            String[] parts = dataList.get(i).split(" \\| ");
            if (parts.length >= 6) {
                try {
                    Date date = inputFormat.parse(parts[0]);
                    String time = outputFormat.format(date);

                    float batteryLevel = Float.parseFloat(parts[5].replace("%", ""));
                    float batteryTemp = Float.parseFloat(parts[2].replace("°C", ""));
                    float voltage = Float.parseFloat(parts[3].replace("V", ""));

                    batteryLevelEntries.add(new Entry(i, batteryLevel));
                    batteryTempEntries.add(new Entry(i, batteryTemp));
                    voltageEntries.add(new Entry(i, voltage));
                    xAxisLabels.add(time);
                } catch (ParseException | NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }

        // Battery Level Line
        LineDataSet batteryLevelDataSet = new LineDataSet(batteryLevelEntries, "Battery Level (%)");
        batteryLevelDataSet.setColor(Color.BLUE);
        batteryLevelDataSet.setCircleColor(Color.BLUE);
        batteryLevelDataSet.setDrawCircles(true);
        batteryLevelDataSet.setDrawValues(true);
        batteryLevelDataSet.setValueTextColor(Color.BLUE);
        batteryLevelDataSet.setValueTextSize(9f);
        batteryLevelDataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.0f%%", value);
            }
        });
        batteryLevelDataSet.setLineWidth(2f);

        // Temperature Points
        LineDataSet batteryTempDataSet = new LineDataSet(batteryTempEntries, "Temperature (°C)");
        batteryTempDataSet.setColor(Color.RED);
        batteryTempDataSet.setCircleColor(Color.RED);
        batteryTempDataSet.setDrawCircles(true);
        batteryTempDataSet.setDrawValues(true);
        batteryTempDataSet.setValueTextColor(Color.RED);
        batteryTempDataSet.setValueTextSize(9f);
        batteryTempDataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.1f°C", value);
            }
        });
        batteryTempDataSet.setDrawFilled(false);

        // Voltage Points
        LineDataSet voltageDataSet = new LineDataSet(voltageEntries, "Voltage (V)");
        voltageDataSet.setColor(Color.GREEN);
        voltageDataSet.setCircleColor(Color.GREEN);
        voltageDataSet.setDrawCircles(true);
        voltageDataSet.setDrawValues(true);
        voltageDataSet.setValueTextColor(Color.GREEN);
        voltageDataSet.setValueTextSize(9f);
        voltageDataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format(Locale.getDefault(), "%.2fV", value);
            }
        });
        voltageDataSet.setDrawFilled(false);

        LineData lineData = new LineData(batteryLevelDataSet, batteryTempDataSet, voltageDataSet);

        chart.setData(lineData);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(true);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(xAxisLabels));
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(45);
        xAxis.setLabelCount(5, true);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        float maxTemp = Collections.max(batteryTempEntries, (e1, e2) -> Float.compare(e1.getY(), e2.getY())).getY();
        leftAxis.setAxisMaximum(Math.max(100f, maxTemp + 10f));

        YAxis rightAxis = chart.getAxisRight();
        float maxVoltage = Collections.max(voltageEntries, (e1, e2) -> Float.compare(e1.getY(), e2.getY())).getY();
        rightAxis.setAxisMinimum(Math.max(0f, maxVoltage - 1f));
        rightAxis.setAxisMaximum(maxVoltage + 0.5f);
        rightAxis.setDrawGridLines(false);

        chart.setVisibleXRangeMinimum(5); // Show at least 5 X values

        chart.invalidate();
    }
}
