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



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupTargetSlider();
        setupCheckBoxes();
        setupButtons();
        setupListView();

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryInfoReceiver, filter);

        batteryUsageData = new ArrayList<>();

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
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, dataList);
        dataListView.setAdapter(adapter);

        lastRecordedBatteryLevel = getCurrentBatteryLevel();

        Runnable batteryMonitoringRunnable = new Runnable() {
            @Override
            public void run() {
                if (running) {
                    int currentBatteryLevel = getCurrentBatteryLevel();
                    if (currentBatteryLevel < lastRecordedBatteryLevel) {
                        updateBatteryInfo();
                        lastRecordedBatteryLevel = currentBatteryLevel;
                    }
                    if (currentBatteryLevel <= targetBatteryLevel) {
                        stopBatteryDrain();
                    } else {
                        handler.postDelayed(this, 1000);
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

        cpuLoadCheck = findViewById(R.id.cpuLoadCheck);
        flashlightCheck = findViewById(R.id.flashlightCheck);
        brightnessCheck = findViewById(R.id.brightnessCheck);
        gpsCheck = findViewById(R.id.gpsCheck);
        vibratorCheck = findViewById(R.id.vibratorCheck);
        networkCheck = findViewById(R.id.networkCheck);
        bluetoothCheck = findViewById(R.id.bluetoothCheck);

        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        dataListView = findViewById(R.id.dataListView);
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
    }

    private void startBatteryDrain() {
        dataList.clear();
        adapter.notifyDataSetChanged();
        lastRecordedBatteryLevel = getCurrentBatteryLevel();

        acquireWakeLock();

        if (cpuLoadCheck.isChecked()) activateHighCPULoad();
        if (flashlightCheck.isChecked()) activateFlashlight();
        if (brightnessCheck.isChecked()) activateHighBrightness();
        if (gpsCheck.isChecked()) activateGPS();
        if (vibratorCheck.isChecked()) activateVibrator();
        if (networkCheck.isChecked())
        {
            activateNetworkConnection();
            Toast.makeText(MainActivity.this, "Connecting to WiFi...", Toast.LENGTH_SHORT).show();
        }
        else
        {

        }
        if (bluetoothCheck.isChecked()) requestBluetoothPermissionAndEnable();

        running = true;
        handler.postDelayed(batteryMonitoringRunnable, 1000);
    }

    private void stopBatteryDrain() {
        running = false;
        releaseWakeLock();
        stopScheduledTasks();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Generate Graph")
                .setMessage("Do you want to generate a graph of the battery drain?")
                .setPositiveButton("Yes", (dialog, which) -> generateGraph())
                .setNegativeButton("No", null)
                .show();
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
                cameraManager.setTorchMode(cameraManager.getCameraIdList()[0], true);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void activateHighBrightness() {
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
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(1000);
            }
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
                batteryUsageData.add((float) currentBatteryLevel);
                updateBatteryInfo();
                if (currentBatteryLevel <= targetBatteryLevel) {
                    stopBatteryDrain();
                } else {
                    handler.postDelayed(this, 1000);
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
            String technology = batteryIntent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);

            int batteryLevel = (int) ((level / (float) scale) * 100);
            String healthString = getHealthString(health);
            float temperatureCelsius = temperature / 10.0f;
            float voltageVolts = voltage / 1000.0f;

            SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault());
            String currentTime = sdf.format(new Date());

            String dataEntry = String.format(Locale.getDefault(),
                    "%s | %s | %.1f°C | %.3fV | %s | %d%%",
                    currentTime, healthString, temperatureCelsius, voltageVolts, technology, batteryLevel);

            dataList.add(dataEntry);
            adapter.notifyDataSetChanged();

            // Update TextViews
            batteryHealthText.setText("Health: " + healthString);
            batteryTempText.setText("Temperature: " + temperatureCelsius + "°C");
            batteryVoltageText.setText("Voltage: " + voltageVolts + "V");
            batteryTechText.setText("Technology: " + technology);
            batteryLevelText.setText("Level: " + batteryLevel + "%");
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
        Intent intent = new Intent(this, GraphActivity.class);
        intent.putStringArrayListExtra("data", new ArrayList<>(dataList));
        startActivity(intent);
    }

    private void saveBatteryUsageData() {
        File file = new File(getFilesDir(), "battery_usage_data_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".csv");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("Timestamp,Battery Level\n");
            for (int i = 0; i < batteryUsageData.size(); i++) {
                writer.write(System.currentTimeMillis() + "," + batteryUsageData.get(i) + "\n");
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
}