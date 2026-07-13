package com.pu.localapp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DetailActivity extends Activity {
    static final String EXTRA_ACTIVITY_ID = "activity_id";
    static final String EXTRA_ACCOUNT_KEY = "account_key";
    static final String EXTRA_ACTIVITY_JSON = "activity_json";
    private static final int DETAIL_PEACH = Color.rgb(255, 226, 204);
    private static final int DETAIL_CARD = Color.rgb(255, 250, 246);
    private static final int DETAIL_BODY_BG = Color.rgb(247, 247, 247);
    private AppDb db;
    private PuApi api;
    private ServerApi serverApi;
    private Models.Account account;
    private Models.Activity activity;
    private Models.Activity activityFromList;
    private LinearLayout content;
    private LinearLayout detailBody;
    private Button reserveBtn;
    private Button joinBtn;
    private TextView countdownView;
    private TextView statusBadge;
    private LinearLayout statsRow;
    private TextView memberCountView;
    private TextView signInCountView;
    private TextView signOutCountView;
    private TextView cdDay;
    private TextView cdHour;
    private TextView cdMinute;
    private TextView cdSecond;
    private final ImageLoader imageLoader = new ImageLoader();
    private final Handler countdownHandler = new Handler(Looper.getMainLooper());
    private final Runnable countdownTicker = new Runnable() {
        @Override
        public void run() {
            updateCountdownUi();
            countdownHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new AppDb(this);
        api = new PuApi(this);
        serverApi = new ServerApi(this);
        account = db.getAccountByKey(getIntent().getStringExtra(EXTRA_ACCOUNT_KEY));
        long activityId = getIntent().getLongExtra(EXTRA_ACTIVITY_ID, 0);
        activityFromList = activityFromIntent();
        buildUi();
        loadDetail(activityId);
    }

    @Override
    protected void onDestroy() {
        countdownHandler.removeCallbacks(countdownTicker);
        super.onDestroy();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.SURFACE);
        Ui.applySystemBars(root);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout top = new FrameLayout(this);
        top.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
        top.setBackgroundColor(DETAIL_PEACH);
        Button back = Ui.button(this, "‹", Ui.SURFACE, Ui.TEXT);
        back.setTextSize(Ui.fontSp(this, 25));
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 36), Gravity.LEFT | Gravity.CENTER_VERTICAL);
        blp.setMargins(Ui.dp(this, 11), 0, 0, 0);
        top.addView(back, blp);
        TextView title = Ui.text(this, "活动详情", 17, Ui.TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        page.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(DETAIL_BODY_BG);
        scroll.setPadding(0, 0, 0, Ui.dp(this, 88));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, Ui.dp(this, 20));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout actionBar = new LinearLayout(this);
        actionBar.setOrientation(LinearLayout.HORIZONTAL);
        actionBar.setGravity(Gravity.CENTER);
        actionBar.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 12));
        actionBar.setBackground(Ui.strokeBg(Color.argb(250, 255, 255, 255), Ui.LINE_STRONG, 1, 0, this));
        actionBar.setElevation(Ui.dp(this, 8));
        reserveBtn = Ui.softButton(this, "预约");
        joinBtn = Ui.primaryButton(this, "报名");
        reserveBtn.setTextSize(Ui.fontSp(this, 14));
        joinBtn.setTextSize(Ui.fontSp(this, 14));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
        btnLp.setMargins(0, 0, Ui.dp(this, 8), 0);
        actionBar.addView(reserveBtn, btnLp);
        LinearLayout.LayoutParams joinLp = new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f);
        joinLp.setMargins(Ui.dp(this, 8), 0, 0, 0);
        actionBar.addView(joinBtn, joinLp);
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(actionBar, alp);
        setContentView(root);
    }

    private void loadDetail(long activityId) {
        showLoadingRows("正在加载活动详情...");
        new Thread(() -> {
            try {
                Models.Activity loaded = api.getActivityInfo(account, activityId);
                loaded.fillMissingFrom(activityFromList);
                runOnUiThread(() -> {
                    activity = loaded;
                    renderDetail();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    showLoadingRows("详情加载失败: " + message(ex));
                    setButtonsEnabled(false);
                });
            }
        }).start();
    }

    private Models.Activity activityFromIntent() {
        String json = getIntent().getStringExtra(EXTRA_ACTIVITY_JSON);
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return Models.Activity.fromJson(new JSONObject(json));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void renderDetail() {
        content.removeAllViews();
        detailBody = null;
        addHero();
        addDetailBodyPanel();
        addSection("归属院系", firstNonEmpty(joinNames(activity.allowCollege), activity.creatorName, "无限制"));
        addSection("活动附件", firstNonEmpty(rawText("attachmentName", "fileName", "annexName"), "无"));
        addSection("活动简介", firstNonEmpty(activity.description, "暂无简介"));
        addSection("活动标签", firstNonEmpty(rawText("tags", "tagName", "labelName"), "无"));
        addSection("活动报名与签到", signRuleText());
        addSection("参与对象", targetText());
        addSection("参与人员分配", firstNonEmpty(rawText("allocation", "assignMode", "participantLimit"), "无限制"));
        addSection("联系方式", contactText());
        reserveBtn.setOnClickListener(v -> reserve());
        joinBtn.setOnClickListener(v -> join());
        startCountdownTicker();
        loadMemberSignStats();
    }

    private void addDetailBodyPanel() {
        FrameLayout bodyHost = new FrameLayout(this);
        bodyHost.setBackgroundColor(DETAIL_PEACH);
        detailBody = new LinearLayout(this);
        detailBody.setOrientation(LinearLayout.VERTICAL);
        detailBody.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 10));
        detailBody.setBackground(topRoundedBg(DETAIL_BODY_BG, 12));
        bodyHost.addView(detailBody, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, -1), 0, 0);
        content.addView(bodyHost, lp);
    }

    private void addHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 14));
        hero.setBackgroundColor(DETAIL_PEACH);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);

        FrameLayout imageWrap = new FrameLayout(this);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(Ui.bg(Color.WHITE, 9, this));
        image.setClipToOutline(true);
        imageWrap.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        statusBadge = Ui.chip(this, displayStatus(), statusColor(), Color.WHITE);
        statusBadge.setTextSize(Ui.fontSp(this, 11));
        imageWrap.addView(statusBadge, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT));
        top.addView(imageWrap, new LinearLayout.LayoutParams(Ui.dp(this, 84), Ui.dp(this, 84)));
        imageLoader.load(image, activity.coverUrl);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ilp.setMargins(Ui.dp(this, 13), Ui.dp(this, 2), 0, 0);
        top.addView(info, ilp);
        TextView title = Ui.text(this, firstNonEmpty(activity.name, "未知活动"), 15, Ui.TEXT, Typeface.BOLD);
        title.setMaxLines(3);
        title.setLineSpacing(Ui.dp(this, 4), 1.0f);
        info.addView(title);
        TextView category = Ui.text(this, "分类：" + firstNonEmpty(activity.categoryName, "未分类"), 12, Ui.PRIMARY, Typeface.NORMAL);
        LinearLayout.LayoutParams categoryLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        categoryLp.setMargins(0, Ui.dp(this, 10), 0, 0);
        info.addView(category, categoryLp);
        info.addView(Ui.text(this, "兑换学时： " + creditText(activity.credit) + "      PU银豆： " + firstNonEmpty(activity.integrity, "0"), 13, Ui.TEXT, Typeface.NORMAL));
        hero.addView(top);

        LinearLayout countdownRow = new LinearLayout(this);
        countdownRow.setOrientation(LinearLayout.HORIZONTAL);
        countdownRow.setGravity(Gravity.CENTER_VERTICAL);
        countdownView = Ui.text(this, countdownLabel(), 12, Ui.TEXT, Typeface.NORMAL);
        countdownRow.addView(countdownView);
        cdDay = countdownBox();
        cdHour = countdownBox();
        cdMinute = countdownBox();
        cdSecond = countdownBox();
        countdownRow.addView(cdDay, countdownBoxLp());
        countdownRow.addView(cdHour, countdownBoxLp());
        countdownRow.addView(timeColon());
        countdownRow.addView(cdMinute, countdownBoxLp());
        countdownRow.addView(timeColon());
        countdownRow.addView(cdSecond, countdownBoxLp());
        LinearLayout.LayoutParams countdownLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        countdownLp.setMargins(0, Ui.dp(this, 14), 0, 0);
        hero.addView(countdownRow, countdownLp);

        addOfficialInfoCard(hero);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(this, 8));
        content.addView(hero, lp);
    }

    private void addStat(LinearLayout stats, String value, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        TextView valueView = Ui.text(this, firstNonEmpty(value, "0"), 18, Ui.TEXT, Typeface.NORMAL);
        valueView.setGravity(Gravity.CENTER);
        TextView labelView = Ui.text(this, label, 12, Ui.MUTED, Typeface.NORMAL);
        labelView.setGravity(Gravity.CENTER);
        box.addView(valueView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.setMargins(0, Ui.dp(this, 3), 0, 0);
        box.addView(labelView, labelLp);
        stats.addView(box, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if ("已报名".equals(label)) memberCountView = valueView;
        if ("已签到".equals(label)) signInCountView = valueView;
        if ("已签退".equals(label)) signOutCountView = valueView;
    }

    private void addOfficialInfoCard(LinearLayout hero) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 6), Ui.dp(this, 17), Ui.dp(this, 6), Ui.dp(this, 14));
        card.setBackground(Ui.bg(DETAIL_CARD, 8, this));

        statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setGravity(Gravity.CENTER);
        addStat(statsRow, String.valueOf(activity.allowUserCount), "可参与人数");
        addStat(statsRow, countDisplay(activity.joinUserCount), "已报名");
        addStat(statsRow, signCountText(), "已签到");
        addStat(statsRow, quitCountText(), "已签退");
        card.addView(statsRow);

        View dashed = new DashedLine(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
        dlp.setMargins(0, Ui.dp(this, 17), 0, Ui.dp(this, 17));
        card.addView(dashed, dlp);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.VERTICAL);
        timeRow.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        timeRow.addView(timeHeaderRow());
        timeRow.addView(timePointRow());
        card.addView(timeRow);

        View solid = Ui.line(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1));
        slp.setMargins(Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 14));
        card.addView(solid, slp);

        TextView duration = Ui.text(this, "活动时长： " + durationText(), 15, Ui.TEXT, Typeface.NORMAL);
        duration.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), Ui.dp(this, 8));
        card.addView(duration);
        TextView address = Ui.text(this, "活动地址： " + firstNonEmpty(activity.address, "暂无地址"), 15, Ui.TEXT, Typeface.NORMAL);
        address.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        card.addView(address);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 13), 0, 0);
        hero.addView(card, lp);
    }

    private void addTimeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        card.setBackground(Ui.bg(Ui.SURFACE, 10, this));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(timeBlock("报名时间", activity.joinStartTime, activity.joinEndTime), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(timeBlock("活动时间", activity.startTime, activity.endTime), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(row);
        card.addView(Ui.line(this));
        TextView duration = Ui.text(this, "活动时长： " + durationText(), 15, Ui.TEXT, Typeface.NORMAL);
        duration.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 5));
        card.addView(duration);
        card.addView(Ui.text(this, "活动地址： " + firstNonEmpty(activity.address, "暂无地址"), 15, Ui.TEXT, Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Ui.dp(this, 10));
        content.addView(card, lp);
    }

    private LinearLayout timeBlock(String title, String start, String end) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(sectionHeader(title, 16));
        LinearLayout range = new LinearLayout(this);
        range.setOrientation(LinearLayout.HORIZONTAL);
        range.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(Ui.dp(this, 18), Ui.dp(this, 15), Ui.dp(this, 2), 0);
        block.addView(range, rlp);
        range.addView(timePoint(start), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        DashedLine mid = new DashedLine(this);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 1));
        mlp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        range.addView(mid, mlp);
        range.addView(timePoint(end), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return block;
    }

    private LinearLayout timeHeaderRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(sectionHeader("报名时间", 15), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(sectionHeader("活动时间", 15), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private LinearLayout timePointRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 14), 0, 0);
        row.setLayoutParams(lp);
        row.addView(timePoint(activity.joinStartTime), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(timeDash(), new LinearLayout.LayoutParams(Ui.dp(this, 14), Ui.dp(this, 1)));
        row.addView(timePoint(activity.joinEndTime), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(timePoint(activity.startTime), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(timeDash(), new LinearLayout.LayoutParams(Ui.dp(this, 14), Ui.dp(this, 1)));
        row.addView(timePoint(activity.endTime), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View timeDash() {
        DashedLine line = new DashedLine(this);
        line.setAlpha(0.85f);
        return line;
    }

    private LinearLayout timePoint(String value) {
        LinearLayout point = new LinearLayout(this);
        point.setOrientation(LinearLayout.VERTICAL);
        point.setGravity(Gravity.CENTER);
        TextView time = Ui.text(this, shortTime(value), 15, Ui.TEXT, Typeface.NORMAL);
        time.setGravity(Gravity.CENTER);
        makeSingleLineFit(time, 12, 15);
        TextView date = Ui.text(this, shortDate(value), 12, Ui.MUTED, Typeface.NORMAL);
        date.setGravity(Gravity.CENTER);
        makeSingleLineFit(date, 10, 12);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.setMargins(0, Ui.dp(this, 6), 0, 0);
        point.addView(time, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        point.addView(date, dlp);
        return point;
    }

    private void makeSingleLineFit(TextView view, int minSp, int maxSp) {
        view.setSingleLine(true);
        view.setMaxLines(1);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            view.setAutoSizeTextTypeUniformWithConfiguration(minSp, maxSp, 1, TypedValue.COMPLEX_UNIT_SP);
        }
    }

    private void addSection(String title, String value) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(Ui.dp(this, 15), Ui.dp(this, 13), Ui.dp(this, 15), Ui.dp(this, 13));
        section.setBackground(Ui.strokeBg(Color.WHITE, Color.rgb(239, 239, 239), 8, this));
        LinearLayout label = sectionHeader(title, 14);
        TextView body = Ui.text(this, value == null || value.trim().isEmpty() ? "无" : value, 13, Ui.MUTED, Typeface.NORMAL);
        body.setPadding(Ui.dp(this, 2), Ui.dp(this, 9), Ui.dp(this, 2), Ui.dp(this, 6));
        section.addView(label);
        section.addView(body);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 8));
        (detailBody == null ? content : detailBody).addView(section, lp);
    }

    private GradientDrawable topRoundedBg(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float radius = Ui.dp(this, radiusDp);
        drawable.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        return drawable;
    }

    private LinearLayout sectionHeader(String title, int sp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(Ui.dp(this, 3), Ui.PRIMARY);
        dot.setBackground(ring);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(this, 14), Ui.dp(this, 14));
        dlp.setMargins(0, 0, Ui.dp(this, 8), 0);
        row.addView(dot, dlp);
        row.addView(Ui.text(this, title, sp, Ui.TEXT, Typeface.BOLD));
        return row;
    }

    private TextView countdownBox() {
        TextView tv = Ui.text(this, "00", 13, Ui.TEXT, Typeface.NORMAL);
        tv.setGravity(Gravity.CENTER);
        tv.setMinWidth(Ui.dp(this, 34));
        tv.setPadding(Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5), Ui.dp(this, 5));
        tv.setBackground(Ui.bg(Color.WHITE, 5, this));
        return tv;
    }

    private LinearLayout.LayoutParams countdownBoxLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(this, 5), 0, 0, 0);
        return lp;
    }

    private TextView timeColon() {
        TextView tv = Ui.text(this, ":", 13, Ui.TEXT, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(this, 3), 0, 0, 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void reserve() {
        long now = BeijingTime.now(this);
        if (!activity.notStarted(now)) {
            showMessage("不能预约", "报名已经开始，请直接点击“报名”。");
            return;
        }
        if (!activity.eligibleFor(account) || activity.isFull()) {
            showMessage("不能预约", "当前账号不符合参与条件，或活动已经满员。");
            return;
        }
        long joinStart = TimeUtil.parseMillis(activity.joinStartTime);
        if (joinStart <= 0) {
            showMessage("活动报名时间异常", "当前活动没有有效的报名开始时间，无法创建到点预约。请刷新详情后再试。");
            return;
        }
        if (hasPendingReservation(activity.id)) {
            showMessage("您已预约该活动", "无需重复预约。执行结果可在“我的活动”里查看。");
            return;
        }
        if (AppSettings.serverEnabled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("选择预约方式")
                    .setItems(new String[]{"服务器预约", "本地预约"}, (dialog, which) -> {
                        if (which == 0) reserveOnServer();
                        else reserveLocal();
                    })
                    .show();
        } else {
            reserveLocal();
        }
    }

    private void reserveLocal() {
        ReservationScheduler scheduler = new ReservationScheduler(this);
        scheduler.schedule(account, activity);
        Intent exactSettings = scheduler.exactAlarmSettingsIntent();
        Intent notificationSettings = notificationSettingsIntent();
        boolean exact = scheduler.canScheduleExact();
        boolean notifications = notificationEnabled();
        String actionText = !exact && exactSettings != null ? "去授权" : (!notifications && notificationSettings != null ? "去设置" : null);
        Runnable action = !exact && exactSettings != null ? () -> startActivity(exactSettings) : (!notifications && notificationSettings != null ? () -> startActivity(notificationSettings) : null);
        showMessage(
                "预约已保存",
                "执行时间：" + firstNonEmpty(activity.joinStartTime, "报名开始时") +
                        "\n精确闹钟：" + (exact ? "已可用" : "未授权，系统可能延迟执行") +
                        "\n通知权限：" + (notifications ? "已可用" : "未开启，请到系统设置允许通知") +
                        "\n开机后会尝试恢复待执行预约。\n建议保持联网，不要强行停止 App，并关闭严格省电限制。",
                actionText,
                action
        );
    }

    private void reserveOnServer() {
        if (!serverApi.configured()) {
            showMessage("服务器未配置", "请先到“我的 - 设置”填写服务器连接地址。");
            return;
        }
        ProgressDialog dialog = ProgressDialog.show(this, "", "正在提交服务器预约...", true, false);
        new Thread(() -> {
            try {
                Models.Reservation remote = serverApi.createReservation(account, activity);
                Models.Reservation local = new Models.Reservation();
                local.accountKey = account.key();
                local.sid = account.sid;
                local.username = account.username;
                local.activityId = remote.activityId == 0 ? activity.id : remote.activityId;
                local.activityName = firstNonEmpty(remote.activityName, activity.name);
                local.runAt = remote.runAt == 0 ? TimeUtil.parseMillis(activity.joinStartTime) : remote.runAt;
                local.status = firstNonEmpty(remote.status, "pending");
                local.lastResult = firstNonEmpty(remote.lastResult, "服务器已接收预约任务");
                local.retryCount = remote.retryCount;
                local.createdAt = remote.createdAt == 0 ? BeijingTime.now(this) : remote.createdAt;
                local.executor = "server";
                local.remoteId = remote.remoteId;
                local.serverUrl = AppSettings.serverBaseUrl(this);
                long id = db.upsertReservation(local);
                db.updateReservationRemote(id, remote.remoteId, local.serverUrl);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    showMessage("服务器预约已保存",
                            "执行时间：" + firstNonEmpty(activity.joinStartTime, "报名开始时") +
                                    "\n服务器任务：" + firstNonEmpty(remote.remoteId, "已创建") +
                                    "\n结果可在“我的活动”里刷新查看。");
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    showMessage("服务器预约失败", message(ex) + "\n可改用本地预约，或检查设置里的服务器地址。");
                });
            }
        }).start();
    }

    private boolean hasPendingReservation(long activityId) {
        if (account == null || activityId == 0) return false;
        for (Models.Reservation reservation : db.reservationsForAccount(account.key())) {
            if (reservation.activityId == activityId && "pending".equals(reservation.status)) return true;
        }
        return false;
    }

    private boolean notificationEnabled() {
        if (android.os.Build.VERSION.SDK_INT < 33) return true;
        return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private Intent notificationSettingsIntent() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        return intent;
    }

    private void join() {
        if (activity.isFull()) {
            showMessage("不能报名", "活动已经满员。");
            return;
        }
        ProgressDialog dialog = ProgressDialog.show(this, "", "正在报名...", true, false);
        new Thread(() -> {
            try {
                String msg = api.join(account, activity.id);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    showMessage("报名结果", msg);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    showMessage("报名失败", message(ex));
                });
            }
        }).start();
    }

    private void refreshButtons() {
        long now = BeijingTime.now(this);
        reserveBtn.setText(activity.notStarted(now) ? "预约" : "报名已开始");
        reserveBtn.setTextColor(activity.notStarted(now) ? Ui.PRIMARY : Ui.MUTED);
        reserveBtn.setBackground(activity.notStarted(now)
                ? Ui.ripple(Ui.strokeBg(Ui.PRIMARY_SOFT, Color.rgb(255, 210, 178), 1, 14, this), Color.argb(36, 255, 122, 26))
                : Ui.ripple(Ui.strokeBg(Color.rgb(246, 246, 246), Ui.LINE_STRONG, 1, 14, this), Color.argb(24, 0, 0, 0)));
        if (activity.isFull()) {
            joinBtn.setText("已满员");
            joinBtn.setEnabled(false);
            joinBtn.setTextColor(Ui.MUTED);
            joinBtn.setBackground(Ui.bg(Color.rgb(238, 238, 238), 12, this));
        } else {
            joinBtn.setText("报名");
            joinBtn.setEnabled(true);
            joinBtn.setTextColor(Color.WHITE);
            joinBtn.setBackground(Ui.bg(Ui.PRIMARY, 12, this));
        }
    }

    private void startCountdownTicker() {
        countdownHandler.removeCallbacks(countdownTicker);
        updateCountdownUi();
        countdownHandler.postDelayed(countdownTicker, 1000);
    }

    private void updateCountdownUi() {
        if (activity == null) return;
        if (countdownView != null) {
            countdownView.setText(countdownLabel());
            updateCountdownBlocks();
        }
        if (statusBadge != null) {
            statusBadge.setText(displayStatus());
        }
        refreshButtons();
    }

    private void updateCountdownBlocks() {
        if (cdDay == null || cdHour == null || cdMinute == null || cdSecond == null) return;
        long target = countdownTargetMillis();
        long diff = target <= 0 ? 0 : Math.max(0, target - BeijingTime.now(this));
        long seconds = diff / 1000L;
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long secs = seconds % 60L;
        cdDay.setText(days + "天");
        cdHour.setText(String.format(Locale.CHINA, "%02d", hours));
        cdMinute.setText(String.format(Locale.CHINA, "%02d", minutes));
        cdSecond.setText(String.format(Locale.CHINA, "%02d", secs));
    }

    private void setButtonsEnabled(boolean enabled) {
        reserveBtn.setEnabled(enabled);
        joinBtn.setEnabled(enabled);
    }

    private void loadMemberSignStats() {
        long activityId = activity == null ? 0 : activity.id;
        if (activityId == 0) return;
        boolean hasOfficialSignStats = activity.signInUserCount >= 0 && activity.signOutUserCount >= 0;
        if (hasOfficialSignStats) return;
        new Thread(() -> {
            try {
                Models.SignStats stats = api.getActivitySignStats(account, activityId);
                runOnUiThread(() -> applyMemberSignStats(stats));
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void applyMemberSignStats(Models.SignStats stats) {
        if (stats == null) return;
        if (memberCountView != null && activity.joinUserCount <= 0 && stats.memberCount > 0) {
            memberCountView.setText(String.valueOf(stats.memberCount));
        }
        if (signInCountView != null && activity.signInUserCount < 0 && stats.signInCount >= 0) {
            signInCountView.setText(String.valueOf(stats.signInCount));
        }
        if (signOutCountView != null && activity.signOutUserCount < 0 && stats.signOutCount >= 0) {
            signOutCountView.setText(String.valueOf(stats.signOutCount));
        }
    }

    private void showLoadingRows(String text) {
        content.removeAllViews();
        TextView tv = Ui.text(this, text, 17, Ui.MUTED, Typeface.NORMAL);
        tv.setGravity(Gravity.CENTER);
        content.addView(tv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 220)));
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private void showMessage(String title, String text) {
        showMessage(title, text, null, null);
    }

    private void showMessage(String title, String text, String secondaryText, Runnable secondaryAction) {
        Dialog dialog = new Dialog(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 16));
        card.setBackground(Ui.strokeBg(Color.WHITE, Color.rgb(238, 238, 238), 12, this));

        TextView titleView = Ui.text(this, title, 20, Ui.TEXT, Typeface.BOLD);
        card.addView(titleView);

        TextView body = Ui.text(this, text, 15, Ui.MUTED, Typeface.NORMAL);
        body.setLineSpacing(Ui.dp(this, 4), 1.0f);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.setMargins(0, Ui.dp(this, 14), 0, Ui.dp(this, 18));
        card.addView(body, bodyLp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        if (secondaryText != null && secondaryAction != null) {
            Button secondary = Ui.softButton(this, secondaryText);
            secondary.setTextSize(Ui.fontSp(this, 14));
            secondary.setOnClickListener(v -> {
                dialog.dismiss();
                secondaryAction.run();
            });
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Ui.dp(this, 92), Ui.dp(this, 38));
            slp.setMargins(0, 0, Ui.dp(this, 10), 0);
            buttons.addView(secondary, slp);
        }
        Button ok = Ui.primaryButton(this, "知道了");
        ok.setTextSize(Ui.fontSp(this, 14));
        ok.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(ok, new LinearLayout.LayoutParams(Ui.dp(this, 92), Ui.dp(this, 38)));
        card.addView(buttons);

        dialog.setContentView(card);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setDimAmount(0.42f);
                dialog.getWindow().setLayout(Ui.dp(this, 304), ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private String displayStatus() {
        long now = BeijingTime.now(this);
        long start = TimeUtil.parseMillis(activity.startTime);
        long end = TimeUtil.parseMillis(activity.endTime);
        if (start > 0 && now < start) return "未开始";
        if (end > 0 && now <= end) return "进行中";
        if (end > 0 && now > end) return "已结束";
        if (activity.inJoinWindow(now)) return "报名中";
        return firstNonEmpty(activity.statusName, "活动");
    }

    private int statusColor() {
        String status = displayStatus();
        if ("进行中".equals(status) || "报名中".equals(status)) return Ui.PRIMARY;
        if ("已结束".equals(status)) return Ui.MUTED;
        return Ui.DANGER;
    }

    private String creditText(String value) {
        if (value == null || value.trim().isEmpty()) return "0";
        try {
            float n = Float.parseFloat(value.trim());
            if (Math.abs(n - Math.round(n)) < 0.001f) return String.valueOf(Math.round(n));
            return String.format(Locale.CHINA, "%.2f", n);
        } catch (Exception ignored) {
            return value.trim();
        }
    }

    private String countdownLabel() {
        long now = BeijingTime.now(this);
        long joinStart = TimeUtil.parseMillis(activity.joinStartTime);
        long activityStart = TimeUtil.parseMillis(activity.startTime);
        long activityEnd = TimeUtil.parseMillis(activity.endTime);
        if (joinStart > 0 && now < joinStart) return "距离活动报名开始:";
        if (activityStart > 0 && now < activityStart) return "距离活动开始:";
        if (activityEnd > 0 && now <= activityEnd) return "距离活动结束:";
        return "活动已结束:";
    }

    private long countdownTargetMillis() {
        long now = BeijingTime.now(this);
        long joinStart = TimeUtil.parseMillis(activity.joinStartTime);
        long activityStart = TimeUtil.parseMillis(activity.startTime);
        long activityEnd = TimeUtil.parseMillis(activity.endTime);
        if (joinStart > 0 && now < joinStart) return joinStart;
        if (activityStart > 0 && now < activityStart) return activityStart;
        if (activityEnd > 0 && now <= activityEnd) return activityEnd;
        return 0;
    }

    private String countdownText() {
        long start = TimeUtil.parseMillis(activity.joinStartTime);
        if (start <= 0) return "未知";
        long diff = start - BeijingTime.now(this);
        if (diff <= 0) return "报名已开始";
        long seconds = diff / 1000;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format(Locale.CHINA, "%d天 %02d:%02d:%02d", days, hours, minutes, secs);
    }

    private String durationText() {
        long start = TimeUtil.parseMillis(activity.startTime);
        long end = TimeUtil.parseMillis(activity.endTime);
        if (start <= 0 || end <= start) return "未知";
        long hours = (end - start) / 3600000L;
        long minutes = ((end - start) % 3600000L) / 60000L;
        if (hours > 0 && minutes > 0) return hours + "小时" + minutes + "分钟";
        if (hours > 0) return hours + "小时";
        return minutes + "分钟";
    }

    private String shortTime(String value) {
        if (value == null || value.length() < 16) return "--:--";
        return value.substring(11, 16);
    }

    private String shortDate(String value) {
        if (value == null || value.length() < 10) return "未知日期";
        return value.substring(0, 10).replace('-', '.');
    }

    private String signRuleText() {
        String sign = firstNonEmpty(rawText("signTypeName", "signWay", "signType"), "扫码签到(可提前60分钟)");
        String quit = firstNonEmpty(rawText("quitTypeName", "signOutWay", "quitType"), "需要签退(可提前30分钟)");
        String apply = firstNonEmpty(rawText("applyTypeName", "joinTypeName", "enrollType"), "报名制（报名无需审核）");
        return "报名方式： " + apply + "\n签到方式： " + sign + "\n签退情况： " + quit;
    }

    private String signCountText() {
        if (activity.signInUserCount >= 0) return String.valueOf(activity.signInUserCount);
        return firstNonEmpty(
                rawText("signInUserCount", "signedUserCount", "signedCount", "signUserCount", "checkInUserCount", "checkInCount", "attendanceCount", "actualSignCount"),
                "--"
        );
    }

    private String quitCountText() {
        if (activity.signOutUserCount >= 0) return String.valueOf(activity.signOutUserCount);
        return firstNonEmpty(
                rawText("signedOutUserCount", "signedOutCount", "quitUserCount", "signOutUserCount", "signOutCount", "checkoutCount", "actualSignOutCount"),
                "--"
        );
    }

    private String countDisplay(int count) {
        return count < 0 ? "--" : String.valueOf(count);
    }

    private String targetText() {
        String college = joinNames(activity.allowCollege);
        String year = joinNames(activity.allowYear);
        return "活动院系： " + firstNonEmpty(college, "全部院系") + "\n活动年级： " + firstNonEmpty(year, "全部年级");
    }

    private String contactText() {
        String person = firstNonEmpty(rawText("contact", "contactName", "contacts", "contactPerson"), activity.creatorName);
        String phone = firstNonEmpty(rawText("contactPhone", "phone", "mobile", "tel"));
        if (isMaskedPhone(phone)) phone = "";
        if (phone.isEmpty()) phone = extractPhone(activity.description);
        if (phone.isEmpty()) phone = extractPhone(rawText("description", "content", "detail", "intro"));
        if (phone.isEmpty() && activity.raw != null) phone = extractPhone(activity.raw.toString());
        if (phone.isEmpty()) return firstNonEmpty(person, "暂无联系方式");
        return firstNonEmpty(person, "联系人") + "  " + phone.trim();
    }

    private boolean isMaskedPhone(String phone) {
        if (phone == null) return false;
        String compact = phone.trim().replace(" ", "").replace("-", "");
        return !compact.isEmpty() && compact.replace("*", "").isEmpty();
    }

    private String extractPhone(String text) {
        if (text == null || text.trim().isEmpty()) return "";
        Matcher matcher = Pattern.compile("(?<!\\d)1\\d{10}(?!\\d)|(?<!\\d)\\d{3,4}[- ]?\\d{7,8}(?!\\d)").matcher(text);
        return matcher.find() ? matcher.group().trim() : "";
    }

    private String rawText(String... keys) {
        JSONObject raw = activity.raw;
        if (raw == null) return "";
        return Models.firstText(raw, keys);
    }

    private String joinNames(JSONArray arr) {
        if (arr == null || arr.length() == 0) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            String name = Models.firstText(obj, "name", "title", "label");
            if (name.isEmpty()) continue;
            if (builder.length() > 0) builder.append("、");
            builder.append(name);
        }
        return builder.toString();
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }

    private static final class ColorPack {
        static int dark() {
            return android.graphics.Color.rgb(45, 45, 45);
        }
    }

    private static final class DashedLine extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DashedLine(android.content.Context context) {
            super(context);
            paint.setColor(Color.rgb(232, 226, 220));
            paint.setStrokeWidth(Ui.dp(context, 1));
            paint.setStyle(Paint.Style.STROKE);
            paint.setPathEffect(new DashPathEffect(new float[]{Ui.dp(context, 5), Ui.dp(context, 7)}, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float y = getHeight() / 2f;
            canvas.drawLine(0, y, getWidth(), y, paint);
        }
    }
}
