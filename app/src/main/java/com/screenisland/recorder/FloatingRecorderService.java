package com.screenisland.recorder;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Toast;

import com.screenisland.recorder.island.FloatingIslandView;
import com.screenisland.recorder.island.IslandTheme;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FloatingRecorderService extends Service implements FloatingIslandView.Callback {
    public static final String ACTION_OPEN_ISLAND = "com.screenisland.recorder.OPEN_ISLAND";
    public static final String ACTION_START_RECORDING = "com.screenisland.recorder.START_RECORDING";
    public static final String ACTION_STOP_RECORDING = "com.screenisland.recorder.STOP_RECORDING";
    public static final String ACTION_CLOSE = "com.screenisland.recorder.CLOSE";
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final String EXTRA_RECORD_AUDIO = "record_audio";
    public static final String EXTRA_HIGH_QUALITY = "high_quality";
    public static final String EXTRA_COUNTDOWN = "countdown";
    public static final String EXTRA_COMPACT = "compact";

    private static final int NOTIFICATION_ID = 42;
    private static final String CHANNEL_ID = "screen_island_recorder";

    private WindowManager windowManager;
    private FloatingIslandView islandView;
    private WindowManager.LayoutParams islandParams;
    private MediaProjection mediaProjection;
    private MediaRecorder mediaRecorder;
    private VirtualDisplay virtualDisplay;
    private Intent projectionData;
    private int projectionResultCode;
    private boolean recordAudio;
    private boolean highQuality;
    private boolean countdown;
    private boolean compact;
    private boolean recording;
    private boolean paused;
    private boolean countingDown;
    private File currentOutputFile;
    private float downX;
    private float downY;
    private int startX;
    private int startY;
    private long recordStartedAt;
    private long elapsedBeforePause;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateIslandState();
            if (recording || countingDown) {
                handler.postDelayed(this, 500);
            }
        }
    };

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
            countdown = intent.getBooleanExtra(EXTRA_COUNTDOWN, true);
            compact = intent.getBooleanExtra(EXTRA_COMPACT, false);
            showIsland();
        } else if (ACTION_START_RECORDING.equals(action)) {
            requestStartRecording();
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
        handler.removeCallbacksAndMessages(null);
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

    @Override
    public void onRecordToggle() {
        if (recording) {
            stopRecording(true);
        } else {
            requestStartRecording();
        }
    }

    @Override
    public void onPauseToggle() {
        togglePause();
    }

    @Override
    public void onSettings() {
        openSettings();
    }

    @Override
    public void onClose() {
        stopRecording(false);
        stopSelf();
    }

    private void showIsland() {
        if (!canDrawOverlays()) {
            toast("Overlay izni yok");
            return;
        }
        if (islandView != null) {
            islandView.setExpanded(!compact);
            updateIslandState();
            return;
        }
        islandView = new FloatingIslandView(this, this);
        islandView.setExpanded(!compact);
        islandView.setOnTouchListener(new android.view.View.OnTouchListener() {
            private boolean moved;

            @Override
            public boolean onTouch(android.view.View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        moved = false;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = islandParams.x;
                        startY = islandParams.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - downX);
                        int dy = (int) (event.getRawY() - downY);
                        if (Math.abs(dx) > 6 || Math.abs(dy) > 6) {
                            moved = true;
                        }
                        islandParams.x = startX + dx;
                        islandParams.y = startY + dy;
                        windowManager.updateViewLayout(islandView, islandParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            islandView.toggleExpanded();
                        }
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

    private void requestStartRecording() {
        if (recording || countingDown) {
            return;
        }
        if (countdown) {
            startCountdown(3);
        } else {
            startRecording();
        }
    }

    private void startCountdown(final int seconds) {
        countingDown = true;
        if (islandView != null) {
            islandView.setIdle("Başlıyor: " + seconds, "Hazırlan • ada ekranda kalacak");
        }
        if (seconds <= 0) {
            countingDown = false;
            startRecording();
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startCountdown(seconds - 1);
            }
        }, 1000);
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
            paused = false;
            recordStartedAt = System.currentTimeMillis();
            elapsedBeforePause = 0L;
            startAsForeground("Kayıt devam ediyor");
            updateIslandState();
            handler.removeCallbacks(ticker);
            handler.post(ticker);
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

    private void togglePause() {
        if (!recording || mediaRecorder == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return;
        }
        try {
            if (paused) {
                mediaRecorder.resume();
                recordStartedAt = System.currentTimeMillis();
                paused = false;
            } else {
                mediaRecorder.pause();
                elapsedBeforePause += System.currentTimeMillis() - recordStartedAt;
                paused = true;
            }
            updateIslandState();
        } catch (RuntimeException exception) {
            toast("Pause desteklenmedi");
        }
    }

    private void stopRecording(boolean notify) {
        countingDown = false;
        handler.removeCallbacks(ticker);
        if (!recording && mediaRecorder == null) {
            updateIslandState();
            return;
        }
        try {
            if (recording && mediaRecorder != null) {
                mediaRecorder.stop();
            }
        } catch (RuntimeException ignored) {
        }
        recording = false;
        paused = false;
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
        if (islandView == null) {
            return;
        }
        if (recording) {
            islandView.setRecording(formatElapsed(), highQuality ? "60 FPS / HQ" : "30 FPS / Eco", recordAudio && hasAudioPermission(), paused);
        } else if (!countingDown) {
            islandView.setIdle("Hazır", "Sürükle • dokun: küçült/büyüt");
        }
    }

    private String formatElapsed() {
        long elapsed = elapsedBeforePause;
        if (recording && !paused) {
            elapsed += System.currentTimeMillis() - recordStartedAt;
        }
        long totalSeconds = elapsed / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
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
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Screen Island Recorder", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private boolean hasAudioPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return IslandTheme.dp(this, value);
    }
}
