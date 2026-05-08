package com.screenisland.recorder;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Switch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 10;
    private static final int REQUEST_OVERLAY = 11;
    private static final int REQUEST_CAPTURE = 12;

    private static final int COLOR_BG = Color.rgb(7, 10, 18);
    private static final int COLOR_PANEL = Color.rgb(18, 24, 38);
    private static final int COLOR_PANEL_LIGHT = Color.rgb(30, 40, 60);
    private static final int COLOR_TEXT = Color.rgb(245, 248, 255);
    private static final int COLOR_MUTED = Color.rgb(155, 168, 190);
    private static final int COLOR_ACCENT = Color.rgb(105, 92, 255);
    private static final int COLOR_RECORD = Color.rgb(255, 71, 87);

    private LinearLayout content;
    private Switch microphoneSwitch;
    private Switch highQualitySwitch;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            updateStatus();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY) {
            updateStatus();
            if (canDrawOverlays()) {
                requestScreenCapture();
            }
            return;
        }
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            launchFloatingIsland(resultCode, data);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);
        root.setPadding(dp(18), dp(22), dp(18), dp(18));

        TextView title = new TextView(this);
        title.setText("Screen Island");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(34f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Open the recorder dediğinde yüzen ada açılır. Ada üzerinden kayıt başlat, durdur ve ayarlara dön.");
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setTextSize(16f);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        root.addView(subtitle, matchWrap());

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        renderHome();
    }

    private void renderHome() {
        content.removeAllViews();
        addHeroCard();
        addSettingsCard();
        addTipsCard();
        statusText = new TextView(this);
        statusText.setTextColor(COLOR_MUTED);
        statusText.setTextSize(14f);
        statusText.setPadding(0, dp(12), 0, 0);
        content.addView(statusText, matchWrap());
        updateStatus();
    }

    private void addHeroCard() {
        LinearLayout card = card();
        card.setBackground(gradient(Color.rgb(31, 38, 72), Color.rgb(80, 62, 180)));
        TextView title = cardTitle("Yüzen kayıt adası", Color.WHITE);
        card.addView(title, matchWrap());
        TextView body = bodyText("Kayıt iznini ver, sonra küçük ada ekranın üstünde kalır. Uygulamadan çıkınca bile Start / Stop / Ayarlar kısayolları orada olur.");
        body.setTextColor(Color.rgb(220, 226, 255));
        card.addView(body, matchWrap());
        Button open = button("Open the recorder", COLOR_RECORD, Color.WHITE, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openRecorder();
            }
        });
        card.addView(open, matchWrapWithMargins(0, dp(14), 0, 0));
        content.addView(card, matchWrapWithMargins(0, 0, 0, dp(14)));
    }

    private void addSettingsCard() {
        LinearLayout card = card();
        card.addView(cardTitle("Hızlı ayarlar", COLOR_TEXT), matchWrap());
        microphoneSwitch = new Switch(this);
        microphoneSwitch.setText("Mikrofon sesi de kaydedilsin");
        microphoneSwitch.setTextColor(COLOR_TEXT);
        microphoneSwitch.setChecked(true);
        microphoneSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if (checked && !hasAudioPermission()) {
                    requestRuntimePermissions();
                }
            }
        });
        card.addView(microphoneSwitch, matchWrap());

        highQualitySwitch = new Switch(this);
        highQualitySwitch.setText("Yüksek kalite / 60 FPS hedefi");
        highQualitySwitch.setTextColor(COLOR_TEXT);
        highQualitySwitch.setChecked(true);
        card.addView(highQualitySwitch, matchWrap());
        content.addView(card, matchWrapWithMargins(0, 0, 0, dp(14)));
    }

    private void addTipsCard() {
        LinearLayout card = card();
        card.addView(cardTitle("Nasıl çalışır?", COLOR_TEXT), matchWrap());
        card.addView(bodyText("1. Open the recorder'a bas\n2. Android ekran yakalama iznini ver\n3. Yüzen adadan REC ile başlat, STOP ile kaydı bitir\n4. Videolar uygulamanın Movies/ScreenIsland klasörüne kaydedilir"), matchWrap());
        content.addView(card, matchWrapWithMargins(0, 0, 0, dp(14)));
    }

    private void openRecorder() {
        requestRuntimePermissions();
        if (!canDrawOverlays()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY);
            Toast.makeText(this, "Yüzen ada için üstte gösterme iznini aç", Toast.LENGTH_LONG).show();
            return;
        }
        requestScreenCapture();
    }

    private void requestScreenCapture() {
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            Toast.makeText(this, "Ekran kaydı servisi bulunamadı", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    private void launchFloatingIsland(int resultCode, Intent data) {
        Intent service = new Intent(this, FloatingRecorderService.class);
        service.setAction(FloatingRecorderService.ACTION_OPEN_ISLAND);
        service.putExtra(FloatingRecorderService.EXTRA_RESULT_CODE, resultCode);
        service.putExtra(FloatingRecorderService.EXTRA_RESULT_DATA, data);
        service.putExtra(FloatingRecorderService.EXTRA_RECORD_AUDIO, microphoneSwitch.isChecked() && hasAudioPermission());
        service.putExtra(FloatingRecorderService.EXTRA_HIGH_QUALITY, highQualitySwitch.isChecked());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        Toast.makeText(this, "Yüzen ada açıldı", Toast.LENGTH_SHORT).show();
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        List<String> permissions = new ArrayList<>();
        if (!hasAudioPermission()) {
            permissions.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    private boolean hasAudioPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void updateStatus() {
        if (statusText == null) {
            return;
        }
        String overlay = canDrawOverlays() ? "Overlay hazır" : "Overlay izni gerekli";
        String audio = hasAudioPermission() ? "Mikrofon hazır" : "Mikrofon izni gerekli";
        statusText.setText(overlay + " • " + audio + " • " + new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(COLOR_PANEL, dp(22)));
        return card;
    }

    private TextView cardTitle(String text, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(20f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView bodyText(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_MUTED);
        view.setTextSize(15f);
        view.setPadding(0, dp(8), 0, 0);
        return view;
    }

    private Button button(String text, int bg, int fg, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(fg);
        button.setTextSize(16f);
        button.setBackground(rounded(bg, dp(18)));
        button.setOnClickListener(listener);
        return button;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable gradient(int start, int end) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setCornerRadius(dp(22));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrapWithMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
