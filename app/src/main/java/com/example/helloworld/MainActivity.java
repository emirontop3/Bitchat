package com.example.helloworld;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {
    private static final String[] SCPS = {
            "SCP-173", "SCP-096", "SCP-049", "SCP-106", "SCP-939", "SCP-682", "SCP-035", "SCP-999"
    };

    private SiteMapView siteMapView;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#090E16"));
        root.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText("Scp Rp");
        title.setTextSize(30f);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER_HORIZONTAL);

        Spinner scpSpinner = new Spinner(this);
        scpSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SCPS));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button start = new Button(this);
        start.setText("Operasyon Başlat");

        Button lockdown = new Button(this);
        lockdown.setText("Lockdown");

        Button reset = new Button(this);
        reset.setText("Reset");

        actions.addView(start, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(lockdown, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(reset, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        statusView = new TextView(this);
        statusView.setTextColor(Color.parseColor("#D7E2F2"));
        statusView.setText("2D Site haritası hazır: Ofis, Personel, Askeri Bölge, 3 Cage ve bağlantı yolları aktif.");
        statusView.setPadding(0, 10, 0, 10);

        siteMapView = new SiteMapView(this);

        start.setOnClickListener(v -> {
            String scp = scpSpinner.getSelectedItem().toString();
            siteMapView.startOperation(scp);
            statusView.setText("Görev başladı: asker ve personel deneği seçili cage'e götürüyor -> " + scp);
        });

        lockdown.setOnClickListener(v -> {
            siteMapView.setLockdown(true);
            statusView.setText("KIRMIZI ALARM: tüm halkalar kırmızı, geçiş kapıları kilitli.");
        });

        reset.setOnClickListener(v -> {
            siteMapView.resetSimulation();
            statusView.setText("Harita sıfırlandı, yeni senaryoya hazır.");
        });

        root.addView(title);
        root.addView(scpSpinner);
        root.addView(actions);
        root.addView(statusView);
        root.addView(siteMapView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);
    }

    private static class SiteMapView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler handler = new Handler(Looper.getMainLooper());

        // Units
        private float soldierX = 180;
        private float soldierY = 620;
        private float staffX = 140;
        private float staffY = 620;
        private float subjectX = 100;
        private float subjectY = 620;

        // Targets
        private float[] cageXs = {770, 900, 1030};
        private float cageY = 190;
        private int targetCage = 0;
        private String selectedScp = "SCP-173";

        private boolean running = false;
        private boolean lockdown = false;
        private int phase = 0;

        private final Runnable gameLoop = new Runnable() {
            @Override
            public void run() {
                if (running && !lockdown) {
                    updateMovement();
                    invalidate();
                    handler.postDelayed(this, 16);
                }
            }
        };

        public SiteMapView(Context context) {
            super(context);
            setBackgroundColor(Color.parseColor("#121C2B"));
        }

        void startOperation(String scp) {
            selectedScp = scp;
            lockdown = false;
            running = true;
            phase = 1;
            targetCage = pickCageForScp(scp);
            soldierX = 180;
            soldierY = 620;
            staffX = 140;
            staffY = 620;
            subjectX = 100;
            subjectY = 620;
            handler.removeCallbacks(gameLoop);
            handler.post(gameLoop);
            invalidate();
        }

        void setLockdown(boolean enabled) {
            lockdown = enabled;
            running = !enabled && running;
            if (enabled) {
                handler.removeCallbacks(gameLoop);
            } else if (running) {
                handler.post(gameLoop);
            }
            invalidate();
        }

        void resetSimulation() {
            running = false;
            lockdown = false;
            phase = 0;
            selectedScp = "SCP-173";
            soldierX = 180;
            soldierY = 620;
            staffX = 140;
            staffY = 620;
            subjectX = 100;
            subjectY = 620;
            handler.removeCallbacks(gameLoop);
            invalidate();
        }

        private int pickCageForScp(String scp) {
            if ("SCP-173".equals(scp) || "SCP-682".equals(scp)) return 0;
            if ("SCP-096".equals(scp) || "SCP-106".equals(scp) || "SCP-035".equals(scp)) return 1;
            return 2;
        }

        private void updateMovement() {
            float targetX = cageXs[targetCage] - 50;
            float speed = 2.6f;

            // phase1: from military to office corridor
            if (soldierX < 420 && phase == 1) {
                moveAll(speed, 0);
            } else if (soldierY > 430 && phase <= 2) {
                phase = 2;
                moveAll(0, -speed);
            } else if (soldierX < 700 && phase <= 3) {
                phase = 3;
                moveAll(speed, 0);
            } else if (soldierY > cageY + 20 && phase <= 4) {
                phase = 4;
                moveAll(0, -speed);
            } else if (soldierX < targetX && phase <= 5) {
                phase = 5;
                moveAll(speed, 0);
            } else {
                phase = 6;
                running = false;
                handler.removeCallbacks(gameLoop);
            }
        }

        private void moveAll(float dx, float dy) {
            soldierX += dx;
            soldierY += dy;
            staffX += dx;
            staffY += dy;
            subjectX += dx;
            subjectY += dy;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawRoadNetwork(canvas);
            drawBuildings(canvas);
            drawCages(canvas);
            drawUnits(canvas);
            drawHud(canvas);
        }

        private void drawRoadNetwork(Canvas c) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(24f);
            paint.setColor(lockdown ? Color.parseColor("#B91C1C") : Color.parseColor("#2D415D"));

            Path main = new Path();
            main.moveTo(120, 630);
            main.lineTo(420, 630);
            main.lineTo(420, 430);
            main.lineTo(700, 430);
            main.lineTo(700, 210);
            main.lineTo(1040, 210);
            c.drawPath(main, paint);

            paint.setStrokeWidth(10f);
            paint.setColor(lockdown ? Color.parseColor("#EF4444") : Color.parseColor("#5B7699"));
            c.drawCircle(420, 630, 46, paint); // ring 1
            c.drawCircle(700, 430, 46, paint); // ring 2
            c.drawCircle(700, 210, 46, paint); // ring 3
        }

        private void drawBuildings(Canvas c) {
            paint.setStyle(Paint.Style.FILL);

            // Military
            paint.setColor(Color.parseColor("#253348"));
            c.drawRect(40, 540, 260, 710, paint);
            label(c, "Asker", 85, 620);

            // Personnel
            paint.setColor(Color.parseColor("#2A3C53"));
            c.drawRect(270, 540, 500, 710, paint);
            label(c, "Personel", 315, 620);

            // Office
            paint.setColor(Color.parseColor("#324965"));
            c.drawRect(510, 520, 740, 710, paint);
            label(c, "Ofis", 585, 620);
        }

        private void drawCages(Canvas c) {
            int[] cageColors = {
                    Color.parseColor("#334155"), Color.parseColor("#3F3F46"), Color.parseColor("#374151")
            };

            for (int i = 0; i < 3; i++) {
                float left = 730 + (i * 130);
                float right = left + 110;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(cageColors[i]);
                c.drawRect(left, 140, right, 280, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(4f);
                paint.setColor(i == targetCage ? Color.parseColor("#22D3EE") : Color.parseColor("#94A3B8"));
                c.drawRect(left, 140, right, 280, paint);

                label(c, "CAGE " + (i + 1), left + 20, 215);
            }
        }

        private void drawUnits(Canvas c) {
            paint.setStyle(Paint.Style.FILL);

            paint.setColor(Color.parseColor("#22C55E"));
            c.drawCircle(soldierX, soldierY, 18, paint);

            paint.setColor(Color.parseColor("#60A5FA"));
            c.drawCircle(staffX, staffY, 15, paint);

            paint.setColor(Color.parseColor("#F59E0B"));
            c.drawCircle(subjectX, subjectY, 14, paint);

            paint.setColor(Color.parseColor("#EF4444"));
            c.drawCircle(cageXs[targetCage], cageY, 20, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(22f);
            c.drawText("A", soldierX - 6, soldierY + 7, paint);
            c.drawText("P", staffX - 6, staffY + 7, paint);
            c.drawText("D", subjectX - 6, subjectY + 7, paint);
            c.drawText(selectedScp, cageXs[targetCage] - 45, cageY - 28, paint);
        }

        private void drawHud(Canvas c) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#DDE8FA"));
            paint.setTextSize(24f);

            String state;
            if (lockdown) {
                state = "Durum: LOCKDOWN";
            } else if (phase == 0) {
                state = "Durum: Beklemede";
            } else if (phase == 6) {
                state = "Durum: Teslimat tamamlandı";
            } else {
                state = "Durum: Faz " + phase;
            }

            c.drawText(state, 40, 60, paint);
            c.drawText(String.format(Locale.ROOT, "Hedef: %s -> Cage %d", selectedScp, targetCage + 1), 40, 95, paint);
            c.drawText("Yol Ağı: Asker / Personel / Ofis / 3x Cage bağlı", 40, 130, paint);
        }

        private void label(Canvas c, String text, float x, float y) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#CBD5E1"));
            paint.setTextSize(24f);
            c.drawText(text, x, y, paint);
        }
    }
}
