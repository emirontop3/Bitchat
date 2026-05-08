package com.example.helloworld;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(48, 48, 48, 48);

        TextView title = new TextView(this);
        title.setText("Bitchat");
        title.setTextSize(32f);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("APK build is ready for direct download from GitHub Releases.");
        subtitle.setTextSize(18f);
        subtitle.setGravity(Gravity.CENTER);

        container.addView(title);
        container.addView(subtitle);
        setContentView(container);
    }
}
