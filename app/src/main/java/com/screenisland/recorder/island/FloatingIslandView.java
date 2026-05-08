package com.screenisland.recorder.island;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingIslandView extends LinearLayout {
    public interface Callback {
        void onRecordToggle();
        void onPauseToggle();
        void onSettings();
        void onClose();
    }

    private final Callback callback;
    private final TextView title;
    private final TextView state;
    private final TextView details;
    private final Button recordButton;
    private final Button pauseButton;
    private final Button settingsButton;
    private final Button closeButton;
    private final LinearLayout expandedRow;
    private boolean expanded = true;

    public FloatingIslandView(Context context, Callback callback) {
        super(context);
        this.callback = callback;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(12), dp(9), dp(12), dp(10));
        setBackground(IslandTheme.pillGradient(dp(26)));

        title = new TextView(context);
        title.setText("● Screen Island");
        title.setTextColor(IslandTheme.TEXT);
        title.setTextSize(14f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        addView(title, wrapWrap());

        state = new TextView(context);
        state.setTextColor(IslandTheme.GO);
        state.setTextSize(12f);
        state.setPadding(0, dp(3), 0, dp(2));
        addView(state, wrapWrap());

        details = new TextView(context);
        details.setTextColor(IslandTheme.MUTED);
        details.setTextSize(11f);
        details.setPadding(0, 0, 0, dp(6));
        addView(details, wrapWrap());

        expandedRow = new LinearLayout(context);
        expandedRow.setOrientation(HORIZONTAL);
        expandedRow.setGravity(Gravity.CENTER);

        recordButton = smallButton("REC", IslandTheme.RECORD, Color.WHITE);
        recordButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FloatingIslandView.this.callback.onRecordToggle();
            }
        });
        expandedRow.addView(recordButton, wrapWrapWithMargins(0, 0, dp(6), 0));

        pauseButton = smallButton("PAUSE", IslandTheme.PANEL_LIGHT, IslandTheme.TEXT);
        pauseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FloatingIslandView.this.callback.onPauseToggle();
            }
        });
        expandedRow.addView(pauseButton, wrapWrapWithMargins(0, 0, dp(6), 0));

        settingsButton = smallButton("Ayarlar", IslandTheme.PANEL_LIGHT, IslandTheme.TEXT);
        settingsButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FloatingIslandView.this.callback.onSettings();
            }
        });
        expandedRow.addView(settingsButton, wrapWrapWithMargins(0, 0, dp(6), 0));

        closeButton = smallButton("×", IslandTheme.PANEL_LIGHT, IslandTheme.TEXT);
        closeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                FloatingIslandView.this.callback.onClose();
            }
        });
        expandedRow.addView(closeButton, wrapWrap());
        addView(expandedRow, wrapWrap());

        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleExpanded();
            }
        });
        setIdle("Hazır", "Sürükle • dokun: küçült/büyüt");
    }

    public void setIdle(String label, String detail) {
        state.setText(label);
        state.setTextColor(IslandTheme.GO);
        details.setText(detail);
        recordButton.setText("REC");
        recordButton.setBackground(IslandTheme.rounded(IslandTheme.RECORD, dp(16)));
        pauseButton.setEnabled(false);
        pauseButton.setText("PAUSE");
    }

    public void setRecording(String elapsed, String quality, boolean audio, boolean paused) {
        state.setText(paused ? "Duraklatıldı • " + elapsed : "Kayıt alınıyor • " + elapsed);
        state.setTextColor(paused ? IslandTheme.WARNING : IslandTheme.RECORD);
        details.setText(quality + " • " + (audio ? "Mic açık" : "Mic kapalı"));
        recordButton.setText("STOP");
        recordButton.setBackground(IslandTheme.rounded(paused ? IslandTheme.WARNING : IslandTheme.GO, dp(16)));
        pauseButton.setEnabled(true);
        pauseButton.setText(paused ? "RESUME" : "PAUSE");
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        expandedRow.setVisibility(expanded ? VISIBLE : GONE);
        details.setVisibility(expanded ? VISIBLE : GONE);
    }

    public void toggleExpanded() {
        setExpanded(!expanded);
    }

    private Button smallButton(String text, int bg, int fg) {
        Button button = new Button(getContext());
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(fg);
        button.setTextSize(12f);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dp(10), dp(5), dp(10), dp(5));
        button.setBackground(IslandTheme.rounded(bg, dp(16)));
        return button;
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapWrapWithMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = wrapWrap();
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int dp(int value) {
        return IslandTheme.dp(getContext(), value);
    }
}
