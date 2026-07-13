package com.pu.localapp;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class ServerApi {
    private final Context context;

    ServerApi(Context context) {
        this.context = context.getApplicationContext();
    }

    boolean configured() {
        return !baseUrl().isEmpty();
    }

    Models.Reservation createReservation(Models.Account account, Models.Activity activity) throws Exception {
        JSONObject body = new JSONObject();
        body.put("schoolName", account.schoolName);
        body.put("sid", account.sid);
        body.put("username", account.username);
        body.put("password", account.password);
        body.put("token", account.token);
        body.put("cid", account.cid);
        body.put("yid", account.yid);
        body.put("activityId", activity.id);
        body.put("activityName", activity.name);
        body.put("joinStartTime", activity.joinStartTime);
        body.put("runAt", TimeUtil.parseMillis(activity.joinStartTime));
        JSONObject res = request("POST", "/api/reservations", body);
        JSONObject data = res.optJSONObject("reservation");
        if (data == null) data = res.optJSONObject("data");
        if (data == null) throw new IllegalStateException(res.optString("message", "服务器没有返回预约任务"));
        return reservationFromJson(data);
    }

    List<Models.Reservation> reservations(Models.Account account) throws Exception {
        String endpoint = "/api/reservations?sid=" + encode(String.valueOf(account.sid)) + "&username=" + encode(account.username);
        JSONObject res = request("GET", endpoint, null);
        JSONArray arr = res.optJSONArray("reservations");
        if (arr == null) {
            JSONObject data = res.optJSONObject("data");
            if (data != null) arr = data.optJSONArray("reservations");
        }
        ArrayList<Models.Reservation> result = new ArrayList<>();
        if (arr == null) return result;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null) result.add(reservationFromJson(obj));
        }
        return result;
    }

    Models.Reservation reservation(String remoteId) throws Exception {
        JSONObject res = request("GET", "/api/reservations/" + encode(remoteId), null);
        JSONObject data = res.optJSONObject("reservation");
        if (data == null) data = res.optJSONObject("data");
        if (data == null) throw new IllegalStateException(res.optString("message", "服务器没有返回预约任务"));
        return reservationFromJson(data);
    }

    void cancel(String remoteId) throws Exception {
        request("POST", "/api/reservations/" + encode(remoteId) + "/cancel", new JSONObject());
    }

    void delete(String remoteId) throws Exception {
        request("DELETE", "/api/reservations/" + encode(remoteId), null);
    }

    private Models.Reservation reservationFromJson(JSONObject obj) {
        Models.Reservation r = new Models.Reservation();
        r.remoteId = obj.optString("id", obj.optString("remoteId"));
        r.executor = "server";
        r.serverUrl = baseUrl();
        r.accountKey = obj.optString("accountKey");
        r.sid = obj.optLong("sid");
        r.username = obj.optString("username");
        r.activityId = obj.optLong("activityId");
        r.activityName = obj.optString("activityName");
        r.runAt = obj.optLong("runAt");
        r.status = obj.optString("status", "pending");
        r.lastResult = obj.optString("lastResult", obj.optString("message"));
        r.retryCount = obj.optInt("retryCount");
        r.createdAt = obj.optLong("createdAt");
        return r;
    }

    private JSONObject request(String method, String endpoint, JSONObject body) throws Exception {
        String base = baseUrl();
        if (base.isEmpty()) throw new IllegalStateException("请先在设置里填写服务器地址");
        HttpURLConnection conn = (HttpURLConnection) new URL(base + endpoint).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");
        String token = AppSettings.serverToken(context);
        if (!token.isEmpty()) conn.setRequestProperty("X-Server-Token", token);
        if ("POST".equals(method) || "PUT".equals(method)) {
            conn.setDoOutput(true);
            byte[] bytes = (body == null ? "{}" : body.toString()).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }
        }
        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String text = readAll(stream);
        JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
        if (code >= 400 || !json.optBoolean("ok", true)) {
            String msg = json.optString("message", json.optString("error", "服务器请求失败：" + code));
            throw new IllegalStateException(msg);
        }
        return json;
    }

    private String baseUrl() {
        return AppSettings.serverBaseUrl(context);
    }

    private String encode(String value) throws Exception {
        return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }
}
