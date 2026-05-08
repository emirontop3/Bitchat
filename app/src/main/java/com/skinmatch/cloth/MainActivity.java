package com.skinmatch.cloth;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 1001;
    private static final int REQUEST_PERMISSIONS = 1002;
    private static final int MAX_IMAGE_SIDE = 1280;

    private static final int COLOR_BG = Color.rgb(10, 12, 18);
    private static final int COLOR_PANEL = Color.rgb(20, 25, 36);
    private static final int COLOR_PANEL_SOFT = Color.rgb(31, 38, 54);
    private static final int COLOR_TEXT = Color.rgb(244, 247, 252);
    private static final int COLOR_MUTED = Color.rgb(156, 168, 190);
    private static final int COLOR_ACCENT = Color.rgb(255, 156, 84);
    private static final int COLOR_GO = Color.rgb(92, 225, 180);

    private ImageView preview;
    private TextView status;
    private TextView metrics;
    private Bitmap originalBitmap;
    private Bitmap resultBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestImagePermission();
        buildUi();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            loadImage(data.getData());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(18));
        root.setBackgroundColor(COLOR_BG);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("SkinTone Studio");
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(32f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Fotoğraftaki kıyafet bölgelerini algılar ve kumaş dokusunu koruyarak kişinin tahmini ten rengine boyar. Gerçek vücut/anatomi üretmez; sadece kıyafet piksellerini recolor eder.");
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setTextSize(15f);
        subtitle.setPadding(0, dp(8), 0, dp(16));
        root.addView(subtitle, matchWrap());

        LinearLayout hero = card();
        TextView heroTitle = cardTitle("Yerel AI-benzeri kıyafet segmentasyonu", COLOR_TEXT);
        hero.addView(heroTitle, matchWrap());
        hero.addView(body("• Ten rengi örnekleme\n• Torso/alt gövde kıyafet maskesi\n• Bağlı bileşen temizliği\n• Doku ve gölge koruyan recolor\n• Tamamen cihaz içinde çalışır"), matchWrap());
        root.addView(hero, matchWrapWithMargins(0, 0, 0, dp(14)));

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setBackground(rounded(COLOR_PANEL, dp(18)));
        preview.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(preview, matchWrapWithMargins(0, 0, 0, dp(14)));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(button("Fotoğraf Yükle", COLOR_ACCENT, Color.BLACK, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickImage();
            }
        }), weightWrap());
        buttons.addView(button("Detect + Boya", COLOR_GO, Color.BLACK, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                analyzeAndRecolor();
            }
        }), weightWrap());
        root.addView(buttons, matchWrapWithMargins(0, 0, 0, dp(10)));

        Button save = button("Sonucu Kaydet", COLOR_PANEL_SOFT, COLOR_TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                saveResult();
            }
        });
        root.addView(save, matchWrapWithMargins(0, 0, 0, dp(14)));

        status = new TextView(this);
        status.setText("Başlamak için insan fotoğrafı yükle.");
        status.setTextColor(COLOR_TEXT);
        status.setTextSize(15f);
        status.setPadding(0, dp(6), 0, dp(4));
        root.addView(status, matchWrap());

        metrics = new TextView(this);
        metrics.setTextColor(COLOR_MUTED);
        metrics.setTextSize(13f);
        root.addView(metrics, matchWrap());

        setContentView(scroll);
    }

    private void pickImage() {
        requestImagePermission();
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "İnsan fotoğrafı seç"), PICK_IMAGE);
    }

    private void loadImage(Uri uri) {
        try {
            InputStream stream = getContentResolver().openInputStream(uri);
            Bitmap decoded = BitmapFactory.decodeStream(stream);
            if (stream != null) {
                stream.close();
            }
            if (decoded == null) {
                toast("Fotoğraf okunamadı");
                return;
            }
            originalBitmap = scaleDown(decoded, MAX_IMAGE_SIDE);
            resultBitmap = null;
            preview.setImageBitmap(originalBitmap);
            status.setText("Fotoğraf yüklendi. Şimdi Detect + Boya yap.");
            metrics.setText(originalBitmap.getWidth() + "×" + originalBitmap.getHeight() + " px");
        } catch (IOException exception) {
            toast("Fotoğraf açılamadı: " + exception.getMessage());
        }
    }

    private void analyzeAndRecolor() {
        if (originalBitmap == null) {
            toast("Önce fotoğraf yükle");
            return;
        }
        long started = System.currentTimeMillis();
        ClothingAnalyzer.AnalysisResult result = ClothingAnalyzer.recolorClothing(originalBitmap);
        resultBitmap = result.bitmap;
        preview.setImageBitmap(resultBitmap);
        status.setText("Kıyafet maskesi ten rengine boyandı.");
        metrics.setText("Ten rengi: #" + String.format(Locale.US, "%06X", 0xFFFFFF & result.skinColor)
                + " • Mask: " + result.maskPixels + " px"
                + " • Güven: " + result.confidence + "%"
                + " • " + (System.currentTimeMillis() - started) + " ms");
    }

    private void saveResult() {
        if (resultBitmap == null) {
            toast("Kaydedilecek sonuç yok");
            return;
        }
        String name = "skintone-studio-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".png";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SkinToneStudio");
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    throw new IOException("MediaStore URI oluşturulamadı");
                }
                OutputStream output = getContentResolver().openOutputStream(uri);
                resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                if (output != null) {
                    output.close();
                }
            } else {
                File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SkinToneStudio");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IOException("Klasör oluşturulamadı");
                }
                File file = new File(directory, name);
                FileOutputStream output = new FileOutputStream(file);
                resultBitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                output.close();
            }
            toast("Kaydedildi: " + name);
        } catch (IOException exception) {
            toast("Kaydedilemedi: " + exception.getMessage());
        }
    }

    private void requestImagePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (!permissions.isEmpty()) {
            requestPermissions(permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
        }
    }

    private Bitmap scaleDown(Bitmap bitmap, int maxSide) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int largest = Math.max(width, height);
        if (largest <= maxSide) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, true);
        }
        float scale = maxSide / (float) largest;
        return Bitmap.createScaledBitmap(bitmap, Math.round(width * scale), Math.round(height * scale), true);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(rounded(COLOR_PANEL, dp(20)));
        return card;
    }

    private TextView cardTitle(String text, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(19f);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(COLOR_MUTED);
        view.setTextSize(14f);
        view.setPadding(0, dp(8), 0, 0);
        return view;
    }

    private Button button(String text, int bg, int fg, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextColor(fg);
        button.setTextSize(15f);
        button.setBackground(rounded(bg, dp(16)));
        button.setOnClickListener(listener);
        return button;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        return params;
    }

    private LinearLayout.LayoutParams matchWrapWithMargins(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static class ClothingAnalyzer {
        static AnalysisResult recolorClothing(Bitmap input) {
            Bitmap source = input.copy(Bitmap.Config.ARGB_8888, false);
            int width = source.getWidth();
            int height = source.getHeight();
            int[] pixels = new int[width * height];
            source.getPixels(pixels, 0, width, 0, 0, width, height);

            int skin = estimateSkinColor(pixels, width, height);
            boolean[] rawMask = buildClothingMask(pixels, width, height, skin);
            boolean[] cleanMask = keepLargeComponents(rawMask, width, height, Math.max(120, width * height / 900));
            cleanMask = smoothMask(cleanMask, width, height, 2);

            int[] output = pixels.clone();
            int maskPixels = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int index = y * width + x;
                    if (!cleanMask[index]) {
                        continue;
                    }
                    output[index] = recolorPreserveTexture(pixels[index], skin, x, y, width, height);
                    maskPixels++;
                }
            }
            Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            result.setPixels(output, 0, width, 0, 0, width, height);
            int confidence = Math.min(98, Math.max(18, (maskPixels * 100) / Math.max(1, width * height / 3)));
            return new AnalysisResult(result, skin, maskPixels, confidence);
        }

        private static int estimateSkinColor(int[] pixels, int width, int height) {
            List<Integer> samples = new ArrayList<>();
            collectSkinSamples(samples, pixels, width, height, width / 4, height / 12, width * 3 / 4, height * 38 / 100);
            if (samples.size() < 80) {
                collectSkinSamples(samples, pixels, width, height, width / 8, height / 12, width * 7 / 8, height * 70 / 100);
            }
            if (samples.isEmpty()) {
                return Color.rgb(190, 140, 105);
            }
            int[] reds = new int[samples.size()];
            int[] greens = new int[samples.size()];
            int[] blues = new int[samples.size()];
            for (int i = 0; i < samples.size(); i++) {
                int color = samples.get(i);
                reds[i] = Color.red(color);
                greens[i] = Color.green(color);
                blues[i] = Color.blue(color);
            }
            java.util.Arrays.sort(reds);
            java.util.Arrays.sort(greens);
            java.util.Arrays.sort(blues);
            int middle = samples.size() / 2;
            return Color.rgb(reds[middle], greens[middle], blues[middle]);
        }

        private static void collectSkinSamples(List<Integer> samples, int[] pixels, int width, int height, int left, int top, int right, int bottom) {
            int step = Math.max(1, Math.min(width, height) / 220);
            for (int y = Math.max(0, top); y < Math.min(height, bottom); y += step) {
                for (int x = Math.max(0, left); x < Math.min(width, right); x += step) {
                    int color = pixels[y * width + x];
                    if (isSkinLike(color)) {
                        samples.add(color);
                    }
                }
            }
        }

        private static boolean[] buildClothingMask(int[] pixels, int width, int height, int skin) {
            boolean[] mask = new boolean[width * height];
            int centerX = width / 2;
            int torsoTop = height * 30 / 100;
            int torsoBottom = height * 88 / 100;
            float rx = width * 0.40f;
            float ry = height * 0.36f;
            int background = estimateBorderColor(pixels, width, height);
            for (int y = torsoTop; y < torsoBottom; y++) {
                for (int x = width / 10; x < width * 9 / 10; x++) {
                    int index = y * width + x;
                    int color = pixels[index];
                    float ellipse = ((x - centerX) * (x - centerX)) / (rx * rx)
                            + ((y - height * 58 / 100f) * (y - height * 58 / 100f)) / (ry * ry);
                    if (ellipse > 1.28f) {
                        continue;
                    }
                    if (isSkinLike(color)) {
                        continue;
                    }
                    int skinDistance = colorDistance(color, skin);
                    int bgDistance = colorDistance(color, background);
                    float[] hsv = new float[3];
                    Color.colorToHSV(color, hsv);
                    boolean fabricLike = skinDistance > 38 && (hsv[1] > 0.10f || hsv[2] < 0.78f) && bgDistance > 18;
                    boolean strongEdge = localContrast(pixels, width, height, x, y) > 24;
                    if (fabricLike || (strongEdge && skinDistance > 55)) {
                        mask[index] = true;
                    }
                }
            }
            return mask;
        }

        private static boolean[] keepLargeComponents(boolean[] mask, int width, int height, int minSize) {
            boolean[] visited = new boolean[mask.length];
            boolean[] output = new boolean[mask.length];
            int[] dirs = new int[]{1, -1, width, -width};
            for (int i = 0; i < mask.length; i++) {
                if (!mask[i] || visited[i]) {
                    continue;
                }
                ArrayList<Integer> component = new ArrayList<>();
                ArrayDeque<Integer> queue = new ArrayDeque<>();
                queue.add(i);
                visited[i] = true;
                while (!queue.isEmpty()) {
                    int current = queue.removeFirst();
                    component.add(current);
                    int x = current % width;
                    for (int dir : dirs) {
                        int next = current + dir;
                        if (next < 0 || next >= mask.length || visited[next] || !mask[next]) {
                            continue;
                        }
                        if ((dir == 1 && x == width - 1) || (dir == -1 && x == 0)) {
                            continue;
                        }
                        visited[next] = true;
                        queue.add(next);
                    }
                }
                if (component.size() >= minSize) {
                    for (int pixel : component) {
                        output[pixel] = true;
                    }
                }
            }
            return output;
        }

        private static boolean[] smoothMask(boolean[] mask, int width, int height, int passes) {
            boolean[] current = mask;
            for (int pass = 0; pass < passes; pass++) {
                boolean[] next = current.clone();
                for (int y = 1; y < height - 1; y++) {
                    for (int x = 1; x < width - 1; x++) {
                        int count = 0;
                        for (int yy = -1; yy <= 1; yy++) {
                            for (int xx = -1; xx <= 1; xx++) {
                                if (current[(y + yy) * width + (x + xx)]) {
                                    count++;
                                }
                            }
                        }
                        next[y * width + x] = count >= 5;
                    }
                }
                current = next;
            }
            return current;
        }

        private static int recolorPreserveTexture(int original, int skin, int x, int y, int width, int height) {
            float originalLuma = luma(original) / 255f;
            float skinLuma = Math.max(0.18f, luma(skin) / 255f);
            float shade = clamp(0.62f + originalLuma / skinLuma * 0.38f, 0.45f, 1.28f);
            int r = clampColor((int) (Color.red(skin) * shade));
            int g = clampColor((int) (Color.green(skin) * shade));
            int b = clampColor((int) (Color.blue(skin) * shade));
            float verticalBlend = clamp((y - height * 0.28f) / (height * 0.50f), 0f, 1f);
            float blend = 0.78f + verticalBlend * 0.12f;
            return Color.rgb(
                    clampColor((int) (Color.red(original) * (1f - blend) + r * blend)),
                    clampColor((int) (Color.green(original) * (1f - blend) + g * blend)),
                    clampColor((int) (Color.blue(original) * (1f - blend) + b * blend))
            );
        }

        private static boolean isSkinLike(int color) {
            int r = Color.red(color);
            int g = Color.green(color);
            int b = Color.blue(color);
            int max = Math.max(r, Math.max(g, b));
            int min = Math.min(r, Math.min(g, b));
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            boolean rgbRule = r > 70 && g > 35 && b > 20 && max - min > 12 && r >= g && r >= b && Math.abs(r - g) > 8;
            boolean hsvRule = (hsv[0] < 52 || hsv[0] > 340) && hsv[1] > 0.12f && hsv[1] < 0.78f && hsv[2] > 0.30f;
            return rgbRule && hsvRule;
        }

        private static int estimateBorderColor(int[] pixels, int width, int height) {
            long r = 0;
            long g = 0;
            long b = 0;
            int count = 0;
            int step = Math.max(1, Math.min(width, height) / 120);
            for (int x = 0; x < width; x += step) {
                int top = pixels[x];
                int bottom = pixels[(height - 1) * width + x];
                r += Color.red(top) + Color.red(bottom);
                g += Color.green(top) + Color.green(bottom);
                b += Color.blue(top) + Color.blue(bottom);
                count += 2;
            }
            for (int y = 0; y < height; y += step) {
                int left = pixels[y * width];
                int right = pixels[y * width + width - 1];
                r += Color.red(left) + Color.red(right);
                g += Color.green(left) + Color.green(right);
                b += Color.blue(left) + Color.blue(right);
                count += 2;
            }
            return Color.rgb((int) (r / count), (int) (g / count), (int) (b / count));
        }

        private static int localContrast(int[] pixels, int width, int height, int x, int y) {
            int center = pixels[y * width + x];
            int maxDistance = 0;
            for (int yy = -1; yy <= 1; yy++) {
                for (int xx = -1; xx <= 1; xx++) {
                    int nx = x + xx;
                    int ny = y + yy;
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) {
                        continue;
                    }
                    maxDistance = Math.max(maxDistance, colorDistance(center, pixels[ny * width + nx]));
                }
            }
            return maxDistance;
        }

        private static int colorDistance(int a, int b) {
            int dr = Color.red(a) - Color.red(b);
            int dg = Color.green(a) - Color.green(b);
            int db = Color.blue(a) - Color.blue(b);
            return (int) Math.sqrt(dr * dr + dg * dg + db * db);
        }

        private static int luma(int color) {
            return (int) (0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color));
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private static int clampColor(int value) {
            return Math.max(0, Math.min(255, value));
        }

        static class AnalysisResult {
            final Bitmap bitmap;
            final int skinColor;
            final int maskPixels;
            final int confidence;

            AnalysisResult(Bitmap bitmap, int skinColor, int maskPixels, int confidence) {
                this.bitmap = bitmap;
                this.skinColor = skinColor;
                this.maskPixels = maskPixels;
                this.confidence = confidence;
            }
        }
    }
}
