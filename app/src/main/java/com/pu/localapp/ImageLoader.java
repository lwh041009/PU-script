package com.pu.localapp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ImageLoader {
    private final Map<String, Bitmap> cache = new ConcurrentHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    void load(ImageView image, String url) {
        if (url == null || url.trim().isEmpty()) {
            image.setImageResource(android.R.drawable.sym_def_app_icon);
            return;
        }
        String normalized = url.startsWith("//") ? "https:" + url : url;
        Bitmap cached = cache.get(normalized);
        if (cached != null) {
            image.setImageBitmap(cached);
            return;
        }
        image.setImageResource(android.R.drawable.sym_def_app_icon);
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(normalized).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(8000);
                try (InputStream is = conn.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    if (bitmap != null) {
                        cache.put(normalized, bitmap);
                        main.post(() -> image.setImageBitmap(bitmap));
                    }
                }
            } catch (Exception ignored) {
            }
        }).start();
    }
}
