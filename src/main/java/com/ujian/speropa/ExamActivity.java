package com.ujian.speropa;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class ExamActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private AudioManager audioManager;
    private NotificationManager notificationManager;
    private WifiManager wifiManager;
    private PowerManager powerManager;
    private KeyguardManager keyguardManager;
    private Vibrator vibrator;

    private SoundPool soundPool;
    private int soundWarningId, soundExitId, soundLockId;

    private boolean isExamRunning = false;
    private boolean isExitAllowed = false;
    private boolean isDialogActive = false;
    private boolean isAppLocked = false;
    private boolean isRepinning = false;
    private boolean isInternetConnected = false;
    private boolean isCheckingInternet = false;

    private boolean isFatalLock = false;
    private int overlayWarningCount = 0;
    private static final int MAX_OVERLAY_WARNINGS = 3;

    private Handler fatalSoundHandler = new Handler(Looper.getMainLooper());
    private Runnable fatalSoundRunnable;
    private boolean isFatalAlarmActive = false;
    private Handler delayedAlarmHandler = new Handler(Looper.getMainLooper());
    private Runnable delayedAlarmRunnable;

    private long lastUnpinSoundTime = 0;
    private AlertDialog warningDialog;

    // VARIABEL WIFI BARU
    private List<ScanResult> wifiList = new ArrayList<>();
    private WifiListAdapter wifiAdapter;
    private BroadcastReceiver wifiScanReceiver;
    private AlertDialog wifiDialog; // Menyimpan referensi dialog wifi agar bisa diupdate

    private static final String EXAM_URL = "https://speropa.github.io/ujian";
    private static final String EXAM_PIN = "201184";

    private long examStartTime = 0;
    private static final long STARTUP_BUFFER_MS = 5000;
    private int warningCount = 0;
    private static final int MAX_WARNINGS = 5;
    private static final long LOCK_DURATION = 10 * 60 * 1000;

    private CountDownTimer lockoutTimer;
    private Timer unpinWatchdog;
    private Handler statusHandler = new Handler();
    private Runnable statusRunnable;
    private Handler permissionHandler = new Handler();
    private Runnable permissionRunnable;

    private Handler cheatCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable cheatCheckRunnable;

    private RelativeLayout headerLayout;
    private LinearLayout lockLayout;
    private LinearLayout layoutError;
    private LinearLayout layoutWelcome;
    private LinearLayout layoutStandby;
    private LinearLayout splashScreen;

    private TextView tvCountdown;
    private TextView tvLockTitle, tvLockMessage;
    private Button btnFatalExit;

    private TextView tvCheckDND, tvCheckLoc, tvCheckBT, tvCheckNet;
    private Button btnLockApp, btnOpenSettings;
    private EditText etExamCode;

    private BroadcastReceiver headerReceiver;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        hideSystemUI();
        setContentView(R.layout.activity_exam);

        splashScreen = findViewById(R.id.splashScreen);
        headerLayout = findViewById(R.id.headerLayout);
        progressBar = findViewById(R.id.progressBar);
        lockLayout = findViewById(R.id.lockLayout);
        layoutError = findViewById(R.id.layoutError);
        layoutWelcome = findViewById(R.id.layoutWelcome);
        layoutStandby = findViewById(R.id.layoutStandby);
        webView = findViewById(R.id.webView);

        tvCountdown = findViewById(R.id.tvCountdown);
        tvLockTitle = findViewById(R.id.tvLockTitle);
        tvLockMessage = findViewById(R.id.tvLockMessage);
        btnFatalExit = findViewById(R.id.btnFatalExit);

        if (btnFatalExit != null) {
            btnFatalExit.setOnClickListener(v -> performValidExit());
        }

        tvCheckDND = findViewById(R.id.tvCheckDND);
        tvCheckLoc = findViewById(R.id.tvCheckLoc);
        tvCheckBT = findViewById(R.id.tvCheckBT);
        tvCheckNet = findViewById(R.id.tvCheckNet);
        btnLockApp = findViewById(R.id.btnLockApp);
        btnOpenSettings = findViewById(R.id.btnOpenSettings);
        etExamCode = findViewById(R.id.etExamCode);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        soundPool = new SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(audioAttributes)
                .build();

        soundWarningId = soundPool.load(this, R.raw.warning, 1);
        soundExitId = soundPool.load(this, R.raw.exit, 1);
        soundLockId = soundPool.load(this, R.raw.kunci, 1);

        disableDND();

        setupWebView();
        setupHeader();
        setupButtons();
        setupPermissionChecker();

        runSplashAnimation();
    }

    private void runSplashAnimation() {
        new Handler().postDelayed(() -> {
            if (splashScreen != null) {
                AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
                fadeOut.setDuration(500);
                fadeOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {}
                    @Override
                    public void onAnimationEnd(Animation animation) {
                        splashScreen.setVisibility(View.GONE);
                    }
                    @Override
                    public void onAnimationRepeat(Animation animation) {}
                });
                splashScreen.startAnimation(fadeOut);
            }
        }, 2500);
    }

    private void triggerVibration(long duration) {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(duration);
                }
            }
        } catch (Exception e) {}
    }

    private void playSound(int soundId, float volume) {
        if (soundPool != null && soundId != 0) {
            try {
                soundPool.play(soundId, volume, volume, 1, 0, 1.0f);
            } catch (Exception e) {}
        }
    }

    private void enableDND() {
        if (notificationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (notificationManager.isNotificationPolicyAccessGranted()) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS);
            }
        }
    }

    private void disableDND() {
        if (notificationManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (notificationManager.isNotificationPolicyAccessGranted()) {
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
            }
        }
    }

    private void setupHeader() {
        ImageButton btnMenu = findViewById(R.id.btnMenu);
        if (btnMenu != null) btnMenu.setOnClickListener(v -> showMenuDialog());

        TextView tvBattery = findViewById(R.id.tvBattery);

        headerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH.mm", Locale.getDefault());
                String currentTime = sdf.format(new Date());

                int level = -1;
                Intent batteryIntent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (batteryIntent != null) {
                    level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                }

                if (tvBattery != null) {
                    if (level != -1) {
                        tvBattery.setText(currentTime + " | " + level + "%");
                    } else {
                        tvBattery.setText(currentTime);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        registerReceiver(headerReceiver, filter);

        headerReceiver.onReceive(this, new Intent());
    }

    private void checkInternetConnection(final InternetCallback callback) {
        if (isCheckingInternet) return;
        isCheckingInternet = true;

        new Thread(() -> {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress("8.8.8.8", 53), 1500);
                socket.close();

                new Handler(Looper.getMainLooper()).post(() -> {
                    isCheckingInternet = false;
                    callback.onResult(true);
                });
            } catch (IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    isCheckingInternet = false;
                    callback.onResult(false);
                });
            }
        }).start();
    }

    interface InternetCallback {
        void onResult(boolean isConnected);
    }

    private void reloadPage() {
        checkInternetConnection(isConnected -> {
            if (isConnected) {
                hideErrorPage();
                if (webView.getUrl() == null || webView.getUrl().equals("about:blank")) {
                    webView.loadUrl(EXAM_URL);
                } else {
                    webView.reload();
                }
            } else {
                Toast.makeText(this, "Tidak ada koneksi internet!", Toast.LENGTH_SHORT).show();
                showErrorPage();
            }
        });
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showErrorPage() {
        webView.setVisibility(View.GONE);
        layoutError.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }

    private void hideErrorPage() {
        layoutError.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void setupButtons() {
        btnOpenSettings.setOnClickListener(v -> {
            boolean handled = false;
            try {
                BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
                if (bt != null && bt.isEnabled()) {
                    bt.disable();
                    Toast.makeText(this, "Mematikan Bluetooth...", Toast.LENGTH_SHORT).show();
                    handled = true;
                }
            } catch (SecurityException e) {
                try {
                    startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                    handled = true;
                } catch (Exception ex) {}
            }

            if (handled) return;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !notificationManager.isNotificationPolicyAccessGranted()) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                return;
            }

            try {
                ActivityCompat.requestPermissions(ExamActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });

        btnLockApp.setOnClickListener(v -> {
            startLockTask();
            layoutWelcome.setVisibility(View.GONE);
            layoutStandby.setVisibility(View.VISIBLE);
            hideSystemUI();
        });

        Button btnEnter = findViewById(R.id.btnEnterExam);
        if (btnEnter != null) {
            btnEnter.setOnClickListener(v -> {
                String code = etExamCode.getText().toString().trim();
                if (!code.equals(EXAM_PIN)) {
                    Toast.makeText(this, "KODE SEKOLAH SALAH!", Toast.LENGTH_SHORT).show();
                    etExamCode.setText("");
                    return;
                }

                checkInternetConnection(isConnected -> {
                    if (isConnected) {
                        hideKeyboard();
                        startExamMode();
                    } else {
                        Toast.makeText(this, "Internet tidak tersedia! Pastikan Anda memiliki kuota.", Toast.LENGTH_LONG).show();
                        showInAppWifiScanner();
                    }
                });
            });
        }

        Button btnExit = findViewById(R.id.btnExitApp);
        if (btnExit != null) {
            btnExit.setOnClickListener(v -> {
                showExitConfirmation();
            });
        }

        Button btnRetry = findViewById(R.id.btnRetry);
        if (btnRetry != null) btnRetry.setOnClickListener(v -> reloadPage());
        Button btnWifiErr = findViewById(R.id.btnWifiSettingsError);
        if (btnWifiErr != null) btnWifiErr.setOnClickListener(v -> showInAppWifiScanner());
    }

    private void setupPermissionChecker() {
        permissionRunnable = new Runnable() {
            @Override
            public void run() {
                if (layoutWelcome.getVisibility() == View.VISIBLE) {
                    checkSystemRequirements();
                    permissionHandler.postDelayed(this, 4000);
                }
            }
        };
    }

    private void checkSystemRequirements() {
        boolean dnd = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            dnd = notificationManager.isNotificationPolicyAccessGranted();
        }
        boolean loc = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        boolean btOff = true;
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            btOff = (bt == null || !bt.isEnabled());
        } catch (Exception e) {
            btOff = true;
        }

        updateCheckText(tvCheckDND, dnd, "Izin Akses 'Jangan Ganggu'");
        updateCheckText(tvCheckLoc, loc, "Izin Lokasi (Wajib)");
        updateCheckText(tvCheckBT, btOff, "Bluetooth Non-Aktif");

        final boolean finalDnd = dnd;
        final boolean finalLoc = loc;
        final boolean finalBtOff = btOff;

        checkInternetConnection(isConnected -> {
            isInternetConnected = isConnected;
            updateCheckText(tvCheckNet, isConnected, isConnected ? "Koneksi Internet Stabil" : "Tidak Ada Internet");

            if (finalDnd && finalLoc && finalBtOff && isInternetConnected) {
                btnLockApp.setEnabled(true);
                btnLockApp.setVisibility(View.VISIBLE);
                btnOpenSettings.setVisibility(View.GONE);
            } else {
                btnLockApp.setEnabled(false);
                btnLockApp.setVisibility(View.GONE);
                btnOpenSettings.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updateCheckText(TextView tv, boolean ok, String label) {
        if (ok) {
            tv.setText("✅ " + label);
            tv.setTextColor(Color.parseColor("#009900"));
        } else {
            tv.setText("❌ " + label);
            tv.setTextColor(Color.parseColor("#D32F2F"));
        }
    }

    private void startExamMode() {
        isExamRunning = true;
        examStartTime = System.currentTimeMillis();

        enableDND();

        layoutStandby.setVisibility(View.GONE);
        layoutWelcome.setVisibility(View.GONE);

        headerLayout.setVisibility(View.VISIBLE);
        webView.setVisibility(View.VISIBLE);

        if (webView.getUrl() == null || webView.getUrl().equals("about:blank")) {
            webView.loadUrl(EXAM_URL);
        }

        hideSystemUI();
        ensureLocked();

        if (statusRunnable == null) startStatusMonitoring();
        if (unpinWatchdog == null) startUnpinWatchdog();

        Toast.makeText(this, "UJIAN DIMULAI.", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (!isExamRunning) return;

        if (hasFocus) {
            stopFatalAlarm();
            if (delayedAlarmHandler != null && delayedAlarmRunnable != null) {
                delayedAlarmHandler.removeCallbacks(delayedAlarmRunnable);
            }
            if (cheatCheckHandler != null && cheatCheckRunnable != null) {
                cheatCheckHandler.removeCallbacks(cheatCheckRunnable);
            }

            isRepinning = false;

            if (!isAppLocked && !isFatalLock && !isDialogActive) {
                hideSystemUI();
                ensureLocked();
            }
        } else {
            if (isRepinning) return;

            if (powerManager != null && !powerManager.isInteractive()) return;
            if (keyguardManager != null && keyguardManager.isKeyguardLocked()) return;
            if (isExitAllowed) return;

            if (isFatalLock) {
                startDelayedFatalAlarm();
                return;
            }

            if (isAppLocked) return;

            if (cheatCheckRunnable == null) {
                cheatCheckRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (isDialogActive) {

                        } else if (!hasWindowFocus() && isExamRunning && !isExitAllowed && !isAppLocked && !isFatalLock && !isRepinning) {
                            handleOverlayViolation();
                            cheatCheckHandler.postDelayed(this, 3000);
                        }
                    }
                };
            }
            cheatCheckHandler.postDelayed(cheatCheckRunnable, 3000);
        }
    }

    private void startDelayedFatalAlarm() {
        if (isFatalAlarmActive) return;

        if (delayedAlarmRunnable == null) {
            delayedAlarmRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!hasWindowFocus() && isFatalLock && !isExitAllowed) {
                        startFatalAlarmLoop();
                    }
                }
            };
        }
        delayedAlarmHandler.postDelayed(delayedAlarmRunnable, 5000);
    }

    private void startFatalAlarmLoop() {
        if (isFatalAlarmActive) return;
        isFatalAlarmActive = true;

        if (fatalSoundRunnable == null) {
            fatalSoundRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isFatalAlarmActive && !isExitAllowed) {
                        playSound(soundWarningId, 1.0f);
                        triggerVibration(1000);
                        fatalSoundHandler.postDelayed(this, 2000);
                    }
                }
            };
        }
        fatalSoundHandler.post(fatalSoundRunnable);
    }

    private void stopFatalAlarm() {
        isFatalAlarmActive = false;
        if (fatalSoundHandler != null && fatalSoundRunnable != null) {
            fatalSoundHandler.removeCallbacks(fatalSoundRunnable);
        }
    }

    private void performValidExit() {
        if (isExitAllowed) return;

        isExitAllowed = true;
        stopFatalAlarm();
        if (delayedAlarmHandler != null && delayedAlarmRunnable != null) {
            delayedAlarmHandler.removeCallbacks(delayedAlarmRunnable);
        }

        disableDND();

        playSound(soundExitId, 0.95f);
        Toast.makeText(this, "Mohon tunggu, aplikasi akan keluar dengan sendiri!", Toast.LENGTH_LONG).show();

        new Handler().postDelayed(() -> {
            try { stopLockTask(); } catch (Exception e) {}
            finishAndRemoveTask();
            System.exit(0);
        }, 4000);
    }

    private void ensureLocked() {
        if (isExitAllowed) return;
        if (isFinishing()) return;

        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
                    isRepinning = true;
                    startLockTask();
                    new Handler().postDelayed(() -> isRepinning = false, 5000);
                }
            } else {
                startLockTask();
            }
        } catch (Exception e) {}
    }

    private void handleOverlayViolation() {
        if (isExitAllowed || isAppLocked || isFatalLock) return;

        overlayWarningCount++;
        triggerVibration(1000);
        playSound(soundWarningId, 1.0f);

        runOnUiThread(() ->
                Toast.makeText(this, "PERINGATAN OVERLAY " + overlayWarningCount + "/3\nTutup aplikasi lain!", Toast.LENGTH_LONG).show()
        );

        if (overlayWarningCount >= MAX_OVERLAY_WARNINGS) {
            startFatalLockout();
        }
    }

    private void startFatalLockout() {
        isFatalLock = true;

        if (cheatCheckHandler != null) cheatCheckHandler.removeCallbacks(cheatCheckRunnable);

        if (warningDialog != null && warningDialog.isShowing()) {
            warningDialog.dismiss();
        }
        isDialogActive = false;

        lockLayout.setVisibility(View.VISIBLE);
        lockLayout.bringToFront();
        lockLayout.setBackgroundColor(Color.parseColor("#330000"));

        tvCountdown.setVisibility(View.GONE);
        btnFatalExit.setVisibility(View.VISIBLE);

        tvLockTitle.setText("PELANGGARAN BERAT");
        tvLockMessage.setText("Anda terdeteksi membuka aplikasi lain (Overlay) lebih dari 3 kali.\nAkses ujian dicabut permanen.");

        if (hasWindowFocus()) {
            hideSystemUI();
        }

        playSound(soundLockId, 1.0f);

        if (!hasWindowFocus()) {
            startDelayedFatalAlarm();
        }
    }

    private void handleViolation(String reason) {
        if (isExitAllowed || isAppLocked || isFatalLock) return;

        warningCount++;
        triggerVibration(500);

        if (warningCount > MAX_WARNINGS) {
            startAppLockout();
            return;
        }

        playSound(soundWarningId, 0.65f);

        Toast.makeText(this, "PERINGATAN " + warningCount + "/5: " + reason, Toast.LENGTH_SHORT).show();

        if (warningCount == 3) {
            if (warningDialog != null && warningDialog.isShowing()) {
                warningDialog.dismiss();
            }

            isDialogActive = true;
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle("PERINGATAN KERAS")
                    .setMessage("Tinggal 2 kesempatan lagi!")
                    .setPositiveButton("SAYA MENGERTI", (d, w) -> {
                        isDialogActive = false;
                        hideSystemUI();
                    })
                    .setCancelable(false);

            warningDialog = builder.create();
            warningDialog.show();
        }
    }

    private void startUnpinWatchdog() {
        if (unpinWatchdog != null) unpinWatchdog.cancel();
        unpinWatchdog = new Timer();
        unpinWatchdog.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                if (!isExamRunning || isExitAllowed) return;
                if (System.currentTimeMillis() - examStartTime < STARTUP_BUFFER_MS) return;

                runOnUiThread(() -> {
                    try {
                        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if (am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {

                                if (System.currentTimeMillis() - lastUnpinSoundTime > 3000) {
                                    playSound(soundWarningId, 1.0f);
                                    triggerVibration(500);
                                    lastUnpinSoundTime = System.currentTimeMillis();
                                    Toast.makeText(ExamActivity.this, "JANGAN LEPAS PIN! APLIKASI AKAN TERUS BUNYI!", Toast.LENGTH_SHORT).show();
                                }

                                if (!isRepinning && !isFatalLock) {
                                    handleViolation("Upaya Melepas Pin");
                                }

                                ensureLocked();
                            }
                        }
                    } catch (Exception e) {}
                });
            }
        }, 1000, 500);
    }

    private void startAppLockout() {
        if (isAppLocked || isFatalLock) return;
        if (lockoutTimer != null) lockoutTimer.cancel();

        isAppLocked = true;

        if (warningDialog != null && warningDialog.isShowing()) {
            warningDialog.dismiss();
        }
        isDialogActive = false;

        lockLayout.setVisibility(View.VISIBLE);
        lockLayout.bringToFront();
        lockLayout.setBackgroundColor(Color.parseColor("#D32F2F"));

        tvCountdown.setVisibility(View.VISIBLE);
        btnFatalExit.setVisibility(View.GONE);

        tvLockTitle.setText("APLIKASI TERKUNCI");
        tvLockMessage.setText("Anda melakukan pelanggaran berat.");

        playSound(soundLockId, 0.95f);
        triggerVibration(1000);

        lockoutTimer = new CountDownTimer(LOCK_DURATION, 1000) {
            public void onTick(long m) {
                tvCountdown.setText(String.format("%02d:%02d", (m / 1000) / 60, (m / 1000) % 60));
                hideSystemUI();
            }

            public void onFinish() {
                isAppLocked = false;
                warningCount = 0;
                lockLayout.setVisibility(View.GONE);
                hideSystemUI();
            }
        }.start();
    }

    private void showExitConfirmation() {
        if (isAppLocked || isFatalLock) return;
        isDialogActive = true;
        new AlertDialog.Builder(this)
                .setTitle("Konfirmasi Keluar")
                .setMessage("Yakin selesai?")
                .setCancelable(false)
                .setPositiveButton("YA", (d, w) -> {
                    performValidExit();
                })
                .setNegativeButton("BATAL", (d, w) -> {
                    isDialogActive = false;
                    d.dismiss();
                    hideSystemUI();
                })
                .show();
    }

    private void showMenuDialog() {
        if (isAppLocked || isFatalLock) return;
        isDialogActive = true;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_menu, null);
        builder.setView(dialogView);
        builder.setCancelable(false);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvCurrentWifi = dialogView.findViewById(R.id.tvCurrentWifi);
        Button btnChangeWifi = dialogView.findViewById(R.id.btnChangeWifi);
        WifiInfo info = wifiManager.getConnectionInfo();
        String ssid = (info != null && info.getNetworkId() != -1) ? info.getSSID().replace("\"", "") : "Tidak Terhubung";
        tvCurrentWifi.setText(ssid);

        btnChangeWifi.setOnClickListener(v -> {
            dialog.dismiss();
            showInAppWifiScanner();
        });

        SeekBar seekBar = dialogView.findViewById(R.id.seekBarBrightness);
        WindowManager.LayoutParams lp = getWindow().getAttributes();

        float currentBrightnessValue = lp.screenBrightness;
        int progress;

        if (currentBrightnessValue < 0) {
            int sysVal = 125;
            try {
                sysVal = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            } catch (Settings.SettingNotFoundException e) {}
            progress = (int) ((sysVal / 255.0f) * 100);
        } else {
            progress = (int) (currentBrightnessValue * 100);
        }

        if (progress < 1) progress = 1;
        seekBar.setProgress(progress);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean f) {
                if(p < 1) p = 1;
                WindowManager.LayoutParams l = getWindow().getAttributes();
                l.screenBrightness = p/100.0f;
                getWindow().setAttributes(l);
            }
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        });

        dialogView.findViewById(R.id.btnExitApp).setOnClickListener(v -> {
            dialog.dismiss();
            showExitConfirmation();
        });

        dialogView.findViewById(R.id.btnCloseMenu).setOnClickListener(v -> {
            isDialogActive = false;
            dialog.dismiss();
            hideSystemUI();
        });

        dialog.show();
    }

    // --- REVISI: SCANNER WIFI KUSTOM YANG DIPERBAGUS ---
    private void showInAppWifiScanner() {
        if (isDialogActive) return;
        isDialogActive = true;

        if (!wifiManager.isWifiEnabled()) wifiManager.setWifiEnabled(true);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pilih Jaringan Wi-Fi");

        // Gunakan Layout custom atau list view
        ListView listView = new ListView(this);

        // Inisialisasi list dan adapter
        wifiList = new ArrayList<>();
        wifiAdapter = new WifiListAdapter(this, wifiList);
        listView.setAdapter(wifiAdapter);

        builder.setView(listView);

        builder.setNegativeButton("TUTUP", (d, w) -> {
            isDialogActive = false;
            d.dismiss();
            hideSystemUI();

            // Stop scanning saat tutup
            if (wifiScanReceiver != null) {
                try { unregisterReceiver(wifiScanReceiver); } catch (Exception e) {}
                wifiScanReceiver = null;
            }
        });

        builder.setNeutralButton("SCAN ULANG", null);

        wifiDialog = builder.create();

        // Setup tombol Scan Ulang agar tidak menutup dialog
        wifiDialog.setOnShowListener(d -> {
            wifiDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                Toast.makeText(ExamActivity.this, "Memindai ulang...", Toast.LENGTH_SHORT).show();
                wifiManager.startScan();
            });
        });

        // Handler saat item diklik (JANGAN DISMISS DIALOG WIFI)
        listView.setOnItemClickListener((parent, view, position, id) -> {
            ScanResult result = wifiList.get(position);
            showPasswordDialog(result.SSID, result.capabilities);
            // Dialog WIFI tetap terbuka di background
        });

        wifiDialog.setCancelable(false);
        wifiDialog.show();

        startWifiScan();
    }

    private void startWifiScan() {
        if (wifiScanReceiver != null) return;

        wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    List<ScanResult> results = wifiManager.getScanResults();
                    wifiList.clear();

                    // Filter SSID kosong
                    for (ScanResult r : results) {
                        if (r.SSID != null && !r.SSID.isEmpty()) {
                            wifiList.add(r);
                        }
                    }

                    // Sortir berdasarkan kekuatan sinyal (terkuat di atas)
                    Collections.sort(wifiList, (o1, o2) -> o2.level - o1.level);

                    if (wifiAdapter != null) wifiAdapter.notifyDataSetChanged();

                } catch (Exception e) {}
            }
        };
        registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        wifiManager.startScan();
    }

    private void showPasswordDialog(String ssid, String capabilities) {
        // Tampilkan dialog password di atas dialog list wifi
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sambung ke: " + ssid);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(50, 20, 50, 0);
        input.setLayoutParams(p);
        input.setHint("Masukkan Password");
        layout.addView(input);

        builder.setView(layout);

        builder.setPositiveButton("SAMBUNG", (d, w) -> {
            connectToWifi(ssid, input.getText().toString(), capabilities);
            // Dialog password tutup, tapi dialog wifi tetap ada
        });

        builder.setNegativeButton("BATAL", (d, w) -> d.dismiss());

        builder.setCancelable(false);
        builder.show();
    }

    private void connectToWifi(String ssid, String pass, String cap) {
        try {
            WifiConfiguration c = new WifiConfiguration();
            c.SSID = "\"" + ssid + "\"";

            if (cap.contains("WPA") || cap.contains("WEP")) {
                c.preSharedKey = "\"" + pass + "\"";
            } else {
                c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            }

            int id = wifiManager.addNetwork(c);
            wifiManager.disconnect();
            wifiManager.enableNetwork(id, true);
            wifiManager.reconnect();

            Toast.makeText(this, "Menghubungkan ke " + ssid + "...", Toast.LENGTH_SHORT).show();

            // Cek status setelah beberapa detik
            new Handler().postDelayed(() -> {
                WifiInfo info = wifiManager.getConnectionInfo();
                String currentSSID = (info != null) ? info.getSSID().replace("\"", "") : "";

                if (currentSSID.equals(ssid)) {
                    Toast.makeText(this, "BERHASIL TERHUBUNG!", Toast.LENGTH_SHORT).show();
                    // Jika berhasil, update list agar status berubah
                    if (wifiAdapter != null) wifiAdapter.notifyDataSetChanged();
                    // Reload webview jika sedang ujian
                    if (isExamRunning) reloadPage();
                } else {
                    Toast.makeText(this, "Gagal terhubung. Cek password atau coba lagi.", Toast.LENGTH_LONG).show();
                }
            }, 8000); // Beri waktu 8 detik untuk connect

        } catch (Exception e) {
            Toast.makeText(this, "Gagal memproses koneksi", Toast.LENGTH_SHORT).show();
        }
    }

    // --- INNER CLASS: ADAPTER CUSTOM UNTUK LIST WIFI ---
    private class WifiListAdapter extends BaseAdapter {
        private Context context;
        private List<ScanResult> list;

        public WifiListAdapter(Context context, List<ScanResult> list) {
            this.context = context;
            this.list = list;
        }

        @Override
        public int getCount() { return list.size(); }

        @Override
        public Object getItem(int position) { return list.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_wifi_scan, parent, false);
            }

            ScanResult result = list.get(position);

            TextView tvSsid = convertView.findViewById(R.id.tvSsid);
            TextView tvStatus = convertView.findViewById(R.id.tvStatus);
            ImageView imgSignal = convertView.findViewById(R.id.imgSignal);
            ImageView imgLock = convertView.findViewById(R.id.imgLock);

            tvSsid.setText(result.SSID);

            // Cek apakah wifi ini sedang terhubung
            WifiInfo info = wifiManager.getConnectionInfo();
            String currentSSID = (info != null) ? info.getSSID().replace("\"", "") : "";

            if (currentSSID.equals(result.SSID)) {
                tvStatus.setText("Terhubung");
                tvStatus.setVisibility(View.VISIBLE);
                tvStatus.setTextColor(Color.parseColor("#009900")); // Hijau
            } else {
                tvStatus.setVisibility(View.GONE);
            }

            // Hitung level sinyal (0-4)
            int level = WifiManager.calculateSignalLevel(result.level, 5);

            // Ubah warna ikon sinyal berdasarkan kekuatan
            if (level >= 4) imgSignal.setColorFilter(Color.parseColor("#009900")); // Hijau (Kuat)
            else if (level >= 2) imgSignal.setColorFilter(Color.parseColor("#FF9800")); // Oranye (Sedang)
            else imgSignal.setColorFilter(Color.parseColor("#D32F2F")); // Merah (Lemah)

            // Cek Keamanan (Ada gembok atau tidak)
            String cap = result.capabilities;
            if (cap.contains("WPA") || cap.contains("WEP") || cap.contains("PSK")) {
                imgLock.setVisibility(View.VISIBLE);
            } else {
                imgLock.setVisibility(View.GONE); // Open Wifi
            }

            return convertView;
        }
    }

    private void setupWebView() {
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setSavePassword(false);
        webSettings.setSaveFormData(false);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }

        webView.setOnLongClickListener(v -> true);
        webView.setLongClickable(false);

        String defaultUA = webSettings.getUserAgentString();
        webSettings.setUserAgentString(defaultUA + " speropa");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    if (newProgress == 100) {
                        progressBar.setVisibility(View.GONE);
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(newProgress);
                    }
                }
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                isDialogActive = true;
                new AlertDialog.Builder(ExamActivity.this)
                        .setTitle("Pesan")
                        .setMessage(message)
                        .setPositiveButton("OK", (d, w) -> {
                            result.confirm();
                            isDialogActive = false;
                            hideSystemUI();
                        })
                        .setCancelable(false).show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                isDialogActive = true;
                new AlertDialog.Builder(ExamActivity.this)
                        .setTitle("Konfirmasi").setMessage(message)
                        .setPositiveButton("YA", (d, w) -> {
                            result.confirm();
                            isDialogActive = false;
                            hideSystemUI();
                        })
                        .setNegativeButton("TIDAK", (d, w) -> {
                            result.cancel();
                            isDialogActive = false;
                            hideSystemUI();
                        })
                        .setCancelable(false).show();
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    view.loadUrl("about:blank");
                    showErrorPage();
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                view.loadUrl("about:blank");
                showErrorPage();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!url.equals("about:blank") && layoutError.getVisibility() == View.VISIBLE) hideErrorPage();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isExamRunning && layoutWelcome.getVisibility() == View.VISIBLE) {
            permissionHandler.post(permissionRunnable);
        } else if (isExamRunning) {
            ensureLocked();
            hideSystemUI();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        permissionHandler.removeCallbacks(permissionRunnable);
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isExamRunning) return super.onKeyDown(keyCode, event);
        if (isAppLocked || isFatalLock) return true;

        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_APP_SWITCH || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (isDialogActive && keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event);

            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (isExamRunning && !isExitAllowed) {
                    handleViolation("Tombol Volume Dilarang");
                    return true;
                }
            }

            if (isExamRunning && !isExitAllowed) {
                handleViolation("Tombol Terlarang");
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (isExamRunning && !isExitAllowed) {
            handleViolation("Tombol Kembali Dilarang");
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!isExamRunning) return;

        if (!isExitAllowed && !isDialogActive) {
            handleViolation("Tombol Home/Recent");

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Intent intent = new Intent(getApplicationContext(), ExamActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            }

            ensureLocked();
        }
    }

    private void startStatusMonitoring() {
        statusRunnable = new Runnable() {
            public void run() {
                if (!isDialogActive && !isAppLocked && !isFatalLock) {
                    hideSystemUI();

                    checkInternetConnection(isConnected -> {
                        if (!isConnected && isExamRunning) {
                            Toast.makeText(ExamActivity.this, "KONEKSI INTERNET TERPUTUS!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                statusHandler.postDelayed(this, 4000);
            }
        };
        statusHandler.post(statusRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disableDND();

        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }

        stopFatalAlarm();
        if (delayedAlarmHandler != null && delayedAlarmRunnable != null) {
            delayedAlarmHandler.removeCallbacks(delayedAlarmRunnable);
        }

        if (webView != null) webView.clearCache(true);
        try {
            unregisterReceiver(headerReceiver);
        } catch (Exception e) {}
        statusHandler.removeCallbacks(statusRunnable);
        permissionHandler.removeCallbacks(permissionRunnable);
        if (unpinWatchdog != null) unpinWatchdog.cancel();
        try {
            if (wifiScanReceiver != null) unregisterReceiver(wifiScanReceiver);
        } catch (Exception e) {}
        if (lockoutTimer != null) lockoutTimer.cancel();
    }
}