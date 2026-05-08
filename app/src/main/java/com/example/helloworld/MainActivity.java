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
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private static final String[] SCPS = {
            "SCP-173", "SCP-096", "SCP-049", "SCP-106", "SCP-939", "SCP-682", "SCP-035", "SCP-999", "SCP-457", "SCP-053"
    };

    private SiteMapView siteMapView;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#080D16"));
        root.setPadding(20, 20, 20, 20);

        TextView title = new TextView(this);
        title.setText("Scp Rp");
        title.setTextSize(30f);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER_HORIZONTAL);

        Spinner scpSpinner = new Spinner(this);
        scpSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SCPS));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button start = new Button(this);
        start.setText("Operasyon");
        Button lockdown = new Button(this);
        lockdown.setText("Lockdown");
        Button reset = new Button(this);
        reset.setText("Reset");
        Button boost = new Button(this);
        boost.setText("Hız+");

        row.addView(start, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(lockdown, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(reset, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(boost, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        statusView = new TextView(this);
        statusView.setTextColor(Color.parseColor("#D6E3F7"));
        statusView.setText("Gelişmiş 2D tesis aktif. Ekrana dokunarak devriye noktası ekleyebilirsin.");
        statusView.setPadding(0, 8, 0, 10);

        siteMapView = new SiteMapView(this);

        start.setOnClickListener(v -> {
            String scp = scpSpinner.getSelectedItem().toString();
            siteMapView.startOperation(scp);
            statusView.setText("Operasyon başladı: " + scp + " için ekip cage transferinde.");
        });

        lockdown.setOnClickListener(v -> {
            siteMapView.toggleLockdown();
            statusView.setText(siteMapView.isLockdown() ? "ALARM: LOCKDOWN aktif." : "LOCKDOWN kaldırıldı.");
        });

        reset.setOnClickListener(v -> {
            siteMapView.resetSimulation();
            statusView.setText("Sistem sıfırlandı, hazır.");
        });

        boost.setOnClickListener(v -> {
            siteMapView.cycleSpeed();
            statusView.setText("Hız modu: " + siteMapView.getSpeedLabel());
        });

        root.addView(title);
        root.addView(scpSpinner);
        root.addView(row);
        root.addView(statusView);
        root.addView(siteMapView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);
    }

    private static class SiteMapView extends View {
        private static class Unit {
            float x;
            float y;
            final float radius;
            final int color;
            final String tag;

            Unit(float x, float y, float radius, int color, String tag) {
                this.x = x;
                this.y = y;
                this.radius = radius;
                this.color = color;
                this.tag = tag;
            }
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Random random = new Random();
        private final List<float[]> patrolPoints = new ArrayList<>();

        private final Unit soldier = new Unit(190, 640, 18, Color.parseColor("#22C55E"), "A");
        private final Unit staff = new Unit(150, 640, 15, Color.parseColor("#60A5FA"), "P");
        private final Unit subject = new Unit(110, 640, 14, Color.parseColor("#F59E0B"), "D");

        private final float[] cageXs = {760, 900, 1040};
        private final String[] speedLabels = {"Normal", "Hızlı", "Turbo"};

        private float cageY = 200;
        private int targetCage = 0;
        private int phase = 0;
        private int speedMode = 0;
        private String selectedScp = "SCP-173";

        private boolean running = false;
        private boolean lockdown = false;

        private final Runnable loop = new Runnable() {
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
            setBackgroundColor(Color.parseColor("#10192A"));
            patrolPoints.add(new float[]{500, 640});
            patrolPoints.add(new float[]{700, 430});
            patrolPoints.add(new float[]{860, 220});
        }

        boolean isLockdown() { return lockdown; }
        String getSpeedLabel() { return speedLabels[speedMode]; }

        void cycleSpeed() {
            speedMode = (speedMode + 1) % speedLabels.length;
        }

        void startOperation(String scp) {
            selectedScp = scp;
            targetCage = pickCageForScp(scp);
            phase = 1;
            lockdown = false;
            running = true;

            soldier.x = 190; soldier.y = 640;
            staff.x = 150; staff.y = 640;
            subject.x = 110; subject.y = 640;

            handler.removeCallbacks(loop);
            handler.post(loop);
            invalidate();
        }

        void toggleLockdown() {
            lockdown = !lockdown;
            if (lockdown) {
                handler.removeCallbacks(loop);
            } else if (running) {
                handler.post(loop);
            }
            invalidate();
        }

        void resetSimulation() {
            running = false;
            lockdown = false;
            phase = 0;
            selectedScp = "SCP-173";
            speedMode = 0;
            soldier.x = 190; soldier.y = 640;
            staff.x = 150; staff.y = 640;
            subject.x = 110; subject.y = 640;
            patrolPoints.clear();
            patrolPoints.add(new float[]{500, 640});
            patrolPoints.add(new float[]{700, 430});
            patrolPoints.add(new float[]{860, 220});
            handler.removeCallbacks(loop);
            invalidate();
        }

        private int pickCageForScp(String scp) {
            if ("SCP-173".equals(scp) || "SCP-682".equals(scp) || "SCP-457".equals(scp)) return 0;
            if ("SCP-096".equals(scp) || "SCP-106".equals(scp) || "SCP-035".equals(scp)) return 1;
            return 2;
        }

        private void updateMovement() {
            float speed = speedMode == 0 ? 2.6f : speedMode == 1 ? 3.8f : 5.1f;
            float targetX = cageXs[targetCage] - 40;

            if (phase == 1 && soldier.x < 430) {
                moveAll(speed, 0);
            } else if (phase <= 2 && soldier.y > 450) {
                phase = 2;
                moveAll(0, -speed);
            } else if (phase <= 3 && soldier.x < 700) {
                phase = 3;
                moveAll(speed, 0);
            } else if (phase <= 4 && soldier.y > 230) {
                phase = 4;
                moveAll(0, -speed);
            } else if (phase <= 5 && soldier.x < targetX) {
                phase = 5;
                moveAll(speed, 0);
            } else {
                phase = 6;
                running = false;
                handler.removeCallbacks(loop);
            }

            if (random.nextInt(240) == 7 && patrolPoints.size() < 8) {
                patrolPoints.add(new float[]{260 + random.nextInt(750), 170 + random.nextInt(520)});
            }
        }

        private void moveAll(float dx, float dy) {
            soldier.x += dx;
            soldier.y += dy;
            staff.x += dx;
            staff.y += dy;
            subject.x += dx;
            subject.y += dy;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                patrolPoints.add(new float[]{event.getX(), event.getY()});
                invalidate();
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawPaths(canvas);
            drawSectors(canvas);
            drawCages(canvas);
            drawPatrolPoints(canvas);
            drawUnits(canvas);
            drawHud(canvas);
        }

        private void drawPaths(Canvas c) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(24f);
            paint.setColor(lockdown ? Color.parseColor("#B91C1C") : Color.parseColor("#2A405C"));

            Path p = new Path();
            p.moveTo(120, 650);
            p.lineTo(430, 650);
            p.lineTo(430, 450);
            p.lineTo(700, 450);
            p.lineTo(700, 230);
            p.lineTo(1080, 230);
            c.drawPath(p, paint);

            paint.setStrokeWidth(9f);
            paint.setColor(lockdown ? Color.parseColor("#EF4444") : Color.parseColor("#6B87AB"));
            c.drawCircle(430, 650, 46, paint);
            c.drawCircle(700, 450, 46, paint);
            c.drawCircle(700, 230, 46, paint);
        }

        private void drawSectors(Canvas c) {
            drawSector(c, 40, 560, 250, 760, "Asker", "#253348");
            drawSector(c, 260, 560, 500, 760, "Personel", "#2C3F57");
            drawSector(c, 510, 540, 760, 760, "Ofis", "#355070");
        }

        private void drawSector(Canvas c, float l, float t, float r, float b, String name, String color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor(color));
            c.drawRect(l, t, r, b, paint);
            paint.setColor(Color.parseColor("#D0DCEC"));
            paint.setTextSize(24f);
            c.drawText(name, l + 20, (t + b) / 2, paint);
        }

        private void drawCages(Canvas c) {
            for (int i = 0; i < 3; i++) {
                float left = 730 + i * 140;
                float right = left + 120;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor(i == targetCage ? "#334155" : "#1F2937"));
                c.drawRect(left, 140, right, 290, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(4f);
                paint.setColor(i == targetCage ? Color.parseColor("#22D3EE") : Color.parseColor("#9CA3AF"));
                c.drawRect(left, 140, right, 290, paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.parseColor("#E2E8F0"));
                paint.setTextSize(22f);
                c.drawText("CAGE " + (i + 1), left + 20, 220, paint);
            }
        }

        private void drawPatrolPoints(Canvas c) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#A78BFA"));
            for (float[] point : patrolPoints) {
                c.drawCircle(point[0], point[1], 7, paint);
            }
        }

        private void drawUnits(Canvas c) {
            drawUnit(c, soldier);
            drawUnit(c, staff);
            drawUnit(c, subject);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#EF4444"));
            c.drawCircle(cageXs[targetCage], cageY, 18, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(20f);
            c.drawText(selectedScp, cageXs[targetCage] - 45, cageY - 24, paint);
        }

        private void drawUnit(Canvas c, Unit unit) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(unit.color);
            c.drawCircle(unit.x, unit.y, unit.radius, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(19f);
            c.drawText(unit.tag, unit.x - 6, unit.y + 7, paint);
        }

        private void drawHud(Canvas c) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#DCE7FA"));
            paint.setTextSize(24f);

            String state;
            if (lockdown) state = "LOCKDOWN";
            else if (phase == 0) state = "Beklemede";
            else if (phase == 6) state = "Teslimat Tamam";
            else state = "Faz " + phase;

            c.drawText("Durum: " + state, 40, 60, paint);
            c.drawText(String.format(Locale.ROOT, "SCP: %s -> Cage %d", selectedScp, targetCage + 1), 40, 95, paint);
            c.drawText("Hız: " + speedLabels[speedMode] + " | Devriye Noktası: " + patrolPoints.size(), 40, 130, paint);
        }
    }
}
