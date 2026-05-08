package com.bitchatter.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends Activity {
    private static final String APP_NAME = "bitchatter";
    private static final String APP_PROTOCOL_ID = "bitchatter.secure.bluetooth.v2";
    private static final String BEACON_PREFIX = "bitchatter:";
    private static final UUID CHAT_UUID = UUID.fromString("8a5f7e31-9274-4fbf-9f5f-8d8fda8f7b11");
    private static final int PERMISSION_REQUEST = 101;
    private static final int PICK_MEDIA_REQUEST = 202;
    private static final int DISCOVERABLE_SECONDS = 300;
    private static final int MAX_MESSAGE_LENGTH = 1200;
    private static final int MAX_MEDIA_BYTES = 3 * 1024 * 1024;
    private static final int MAX_CHAT_LINES = 5000;

    private static final int COLOR_BG = Color.rgb(8, 12, 18);
    private static final int COLOR_PANEL = Color.rgb(18, 24, 33);
    private static final int COLOR_PANEL_LIGHT = Color.rgb(28, 36, 48);
    private static final int COLOR_GREEN = Color.rgb(0, 224, 132);
    private static final int COLOR_GREEN_DARK = Color.rgb(0, 150, 95);
    private static final int COLOR_TEXT = Color.rgb(240, 244, 248);
    private static final int COLOR_MUTED = Color.rgb(147, 160, 175);
    private static final int COLOR_BUBBLE_ME = Color.rgb(0, 72, 52);
    private static final int COLOR_BUBBLE_REMOTE = Color.rgb(31, 41, 55);
    private static final int COLOR_DANGER = Color.rgb(230, 64, 84);

    private final Map<String, BluetoothDevice> nearbyDevices = new LinkedHashMap<>();
    private final List<PendingInvite> pendingInvites = new ArrayList<>();
    private final List<ChatLine> chatLines = new ArrayList<>();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothServerSocket serverSocket;
    private ChatSession activeSession;
    private LinearLayout root;
    private LinearLayout header;
    private LinearLayout content;
    private LinearLayout bottomBar;
    private TextView titleText;
    private TextView subtitleText;
    private TextView statusText;
    private String localId;
    private String localName;
    private String currentScreen = "nearby";
    private String lastBluetoothName;
    private boolean receiverRegistered;
    private boolean recording;
    private volatile boolean serverRunning;
    private MediaRecorder recorder;
    private File recordingFile;

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && isBitchatterBeacon(device)) {
                    rememberDevice(device);
                    if ("nearby".equals(currentScreen)) {
                        showNearbyScreen();
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                updateStatus("Tarama bitti • sadece bitchatter cihazları: " + nearbyDevices.size());
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                refreshHeader();
                startBluetoothServer();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadIdentity();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        buildLayout();
        requestNeededPermissions();
        registerBluetoothReceiver();
        addBondedBitchatterDevices();
        startBluetoothServer();
        showNearbyScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        addBondedBitchatterDevices();
        refreshHeader();
        startBluetoothServer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiverRegistered) {
            unregisterReceiver(bluetoothReceiver);
        }
        stopDiscovery();
        stopBluetoothServer();
        closeActiveSession();
        stopRecording(false);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            addBondedBitchatterDevices();
            startBluetoothServer();
            refreshHeader();
            showCurrentScreen();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_MEDIA_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            sendMediaFromUri(data.getData());
        }
    }

    private void loadIdentity() {
        SharedPreferences preferences = getSharedPreferences("bitchatter_identity", MODE_PRIVATE);
        localId = preferences.getString("local_id", null);
        localName = preferences.getString("local_name", null);
        if (localId == null || localName == null) {
            localId = UUID.randomUUID().toString();
            localName = "bc-" + localId.substring(0, 4).toLowerCase(Locale.US);
            preferences.edit().putString("local_id", localId).putString("local_name", localName).apply();
        }
    }

    private void saveName(String newName) {
        String clean = newName.trim();
        if (clean.length() < 2) {
            toast("İsim en az 2 karakter olmalı");
            return;
        }
        if (clean.length() > 24) {
            clean = clean.substring(0, 24);
        }
        localName = clean;
        getSharedPreferences("bitchatter_identity", MODE_PRIVATE).edit().putString("local_name", localName).apply();
        setBitchatterBluetoothName();
        refreshHeader();
        showProfileScreen();
    }

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(18), dp(18), dp(14));
        header.setBackground(gradient(COLOR_PANEL, Color.rgb(7, 38, 32)));

        titleText = new TextView(this);
        titleText.setText(APP_NAME);
        titleText.setTextColor(COLOR_TEXT);
        titleText.setTextSize(28f);
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(titleText, matchWrap());

        subtitleText = new TextView(this);
        subtitleText.setTextColor(COLOR_MUTED);
        subtitleText.setTextSize(13f);
        subtitleText.setPadding(0, dp(4), 0, 0);
        header.addView(subtitleText, matchWrap());

        statusText = new TextView(this);
        statusText.setTextColor(COLOR_GREEN);
        statusText.setTextSize(13f);
        statusText.setPadding(0, dp(8), 0, 0);
        header.addView(statusText, matchWrap());
        root.addView(header, matchWrap());

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(12), dp(12), dp(12));
        scrollView.addView(content, matchWrap());
        root.addView(scrollView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(dp(6), dp(6), dp(6), dp(6));
        bottomBar.setBackgroundColor(COLOR_PANEL);
        root.addView(bottomBar, matchWrap());

        setContentView(root);
        refreshHeader();
    }

    private void refreshHeader() {
        String bluetoothState = bluetoothAdapter == null ? "Bluetooth yok" : bluetoothAdapter.isEnabled() ? "Bluetooth açık" : "Bluetooth kapalı";
        subtitleText.setText(localName + " • ID " + shortId(localId) + " • " + bluetoothState + " • sadece " + BEACON_PREFIX + " cihazları");
    }

    private void rebuildBottomBar() {
        bottomBar.removeAllViews();
        bottomBar.addView(navButton("Yakın", "nearby"), weightWrap());
        bottomBar.addView(navButton("İstek" + (pendingInvites.isEmpty() ? "" : " " + pendingInvites.size()), "requests"), weightWrap());
        bottomBar.addView(navButton("Sohbet", "chat"), weightWrap());
        bottomBar.addView(navButton("Profil", "profile"), weightWrap());
    }

    private Button navButton(String label, final String screen) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(screen.equals(currentScreen) ? Color.BLACK : COLOR_TEXT);
        button.setBackground(rounded(screen.equals(currentScreen) ? COLOR_GREEN : COLOR_PANEL_LIGHT, dp(18)));
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                currentScreen = screen;
                showCurrentScreen();
            }
        });
        return button;
    }

    private void showCurrentScreen() {
        if ("requests".equals(currentScreen)) {
            showRequestsScreen();
        } else if ("chat".equals(currentScreen)) {
            showChatScreen();
        } else if ("profile".equals(currentScreen)) {
            showProfileScreen();
        } else {
            showNearbyScreen();
        }
    }

    private void showNearbyScreen() {
        currentScreen = "nearby";
        refreshHeader();
        rebuildBottomBar();
        content.removeAllViews();
        addHeroCard("Sadece bitchatter kullanıcıları", "Yakındaki listeye yalnızca Bluetooth adında uygulama imzası (" + BEACON_PREFIX + ") yayan ve güvenli el sıkışmayı geçen cihazlar alınır.");

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(primaryButton("Bluetooth Aç", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enableBluetooth();
            }
        }), weightWrap());
        actions.addView(primaryButton("Yayın Yap", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                makeDiscoverable();
            }
        }), weightWrap());
        content.addView(actions, matchWrap());

        content.addView(primaryButton("bitchatter Cihazlarını Tara", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDiscovery();
            }
        }), matchWrapWithMargins(0, 0, 0, dp(12)));

        addBondedBitchatterDevices();
        if (nearbyDevices.isEmpty()) {
            addEmptyState("Henüz uygun cihaz yok", "Karşı taraf da bitchatter içinde 'Yayın Yap' düğmesine basmalı. Normal Bluetooth cihazları gizlenir.");
            return;
        }
        for (BluetoothDevice device : nearbyDevices.values()) {
            addDeviceCard(device);
        }
    }

    private void showRequestsScreen() {
        currentScreen = "requests";
        rebuildBottomBar();
        content.removeAllViews();
        addHeroCard("Güvenli istekler", "Davetler ECDH + AES-GCM kanalı üstünden gelir. Kodu karşı tarafla karşılaştırabilirsin.");
        if (pendingInvites.isEmpty()) {
            addEmptyState("Bekleyen istek yok", "Biri sana mesajlaşma isteği gönderdiğinde burada görünür.");
            return;
        }
        List<PendingInvite> snapshot = new ArrayList<>(pendingInvites);
        for (final PendingInvite invite : snapshot) {
            LinearLayout card = cardLayout();
            addCardTitle(card, invite.remoteName);
            addCardText(card, "ID: " + shortId(invite.remoteId));
            addCardText(card, "Güvenlik kodu: " + invite.fingerprint);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(primaryButton("Kabul Et", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    acceptInvite(invite);
                }
            }), weightWrap());
            row.addView(dangerButton("Reddet", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    rejectInvite(invite);
                }
            }), weightWrap());
            card.addView(row, matchWrap());
            content.addView(card, matchWrapWithMargins(0, 0, 0, dp(10)));
        }
    }

    private void showChatScreen() {
        currentScreen = "chat";
        rebuildBottomBar();
        content.removeAllViews();
        if (activeSession == null) {
            addEmptyState("Aktif sohbet yok", "Yakın sekmesinden bir bitchatter kullanıcısına istek gönder veya gelen isteği kabul et.");
            return;
        }
        addChatHeader();
        for (ChatLine line : chatLines) {
            addBubble(line);
        }
        addComposer();
    }

    private void showProfileScreen() {
        currentScreen = "profile";
        rebuildBottomBar();
        content.removeAllViews();
        addHeroCard("Profil", "Adını değiştir, benzersiz ID'ni gör ve etrafa doğru bitchatter imzasını yayınla.");
        LinearLayout card = cardLayout();
        addCardTitle(card, "Kimliğin");
        addCardText(card, "Unique ID: " + localId);
        addCardText(card, "Bluetooth yayın adı: " + beaconName());
        final EditText nameInput = new EditText(this);
        nameInput.setText(localName);
        nameInput.setSingleLine(true);
        nameInput.setTextColor(COLOR_TEXT);
        nameInput.setHintTextColor(COLOR_MUTED);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        nameInput.setBackground(rounded(COLOR_PANEL_LIGHT, dp(14)));
        nameInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(nameInput, matchWrapWithMargins(0, dp(10), 0, dp(10)));
        card.addView(primaryButton("Adı Kaydet", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveName(nameInput.getText().toString());
            }
        }), matchWrap());
        card.addView(secondaryButton("bitchatter Yayınını Yenile", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                makeDiscoverable();
            }
        }), matchWrapWithMargins(0, dp(8), 0, 0));
        content.addView(card, matchWrap());
    }

    private void addChatHeader() {
        LinearLayout card = cardLayout();
        addCardTitle(card, activeSession.remoteName);
        addCardText(card, "Şifreli bağlantı • ID " + shortId(activeSession.remoteId));
        addCardText(card, "Güvenlik kodu: " + activeSession.channel.fingerprint);
        addCardText(card, "Mesaj limiti: " + MAX_CHAT_LINES + " satır • Medya limiti: 3 MB");
        content.addView(card, matchWrapWithMargins(0, 0, 0, dp(10)));
    }

    private void addComposer() {
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.addView(secondaryButton("📷 Görsel/Video", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickMedia();
            }
        }), weightWrap());
        tools.addView(secondaryButton(recording ? "■ Kaydı Bitir" : "🎙 Ses", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleRecording();
            }
        }), weightWrap());
        content.addView(tools, matchWrap());

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(0, dp(8), 0, 0);
        final EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(3);
        input.setHint("Mesaj yaz");
        input.setTextColor(COLOR_TEXT);
        input.setHintTextColor(COLOR_MUTED);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_MESSAGE_LENGTH)});
        input.setBackground(rounded(COLOR_PANEL_LIGHT, dp(22)));
        input.setPadding(dp(14), dp(8), dp(14), dp(8));
        composer.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button send = new Button(this);
        send.setText("➤");
        send.setTextSize(18f);
        send.setTextColor(Color.BLACK);
        send.setBackground(rounded(COLOR_GREEN, dp(22)));
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String message = input.getText().toString().trim();
                if (!message.isEmpty()) {
                    sendChatMessage(message);
                    input.setText("");
                }
            }
        });
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        sendParams.setMargins(dp(8), 0, 0, 0);
        composer.addView(send, sendParams);
        content.addView(composer, matchWrap());
    }

    private void addDeviceCard(final BluetoothDevice device) {
        LinearLayout card = cardLayout();
        addCardTitle(card, displayDeviceName(device));
        addCardText(card, deviceAddress(device));
        addCardText(card, "Doğrulama: " + BEACON_PREFIX + " yayını algılandı");
        addCardText(card, isBonded(device) ? "Eşleşmiş cihaz • daha hızlı" : "Yeni cihaz • Android eşleşme isteyebilir");
        card.addView(primaryButton("Mesajlaşma İsteği Gönder", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendInvite(device);
            }
        }), matchWrap());
        content.addView(card, matchWrapWithMargins(0, 0, 0, dp(10)));
    }

    private void addHeroCard(String title, String body) {
        LinearLayout card = cardLayout();
        card.setBackground(gradient(Color.rgb(12, 22, 32), Color.rgb(0, 70, 50)));
        addCardTitle(card, title, COLOR_TEXT);
        TextView bodyText = new TextView(this);
        bodyText.setText(body);
        bodyText.setTextColor(COLOR_MUTED);
        bodyText.setTextSize(15f);
        bodyText.setPadding(0, dp(6), 0, 0);
        card.addView(bodyText, matchWrap());
        content.addView(card, matchWrapWithMargins(0, 0, 0, dp(12)));
    }

    private void addEmptyState(String title, String body) {
        LinearLayout card = cardLayout();
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(18), dp(30), dp(18), dp(30));
        addCardTitle(card, title);
        TextView text = new TextView(this);
        text.setText(body);
        text.setTextColor(COLOR_MUTED);
        text.setGravity(Gravity.CENTER);
        text.setTextSize(15f);
        card.addView(text, matchWrap());
        content.addView(card, matchWrap());
    }

    private LinearLayout cardLayout() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(COLOR_PANEL, dp(18)));
        return card;
    }

    private void addCardTitle(LinearLayout card, String text) {
        addCardTitle(card, text, COLOR_TEXT);
    }

    private void addCardTitle(LinearLayout card, String text, int color) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(color);
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(title, matchWrap());
    }

    private void addCardText(LinearLayout card, String text) {
        TextView line = new TextView(this);
        line.setText(text);
        line.setTextColor(COLOR_MUTED);
        line.setTextSize(14f);
        line.setPadding(0, dp(4), 0, dp(4));
        card.addView(line, matchWrap());
    }

    private void addBubble(final ChatLine line) {
        FrameLayout row = new FrameLayout(this);
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(9), dp(12), dp(9));
        bubble.setBackground(rounded(line.mine ? COLOR_BUBBLE_ME : COLOR_BUBBLE_REMOTE, dp(16)));
        if (line.kind.startsWith("image/") && line.bytes != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(line.bytes, 0, line.bytes.length);
            if (bitmap != null) {
                ImageView image = new ImageView(this);
                image.setImageBitmap(bitmap);
                image.setAdjustViewBounds(true);
                image.setMaxHeight(dp(260));
                bubble.addView(image, matchWrap());
            }
        }
        TextView text = new TextView(this);
        text.setText(line.text);
        text.setTextSize(16f);
        text.setTextColor(COLOR_TEXT);
        bubble.addView(text, matchWrap());
        if (line.kind.startsWith("audio/") && line.bytes != null) {
            bubble.addView(secondaryButton("▶ Sesi Oynat", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    playAudio(line);
                }
            }), matchWrapWithMargins(0, dp(6), 0, 0));
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, line.mine ? Gravity.END : Gravity.START);
        params.setMargins(line.mine ? dp(56) : 0, dp(5), line.mine ? 0 : dp(56), dp(5));
        row.addView(bubble, params);
        content.addView(row, matchWrap());
    }

    private Button primaryButton(String text, View.OnClickListener listener) {
        return button(text, COLOR_GREEN, Color.BLACK, listener);
    }

    private Button secondaryButton(String text, View.OnClickListener listener) {
        return button(text, COLOR_PANEL_LIGHT, COLOR_TEXT, listener);
    }

    private Button dangerButton(String text, View.OnClickListener listener) {
        return button(text, COLOR_DANGER, Color.WHITE, listener);
    }

    private Button button(String text, int bg, int fg, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(fg);
        button.setBackground(rounded(bg, dp(14)));
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
        drawable.setCornerRadius(0);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        return params;
    }

    private LinearLayout.LayoutParams matchWrapWithMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void requestNeededPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addPermissionIfMissing(permissions, Manifest.permission.BLUETOOTH_SCAN);
            addPermissionIfMissing(permissions, Manifest.permission.BLUETOOTH_CONNECT);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            addPermissionIfMissing(permissions, Manifest.permission.ACCESS_FINE_LOCATION);
        }
        addPermissionIfMissing(permissions, Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33) {
            addPermissionIfMissing(permissions, Manifest.permission.READ_MEDIA_IMAGES);
            addPermissionIfMissing(permissions, Manifest.permission.READ_MEDIA_VIDEO);
            addPermissionIfMissing(permissions, Manifest.permission.READ_MEDIA_AUDIO);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            addPermissionIfMissing(permissions, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), PERMISSION_REQUEST);
        }
    }

    private void addPermissionIfMissing(List<String> permissions, String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission);
        }
    }

    private boolean hasScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasRecordPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bluetoothReceiver, filter);
        }
        receiverRegistered = true;
    }

    private boolean ensureBluetoothAvailable() {
        if (bluetoothAdapter == null) {
            updateStatus("Bu cihaz Bluetooth desteklemiyor");
            return false;
        }
        return true;
    }

    private void enableBluetooth() {
        if (!ensureBluetoothAvailable()) {
            return;
        }
        if (!hasConnectPermission()) {
            requestNeededPermissions();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            toast("Bluetooth zaten açık");
        }
    }

    private void makeDiscoverable() {
        if (!ensureBluetoothAvailable()) {
            return;
        }
        if (!hasConnectPermission()) {
            requestNeededPermissions();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            enableBluetooth();
            return;
        }
        setBitchatterBluetoothName();
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS);
        startActivity(discoverableIntent);
        updateStatus("bitchatter yayını açık: " + beaconName());
    }

    private void setBitchatterBluetoothName() {
        if (!ensureBluetoothAvailable() || !hasConnectPermission()) {
            return;
        }
        String target = beaconName();
        String current = bluetoothAdapter.getName();
        if (lastBluetoothName == null && current != null && !current.startsWith(BEACON_PREFIX)) {
            lastBluetoothName = current;
        }
        if (!target.equals(current)) {
            bluetoothAdapter.setName(target);
        }
    }

    private String beaconName() {
        return BEACON_PREFIX + localName + ":" + shortId(localId);
    }

    private void startDiscovery() {
        if (!ensureBluetoothAvailable()) {
            return;
        }
        if (!hasScanPermission() || !hasConnectPermission()) {
            requestNeededPermissions();
            updateStatus("Bluetooth izinleri gerekli");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            enableBluetooth();
            return;
        }
        setBitchatterBluetoothName();
        addBondedBitchatterDevices();
        stopDiscovery();
        boolean started = bluetoothAdapter.startDiscovery();
        updateStatus(started ? "Sadece bitchatter yayınları taranıyor..." : "Tarama başlatılamadı");
        showNearbyScreen();
    }

    private void stopDiscovery() {
        if (bluetoothAdapter != null && hasScanPermission() && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    private void addBondedBitchatterDevices() {
        if (!ensureBluetoothAvailable() || !hasConnectPermission()) {
            return;
        }
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        if (bondedDevices == null) {
            return;
        }
        for (BluetoothDevice device : bondedDevices) {
            if (isBitchatterBeacon(device)) {
                rememberDevice(device);
            }
        }
    }

    private boolean isBitchatterBeacon(BluetoothDevice device) {
        if (!hasConnectPermission()) {
            return false;
        }
        String name = device.getName();
        return name != null && name.startsWith(BEACON_PREFIX);
    }

    private void rememberDevice(BluetoothDevice device) {
        String address = deviceAddress(device);
        if (address != null && !nearbyDevices.containsKey(address)) {
            nearbyDevices.put(address, device);
        }
    }

    private void startBluetoothServer() {
        if (serverRunning || !ensureBluetoothAvailable() || !hasConnectPermission() || !bluetoothAdapter.isEnabled()) {
            return;
        }
        setBitchatterBluetoothName();
        serverRunning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, CHAT_UUID);
                    updateStatus("Güvenli bitchatter bağlantıları dinleniyor");
                    while (serverRunning) {
                        BluetoothSocket socket = serverSocket.accept();
                        handleIncomingSocket(socket);
                    }
                } catch (IOException exception) {
                    if (serverRunning) {
                        updateStatus("Dinleme durdu: " + exception.getMessage());
                    }
                    serverRunning = false;
                }
            }
        }, "bitchatter-server").start();
    }

    private void stopBluetoothServer() {
        serverRunning = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleIncomingSocket(final BluetoothSocket socket) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final SecureChannel channel = SecureChannel.server(socket, localId, localName);
                    ProtocolMessage message = channel.receive();
                    if (message == null || !"INVITE".equals(message.type)) {
                        closeQuietly(socket);
                        return;
                    }
                    final PendingInvite invite = new PendingInvite(socket, channel, message.senderId, message.senderName);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pendingInvites.add(invite);
                            updateStatus(invite.remoteName + " istek gönderdi");
                            toast("Yeni istek: " + invite.remoteName);
                            if ("requests".equals(currentScreen)) {
                                showRequestsScreen();
                            } else {
                                rebuildBottomBar();
                            }
                        }
                    });
                } catch (IOException | GeneralSecurityException exception) {
                    closeQuietly(socket);
                    updateStatus("Gelen bağlantı bitchatter doğrulamasından geçemedi");
                }
            }
        }, "bitchatter-incoming").start();
    }

    private void sendInvite(final BluetoothDevice device) {
        if (!ensureBluetoothAvailable() || !hasConnectPermission()) {
            requestNeededPermissions();
            return;
        }
        if (!isBitchatterBeacon(device)) {
            updateStatus("Bu cihaz bitchatter yayını yapmıyor");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            enableBluetooth();
            return;
        }
        updateStatus(displayDeviceName(device) + " için güvenli istek gönderiliyor...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                BluetoothSocket socket = null;
                try {
                    stopDiscovery();
                    socket = device.createRfcommSocketToServiceRecord(CHAT_UUID);
                    socket.connect();
                    SecureChannel channel = SecureChannel.client(socket, localId, localName);
                    channel.send(ProtocolMessage.identity("INVITE", localId, localName));
                    ProtocolMessage response = channel.receive();
                    if (response != null && "ACCEPT".equals(response.type)) {
                        startChatSession(new ChatSession(socket, channel, response.senderId, response.senderName));
                    } else {
                        closeQuietly(socket);
                        updateStatus("İstek reddedildi veya cevap alınamadı");
                    }
                } catch (IOException | GeneralSecurityException exception) {
                    closeQuietly(socket);
                    updateStatus("Bağlanamadı: " + exception.getMessage());
                }
            }
        }, "bitchatter-invite").start();
    }

    private void acceptInvite(final PendingInvite invite) {
        pendingInvites.remove(invite);
        try {
            invite.channel.send(ProtocolMessage.identity("ACCEPT", localId, localName));
            startChatSession(new ChatSession(invite.socket, invite.channel, invite.remoteId, invite.remoteName));
        } catch (IOException exception) {
            closeQuietly(invite.socket);
            updateStatus("İstek kabul edilemedi");
        }
    }

    private void rejectInvite(PendingInvite invite) {
        pendingInvites.remove(invite);
        try {
            invite.channel.send(ProtocolMessage.identity("REJECT", localId, localName));
        } catch (IOException ignored) {
        }
        closeQuietly(invite.socket);
        showRequestsScreen();
    }

    private void startChatSession(final ChatSession session) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                closeActiveSession();
                activeSession = session;
                chatLines.clear();
                addChatLine(ChatLine.system("Güvenli sohbet başladı • kod " + session.channel.fingerprint));
                updateStatus("Sohbet: " + session.remoteName);
                currentScreen = "chat";
                showChatScreen();
            }
        });
        session.startListening();
    }

    private void sendChatMessage(String message) {
        ChatSession session = activeSession;
        if (session == null) {
            return;
        }
        try {
            session.channel.send(ProtocolMessage.chat(localId, localName, message));
            addChatLine(ChatLine.mine(message));
            showChatScreen();
        } catch (IOException exception) {
            updateStatus("Mesaj gönderilemedi");
            onConnectionClosed();
        }
    }

    private void pickMedia() {
        if (activeSession == null) {
            toast("Önce sohbet başlat");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*", "audio/*"});
        startActivityForResult(Intent.createChooser(intent, "Görsel, video veya ses seç"), PICK_MEDIA_REQUEST);
    }

    private void sendMediaFromUri(Uri uri) {
        try {
            String mime = getContentResolver().getType(uri);
            if (mime == null) {
                mime = "application/octet-stream";
            }
            String name = displayName(uri);
            byte[] bytes = readUriBytes(uri, MAX_MEDIA_BYTES);
            sendMediaBytes(mime, name, bytes);
        } catch (IOException exception) {
            updateStatus("Medya gönderilemedi: " + exception.getMessage());
        }
    }

    private void sendMediaBytes(String mime, String name, byte[] bytes) throws IOException {
        ChatSession session = activeSession;
        if (session == null) {
            return;
        }
        String body = encode(mime) + "," + encode(name) + "," + b64(bytes);
        session.channel.send(ProtocolMessage.media(localId, localName, body));
        addChatLine(ChatLine.media("Ben: " + mediaLabel(mime, name, bytes.length), true, mime, name, bytes));
        showChatScreen();
    }

    private void toggleRecording() {
        if (recording) {
            stopRecording(true);
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (activeSession == null) {
            toast("Önce sohbet başlat");
            return;
        }
        if (!hasRecordPermission()) {
            requestNeededPermissions();
            return;
        }
        try {
            recordingFile = new File(getCacheDir(), "voice-" + System.currentTimeMillis() + ".m4a");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(recordingFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            updateStatus("Ses kaydı başladı");
            showChatScreen();
        } catch (IOException | RuntimeException exception) {
            stopRecording(false);
            updateStatus("Ses kaydı başlatılamadı");
        }
    }

    private void stopRecording(boolean send) {
        if (!recording && recorder == null) {
            return;
        }
        try {
            if (recorder != null) {
                recorder.stop();
            }
        } catch (RuntimeException ignored) {
        }
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
        recording = false;
        if (send && recordingFile != null && recordingFile.exists()) {
            try {
                byte[] bytes = readFileBytes(recordingFile, MAX_MEDIA_BYTES);
                sendMediaBytes("audio/mp4", recordingFile.getName(), bytes);
            } catch (IOException exception) {
                updateStatus("Ses gönderilemedi");
            }
        }
        showChatScreen();
    }

    private void onRemoteMessage(final ProtocolMessage message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if ("MSG".equals(message.type)) {
                    addChatLine(ChatLine.remote(message.body));
                } else if ("MEDIA".equals(message.type)) {
                    addChatLine(mediaLineFromMessage(message));
                }
                if ("chat".equals(currentScreen)) {
                    showChatScreen();
                }
            }
        });
    }

    private ChatLine mediaLineFromMessage(ProtocolMessage message) {
        try {
            String[] parts = message.body.split(",", 3);
            if (parts.length != 3) {
                return ChatLine.remote("Medya alınamadı");
            }
            String mime = decode(parts[0]);
            String name = decode(parts[1]);
            byte[] bytes = fromB64(parts[2]);
            return ChatLine.media(message.senderName + ": " + mediaLabel(mime, name, bytes.length), false, mime, name, bytes);
        } catch (IllegalArgumentException exception) {
            return ChatLine.remote("Medya çözülemedi");
        }
    }

    private void playAudio(ChatLine line) {
        try {
            File file = new File(getCacheDir(), "play-" + System.currentTimeMillis() + ".m4a");
            FileOutputStream output = new FileOutputStream(file);
            output.write(line.bytes);
            output.close();
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer completedPlayer) {
                    completedPlayer.release();
                }
            });
            player.prepare();
            player.start();
            toast("Ses oynatılıyor");
        } catch (IOException exception) {
            updateStatus("Ses oynatılamadı");
        }
    }

    private void onConnectionClosed() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (activeSession != null) {
                    activeSession.close();
                    activeSession = null;
                }
                addChatLine(ChatLine.system("Bağlantı kapandı"));
                updateStatus("Sohbet bağlantısı kapandı");
                if ("chat".equals(currentScreen)) {
                    showChatScreen();
                }
            }
        });
    }

    private void addChatLine(ChatLine line) {
        chatLines.add(line);
        while (chatLines.size() > MAX_CHAT_LINES) {
            chatLines.remove(0);
        }
    }

    private void closeActiveSession() {
        if (activeSession != null) {
            activeSession.close();
            activeSession = null;
        }
    }

    private String displayDeviceName(BluetoothDevice device) {
        String name = safeDeviceName(device);
        if (name.startsWith(BEACON_PREFIX)) {
            String rest = name.substring(BEACON_PREFIX.length());
            int lastColon = rest.lastIndexOf(':');
            return lastColon > 0 ? rest.substring(0, lastColon) : rest;
        }
        return name;
    }

    private String safeDeviceName(BluetoothDevice device) {
        String name = null;
        if (hasConnectPermission()) {
            name = device.getName();
        }
        return name == null || name.trim().isEmpty() ? "Bilinmeyen bitchatter" : name;
    }

    private String deviceAddress(BluetoothDevice device) {
        if (!hasConnectPermission()) {
            return "izin gerekli";
        }
        String address = device.getAddress();
        return address == null ? "adres yok" : address;
    }

    private boolean isBonded(BluetoothDevice device) {
        return hasConnectPermission() && device.getBondState() == BluetoothDevice.BOND_BONDED;
    }

    private String shortId(String id) {
        if (id == null || id.length() < 8) {
            return "--------";
        }
        return id.substring(0, 8);
    }

    private String now() {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date());
    }

    private String displayName(Uri uri) {
        String result = "media";
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            try {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    result = cursor.getString(nameIndex);
                }
            } finally {
                cursor.close();
            }
        }
        return result == null ? "media" : result;
    }

    private byte[] readUriBytes(Uri uri, int maxBytes) throws IOException {
        InputStream input = getContentResolver().openInputStream(uri);
        if (input == null) {
            throw new IOException("Dosya açılamadı");
        }
        return readStream(input, maxBytes);
    }

    private byte[] readFileBytes(File file, int maxBytes) throws IOException {
        return readStream(new FileInputStream(file), maxBytes);
    }

    private byte[] readStream(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        try {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Medya 3 MB sınırını aşıyor");
                }
                output.write(buffer, 0, read);
            }
        } finally {
            input.close();
        }
        return output.toByteArray();
    }

    private String mediaLabel(String mime, String name, int bytes) {
        String kind = mime.startsWith("image/") ? "Görsel" : mime.startsWith("video/") ? "Video" : mime.startsWith("audio/") ? "Ses" : "Dosya";
        return kind + " • " + name + " • " + (bytes / 1024 + 1) + " KB • " + now();
    }

    private void updateStatus(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusText.setText(status);
            }
        });
    }

    private void toast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void closeQuietly(BluetoothSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String encode(String value) {
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String decode(String value) {
        return new String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8);
    }

    private static String b64(byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private static byte[] fromB64(String value) {
        return Base64.decode(value, Base64.NO_WRAP);
    }

    private static class PendingInvite {
        final BluetoothSocket socket;
        final SecureChannel channel;
        final String remoteId;
        final String remoteName;
        final String fingerprint;

        PendingInvite(BluetoothSocket socket, SecureChannel channel, String remoteId, String remoteName) {
            this.socket = socket;
            this.channel = channel;
            this.remoteId = remoteId;
            this.remoteName = remoteName;
            this.fingerprint = channel.fingerprint;
        }
    }

    private static class ChatLine {
        final String text;
        final boolean mine;
        final String kind;
        final String name;
        final byte[] bytes;

        ChatLine(String text, boolean mine, String kind, String name, byte[] bytes) {
            this.text = text;
            this.mine = mine;
            this.kind = kind;
            this.name = name;
            this.bytes = bytes;
        }

        static ChatLine mine(String text) {
            return new ChatLine(text, true, "text/plain", "", null);
        }

        static ChatLine remote(String text) {
            return new ChatLine(text, false, "text/plain", "", null);
        }

        static ChatLine system(String text) {
            return new ChatLine("🔒 " + text, false, "text/system", "", null);
        }

        static ChatLine media(String text, boolean mine, String kind, String name, byte[] bytes) {
            return new ChatLine(text, mine, kind, name, bytes);
        }
    }

    private class ChatSession {
        final BluetoothSocket socket;
        final SecureChannel channel;
        final String remoteId;
        final String remoteName;
        volatile boolean open = true;

        ChatSession(BluetoothSocket socket, SecureChannel channel, String remoteId, String remoteName) {
            this.socket = socket;
            this.channel = channel;
            this.remoteId = remoteId;
            this.remoteName = remoteName;
        }

        void startListening() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (open) {
                        try {
                            ProtocolMessage message = channel.receive();
                            if (message == null) {
                                break;
                            }
                            if ("MSG".equals(message.type) || "MEDIA".equals(message.type)) {
                                onRemoteMessage(message);
                            }
                        } catch (IOException | GeneralSecurityException exception) {
                            break;
                        }
                    }
                    open = false;
                    onConnectionClosed();
                }
            }, "bitchatter-chat-listener").start();
        }

        void close() {
            open = false;
            closeQuietly(socket);
        }
    }

    private static class SecureChannel {
        final BufferedReader reader;
        final PrintWriter writer;
        final SecretKeySpec key;
        final SecureRandom random = new SecureRandom();
        final String fingerprint;

        SecureChannel(BufferedReader reader, PrintWriter writer, SecretKeySpec key, String fingerprint) {
            this.reader = reader;
            this.writer = writer;
            this.key = key;
            this.fingerprint = fingerprint;
        }

        static SecureChannel client(BluetoothSocket socket, String localId, String localName) throws IOException, GeneralSecurityException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            KeyPair pair = newKeyPair();
            writer.println(ProtocolMessage.hello(localId, localName, pair.getPublic().getEncoded()));
            ProtocolMessage response = ProtocolMessage.parse(reader.readLine());
            if (response == null || !"HELLO".equals(response.type) || !response.body.startsWith(APP_PROTOCOL_ID + ",")) {
                throw new IOException("bitchatter el sıkışması başarısız");
            }
            return fromHandshake(reader, writer, pair, response.publicKeyBytes(), localId, response.senderId);
        }

        static SecureChannel server(BluetoothSocket socket, String localId, String localName) throws IOException, GeneralSecurityException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            ProtocolMessage hello = ProtocolMessage.parse(reader.readLine());
            if (hello == null || !"HELLO".equals(hello.type) || !hello.body.startsWith(APP_PROTOCOL_ID + ",")) {
                throw new IOException("bitchatter el sıkışması başarısız");
            }
            KeyPair pair = newKeyPair();
            writer.println(ProtocolMessage.hello(localId, localName, pair.getPublic().getEncoded()));
            return fromHandshake(reader, writer, pair, hello.publicKeyBytes(), hello.senderId, localId);
        }

        private static SecureChannel fromHandshake(BufferedReader reader, PrintWriter writer, KeyPair pair, byte[] remotePublicBytes, String firstId, String secondId) throws GeneralSecurityException {
            PublicKey remotePublic = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(remotePublicBytes));
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(pair.getPrivate());
            agreement.doPhase(remotePublic, true);
            byte[] sharedSecret = agreement.generateSecret();
            byte[] salt = (APP_PROTOCOL_ID + "|" + firstId + "|" + secondId).getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = hkdf(sharedSecret, salt, "bitchatter-v2".getBytes(StandardCharsets.UTF_8), 32);
            byte[] fingerprintBytes = MessageDigest.getInstance("SHA-256").digest(keyBytes);
            String fingerprint = b64(fingerprintBytes).substring(0, 12);
            return new SecureChannel(reader, writer, new SecretKeySpec(keyBytes, "AES"), fingerprint);
        }

        static KeyPair newKeyPair() throws GeneralSecurityException {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(256);
            return generator.generateKeyPair();
        }

        synchronized void send(ProtocolMessage message) throws IOException {
            try {
                byte[] nonce = new byte[12];
                random.nextBytes(nonce);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
                byte[] encrypted = cipher.doFinal(message.serialize().getBytes(StandardCharsets.UTF_8));
                writer.println("SEC|" + b64(nonce) + "|" + b64(encrypted));
                if (writer.checkError()) {
                    throw new IOException("Mesaj yazılamadı");
                }
            } catch (GeneralSecurityException exception) {
                throw new IOException("Şifreleme hatası", exception);
            }
        }

        ProtocolMessage receive() throws IOException, GeneralSecurityException {
            String line = reader.readLine();
            if (line == null) {
                return null;
            }
            String[] parts = line.split("\\|", 3);
            if (parts.length != 3 || !"SEC".equals(parts[0])) {
                return null;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, fromB64(parts[1])));
            byte[] plaintext = cipher.doFinal(fromB64(parts[2]));
            return ProtocolMessage.parse(new String(plaintext, StandardCharsets.UTF_8));
        }

        private static byte[] hkdf(byte[] secret, byte[] salt, byte[] info, int length) throws GeneralSecurityException {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            byte[] prk = mac.doFinal(secret);
            byte[] output = new byte[length];
            byte[] previous = new byte[0];
            int copied = 0;
            int counter = 1;
            while (copied < length) {
                mac.init(new SecretKeySpec(prk, "HmacSHA256"));
                mac.update(previous);
                mac.update(info);
                mac.update((byte) counter);
                previous = mac.doFinal();
                int toCopy = Math.min(previous.length, length - copied);
                System.arraycopy(previous, 0, output, copied, toCopy);
                copied += toCopy;
                counter++;
            }
            return output;
        }
    }

    private static class ProtocolMessage {
        final String type;
        final String senderId;
        final String senderName;
        final String body;

        ProtocolMessage(String type, String senderId, String senderName, String body) {
            this.type = type;
            this.senderId = senderId;
            this.senderName = senderName;
            this.body = body;
        }

        static String hello(String senderId, String senderName, byte[] publicKeyBytes) {
            return "HELLO|" + senderId + "|" + encode(senderName) + "|" + APP_PROTOCOL_ID + "," + b64(publicKeyBytes);
        }

        static ProtocolMessage identity(String type, String senderId, String senderName) {
            return new ProtocolMessage(type, senderId, senderName, "");
        }

        static ProtocolMessage chat(String senderId, String senderName, String body) {
            return new ProtocolMessage("MSG", senderId, senderName, body);
        }

        static ProtocolMessage media(String senderId, String senderName, String body) {
            return new ProtocolMessage("MEDIA", senderId, senderName, body);
        }

        String serialize() {
            return type + "|" + senderId + "|" + encode(senderName) + "|" + encode(body);
        }

        byte[] publicKeyBytes() {
            int comma = body.indexOf(',');
            return fromB64(comma >= 0 ? body.substring(comma + 1) : body);
        }

        static ProtocolMessage parse(String line) {
            if (line == null) {
                return null;
            }
            String[] parts = line.split("\\|", 4);
            if (parts.length < 3) {
                return null;
            }
            try {
                String body = parts.length == 4 ? decode(parts[3]) : "";
                if ("HELLO".equals(parts[0]) && parts.length == 4) {
                    body = parts[3];
                }
                return new ProtocolMessage(parts[0], parts[1], decode(parts[2]), body);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}
