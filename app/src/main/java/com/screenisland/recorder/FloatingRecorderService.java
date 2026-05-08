package com.screenisland.recorder;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FloatingRecorderService extends Service {
    public static final String ACTION_OPEN_ISLAND = "com.screenisland.recorder.OPEN_ISLAND";
    public static final String ACTION_START_RECORDING = "com.screenisland.recorder.START_RECORDING";
    public static final String ACTION_STOP_RECORDING = "com.screenisland.recorder.STOP_RECORDING";
    public static final String ACTION_CLOSE = "com.screenisland.recorder.CLOSE";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_RECORD_AUDIO = "record_audio";
    public static final String EXTRA_HIGH_QUALITY = "high_quality";

    private static final int NOTIFICATION_ID = 42;
    private static final String CHANNEL_ID = "screen_island_recorder";
    private static final int COLOR_PANEL = Color.rgb(14, 18, 28);
    private static final int COLOR_PANEL_LIGHT = Color.rgb(30, 39, 58);
    private static final int COLOR_ACCENT = Color.rgb(105, 92, 255);
    private static final int COLOR_RECORD = Color.rgb(255, 71, 87);
    private static final int COLOR_GO = Color.rgb(0, 224, 132);

    private WindowManager windowManager;
    private LinearLayout islandView;
    private TextView stateText;
    private Button recordButton;
    private WindowManager.LayoutParams islandParams;
    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private VirtualDisplay virtualDisplay;
    private Intent projectionData;
    private int projectionResultCode;
    private boolean recordAudio;
    private boolean highQuality;
    private boolean recording;
    private File currentOutputFile;
    private float downX;
    private float downY;
    private int startX;
    private int startY;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startAsForeground("Yüzen ada hazır");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_OPEN_ISLAND.equals(action)) {
            projectionResultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            projectionData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            recordAudio = intent.getBooleanExtra(EXTRA_RECORD_AUDIO, false);
            highQuality = intent.getBooleanExtra(EXTRA_HIGH_QUALITY, true);
            showIsland();
        } else if (ACTION_START_RECORDING.equals(action)) {
            startRecording();
        } else if (ACTION_STOP_RECORDING.equals(action)) {
            stopRecording(true);
        } else if (ACTION_CLOSE.equals(action)) {
            stopRecording(false);
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecording(false);
        if (islandView != null && windowManager != null) {
            windowManager.removeView(islandView);
            islandView = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showIsland() {
        if (!canDrawOverlays()) {
            toast("Overlay izni yok");
            return;
        }
        if (islandView != null) {
            updateIslandState();
            return;
        }
        islandView = new LinearLayout(this);
        islandView.setOrientation(LinearLayout.VERTICAL);
        islandView.setPadding(dp(10), dp(8), dp(10), dp(10));
        islandView.setBackground(rounded(COLOR_PANEL, dp(24)));
        islandView.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("● Screen Island");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        islandView.addView(title, wrapWrap());

        stateText = new TextView(this);
        stateText.setTextColor(Color.rgb(180, 190, 210));
        stateText.setTextSize(12f);
        stateText.setPadding(0, dp(3), 0, dp(5));
        islandView.addView(stateText, wrapWrap());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        recordButton = smallButton("REC", COLOR_RECORD, Color.WHITE, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (recording) {
                    stopRecording(true);
                } else {
                    startRecording();
                }
            }
        });
        row.addView(recordButton, wrapWrapWithMargins(0, 0, dp(6), 0));
        row.addView(smallButton("Ayarlar", COLOR_PANEL_LIGHT, Color.WHITE, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSettings();
            }
        }), wrapWrapWithMargins(0, 0, dp(6), 0));
        row.addView(smallButton("×", COLOR_PANEL_LIGHT, Color.WHITE, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopSelf();
            }
        }), wrapWrap());
        islandView.addView(row, wrapWrap());

        islandView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = islandParams.x;
                        startY = islandParams.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        islandParams.x = startX + (int) (event.getRawX() - downX);
                        islandParams.y = startY + (int) (event.getRawY() - downY);
                        windowManager.updateViewLayout(islandView, islandParams);
                        return true;
                    default:
                        return false;
                }
            }
        });

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        islandParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        islandParams.gravity = Gravity.TOP | Gravity.END;
        islandParams.x = dp(16);
        islandParams.y = dp(88);
        windowManager.addView(islandView, islandParams);
        updateIslandState();
    }

    private void startRecording() {
        if (recording) {
            return;
        }
        if (projectionData == null || projectionResultCode == 0) {
            toast("Ekran yakalama izni yok. Uygulamadan tekrar Open the recorder de.");
            return;
        }
        try {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (manager == null) {
                toast("MediaProjection bulunamadı");
                return;
            }
            if (mediaProjection == null) {
                mediaProjection = manager.getMediaProjection(projectionResultCode, projectionData);
            }
            prepareRecorder();
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "ScreenIslandRecording",
                    metrics.widthPixels,
                    metrics.heightPixels,
                    metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mediaRecorder.getSurface(),
                    null,
                    null
            );
            mediaRecorder.start();
            recording = true;
            startAsForeground("Kayıt devam ediyor");
            updateIslandState();
        } catch (Exception exception) {
            cleanupRecorder();
            toast("Kayıt başlatılamadı: " + exception.getMessage());
        }
    }

    private void prepareRecorder() throws IOException {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = even(metrics.widthPixels);
        int height = even(metrics.heightPixels);
        int fps = highQuality ? 60 : 30;
        int bitrate = highQuality ? 12_000_000 : 6_000_000;
        currentOutputFile = createOutputFile();
        mediaRecorder = new MediaRecorder();
        if (recordAudio && hasAudioPermission()) {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(currentOutputFile.getAbsolutePath());
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setVideoSize(width, height);
        mediaRecorder.setVideoFrameRate(fps);
        mediaRecorder.setVideoEncodingBitRate(bitrate);
        if (recordAudio && hasAudioPermission()) {
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(128_000);
            mediaRecorder.setAudioSamplingRate(44_100);
        }
        mediaRecorder.prepare();
    }

    private void stopRecording(boolean notify) {
        if (!recording && mediaRecorder == null) {
            return;
        }
        try {
            if (recording && mediaRecorder != null) {
                mediaRecorder.stop();
            }
        } catch (RuntimeException ignored) {
        }
        recording = false;
        cleanupRecorder();
        startAsForeground("Yüzen ada hazır");
        updateIslandState();
        if (notify && currentOutputFile != null) {
            toast("Kaydedildi: " + currentOutputFile.getName());
        }
    }

    private void cleanupRecorder() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private File createOutputFile() throws IOException {
        File directory = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "ScreenIsland");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Klasör oluşturulamadı");
        }
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        return new File(directory, "screen-island-" + stamp + ".mp4");
    }

    private int even(int value) {
        return value % 2 == 0 ? value : value - 1;
    }

    private void updateIslandState() {
        if (stateText == null || recordButton == null) {
            return;
        }
        stateText.setText(recording ? "Kayıt alınıyor" : "Hazır • sürükle-bırak");
        recordButton.setText(recording ? "STOP" : "REC");
        recordButton.setBackground(rounded(recording ? COLOR_GO : COLOR_RECORD, dp(16)));
    }

    private void openSettings() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private void startAsForeground(String text) {
        Notification notification = buildNotification(text);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(String text) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setContentTitle("Screen Island")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.presence_video_online)
                .setContentIntent(pendingIntent)
                .setOngoing(true);
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Screen Island Recorder",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private boolean hasAudioPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void toast(final String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private Button smallButton(String text, int bg, int fg, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(fg);
        button.setTextSize(12f);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(10), dp(5), dp(10), dp(5));
        button.setBackground(rounded(bg, dp(16)));
        button.setOnClickListener(listener);
        return button;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrapWithMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = wrapWrap();
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
