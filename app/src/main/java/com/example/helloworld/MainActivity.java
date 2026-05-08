package com.example.helloworld;

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
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final String APP_NAME = "MeshChat";
    private static final UUID CHAT_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final int PERMISSION_REQUEST = 101;
    private static final int DISCOVERABLE_SECONDS = 300;

    private final Map<String, BluetoothDevice> nearbyDevices = new LinkedHashMap<>();
    private final List<PendingInvite> pendingInvites = new ArrayList<>();
    private final List<String> chatLines = new ArrayList<>();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothServerSocket serverSocket;
    private ChatConnection activeConnection;
    private LinearLayout root;
    private LinearLayout content;
    private TextView statusText;
    private TextView identityText;
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
                if (device != null && hasConnectPermission()) {
                    String address = device.getAddress();
                    if (address != null && !nearbyDevices.containsKey(address)) {
                        nearbyDevices.put(address, device);
                        if ("nearby".equals(currentScreen)) {
                            showNearbyScreen();
                        }
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                updateStatus("Tarama tamamlandı. Bulunan cihaz: " + nearbyDevices.size());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadIdentity();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        buildRootLayout();
        requestNeededPermissions();
        registerBluetoothReceiver();
        startBluetoothServer();
        showNearbyScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBluetoothServer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiverRegistered) {
            unregisterReceiver(bluetoothReceiver);
        }
        stopBluetoothServer();
        closeActiveConnection();
    }

    private void loadIdentity() {
        SharedPreferences preferences = getSharedPreferences("meshchat_identity", MODE_PRIVATE);
        localId = preferences.getString("local_id", null);
        localName = preferences.getString("local_name", null);
        if (localId == null || localName == null) {
            localId = UUID.randomUUID().toString();
            localName = "Mesh-" + localId.substring(0, 4).toUpperCase(Locale.US);
            preferences.edit()
                    .putString("local_id", localId)
                    .putString("local_name", localName)
                    .apply();
        }
    }

    private void buildRootLayout() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText(APP_NAME);
        title.setTextSize(28f);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWidthWrapHeight());

        identityText = new TextView(this);
        identityText.setText("Ad: " + localName + "\nUnique ID: " + localId);
        identityText.setTextSize(14f);
        root.addView(identityText, fullWidthWrapHeight());

        statusText = new TextView(this);
        statusText.setTextSize(14f);
        statusText.setText("Bluetooth hazır bekleniyor...");
        root.addView(statusText, fullWidthWrapHeight());

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.addView(tabButton("Yakındaki İnsanlar", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showNearbyScreen();
            }
        }), equalWeightWrapHeight());
        tabs.addView(tabButton("İstekler", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showRequestsScreen();
            }
        }), equalWeightWrapHeight());
        tabs.addView(tabButton("Sohbet", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showChatScreen();
            }
        }), equalWeightWrapHeight());
        root.addView(tabs, fullWidthWrapHeight());

        ScrollView scrollView = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(content);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);
    }

    private Button tabButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams fullWidthWrapHeight() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams equalWeightWrapHeight() {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
    }

    private void showNearbyScreen() {
        currentScreen = "nearby";
        content.removeAllViews();
        addParagraph("Yakındaki Bluetooth cihazlarını tara. Karşı taraf MeshChat'i açmış ve Bluetooth'u görünür olmalı.");

        Button enableButton = new Button(this);
        enableButton.setAllCaps(false);
        enableButton.setText("Bluetooth'u Aç");
        enableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enableBluetooth();
            }
        });
        content.addView(enableButton, fullWidthWrapHeight());

        Button discoverableButton = new Button(this);
        discoverableButton.setAllCaps(false);
        discoverableButton.setText("Kendimi 5 Dakika Görünür Yap");
        discoverableButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                makeDiscoverable();
            }
        });
        content.addView(discoverableButton, fullWidthWrapHeight());

        Button scanButton = new Button(this);
        scanButton.setAllCaps(false);
        scanButton.setText("Yakındaki İnsanları Tara");
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDiscovery();
            }
        });
        content.addView(scanButton, fullWidthWrapHeight());

        if (nearbyDevices.isEmpty()) {
            addParagraph("Henüz cihaz bulunmadı.");
            return;
        }

        addSectionTitle("Bulunan Cihazlar");
        for (BluetoothDevice device : nearbyDevices.values()) {
            addDeviceButton(device);
        }
    }

    private void showRequestsScreen() {
        currentScreen = "requests";
        content.removeAllViews();
        addParagraph("Gelen mesajlaşma istekleri burada görünür. Kabul edersen sohbet açılır.");
        if (pendingInvites.isEmpty()) {
            addParagraph("Bekleyen istek yok.");
            return;
        }

        List<PendingInvite> snapshot = new ArrayList<>(pendingInvites);
        for (final PendingInvite invite : snapshot) {
            addSectionTitle(invite.remoteName);
            addParagraph("Unique ID: " + invite.remoteId);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);

            Button accept = new Button(this);
            accept.setAllCaps(false);
            accept.setText("Kabul Et");
            accept.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    acceptInvite(invite);
                }
            });
            actions.addView(accept, equalWeightWrapHeight());

            Button reject = new Button(this);
            reject.setAllCaps(false);
            reject.setText("Reddet");
            reject.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    rejectInvite(invite);
                }
            });
            actions.addView(reject, equalWeightWrapHeight());
            content.addView(actions, fullWidthWrapHeight());
        }
    }

    private void showChatScreen() {
        currentScreen = "chat";
        content.removeAllViews();

        if (activeConnection == null) {
            addParagraph("Aktif sohbet yok. Önce yakındaki insanlardan birine istek gönder veya gelen isteği kabul et.");
            return;
        }

        addSectionTitle("Sohbet: " + activeConnection.remoteName);
        addParagraph("Karşı taraf ID: " + activeConnection.remoteId);

        for (String line : chatLines) {
            addParagraph(line);
        }

        final EditText messageInput = new EditText(this);
        messageInput.setHint("Mesaj yaz...");
        content.addView(messageInput, fullWidthWrapHeight());

        Button send = new Button(this);
        send.setAllCaps(false);
        send.setText("Gönder");
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String message = messageInput.getText().toString().trim();
                if (!message.isEmpty()) {
                    sendChatMessage(message);
                    messageInput.setText("");
                }
            }
        });
        content.addView(send, fullWidthWrapHeight());
    }

    private void addDeviceButton(final BluetoothDevice device) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setText(deviceLabel(device) + "\nİstek göndermek için dokun");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendInvite(device);
            }
        });
        content.addView(button, fullWidthWrapHeight());
    }

    private void addSectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(20f);
        title.setPadding(0, 24, 0, 8);
        content.addView(title, fullWidthWrapHeight());
    }

    private void addParagraph(String text) {
        TextView paragraph = new TextView(this);
        paragraph.setText(text);
        paragraph.setTextSize(16f);
        paragraph.setPadding(0, 8, 0, 8);
        content.addView(paragraph, fullWidthWrapHeight());
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List<String> permissions = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (!permissions.isEmpty()) {
                requestPermissions(permissions.toArray(new String[0]), PERMISSION_REQUEST);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bluetoothReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void enableBluetooth() {
        if (!ensureBluetoothAvailable() || !hasConnectPermission()) {
            requestNeededPermissions();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } else {
            toast("Bluetooth zaten açık.");
        }
    }

    private void makeDiscoverable() {
        if (!ensureBluetoothAvailable() || !hasConnectPermission()) {
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
            toast("Bluetooth izinleri gerekli.");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            enableBluetooth();
            return;
        }
        nearbyDevices.clear();
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        boolean started = bluetoothAdapter.startDiscovery();
        updateStatus(started ? "Yakındaki insanlar taranıyor..." : "Tarama başlatılamadı.");
        showNearbyScreen();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            startBluetoothServer();
            if ("nearby".equals(currentScreen)) {
                showNearbyScreen();
            }
        }
    }

    private void startBluetoothServer() {
        if (serverRunning || !ensureBluetoothAvailable() || !hasConnectPermission()) {
            return;
        }
        serverRunning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(APP_NAME, CHAT_UUID);
                    while (serverRunning) {
                        BluetoothSocket socket = serverSocket.accept();
                        handleIncomingSocket(socket);
                    }
                } catch (IOException exception) {
                    if (serverRunning) {
                        updateStatus("Bluetooth sunucusu durdu: " + exception.getMessage());
                    }
                    serverRunning = false;
                }
            }
        }).start();
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
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                    String firstLine = reader.readLine();
                    ProtocolMessage message = ProtocolMessage.parse(firstLine);
                    if (message == null || !"INVITE".equals(message.type)) {
                        socket.close();
                        return;
                    }
                    final PendingInvite invite = new PendingInvite(socket, reader, writer, message.senderId, message.senderName);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            pendingInvites.add(invite);
                            updateStatus(invite.remoteName + " mesajlaşma isteği gönderdi.");
                            toast("Yeni istek: " + invite.remoteName);
                            if ("requests".equals(currentScreen)) {
                                showRequestsScreen();
                            }
                        }
                    });
                } catch (IOException exception) {
                    closeQuietly(socket);
                }
            }
        }).start();
    }

    private void sendInvite(final BluetoothDevice device) {
        if (!ensureBluetoothAvailable() || !hasConnectPermission()) {
            requestNeededPermissions();
            return;
        }
        updateStatus(deviceLabel(device) + " için istek gönderiliyor...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                BluetoothSocket socket = null;
                try {
                    if (hasScanPermission() && bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                    }
                    socket = device.createRfcommSocketToServiceRecord(CHAT_UUID);
                    socket.connect();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                    writer.println(ProtocolMessage.identity("INVITE", localId, localName));
                    ProtocolMessage response = ProtocolMessage.parse(reader.readLine());
                    if (response != null && "ACCEPT".equals(response.type)) {
                        startChatConnection(new ChatConnection(socket, reader, writer, response.senderId, response.senderName));
                    } else {
                        closeQuietly(socket);
                        updateStatus("İstek reddedildi veya cevap alınamadı.");
                    }
                } catch (IOException exception) {
                    closeQuietly(socket);
                    updateStatus("Bağlantı kurulamadı: " + exception.getMessage());
                }
            }
        }).start();
    }

    private void acceptInvite(final PendingInvite invite) {
        pendingInvites.remove(invite);
        invite.writer.println(ProtocolMessage.identity("ACCEPT", localId, localName));
        startChatConnection(new ChatConnection(invite.socket, invite.reader, invite.writer, invite.remoteId, invite.remoteName));
    }

    private void rejectInvite(PendingInvite invite) {
        pendingInvites.remove(invite);
        invite.writer.println(ProtocolMessage.identity("REJECT", localId, localName));
        closeQuietly(invite.socket);
        showRequestsScreen();
    }

    private void startChatConnection(final ChatConnection connection) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                closeActiveConnection();
                activeConnection = connection;
                chatLines.clear();
                chatLines.add("Sistem: " + connection.remoteName + " ile sohbet başladı.");
                updateStatus("Aktif sohbet: " + connection.remoteName);
                showChatScreen();
            }
        });
        connection.startListening();
    }

    private void sendChatMessage(String message) {
        ChatConnection connection = activeConnection;
        if (connection == null) {
            return;
        }
        connection.writer.println(ProtocolMessage.chat(localId, localName, message));
        chatLines.add(localName + ": " + message);
        showChatScreen();
    }

    private void onRemoteMessage(final String name, final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                chatLines.add(name + ": " + message);
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
                chatLines.add("Sistem: Bağlantı kapandı.");
                updateStatus("Sohbet bağlantısı kapandı.");
                if ("chat".equals(currentScreen)) {
                    showChatScreen();
                }
            }
        });
    }

    private void closeActiveConnection() {
        if (activeConnection != null) {
            activeConnection.close();
            activeConnection = null;
        }
    }

    private boolean ensureBluetoothAvailable() {
        if (bluetoothAdapter == null) {
            updateStatus("Bu cihaz Bluetooth desteklemiyor.");
            return false;
        }
        return true;
    }

    private String deviceLabel(BluetoothDevice device) {
        String name = null;
        if (hasConnectPermission()) {
            name = device.getName();
        }
        if (name == null || name.trim().isEmpty()) {
            name = "Bilinmeyen cihaz";
        }
        return name + "\n" + device.getAddress();
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

    private static class PendingInvite {
        final BluetoothSocket socket;
        final BufferedReader reader;
        final PrintWriter writer;
        final String remoteId;
        final String remoteName;

        PendingInvite(BluetoothSocket socket, BufferedReader reader, PrintWriter writer, String remoteId, String remoteName) {
            this.socket = socket;
            this.reader = reader;
            this.writer = writer;
            this.remoteId = remoteId;
            this.remoteName = remoteName;
        }
    }

    private class ChatConnection {
        final BluetoothSocket socket;
        final BufferedReader reader;
        final PrintWriter writer;
        final String remoteId;
        final String remoteName;
        volatile boolean open = true;

        ChatConnection(BluetoothSocket socket, BufferedReader reader, PrintWriter writer, String remoteId, String remoteName) {
            this.socket = socket;
            this.reader = reader;
            this.writer = writer;
            this.remoteId = remoteId;
            this.remoteName = remoteName;
        }

        void startListening() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (open) {
                        try {
                            ProtocolMessage message = ProtocolMessage.parse(reader.readLine());
                            if (message == null) {
                                break;
                            }
                            if ("MSG".equals(message.type)) {
                                onRemoteMessage(message.senderName, message.body);
                            }
                        } catch (IOException exception) {
                            break;
                        }
                    }
                    open = false;
                    onConnectionClosed();
                }
            }).start();
        }

        void close() {
            open = false;
            closeQuietly(socket);
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

        static String identity(String type, String senderId, String senderName) {
            return type + "|" + senderId + "|" + encode(senderName);
        }

        static String chat(String senderId, String senderName, String body) {
            return "MSG|" + senderId + "|" + encode(senderName) + "|" + encode(body);
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
                String body = "";
                if (parts.length == 4) {
                    body = decode(parts[3]);
                }
                return new ProtocolMessage(parts[0], parts[1], decode(parts[2]), body);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}
