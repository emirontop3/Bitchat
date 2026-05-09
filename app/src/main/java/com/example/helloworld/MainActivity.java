package com.example.helloworld;

import android.os.Bundle;
import android.view.View;
import android.view.animation.AnticipateOvershootInterpolator;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import androidx.activity.OnBackPressedCallback; // Yeni eklenen
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private LinearLayout searchBox;
    private EditText searchInput;
    private ListView resultsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        searchBox = findViewById(R.id.search_box);
        searchInput = findViewById(R.id.search_input);
        resultsList = findViewById(R.id.results_list);

        // Örnek Veriler
        ArrayList<String> mockResults = new ArrayList<>();
        mockResults.add("Mobil Uygulama Geliştirme");
        mockResults.add("Animasyonlu UI Tasarımları");
        mockResults.add("Android Java Rehberi");
        mockResults.add("Custom Search Engine Projesi");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_list_item_1, mockResults);
        resultsList.setAdapter(adapter);

        // Arama Çubuğu Odak Animasyonu
        searchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                searchBox.animate()
                        .translationY(-20f)
                        .scaleX(1.03f)
                        .scaleY(1.03f)
                        .setDuration(400)
                        .setInterpolator(new AnticipateOvershootInterpolator())
                        .start();

                resultsList.animate()
                        .alpha(1f)
                        .translationY(-10f)
                        .setDuration(600)
                        .start();
            }
        });

        // HATA VEREN KISMIN YENİ HALİ: Modern Geri Tuşu Yönetimi
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (searchInput.hasFocus()) {
                    // Arama açıksa kapat ve odağı temizle
                    searchInput.clearFocus();
                    searchBox.animate().translationY(0).scaleX(1f).scaleY(1f).setDuration(300).start();
                    resultsList.animate().alpha(0f).translationY(0).setDuration(300).start();
                } else {
                    // Arama açık değilse normal geri işlevini yap (uygulamadan çık/geri git)
                    setEnabled(false); // Callback'i devre dışı bırak ki sonsuz döngü olmasın
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }
}
