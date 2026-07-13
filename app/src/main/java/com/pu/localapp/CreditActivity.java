package com.pu.localapp;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

public class CreditActivity extends Activity {
    private AppDb db;
    private PuApi api;
    private Models.Account account;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new AppDb(this);
        api = new PuApi(this);
        account = db.getAccountByKey(getIntent().getStringExtra(DetailActivity.EXTRA_ACCOUNT_KEY));
        buildUi();
        loadCredit();
    }

    private void buildUi() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundResource(R.drawable.bg_credit);
        Ui.applySystemBars(page);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 8));
        Button back = Ui.button(this, "‹", Ui.SURFACE, Ui.TEXT);
        back.setTextSize(Ui.fontSp(this, 22));
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 36)));
        TextView title = Ui.text(this, "我的分数", 22, Ui.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.setMargins(Ui.dp(this, 12), 0, 0, 0);
        top.addView(title, tlp);
        page.addView(top);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
    }

    private void loadCredit() {
        showLoading("正在查询分数...");
        new Thread(() -> {
            try {
                JSONObject act = api.activityCredit(account);
                JSONObject app = api.applyCredit(account);
                JSONObject info = api.userInfo(account);
                runOnUiThread(() -> render(act.optJSONObject("data"), app.optJSONObject("data"), info.optJSONObject("data")));
            } catch (Exception ex) {
                runOnUiThread(() -> showLoading("查询失败: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())));
            }
        }).start();
    }

    private void render(JSONObject act, JSONObject app, JSONObject info) {
        content.removeAllViews();
        String integrity = info == null ? "未知" : Models.firstText(info, "cx", "integrity", "creditValue");
        if (integrity.isEmpty()) integrity = "未知";
        double actCredit = number(act, "credit", "totalScore", "score");
        double appCredit = number(app, "credit", "totalScore", "score");
        addSummary(actCredit, appCredit, integrity);
        addList("二课活动明细", act);
        addList("成果学分明细", app);
    }

    private void addSummary(double actCredit, double appCredit, String integrity) {
        LinearLayout card = card();
        card.addView(Ui.text(this, "总分", 15, Ui.MUTED, Typeface.BOLD));
        card.addView(Ui.text(this, format(actCredit + appCredit), 34, Ui.PRIMARY, Typeface.BOLD));
        card.addView(Ui.text(this, "活动分 " + format(actCredit) + "    成果分 " + format(appCredit) + "    诚信值 " + integrity, 15, Ui.TEXT, Typeface.NORMAL));
        content.addView(card);
    }

    private void addList(String title, JSONObject data) {
        LinearLayout card = card();
        card.addView(Ui.text(this, title, 18, Ui.TEXT, Typeface.BOLD));
        JSONArray list = data == null ? null : data.optJSONArray("list");
        if (list == null || list.length() == 0) {
            TextView empty = Ui.text(this, "暂无明细", 14, Ui.MUTED, Typeface.NORMAL);
            empty.setPadding(0, Ui.dp(this, 10), 0, 0);
            card.addView(empty);
        } else {
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) continue;
                String name = Models.firstText(item, "name", "activityName", "title");
                if (!isCreditCategory(name)) continue;
                card.addView(row(name, Models.firstText(item, "credit", "score", "totalScore")));
            }
        }
        content.addView(card);
    }

    private boolean isCreditCategory(String name) {
        if (name == null) return false;
        String text = name.trim();
        if (text.isEmpty()) return false;
        return "思想政治与道德修养".equals(text)
                || "社会实践与志愿服务".equals(text)
                || "文化艺术与身心发展".equals(text)
                || "学术科技与创新创业".equals(text)
                || "社会工作与技能拓展".equals(text);
    }

    private LinearLayout row(String name, String score) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 10), 0, 0);
        row.addView(Ui.text(this, name.isEmpty() ? "未命名项目" : name, 15, Ui.TEXT, Typeface.NORMAL), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(Ui.text(this, score.isEmpty() ? "0" : score, 15, Ui.PRIMARY, Typeface.BOLD));
        return row;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        card.setBackground(Ui.strokeBg(Ui.SURFACE, Ui.LINE, 8, this));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(this, 12));
        card.setLayoutParams(lp);
        return card;
    }

    private void showLoading(String text) {
        content.removeAllViews();
        TextView tv = Ui.text(this, text, 16, Ui.MUTED, Typeface.NORMAL);
        tv.setGravity(Gravity.CENTER);
        content.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 220)));
    }

    private double number(JSONObject obj, String... keys) {
        if (obj == null) return 0;
        for (String key : keys) {
            if (obj.has(key)) return obj.optDouble(key, 0);
        }
        return 0;
    }

    private String format(double value) {
        return String.format(java.util.Locale.CHINA, "%.2f", value);
    }
}
