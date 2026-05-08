package com.example.helloworld;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private final StringBuilder logBuilder = new StringBuilder();
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 36, 36, 36);
        root.setBackgroundColor(Color.parseColor("#101418"));

        TextView title = new TextView(this);
        title.setText("scp:ultimate Rp game");
        title.setTextSize(28f);
        title.setTextColor(Color.parseColor("#E6EDF3"));
        title.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView desc = new TextView(this);
        desc.setText("Foundation tek katlı tesis simülasyonu • SCP deneyi • RP günlükleri");
        desc.setTextColor(Color.parseColor("#9FB0C0"));
        desc.setPadding(0, 12, 0, 24);

        Spinner scpSpinner = new Spinner(this);
        String[] scps = {"SCP-173", "SCP-096", "SCP-049"};
        scpSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, scps));

        Spinner subjectSpinner = new Spinner(this);
        String[] subjects = {"D-Class", "Araştırmacı", "Silahlı Güvenlik", "Silahsız Sivil"};
        subjectSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, subjects));

        Button runTest = new Button(this);
        runTest.setText("Deneyi Başlat");

        Button lockdown = new Button(this);
        lockdown.setText("Acil Kilitlenme");

        logView = new TextView(this);
        logView.setTextColor(Color.parseColor("#D2DCE7"));
        logView.setBackgroundColor(Color.parseColor("#1A222D"));
        logView.setPadding(20, 20, 20, 20);
        logView.setMinHeight(900);
        logView.setMovementMethod(new ScrollingMovementMethod());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView);

        runTest.setOnClickListener(v -> {
            String scp = scpSpinner.getSelectedItem().toString();
            String subject = subjectSpinner.getSelectedItem().toString();
            appendLog(generateScenario(scp, subject));
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        });

        lockdown.setOnClickListener(v -> {
            appendLog("[ALARM] Tesis çapında kilitlenme başlatıldı. Koridor kapıları mühürlendi, MTF çağrıldı.");
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        });

        root.addView(title);
        root.addView(desc);
        root.addView(scpSpinner);
        root.addView(subjectSpinner);
        root.addView(runTest);
        root.addView(lockdown);
        root.addView(scroll);

        setContentView(root);

        appendLog("[GÜNLÜK] Site-TR tek katlı geniş foundation tesisi aktif. Cage blokları hazır.");
        appendLog("[IPUCU] SCP + denek seç ve Deneyi Başlat'a bas. Sonuçlar lore temelli RP formatındadır.");
    }

    private String generateScenario(String scp, String subject) {
        Map<String, String> reactions = new HashMap<>();

        reactions.put("SCP-173:D-Class", "D-Class göz teması bozduğu an SCP-173 saniyeler içinde boynunu kırmaya çalışır.");
        reactions.put("SCP-173:Araştırmacı", "Araştırmacı prosedür gereği üç kişilik ekip ister; tek başınaysa ölüm riski kritik.");
        reactions.put("SCP-173:Silahlı Güvenlik", "Güvenlik personeli sürekli göz temasıyla geri çekilir; göz kırpma senkronu uygulanır.");
        reactions.put("SCP-173:Silahsız Sivil", "Sivil panikleyip kaçmaya çalışır, görsel temas koptuğunda olay ölümcül olur.");

        reactions.put("SCP-096:D-Class", "D-Class yanlışlıkla yüzünü görürse SCP-096 mutlak takip moduna geçer.");
        reactions.put("SCP-096:Araştırmacı", "Araştırmacı optik cihaz yasağını ihlal ederse gecikmeli fakat kesin bir saldırı tetiklenir.");
        reactions.put("SCP-096:Silahlı Güvenlik", "Güvenlik ateşi SCP-096'yi durduramaz; öncelik tahliye ve izolasyondur.");
        reactions.put("SCP-096:Silahsız Sivil", "Sivilin kısa bir bakışı bile küresel ölçekli takip davranışı doğurur.");

        reactions.put("SCP-049:D-Class", "SCP-049 deneği 'hastalıklı' ilan ederse temas edip öldürmeye çalışır.");
        reactions.put("SCP-049:Araştırmacı", "Araştırmacıyla sakin konuşabilir; yine de ani fiziksel temas ölümcüldür.");
        reactions.put("SCP-049:Silahlı Güvenlik", "Silahlı ekip yaklaşınca SCP-049 iş birliğini kesip agresifleşebilir.");
        reactions.put("SCP-049:Silahsız Sivil", "Sivil üzerinde uzun gözlem yapar, ardından 'tedavi' gerekçesiyle yaklaşır.");

        String key = String.format(Locale.ROOT, "%s:%s", scp, subject);
        String outcome = reactions.getOrDefault(key, "Beklenmeyen anomali: tepki kaydı bulunamadı.");

        return String.format(Locale.ROOT,
                "[TEST] Cage: %s | Denek: %s\n[SONUÇ] %s\n[NOT] RP seçeneği: iletişim, geri çekilme, sedasyon planı tartışılabilir.\n",
                scp, subject, outcome);
    }

    private void appendLog(String line) {
        if (logBuilder.length() > 0) {
            logBuilder.append("\n\n");
        }
        logBuilder.append(line);
        logView.setText(logBuilder.toString());
    }
}
