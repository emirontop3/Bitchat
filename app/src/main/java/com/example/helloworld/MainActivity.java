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
    private final Random random = new Random();
    private final StringBuilder logBuilder = new StringBuilder();
    private TextView logView;

    private static final String[] PRISON_BLOCKS = {
            "A-Blok / D-Class Düşük Risk",
            "B-Blok / D-Class Orta Risk",
            "C-Blok / İtaatsiz Mahkum",
            "D-Blok / İzole Kanat"
    };

    private static final String[] PERSONNEL_PACKS = {
            "2 Güvenlik + 1 Araştırmacı",
            "3 Güvenlik + 1 Medikal",
            "1 MTF Lider + 2 Güvenlik",
            "2 MTF + 1 Medikal + 1 Araştırmacı"
    };

    private static final String[] ESCORT_SQUADS = {
            "Site Alpha Escort",
            "MTF Epsilon-11",
            "Rapid Response Team",
            "Ağır Zırhlı Takım"
    };

    private static final String[] SCPS = {
            "SCP-173", "SCP-096", "SCP-049"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 30, 30, 30);
        root.setBackgroundColor(Color.parseColor("#0D1117"));

        TextView title = new TextView(this);
        title.setText("Scp Rp");
        title.setTextSize(30f);
        title.setTextColor(Color.parseColor("#E6EDF3"));
        title.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView subtitle = new TextView(this);
        subtitle.setText("Sıfırdan kurulan görev sistemi: Hapishane > Ekip > Asker Sevki > Cage Refakati");
        subtitle.setTextColor(Color.parseColor("#9FB0C0"));
        subtitle.setPadding(0, 10, 0, 20);

        Spinner blockSpinner = createSpinner(PRISON_BLOCKS);
        Spinner personnelSpinner = createSpinner(PERSONNEL_PACKS);
        Spinner squadSpinner = createSpinner(ESCORT_SQUADS);
        Spinner scpSpinner = createSpinner(SCPS);

        Button startButton = new Button(this);
        startButton.setText("Görevi Başlat");

        Button lockdownButton = new Button(this);
        lockdownButton.setText("Acil Kilitlenme");

        Button resetButton = new Button(this);
        resetButton.setText("Log Sıfırla");

        logView = new TextView(this);
        logView.setTextColor(Color.parseColor("#D2DCE7"));
        logView.setBackgroundColor(Color.parseColor("#161B22"));
        logView.setPadding(20, 20, 20, 20);
        logView.setMinHeight(900);
        logView.setMovementMethod(new ScrollingMovementMethod());

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(logView);

        startButton.setOnClickListener(v -> {
            String block = blockSpinner.getSelectedItem().toString();
            String personnel = personnelSpinner.getSelectedItem().toString();
            String squad = squadSpinner.getSelectedItem().toString();
            String scp = scpSpinner.getSelectedItem().toString();
            appendLog(runMission(block, personnel, squad, scp));
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        });

        lockdownButton.setOnClickListener(v -> appendLog("[ALARM] RED LOCKDOWN aktif. Kapılar mühürlendi, takviye çağrıldı."));

        resetButton.setOnClickListener(v -> {
            logBuilder.setLength(0);
            logView.setText("");
            appendLog("[SİSTEM] Konsol sıfırlandı.");
        });

        root.addView(title);
        root.addView(subtitle);
        root.addView(blockSpinner);
        root.addView(personnelSpinner);
        root.addView(squadSpinner);
        root.addView(scpSpinner);
        root.addView(startButton);
        root.addView(lockdownButton);
        root.addView(resetButton);
        root.addView(scrollView);

        setContentView(root);

        appendLog("[MAP] Site-TR01 tek kat geniş plan aktif: Hapishane, Hazırlık, Koridor, Cage Bloğu.");
        appendLog("[INFO] Seçimleri yap ve görevi başlat. Her turda farklı sonuç alırsın.");
    }

    private Spinner createSpinner(String[] items) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        return spinner;
    }

    private String runMission(String block, String personnel, String squad, String scp) {
        int discipline = rand(60, 100);
        int stress = rand(25, 95);
        int prep = rand(55, 100);
        int readiness = clamp((discipline + prep) / 2 - (stress / 4), 30, 98);

        String escortLine = buildEscortLine(scp, squad);
        String reactionLine = buildReactionLine(scp, readiness);
        String resultLine = buildResultLine(readiness, stress);

        return String.format(Locale.ROOT,
                "[FAZ-1] %s noktasından denek çıkarıldı.\n" +
                        "[FAZ-2] Personel paketi: %s\n" +
                        "[FAZ-3] %s sevk edildi, ekipman ve kimlik doğrulama tamam.\n" +
                        "[FAZ-4] Hazırlık odası: sedasyon, nabız, kamera sync -> Prep %d/100\n" +
                        "[FAZ-5] %s\n" +
                        "[FAZ-6] Cage hedefi: %s | %s\n" +
                        "[METRİK] Disiplin=%d Stress=%d Readiness=%d\n" +
                        "[SONUÇ] %s\n",
                block, personnel, squad, prep, escortLine, scp, reactionLine, discipline, stress, readiness, resultLine);
    }

    private String buildEscortLine(String scp, String squad) {
        if ("SCP-173".equals(scp)) {
            return squad + " göz kırpma senkronu ile üçlü görüş hattı kurdu.";
        }
        if ("SCP-096".equals(scp)) {
            return squad + " vizör kapalı, dolaylı kamera feed ile ilerledi.";
        }
        return squad + " medikal bariyer ile 049 sözlü temas protokolünü sürdürdü.";
    }

    private String buildReactionLine(String scp, int readiness) {
        if ("SCP-173".equals(scp)) {
            return readiness >= 75
                    ? "173 saldırı penceresi bulamadı."
                    : "173 kısa senkron boşluğunda agresif hamle denedi.";
        }
        if ("SCP-096".equals(scp)) {
            return readiness >= 70
                    ? "096 yüz teması olmadan pasif-ajite kaldı."
                    : "096 için yüz görme alarmı kritik eşiğe yaklaştı.";
        }
        return readiness >= 72
                ? "049 kontrollü kaldı, temas mesafesi korundu."
                : "049 yakın temasla 'tedavi' girişimi denedi.";
    }

    private String buildResultLine(int readiness, int stress) {
        int score = readiness - (stress / 5);
        if (score >= 75) return "Mükemmel - kayıpsız operasyon.";
        if (score >= 58) return "Başarılı - küçük sapmalar yönetildi.";
        return "Kritik - geri çekilme ve ikinci tim gerekli.";
    }

    private int rand(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void appendLog(String line) {
        if (logBuilder.length() > 0) logBuilder.append("\n\n");
        logBuilder.append(line);
        logView.setText(logBuilder.toString());
    }
}
