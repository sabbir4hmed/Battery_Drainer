package com.sabbir.batterydrainer;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private SeekBar targetBatteryLevelSlider;
    private TextView batteryHealthText, batteryTempText, batteryVoltageText, batteryTechText, batteryLevelText;
    private ProgressBar batterybar;
    private TextView targetLevelValue;
    private CheckBox cpuLoadCheck, flashlightCheck, brightnessCheck, gpsCheck, vibratorCheck, networkCheck, bluetoothCheck;
    private Button startButton, stopButton;
    private ListView dataListView;
    private List<String> dataList;
    private ArrayAdapter<String> adapter;
    private Handler handler = new Handler();
    private PowerManager.WakeLock wakeLock;
    private LocationListener locationListener;
    private int lastRecordedBatteryLevel;
    private int targetBatteryLevel;
    private volatile boolean running = false;
    private ScheduledExecutorService cpuLoadExecutor;
    private static final int BLUETOOTH_PERMISSION_REQUEST = 1;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean isNetworkCallbackRegistered = false;
    private List<Float> batteryUsageData;
    private Vibrator vibrator;
    private boolean isVibrating = false;
    private Thread vibrationThread;
    private boolean isFlashlightOn = false;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupTargetSlider();
        setupCheckBoxes();
        setupButtons();


        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryInfoReceiver, filter);

        batteryUsageData = new ArrayList<>();

        lastRecordedBatteryLevel = getCurrentBatteryLevel();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                // Handle network available
            }

            @Override
            public void onLost(@NonNull Network network) {
                // Handle network lost
            }
        };

        dataList = new ArrayList<>();

        lastRecordedBatteryLevel = getCurrentBatteryLevel();

        Runnable batteryMonitoringRunnable = new Runnable() {
            @Override
            public void run() {
                if (running) {
                    int currentBatteryLevel = getCurrentBatteryLevel();
                    updateBatteryInfo();

                    if (currentBatteryLevel <= targetBatteryLevel) {
                        running = false;
                        handler.post(() -> {
                            stopBatteryDrain();
                            Toast.makeText(MainActivity.this, "Battery drain test finished", Toast.LENGTH_LONG).show();
                        });
                    } else {
                        handler.postDelayed(this, 1000); // Update every second
                    }
                }
            }
        };

    }

    private void initializeViews() {
        targetBatteryLevelSlider = findViewById(R.id.targetBatteryLevelSlider);
        batteryHealthText = findViewById(R.id.batteryHealthText);
        batteryTempText = findViewById(R.id.batteryTempText);
        batteryVoltageText = findViewById(R.id.batteryVoltageText);
        batteryTechText = findViewById(R.id.batteryTechText);
        batteryLevelText = findViewById(R.id.batteryLevelText);
        targetLevelValue = findViewById(R.id.targetLevelValue);
        batterybar = findViewById(R.id.batteryLevelProgress);

        cpuLoadCheck = findViewById(R.id.cpuLoadCheck);
        flashlightCheck = findViewById(R.id.flashlightCheck);
        brightnessCheck = findViewById(R.id.brightnessCheck);
        gpsCheck = findViewById(R.id.gpsCheck);
        vibratorCheck = findViewById(R.id.vibratorCheck);
        networkCheck = findViewById(R.id.networkCheck);
        bluetoothCheck = findViewById(R.id.bluetoothCheck);

        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);

    }

    private void setupTargetSlider() {
        targetBatteryLevelSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                targetBatteryLevel = progress;
                targetLevelValue.setText(targetBatteryLevel + "%");
                checkStartButtonState();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupCheckBoxes() {
        CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> checkStartButtonState();

        cpuLoadCheck.setOnCheckedChangeListener(listener);
        flashlightCheck.setOnCheckedChangeListener(listener);
        brightnessCheck.setOnCheckedChangeListener(listener);
        gpsCheck.setOnCheckedChangeListener(listener);
        vibratorCheck.setOnCheckedChangeListener(listener);
        networkCheck.setOnCheckedChangeListener(listener);
        bluetoothCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            checkStartButtonState();
            toggleBluetooth(isChecked);
        });

        checkStartButtonState();
    }

    private void toggleBluetooth(boolean enable) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                if (enable && !bluetoothAdapter.isEnabled()) {
                    bluetoothAdapter.enable();
                    Snackbar.make(findViewById(android.R.id.content), "Bluetooth Enabled", Snackbar.LENGTH_SHORT).show();
                } else if (!enable && bluetoothAdapter.isEnabled()) {
                    bluetoothAdapter.disable();
                    Snackbar.make(findViewById(android.R.id.content), "Bluetooth Disabled", Snackbar.LENGTH_SHORT).show();
                }
            } else {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_PERMISSION_REQUEST);
            }
        } else {
            Snackbar.make(findViewById(android.R.id.content), "Bluetooth is not supported on this device", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void checkStartButtonState() {
        boolean anyChecked = cpuLoadCheck.isChecked() ||
                flashlightCheck.isChecked() ||
                brightnessCheck.isChecked() ||
                gpsCheck.isChecked() ||
                vibratorCheck.isChecked() ||
                networkCheck.isChecked() ||
                bluetoothCheck.isChecked();

        int currentBatteryLevel = getCurrentBatteryLevel();
        boolean isStartButtonEnabled = anyChecked && targetBatteryLevel <= currentBatteryLevel;

        startButton.setEnabled(isStartButtonEnabled);
    }

    private void setupButtons() {
        startButton.setOnClickListener(v -> startBatteryDrain());
        stopButton.setOnClickListener(v -> stopBatteryDrain());
    }

    private void setupListView() {
        dataList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dataList);
        dataListView.setAdapter(adapter);
        dataListView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        dataListView.setStackFromBottom(true);
    }

    private void startBatteryDrain() {
        dataList.clear();
        lastRecordedBatteryLevel = getCurrentBatteryLevel();

        acquireWakeLock();

        if (cpuLoadCheck.isChecked()) activateHighCPULoad();
        if (flashlightCheck.isChecked()) activateFlashlight();
        if (brightnessCheck.isChecked()) activateHighBrightness();
        if (gpsCheck.isChecked()) activateGPS();
        if (vibratorCheck.isChecked()) activateVibrator();
        if (networkCheck.isChecked()) {
            activateNetworkConnection();
            Toast.makeText(MainActivity.this, "Please turn on wifi", Toast.LENGTH_SHORT).show();
        }
        if (bluetoothCheck.isChecked()) requestBluetoothPermissionAndEnable();

        running = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        handler.post(batteryMonitoringRunnable);
    }



    private void stopBatteryDrain() {
        running = false;
        handler.removeCallbacks(batteryMonitoringRunnable);

        releaseWakeLock();
        startButton.setEnabled(true);
        stopScheduledTasks();
        stopVibration();
        deactivateFlashlight();
        deactivatehighbrightness();
        deactivateGPS();
        deactivateNetworkConnection();
        deactivateBluetooth();

        // Uncheck all checkboxes
        runOnUiThread(() -> {
            cpuLoadCheck.setChecked(false);
            flashlightCheck.setChecked(false);
            networkCheck.setChecked(false);
            bluetoothCheck.setChecked(false);
            gpsCheck.setChecked(false);
            vibratorCheck.setChecked(false);
            brightnessCheck.setChecked(false);
        });

        if (!dataList.isEmpty()) {
            runOnUiThread(() -> {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Generate Graph")
                        .setMessage("Do you want to generate a graph of the battery drain?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            Intent intent = new Intent(MainActivity.this, GraphActivity.class);
                            intent.putStringArrayListExtra("data", new ArrayList<>(dataList));
                            startActivity(intent);
                        })
                        .setNegativeButton("No", null)
                        .show();
            });

            saveBatteryUsageData();
        } else {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "No data available for graph", Toast.LENGTH_SHORT).show());
        }
    }

    private void deactivateBluetooth() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.disable();
        }
    }

    private void deactivateNetworkConnection() {
        if (isNetworkCallbackRegistered && connectivityManager != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            isNetworkCallbackRegistered = false;
        }
    }

    private void deactivateGPS() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }


    private void stopflashlight() {

    }


    private void deactivatehighbrightness() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.screenBrightness = -1.0f; // Default brightness
        getWindow().setAttributes(layoutParams);
    }

    private void stopVibration() {
        isVibrating = false;
        if (vibrator != null) {
            vibrator.cancel();
        }
        if (vibrationThread != null) {
            try {
                vibrationThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void deactivatenetworkConnection() {
        if (isNetworkCallbackRegistered && connectivityManager != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            isNetworkCallbackRegistered = false;
        }
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "BatteryDrainerApp::WakeLock");
            wakeLock.acquire();
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    private void activateHighCPULoad() {
        cpuLoadExecutor = Executors.newScheduledThreadPool(1);
        cpuLoadExecutor.scheduleAtFixedRate(() -> {
            double load = Runtime.getRuntime().availableProcessors();
            Log.d("CPU Load", "CPU Load: " + load);
        }, 0, 1, TimeUnit.SECONDS);
    }

    private void activateFlashlight() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager != null) {
            try {
                String cameraId = cameraManager.getCameraIdList()[0];
                cameraManager.setTorchMode(cameraId, true);
                isFlashlightOn = true;
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void activateHighBrightness() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.screenBrightness = 1.0f; // Max brightness
        getWindow().setAttributes(layoutParams);
    }

    private void activateGPS() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    // Do nothing
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}

                @Override
                public void onProviderEnabled(@NonNull String provider) {}

                @Override
                public void onProviderDisabled(@NonNull String provider) {}
            };
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            } else {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            }
        }
    }

    private void activateVibrator() {
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            isVibrating = true;
            vibrationThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (isVibrating) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            vibrator.vibrate(1000);
                        }
                        try {
                            Thread.sleep(1100); // Wait a bit more than the vibration duration to avoid overlap
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            vibrationThread.start();
        }
    }

    private void activateNetworkConnection() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            NetworkRequest.Builder builder = new NetworkRequest.Builder();
            builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);

            connectivityManager.registerNetworkCallback(builder.build(), networkCallback);
            isNetworkCallbackRegistered = true;
        }
    }

    private void requestBluetoothPermissionAndEnable() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {
            toggleBluetooth(true);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH_ADMIN}, BLUETOOTH_PERMISSION_REQUEST);
        }
    }

    private void stopScheduledTasks() {
        if (cpuLoadExecutor != null && !cpuLoadExecutor.isShutdown()) {
            cpuLoadExecutor.shutdown();
        }

        // Additional cleanup if needed
        releaseWakeLock();
    }

    private Runnable batteryMonitoringRunnable = new Runnable() {
        @Override
        public void run() {
            if (running) {
                int currentBatteryLevel = getCurrentBatteryLevel();
                updateBatteryInfo();

                Log.d("BatteryDrain", "Current: " + currentBatteryLevel + ", Target: " + targetBatteryLevel);

                if (currentBatteryLevel <= targetBatteryLevel) {
                    handler.post(() -> {
                        stopBatteryDrain();
                        Toast.makeText(MainActivity.this, "Battery drain test finished", Toast.LENGTH_LONG).show();
                    });
                } else {
                    handler.postDelayed(this, 1000); // Update every second
                }
            }
        }
    };


    private void updateBatteryInfo() {
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            int temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            int voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

            int batteryLevel = (int) ((level / (float) scale) * 100);
            Log.d("BatteryDrain", "Battery Level: " + batteryLevel + "%");
            if (batteryLevel != lastRecordedBatteryLevel) {
                String modelName = Build.MODEL;
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
                SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm:ss", Locale.getDefault());
                String currentDate = dateFormat.format(new Date());
                String currentTime = timeFormat.format(new Date());
                float temperatureCelsius = temperature / 10.0f;
                float voltageVolts = voltage / 1000.0f;
                String chargingState = getChargingState(status);

                String dataEntry = String.format(Locale.getDefault(),
                        "%s | %s | %s | %.1f°C | %.3fV | %s | %d%%",
                        modelName, currentDate, currentTime, temperatureCelsius, voltageVolts, chargingState, batteryLevel);

                dataList.add(dataEntry);
                lastRecordedBatteryLevel = batteryLevel;

                Log.d("BatteryDrain", "Added entry: " + dataEntry);
                Log.d("BatteryDrain", "DataList size: " + dataList.size());
            }

            // Update TextViews
            runOnUiThread(() -> {
                batteryLevelText.setText("Battery Level: " + batteryLevel + "%");
                batteryTechText.setText("Technology: " + batteryIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));
                batteryHealthText.setText("Health: " + getHealthString(health));
                batteryTempText.setText("Temperature: " + (temperature / 10.0f) + "°C");
                batteryVoltageText.setText("Voltage: " + (voltage / 1000.0f) + "V");
                batterybar.setProgress(batteryLevel);
            });
        }
    }

    private String getChargingState(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "Charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "Discharging";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "Full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "Not Charging";
            case BatteryManager.BATTERY_STATUS_UNKNOWN:
                return "Unknown";
            default:
                return "Unknown";
        }
    }

    private String getHealthString(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return "Good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return "Overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return "Dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return "Over Voltage";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return "Unspecified Failure";
            default:
                return "Unknown";
        }
    }

    private void generateGraph() {
        if (!dataList.isEmpty()) {
            Intent intent = new Intent(this, GraphActivity.class);
            intent.putStringArrayListExtra("data", new ArrayList<>(dataList));
            startActivity(intent);
        } else {
            Toast.makeText(this, "No data available for graph", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveBatteryUsageData() {
        File documentsPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BatteryDrainer");
        if (!documentsPath.exists()) {
            documentsPath.mkdirs();
        }

        File file = new File(documentsPath, "battery_usage_data_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".csv");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("Model,Date,Time,Temperature,Voltage,ChargingState,BatteryLevel\n");
            for (String entry : dataList) {
                String[] parts = entry.split(" \\| ");
                writer.write(String.join(",", parts) + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Toast.makeText(this, "Battery usage data saved to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();

}

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BLUETOOTH_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleBluetooth(bluetoothCheck.isChecked());
            } else {
                Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (running) {
            stopBatteryDrain();
        }
        if (isNetworkCallbackRegistered && connectivityManager != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
        deactivateFlashlight();
    }

    private void deactivateFlashlight() {
        if (isFlashlightOn) {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager != null) {
                try {
                    String cameraId = cameraManager.getCameraIdList()[0];
                    cameraManager.setTorchMode(cameraId, false);
                    isFlashlightOn = false;
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private int getCurrentBatteryLevel() {
        Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            return (int) ((level / (float) scale) * 100);
        }
        return -1;
    }

    private BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBatteryInfo();
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryInfoReceiver, filter);
        updateBatteryInfo();
    }
    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(batteryInfoReceiver);
    }
}