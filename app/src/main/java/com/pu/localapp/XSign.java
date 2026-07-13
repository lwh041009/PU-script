package com.pu.localapp;

import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class XSign {
    private static final byte[] PSK = new byte[]{121, 121, 0, 19, 5, 49, 2, 43, 13, 17, 11, 9, 4, 29, 60, 11};
    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private XSign() {
    }

    static String generate() {
        return generate(System.currentTimeMillis());
    }

    static String generate(long nowMillis) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("echo", randomEcho());
            payload.put("timestamp", String.valueOf(nowMillis / 1000L));
            payload.put("client", "web");
            byte[] iv = new byte[16];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(PSK, "AES"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(payload.toString().getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception ex) {
            throw new IllegalStateException("X-Sign failed", ex);
        }
    }

    private static String randomEcho() {
        char[] chars = new char[16];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(chars);
    }
}
