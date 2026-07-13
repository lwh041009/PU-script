package com.pu.localapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class EncryptedPrefs {
    private static final String STORE = "secure_store";
    private static final String ALIAS = "pu_local_app_key";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private final SharedPreferences prefs;

    EncryptedPrefs(Context context) {
        prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        ensureKey();
    }

    void put(String key, String value) {
        prefs.edit().putString(key, encrypt(value == null ? "" : value)).apply();
    }

    String get(String key, String fallback) {
        String raw = prefs.getString(key, null);
        if (raw == null) return fallback;
        try {
            return decrypt(raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    void remove(String key) {
        prefs.edit().remove(key).apply();
    }

    SharedPreferences raw() {
        return prefs;
    }

    private void ensureKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(ALIAS)) return;
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
            )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build();
            generator.init(spec);
            generator.generateKey();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot create secure key", ex);
        }
    }

    private SecretKey key() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        return (SecretKey) keyStore.getKey(ALIAS, null);
    }

    private String encrypt(String value) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] data = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + data.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(data, 0, all, iv.length, data.length);
            return Base64.encodeToString(all, Base64.NO_WRAP);
        } catch (Exception ex) {
            throw new IllegalStateException("Encrypt failed", ex);
        }
    }

    private String decrypt(String value) throws Exception {
        byte[] all = Base64.decode(value, Base64.NO_WRAP);
        byte[] iv = new byte[12];
        byte[] data = new byte[all.length - 12];
        System.arraycopy(all, 0, iv, 0, 12);
        System.arraycopy(all, 12, data, 0, data.length);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(data), StandardCharsets.UTF_8);
    }
}
