package com.example.helloworld;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
    private static final String[] SCPS = {"SCP-173", "SCP-096", "SCP-049"};

    private GameView gameView;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B0F16"));
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("Scp Rp");
        title.setTextSize(30f);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER_HORIZONTAL);

        Spinner scpSpinner = new Spinner(this);
        scpSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, SCPS));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        Button start = new Button(this);
        start.setText("Görev Başlat");
        Button lockdown = new Button(this);
        lockdown.setText("Lockdown");

        controls.addView(start, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        controls.addView(lockdown, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        statusView = new TextView(this);
        statusView.setTextColor(Color.parseColor("#C7D4E5"));
        statusView.setText("Harita hazır. SCP seçip görev başlat.");
        statusView.setPadding(0, 12, 0, 12);

        gameView = new GameView(this);

        start.setOnClickListener(v -> {
            String scp = scpSpinner.getSelectedItem().toString();
            gameView.startMission(scp);
            statusView.setText("Görev aktif: askerler hapishaneden deneği alıp cage'e götürüyor...");
        });

        lockdown.setOnClickListener(v -> {
            gameView.triggerLockdown();
            statusView.setText("LOCKDOWN: Kapılar kapandı, birimler geri çekiliyor.");
        });

        root.addView(title);
        root.addView(scpSpinner);
        root.addView(controls);
        root.addView(statusView);
        root.addView(gameView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        setContentView(root);
    }

    private static class GameView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler handler = new Handler(Looper.getMainLooper());

        private float soldierX = 120;
        private float soldierY = 520;
        private float subjectX = 90;
        private float subjectY = 520;
        private float cageX = 860;
        private float cageY = 260;

        private boolean missionRunning = false;
        private boolean lockdown = false;
        private String scp = "SCP-173";

        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (missionRunning && !lockdown) {
                    moveToCage();
                    invalidate();
                    handler.postDelayed(this, 16);
                }
            }
        };

        public GameView(Context context) {
            super(context);
            setBackgroundColor(Color.parseColor("#101826"));
        }

        void startMission(String selectedScp) {
            scp = selectedScp;
            lockdown = false;
            missionRunning = true;
            soldierX = 120;
            soldierY = 520;
            subjectX = 90;
            subjectY = 520;
            handler.removeCallbacks(tick);
            handler.post(tick);
            invalidate();
        }

        void triggerLockdown() {
            lockdown = true;
            missionRunning = false;
            handler.removeCallbacks(tick);
            invalidate();
        }

        private void moveToCage() {
            float speed = 2.8f;

            if (soldierX < 430) {
                soldierX += speed;
                subjectX += speed;
            } else if (soldierY > 260) {
                soldierY -= speed;
                subjectY -= speed;
            } else if (soldierX < cageX - 70) {
                soldierX += speed;
                subjectX += speed;
            } else {
                missionRunning = false;
                handler.removeCallbacks(tick);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            drawMap(canvas);
            drawUnits(canvas);
            drawHud(canvas);
        }

        private void drawMap(Canvas c) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#202D3F"));
            c.drawRect(40, 440, 300, 600, paint); // prison
            c.drawRect(360, 440, 560, 600, paint); // prep
            c.drawRect(620, 180, 980, 340, paint); // cage area

            paint.setColor(Color.parseColor("#2F3F56"));
            c.drawRect(300, 500, 360, 540, paint); // gate1
            c.drawRect(560, 500, 620, 540, paint); // gate2
            c.drawRect(620, 250, 680, 290, paint); // gate3

            paint.setColor(Color.parseColor("#91A6C6"));
            paint.setTextSize(30f);
            c.drawText("Hapishane", 70, 500, paint);
            c.drawText("Hazırlık", 390, 500, paint);
            c.drawText("SCP Cage", 700, 250, paint);

            if (lockdown) {
                paint.setColor(Color.RED);
                paint.setTextSize(44f);
                c.drawText("LOCKDOWN", 370, 120, paint);
            }
        }

        private void drawUnits(Canvas c) {
            paint.setStyle(Paint.Style.FILL);

            paint.setColor(Color.parseColor("#4ADE80"));
            c.drawCircle(soldierX, soldierY, 24, paint);

            paint.setColor(Color.parseColor("#F59E0B"));
            c.drawCircle(subjectX, subjectY, 18, paint);

            paint.setColor(Color.parseColor("#EF4444"));
            c.drawCircle(cageX, cageY, 26, paint);

            paint.setColor(Color.WHITE);
            paint.setTextSize(24f);
            c.drawText("Asker", soldierX - 30, soldierY - 30, paint);
            c.drawText("Denek", subjectX - 30, subjectY + 42, paint);
            c.drawText(scp, cageX - 45, cageY - 36, paint);
        }

        private void drawHud(Canvas c) {
            paint.setColor(Color.parseColor("#AFC3E6"));
            paint.setTextSize(24f);

            String phase;
            if (lockdown) {
                phase = "Durum: Lockdown";
            } else if (!missionRunning && soldierX >= cageX - 70) {
                phase = "Durum: Teslimat tamamlandı";
            } else if (soldierX < 430) {
                phase = "Durum: Hapishaneden çıkış";
            } else if (soldierY > 260) {
                phase = "Durum: Koridor geçişi";
            } else {
                phase = "Durum: Cage yaklaşımı";
            }

            c.drawText(phase, 40, 60, paint);
            c.drawText(String.format(Locale.ROOT, "Koordinat Asker: (%.0f, %.0f)", soldierX, soldierY), 40, 92, paint);
        }
    }
}
