package com.pu.localapp;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class SettingsActivity extends Activity {
    private LinearLayout preview;
    private TextView currentText;
    private TextView rangeText;
    private TextView serverStatus;
    private EditText percentInput;
    private EditText serverUrlInput;
    private EditText serverTokenInput;
    private SeekBar fontSeekBar;
    private float selectedScale;
    private boolean syncing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        selectedScale = AppSettings.fontScale(this);
        buildUi();
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Ui.BG);
        Ui.applySystemBars(page);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 8));
        Button back = Ui.button(this, "‹", Ui.SURFACE, Ui.TEXT);
        back.setTextSize(Ui.fontSp(this, 22));
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 36)));
        TextView title = Ui.text(this, "设置", 22, Ui.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMargins(Ui.dp(this, 12), 0, 0, 0);
        top.addView(title, titleLp);
        page.addView(top);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(sectionTitle("显示"));
        LinearLayout fontCard = card();
        currentText = Ui.text(this, currentFontText(), 14, Ui.MUTED, Typeface.NORMAL);
        fontCard.addView(Ui.text(this, "字体大小", 17, Ui.TEXT, Typeface.BOLD));
        LinearLayout.LayoutParams currentLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        currentLp.setMargins(0, Ui.dp(this, 7), 0, Ui.dp(this, 12));
        fontCard.addView(currentText, currentLp);
        fontCard.addView(controlRow());
        LinearLayout rangeRow = new LinearLayout(this);
        rangeRow.setOrientation(LinearLayout.HORIZONTAL);
        rangeText = Ui.text(this, "50%", 12, Ui.MUTED, Typeface.NORMAL);
        TextView rangeEnd = Ui.text(this, "200%", 12, Ui.MUTED, Typeface.NORMAL);
        rangeEnd.setGravity(Gravity.RIGHT);
        rangeRow.addView(rangeText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        rangeRow.addView(rangeEnd, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams rangeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rangeLp.setMargins(0, Ui.dp(this, 8), 0, 0);
        fontCard.addView(rangeRow, rangeLp);
        content.addView(fontCard);

        content.addView(sectionTitle("服务器"));
        LinearLayout serverCard = card();
        LinearLayout serverHeader = new LinearLayout(this);
        serverHeader.setOrientation(LinearLayout.HORIZONTAL);
        serverHeader.setGravity(Gravity.CENTER_VERTICAL);
        serverHeader.addView(Ui.text(this, "服务器连接", 17, Ui.TEXT, Typeface.BOLD), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        serverStatus = Ui.statusPill(this, "", Ui.PRIMARY_SOFT, Ui.PRIMARY);
        serverHeader.addView(serverStatus);
        serverCard.addView(serverHeader);
        updateServerStatus(false);
        TextView serverDesc = Ui.text(this, "填写服务器地址后，预约时可以选择由服务器 24 小时执行。", 13, Ui.MUTED, Typeface.NORMAL);
        serverDesc.setLineSpacing(Ui.dp(this, 4), 1.0f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.setMargins(0, Ui.dp(this, 7), 0, Ui.dp(this, 12));
        serverCard.addView(serverDesc, descLp);
        serverCard.addView(fieldLabel("服务器地址"));
        serverUrlInput = textInput("例如 http://192.168.1.10:8787");
        serverUrlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverUrlInput.setText(AppSettings.serverBaseUrl(this));
        serverCard.addView(serverUrlInput, inputLp());
        serverCard.addView(fieldLabel("访问密钥"));
        serverTokenInput = textInput("服务器密钥，可留空");
        serverTokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        serverTokenInput.setText(AppSettings.serverToken(this));
        serverCard.addView(serverTokenInput, inputLp());
        TextView securityTip = Ui.text(this, "地址和密钥仅保存在当前手机中", 12, Ui.MUTED, Typeface.NORMAL);
        securityTip.setPadding(Ui.dp(this, 11), Ui.dp(this, 8), Ui.dp(this, 11), Ui.dp(this, 8));
        securityTip.setBackground(Ui.bg(Color.rgb(247, 248, 250), 10, this));
        LinearLayout.LayoutParams securityLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        securityLp.setMargins(0, Ui.dp(this, 10), 0, 0);
        serverCard.addView(securityTip, securityLp);
        Button testServer = Ui.secondaryButton(this, "测试连接");
        testServer.setTextSize(Ui.fontSp(this, 14));
        testServer.setOnClickListener(v -> testServerConnection());
        LinearLayout.LayoutParams testLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42));
        testLp.setMargins(0, Ui.dp(this, 12), 0, 0);
        serverCard.addView(testServer, testLp);
        Button saveServer = Ui.primaryButton(this, "保存服务器设置");
        saveServer.setTextSize(Ui.fontSp(this, 14));
        saveServer.setOnClickListener(v -> saveServerSettings());
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42));
        saveLp.setMargins(0, Ui.dp(this, 9), 0, 0);
        serverCard.addView(saveServer, saveLp);
        content.addView(serverCard);

        content.addView(sectionTitle("预览"));
        preview = card();
        content.addView(preview);

        syncControlsFromScale();
        renderPreview();
        setContentView(page);
    }

    private TextView sectionTitle(String text) {
        TextView tv = Ui.text(this, text, 14, Ui.MUTED, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        tv.setLayoutParams(lp);
        return tv;
    }

    private LinearLayout card() {
        LinearLayout card = Ui.card(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(this, 16));
        card.setLayoutParams(lp);
        return card;
    }

    private TextView fieldLabel(String text) {
        TextView label = Ui.text(this, text, 12, Ui.MUTED, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 11), 0, 0);
        label.setLayoutParams(lp);
        return label;
    }

    private LinearLayout controlRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        fontSeekBar = new SeekBar(this);
        fontSeekBar.setMax(AppSettings.MAX_FONT_PERCENT - AppSettings.MIN_FONT_PERCENT);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            fontSeekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
            fontSeekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.PRIMARY));
        }
        fontSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || syncing) return;
                applyPercent(AppSettings.MIN_FONT_PERCENT + progress, false);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                applyPercent(AppSettings.MIN_FONT_PERCENT + seekBar.getProgress(), false);
            }
        });

        percentInput = new EditText(this);
        percentInput.setSingleLine(true);
        percentInput.setGravity(Gravity.CENTER);
        percentInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        percentInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        percentInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        percentInput.setTextSize(Ui.fontSp(this, 15));
        percentInput.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        percentInput.setBackground(Ui.strokeBg(Color.rgb(255, 247, 239), Ui.PRIMARY, 1, 8, this));
        percentInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) commitInputPercent();
        });
        percentInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitInputPercent();
                percentInput.clearFocus();
                return true;
            }
            return false;
        });

        row.addView(fontSeekBar, new LinearLayout.LayoutParams(0, Ui.dp(this, 42), 1f));
        TextView percent = Ui.text(this, "%", 15, Ui.MUTED, Typeface.BOLD);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(Ui.dp(this, 68), Ui.dp(this, 40));
        inputLp.setMargins(Ui.dp(this, 12), 0, Ui.dp(this, 4), 0);
        row.addView(percentInput, inputLp);
        row.addView(percent);
        return row;
    }

    private EditText textInput(String hint) {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(hint);
        e.setTextSize(Ui.fontSp(this, 14));
        e.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        e.setBackground(Ui.strokeBg(Color.rgb(250, 250, 250), Ui.LINE_STRONG, 1, 8, this));
        return e;
    }

    private LinearLayout.LayoutParams inputLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
        lp.setMargins(0, Ui.dp(this, 9), 0, 0);
        return lp;
    }

    private void saveServerSettings() {
        String url = serverUrlInput == null ? "" : serverUrlInput.getText().toString();
        String token = serverTokenInput == null ? "" : serverTokenInput.getText().toString();
        AppSettings.setServerBaseUrl(this, url);
        AppSettings.setServerToken(this, token);
        if (serverUrlInput != null) serverUrlInput.setText(AppSettings.serverBaseUrl(this));
        updateServerStatus(false);
        Toast.makeText(this, AppSettings.serverEnabled(this) ? "服务器设置已保存" : "已清空服务器地址", Toast.LENGTH_SHORT).show();
    }

    private void updateServerStatus(boolean connected) {
        if (serverStatus == null) return;
        boolean configured = AppSettings.serverEnabled(this);
        String text = connected ? "连接正常" : configured ? "已配置" : "未配置";
        int bg = connected ? Color.rgb(232, 248, 240) : configured ? Ui.PRIMARY_SOFT : Color.rgb(244, 244, 244);
        int fg = connected ? Ui.SUCCESS : configured ? Ui.PRIMARY : Ui.MUTED;
        serverStatus.setText(text);
        serverStatus.setTextColor(fg);
        serverStatus.setBackground(Ui.bg(bg, 999, this));
    }

    private void testServerConnection() {
        String url = AppSettings.normalizeServerBaseUrl(serverUrlInput == null ? "" : serverUrlInput.getText().toString());
        String token = serverTokenInput == null ? "" : serverTokenInput.getText().toString().trim();
        if (url.isEmpty()) {
            Toast.makeText(this, "请先填写服务器地址", Toast.LENGTH_SHORT).show();
            return;
        }
        ProgressDialog dialog = ProgressDialog.show(this, "", "正在测试服务器连接...", true, false);
        new Thread(() -> {
            try {
                requestAuthed(url, token, "/api/reservations?sid=-1&username=__connection_test__");
                JSONObject health = requestAuthed(url, token, "/health");
                int pending = health.optInt("pending", -1);
                int workerCount = health.optJSONObject("config") == null ? 0 : health.optJSONObject("config").optInt("workerCount");
                String message = "连接成功，密钥正确";
                if (pending >= 0) message += "，待执行 " + pending + " 个";
                if (workerCount > 0) message += "，并发进程 " + workerCount + " 个";
                String finalMessage = message;
                runOnUiThread(() -> {
                    dialog.dismiss();
                    updateServerStatus(true);
                    Toast.makeText(this, finalMessage, Toast.LENGTH_LONG).show();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(this, "连接失败：" + message(ex), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private JSONObject requestAuthed(String baseUrl, String token, String endpoint) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(baseUrl + endpoint).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        if (!token.isEmpty()) conn.setRequestProperty("X-Server-Token", token);
        int code = conn.getResponseCode();
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String text = readAll(stream);
        if (code == 401) throw new IllegalStateException("服务器密钥不正确");
        if (code >= 400) throw new IllegalStateException("HTTP " + code);
        JSONObject json = text.isEmpty() ? new JSONObject() : new JSONObject(text);
        if (!json.optBoolean("ok", false)) throw new IllegalStateException(json.optString("message", "服务器未返回 ok"));
        return json;
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

    private String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private void commitInputPercent() {
        String text = percentInput.getText().toString().trim();
        if (text.isEmpty()) {
            syncControlsFromScale();
            return;
        }
        try {
            int raw = Integer.parseInt(text);
            int percent = AppSettings.clampPercent(raw);
            if (percent != raw) {
                Toast.makeText(this, "字体大小范围是 50% - 200%", Toast.LENGTH_SHORT).show();
            }
            applyPercent(percent, true);
        } catch (Exception ignored) {
            syncControlsFromScale();
        }
    }

    private void applyPercent(int percent, boolean fromInput) {
        percent = AppSettings.clampPercent(percent);
        selectedScale = AppSettings.percentToScale(percent);
        AppSettings.setFontPercent(this, percent);
        syncing = true;
        if (fontSeekBar != null) fontSeekBar.setProgress(percent - AppSettings.MIN_FONT_PERCENT);
        if (percentInput != null && !fromInput) percentInput.setText(String.valueOf(percent));
        syncing = false;
        currentText.setText(currentFontText());
        renderPreview();
    }

    private void syncControlsFromScale() {
        int percent = AppSettings.scaleToPercent(selectedScale);
        syncing = true;
        if (fontSeekBar != null) fontSeekBar.setProgress(percent - AppSettings.MIN_FONT_PERCENT);
        if (percentInput != null) percentInput.setText(String.valueOf(percent));
        syncing = false;
        currentText.setText(currentFontText());
    }

    private void renderPreview() {
        preview.removeAllViews();
        preview.addView(Ui.text(this, "活动详情", 18, Ui.TEXT, Typeface.BOLD));
        TextView body = Ui.text(this, "这里预览标题、正文和辅助说明的大小。选择后会保存到本地，并影响主要页面的文字显示。", 14, Ui.TEXT, Typeface.NORMAL);
        body.setLineSpacing(Ui.dp(this, 4), 1.0f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.setMargins(0, Ui.dp(this, 10), 0, 0);
        preview.addView(body, bodyLp);
        TextView sub = Ui.text(this, "当前：" + currentFontText(), 12, Ui.MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, Ui.dp(this, 10), 0, 0);
        preview.addView(sub, subLp);
    }

    private String currentFontText() {
        int percent = AppSettings.scaleToPercent(selectedScale);
        return AppSettings.fontScaleLabel(selectedScale) + " · " + percent + "%";
    }
}
