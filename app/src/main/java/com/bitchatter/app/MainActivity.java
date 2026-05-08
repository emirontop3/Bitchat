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
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
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
import java.util.ArrayList;
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
    private static final UUID CHAT_UUID = UUID.fromString("8a5f7e31-9274-4fbf-9f5f-8d8fda8f7b11");
    private static final int PERMISSION_REQUEST = 101;
    private static final int DISCOVERABLE_SECONDS = 300;
    private static final int MAX_MESSAGE_LENGTH = 1200;

    private static final int COLOR_HEADER = Color.rgb(7, 94, 84);
    private static final int COLOR_HEADER_DARK = Color.rgb(5, 70, 62);
    private static final int COLOR_ACCENT = Color.rgb(37, 211, 102);
    private static final int COLOR_BACKGROUND = Color.rgb(236, 229, 221);
    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_BUBBLE_ME = Color.rgb(220, 248, 198);
    private static final int COLOR_BUBBLE_REMOTE = Color.WHITE;
    private static final int COLOR_TEXT_MUTED = Color.rgb(92, 92, 92);

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
    private boolean receiverRegistered;
    private volatile boolean serverRunning;

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    rememberDevice(device);
                    if ("nearby".equals(currentScreen)) {
                        showNearbyScreen();
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                updateStatus("Tarama bitti • " + nearbyDevices.size() + " cihaz bulundu");
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
        addBondedDevices();
        startBluetoothServer();
        showNearbyScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        addBondedDevices();
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
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            addBondedDevices();
            startBluetoothServer();
            refreshHeader();
            showCurrentScreen();
        }
    }

    private void loadIdentity() {
        SharedPreferences preferences = getSharedPreferences("bitchatter_identity", MODE_PRIVATE);
        localId = preferences.getString("local_id", null);
        localName = preferences.getString("local_name", null);
        if (localId == null || localName == null) {
            localId = UUID.randomUUID().toString();
            localName = "bc-" + localId.substring(0, 4).toLowerCase(Locale.US);
            preferences.edit()
                    .putString("local_id", localId)
                    .putString("local_name", localName)
                    .apply();
        }
    }

    private void buildLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18), dp(16), dp(18), dp(12));
        header.setBackgroundColor(COLOR_HEADER);

        titleText = new TextView(this);
        titleText.setText(APP_NAME);
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(26f);
        titleText.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(titleText, matchWrap());

        subtitleText = new TextView(this);
        subtitleText.setTextColor(Color.rgb(220, 245, 238));
        subtitleText.setTextSize(13f);
        header.addView(subtitleText, matchWrap());

        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
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
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(dp(6), dp(6), dp(6), dp(6));
        bottomBar.setBackgroundColor(Color.WHITE);
        root.addView(bottomBar, matchWrap());

        setContentView(root);
        refreshHeader();
    }

    private void refreshHeader() {
        String bluetoothState = bluetoothAdapter == null
                ? "Bluetooth yok"
                : bluetoothAdapter.isEnabled() ? "Bluetooth açık" : "Bluetooth kapalı";
        subtitleText.setText(localName + " • ID " + shortId(localId) + " • " + bluetoothState);
    }

    private void rebuildBottomBar() {
        bottomBar.removeAllViews();
        bottomBar.addView(navButton("Yakındakiler", "nearby"), weightWrap());
        bottomBar.addView(navButton("İstekler" + (pendingInvites.isEmpty() ? "" : " (" + pendingInvites.size() + ")"), "requests"), weightWrap());
        bottomBar.addView(navButton("Sohbet", "chat"), weightWrap());
    }

    private Button navButton(String label, final String screen) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextColor(screen.equals(currentScreen) ? Color.WHITE : COLOR_HEADER_DARK);
        button.setBackground(rounded(screen.equals(currentScreen) ? COLOR_HEADER : Color.TRANSPARENT, dp(18)));
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
        } else {
            showNearbyScreen();
        }
    }

    private void showNearbyScreen() {
        currentScreen = "nearby";
        refreshHeader();
        rebuildBottomBar();
        content.removeAllViews();
        addHeroCard(
                "Yakındaki İnsanlar",
                "Bluetooth ile yakındaki bitchatter kullanıcılarını bul, önce istek gönder, kabul edilirse şifreli sohbet başlasın."
        );

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(primaryButton("Bluetooth Aç", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enableBluetooth();
            }
        }), weightWrap());
        actions.addView(primaryButton("Görünür Ol", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                makeDiscoverable();
            }
        }), weightWrap());
        content.addView(actions, matchWrap());

        Button scan = primaryButton("Yakındakileri Tara", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDiscovery();
            }
        });
        content.addView(scan, matchWrap());

        addBondedDevices();
        if (nearbyDevices.isEmpty()) {
            addEmptyState("Henüz kimse yok", "Bluetooth'u aç, görünür ol ve yakındakileri tara. Eşleşmiş cihazlar da burada görünür.");
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
        addHeroCard("Mesaj İstekleri", "Güvenlik için sohbet sadece karşılıklı onaydan sonra açılır.");

        if (pendingInvites.isEmpty()) {
            addEmptyState("Bekleyen istek yok", "Biri sana istek gönderdiğinde burada kabul veya reddet seçenekleri çıkacak.");
            return;
        }

        List<PendingInvite> snapshot = new ArrayList<>(pendingInvites);
        for (final PendingInvite invite : snapshot) {
            LinearLayout card = cardLayout();
            addCardTitle(card, invite.remoteName);
            addCardText(card, "ID: " + shortId(invite.remoteId));
            addCardText(card, "Güvenlik kodu: " + invite.fingerprint);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(primaryButton("Kabul Et", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    acceptInvite(invite);
                }
            }), weightWrap());
            actions.addView(secondaryButton("Reddet", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    rejectInvite(invite);
                }
            }), weightWrap());
            card.addView(actions, matchWrap());
            content.addView(card, matchWrapWithMargins(0, 0, 0, dp(10)));
        }
    }

    private void showChatScreen() {
        currentScreen = "chat";
        rebuildBottomBar();
        content.removeAllViews();

        if (activeSession == null) {
            addEmptyState("Aktif sohbet yok", "Yakındakiler sekmesinden istek gönder veya gelen isteği kabul et.");
            return;
        }

        addChatHeader();
        for (ChatLine line : chatLines) {
            addBubble(line);
        }
        addComposer();
    }

    private void addChatHeader() {
        LinearLayout card = cardLayout();
        addCardTitle(card, activeSession.remoteName);
        addCardText(card, "Şifreli bağlantı • ID " + shortId(activeSession.remoteId));
        addCardText(card, "Güvenlik kodu: " + activeSession.channel.fingerprint);
        content.addView(card, matchWrapWithMargins(0, 0, 0, dp(10)));
    }

    private void addComposer() {
        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(0, dp(8), 0, 0);

        final EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(3);
        input.setHint("Mesaj yaz");
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_MESSAGE_LENGTH)});
        input.setBackground(rounded(Color.WHITE, dp(22)));
        input.setPadding(dp(14), dp(8), dp(14), dp(8));
        composer.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button send = new Button(this);
        send.setText("➤");
        send.setTextSize(18f);
        send.setTextColor(Color.WHITE);
        send.setBackground(rounded(COLOR_HEADER, dp(22)));
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
        String name = safeDeviceName(device);
        addCardTitle(card, name);
        addCardText(card, deviceAddress(device));
        addCardText(card, isBonded(device) ? "Eşleşmiş cihaz • daha hızlı bağlanır" : "Yeni cihaz • önce Bluetooth eşleşmesi gerekebilir");
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
        card.setBackground(rounded(COLOR_HEADER, dp(18)));
        addCardTitle(card, title, Color.WHITE);
        TextView bodyText = new TextView(this);
        bodyText.setText(body);
        bodyText.setTextColor(Color.rgb(220, 245, 238));
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
        text.setTextColor(COLOR_TEXT_MUTED);
        text.setGravity(Gravity.CENTER);
        text.setTextSize(15f);
        card.addView(text, matchWrap());
        content.addView(card, matchWrap());
    }

    private LinearLayout cardLayout() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(COLOR_CARD, dp(16)));
        return card;
    }

    private void addCardTitle(LinearLayout card, String text) {
        addCardTitle(card, text, Color.rgb(30, 30, 30));
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
        line.setTextColor(COLOR_TEXT_MUTED);
        line.setTextSize(14f);
        line.setPadding(0, dp(4), 0, dp(4));
        card.addView(line, matchWrap());
    }

    private void addBubble(ChatLine line) {
        FrameLayout row = new FrameLayout(this);
        TextView bubble = new TextView(this);
        bubble.setText(line.text);
        bubble.setTextSize(16f);
        bubble.setTextColor(Color.rgb(28, 28, 28));
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
        bubble.setBackground(rounded(line.mine ? COLOR_BUBBLE_ME : COLOR_BUBBLE_REMOTE, dp(14)));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                line.mine ? Gravity.END : Gravity.START
        );
        params.setMargins(line.mine ? dp(64) : 0, dp(4), line.mine ? 0 : dp(64), dp(4));
        row.addView(bubble, params);
        content.addView(row, matchWrap());
    }

    private Button primaryButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setBackground(rounded(COLOR_ACCENT, dp(14)));
        button.setOnClickListener(listener);
        return button;
    }

    private Button secondaryButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(COLOR_HEADER_DARK);
        button.setBackground(rounded(Color.rgb(232, 245, 241), dp(14)));
        button.setOnClickListener(listener);
        return button;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
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
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), PERMISSION_REQUEST);
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
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
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
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, DISCOVERABLE_SECONDS);
        startActivity(discoverableIntent);
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
        addBondedDevices();
        stopDiscovery();
        boolean started = bluetoothAdapter.startDiscovery();
        updateStatus(started ? "Hızlı tarama başladı..." : "Tarama başlatılamadı");
        showNearbyScreen();
    }

    private void stopDiscovery() {
        if (bluetoothAdapter != null && hasScanPermission() && bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    private void addBondedDevices() {
        if (!ensureBluetoothAvailable() || !hasConnectPermission()) {
            return;
        }
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        if (bondedDevices == null) {
            return;
        }
        for (BluetoothDevice device : bondedDevices) {
            rememberDevice(device);
        }
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
        serverRunning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, CHAT_UUID);
                    updateStatus("Güvenli bağlantılar dinleniyor");
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
                    updateStatus("Gelen bağlantı güvenli kurulamadı");
                }
            }
        }, "bitchatter-incoming").start();
    }

    private void sendInvite(final BluetoothDevice device) {
        if (!ensureBluetoothAvailable() || !hasConnectPermission()) {
            requestNeededPermissions();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            enableBluetooth();
            return;
        }
        updateStatus(safeDeviceName(device) + " için istek gönderiliyor...");
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
                chatLines.add(ChatLine.system("Güvenli sohbet başladı • kod " + session.channel.fingerprint));
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
            chatLines.add(ChatLine.mine(message));
            showChatScreen();
        } catch (IOException exception) {
            updateStatus("Mesaj gönderilemedi");
            onConnectionClosed();
        }
    }

    private void onRemoteMessage(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                chatLines.add(ChatLine.remote(message));
                if ("chat".equals(currentScreen)) {
                    showChatScreen();
                }
            }
        });
    }

    private void onConnectionClosed() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (activeSession != null) {
                    activeSession.close();
                    activeSession = null;
                }
                chatLines.add(ChatLine.system("Bağlantı kapandı"));
                updateStatus("Sohbet bağlantısı kapandı");
                if ("chat".equals(currentScreen)) {
                    showChatScreen();
                }
            }
        });
    }

    private void closeActiveSession() {
        if (activeSession != null) {
            activeSession.close();
            activeSession = null;
        }
    }

    private String safeDeviceName(BluetoothDevice device) {
        String name = null;
        if (hasConnectPermission()) {
            name = device.getName();
        }
        if (name == null || name.trim().isEmpty()) {
            return "Bilinmeyen kişi";
        }
        return name;
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

        ChatLine(String text, boolean mine) {
            this.text = text;
            this.mine = mine;
        }

        static ChatLine mine(String text) {
            return new ChatLine(text, true);
        }

        static ChatLine remote(String text) {
            return new ChatLine(text, false);
        }

        static ChatLine system(String text) {
            return new ChatLine("🔒 " + text, false);
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
                            if ("MSG".equals(message.type)) {
                                onRemoteMessage(message.body);
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

        static SecureChannel client(BluetoothSocket socket, String localId, String localName)
                throws IOException, GeneralSecurityException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            KeyPair pair = newKeyPair();
            writer.println(ProtocolMessage.hello(localId, localName, pair.getPublic().getEncoded()));
            ProtocolMessage response = ProtocolMessage.parse(reader.readLine());
            if (response == null || !"HELLO".equals(response.type)) {
                throw new IOException("Güvenli el sıkışma başarısız");
            }
            return fromHandshake(reader, writer, pair, response.publicKeyBytes(), localId, response.senderId);
        }

        static SecureChannel server(BluetoothSocket socket, String localId, String localName)
                throws IOException, GeneralSecurityException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            ProtocolMessage hello = ProtocolMessage.parse(reader.readLine());
            if (hello == null || !"HELLO".equals(hello.type)) {
                throw new IOException("Güvenli el sıkışma başarısız");
            }
            KeyPair pair = newKeyPair();
            writer.println(ProtocolMessage.hello(localId, localName, pair.getPublic().getEncoded()));
            return fromHandshake(reader, writer, pair, hello.publicKeyBytes(), hello.senderId, localId);
        }

        private static SecureChannel fromHandshake(
                BufferedReader reader,
                PrintWriter writer,
                KeyPair pair,
                byte[] remotePublicBytes,
                String firstId,
                String secondId
        ) throws GeneralSecurityException {
            PublicKey remotePublic = KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(remotePublicBytes));
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(pair.getPrivate());
            agreement.doPhase(remotePublic, true);
            byte[] sharedSecret = agreement.generateSecret();
            byte[] salt = (firstId + "|" + secondId).getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = hkdf(sharedSecret, salt, "bitchatter-v1".getBytes(StandardCharsets.UTF_8), 32);
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
            return "HELLO|" + senderId + "|" + encode(senderName) + "|" + b64(publicKeyBytes);
        }

        static ProtocolMessage identity(String type, String senderId, String senderName) {
            return new ProtocolMessage(type, senderId, senderName, "");
        }

        static ProtocolMessage chat(String senderId, String senderName, String body) {
            return new ProtocolMessage("MSG", senderId, senderName, body);
        }

        String serialize() {
            return type + "|" + senderId + "|" + encode(senderName) + "|" + encode(body);
        }

        byte[] publicKeyBytes() {
            return fromB64(body);
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
