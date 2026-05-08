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

import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private final StringBuilder logBuilder = new StringBuilder();
    private final Random random = new Random();
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 36, 36, 36);
        root.setBackgroundColor(Color.parseColor("#0D1117"));

        TextView title = new TextView(this);
        title.setText("scp:ultimate Rp game");
        title.setTextSize(26f);
        title.setTextColor(Color.parseColor("#E6EDF3"));
        title.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView desc = new TextView(this);
        desc.setText("AI görev akışı: Hapishane > Personel seçimi > Asker sevki > Hazırlık > Cage refakati");
        desc.setTextColor(Color.parseColor("#9FB0C0"));
        desc.setPadding(0, 12, 0, 22);

        Spinner blockSpinner = new Spinner(this);
        String[] prisonBlocks = {"Block-A (D-Class)", "Block-B (Araştırma Tutuklusu)", "Block-C (Riskli)", "Block-D (İtaatsiz)"};
        blockSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, prisonBlocks));

        Spinner personnelSpinner = new Spinner(this);
        String[] personnel = {"Tek D-Class", "Araştırmacı Eşliği", "Silahlı Güvenlik + D-Class", "Medikal + Güvenlik"};
        personnelSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, personnel));

        Spinner squadSpinner = new Spinner(this);
        String[] squads = {"MTF Epsilon-11", "Site Güvenlik Alpha", "Rapid Response Unit", "Ağır Zırhlı Escort"};
        squadSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, squads));

        Spinner scpSpinner = new Spinner(this);
        String[] scps = {"SCP-173", "SCP-096", "SCP-049"};
        scpSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, scps));

        Button startMission = new Button(this);
        startMission.setText("Operasyonu Başlat");

        Button lockdown = new Button(this);
        lockdown.setText("Acil Kilitlenme");

        logView = new TextView(this);
        logView.setTextColor(Color.parseColor("#D2DCE7"));
        logView.setBackgroundColor(Color.parseColor("#161B22"));
        logView.setPadding(20, 20, 20, 20);
        logView.setMinHeight(900);
        logView.setMovementMethod(new ScrollingMovementMethod());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView);

        startMission.setOnClickListener(v -> {
            String block = blockSpinner.getSelectedItem().toString();
            String selectedPersonnel = personnelSpinner.getSelectedItem().toString();
            String squad = squadSpinner.getSelectedItem().toString();
            String scp = scpSpinner.getSelectedItem().toString();

            appendLog(runMission(block, selectedPersonnel, squad, scp));
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        });

        lockdown.setOnClickListener(v -> {
            appendLog("[ALARM] KIRMIZI KOD: Tüm kapılar mühürlendi, blast door sistemi aktif, MTF takviyesi çağrıldı.");
            scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
        });

        root.addView(title);
        root.addView(desc);
        root.addView(blockSpinner);
        root.addView(personnelSpinner);
        root.addView(squadSpinner);
        root.addView(scpSpinner);
        root.addView(startMission);
        root.addView(lockdown);
        root.addView(scroll);

        setContentView(root);

        appendLog("[SITE MAP] Foundation Site-TR01: Tek katlı geniş plan | Hapishane, hazırlık odası, test koridoru, cage bloğu.");
        appendLog("[AI C2] Operasyon merkezi hazır. Blok/personel/asker/SCP seç ve operasyonu başlat.");
    }

    private String runMission(String block, String personnel, String squad, String scp) {
        int discipline = 60 + random.nextInt(41);
        int stress = 30 + random.nextInt(61);
        int readiness = calculateReadiness(personnel, squad, stress);
        String escortRoute = buildRouteNarrative(scp, squad);
        String reaction = buildScpReaction(scp, personnel, readiness);
        String outcome = buildOutcome(scp, readiness, discipline, stress);

        return String.format(Locale.ROOT,
                "[OPERASYON] Kaynak: %s\n" +
                        "[FAZ-1 Hapishane] %s personeli seçildi. Disiplin skoru: %d/100\n" +
                        "[FAZ-2 Sevkiyat] %s ekibi deneği teslim aldı, kelepçe ve biyometrik doğrulama tamam.\n" +
                        "[FAZ-3 Hazırlık] Kalp ritmi, sedasyon protokolü ve kameralar senkronlandı. Hazırlık: %d/100\n" +
                        "[FAZ-4 Refakat] %s\n" +
                        "[FAZ-5 Cage] Hedef: %s | %s\n" +
                        "[SONUÇ] %s\n" +
                        "[RP AKSİYON] Yeni turda farklı ekip veya blok seçip farklı taktik deneyebilirsin.\n",
                block,
                personnel,
                discipline,
                squad,
                readiness,
                escortRoute,
                scp,
                reaction,
                outcome
        );
    }

    private int calculateReadiness(String personnel, String squad, int stress) {
        int base = 55;
        if (personnel.contains("Silahlı")) {
            base += 15;
        }
        if (personnel.contains("Medikal")) {
            base += 10;
        }
        if (squad.contains("MTF") || squad.contains("Ağır")) {
            base += 12;
        }
        base -= stress / 8;
        return Math.max(35, Math.min(98, base));
    }

    private String buildRouteNarrative(String scp, String squad) {
        if ("SCP-173".equals(scp)) {
            return squad + " timi göz kırpma senkronu ile üçlü görüş hattı kurup deneği 173 cage koridoruna taşıdı.";
        }
        if ("SCP-096".equals(scp)) {
            return squad + " timi kask vizörlerini kapatıp dolaylı kamera beslemesiyle 096 hattında ilerledi.";
        }
        return squad + " timi medikal bariyer arkasında ilerleyip 049 ile sözlü temas protokolünü korudu.";
    }

    private String buildScpReaction(String scp, String personnel, int readiness) {
        if ("SCP-173".equals(scp)) {
            if (readiness >= 75) {
                return "Göz teması kaybı yaşanmadı; 173 hızlı hamle fırsatı bulamadı.";
            }
            return "Ekip senkronu kısa süre bozuldu; 173 ani pozisyon değiştirerek saldırı penceresi oluşturdu.";
        }
        if ("SCP-096".equals(scp)) {
            if (personnel.contains("Araştırmacı") && readiness < 70) {
                return "Protokol ihlali riski oluştu, yüz görme alarmı eşiğe yaklaştı.";
            }
            return "Yüz teması engellendi; 096 pasif-ajite seviyede tutuldu.";
        }
        if (readiness >= 72) {
            return "049 kontrollü diyaloga ikna oldu; temas mesafesi korundu.";
        }
        return "049, 'tedavi' isteğini artırdı ve yakın temas denemesi yaptı.";
    }

    private String buildOutcome(String scp, int readiness, int discipline, int stress) {
        int score = readiness + (discipline / 3) - (stress / 4);
        if ("SCP-096".equals(scp)) {
            score -= 4;
        }
        if (score >= 85) {
            return "MÜKEMMEL: Operasyon kayıpsız tamamlandı, SCP containment stabil.";
        }
        if (score >= 65) {
            return "BAŞARILI: Küçük sapmalar yönetildi, containment devam ediyor.";
        }
        return "KRİTİK: Ekip geri çekildi, acil kilitlenme ve ikinci tim ihtiyacı doğdu.";
    }

    private void appendLog(String line) {
        if (logBuilder.length() > 0) {
            logBuilder.append("\n\n");
        }
        logBuilder.append(line);
        logView.setText(logBuilder.toString());
    }
}
