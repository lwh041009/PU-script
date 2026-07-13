package com.pu.localapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private AppDb db;
    private PuApi api;
    private ServerApi serverApi;
    private Models.Account account;
    private LinearLayout root;
    private FrameLayout content;
    private LinearLayout bottomNav;
    private final List<Models.Activity> allActivities = new ArrayList<>();
    private final List<Models.Activity> myActivities = new ArrayList<>();
    private final List<Models.Reservation> localReservations = new ArrayList<>();
    private final List<Models.ActivityType> activityTypes = new ArrayList<>();
    private LinearLayout myStatusTabs;
    private LinearLayout myListView;
    private HorizontalScrollView myStatusScroll;
    private TextView beijingHour;
    private TextView beijingMinute;
    private TextView beijingSecond;
    private TextView beijingSyncText;
    private String selectedStatus = "全部";
    private String selectedTypeId = "";
    private String searchQuery = "";
    private String mySelectedStatus = "全部";
    private int currentTab = 0;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTicker = new Runnable() {
        @Override
        public void run() {
            updateBeijingClock();
            clockHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new AppDb(this);
        api = new PuApi(this);
        serverApi = new ServerApi(this);
        BeijingTime.syncIfNeeded(this);
        BeijingTime.scheduleNextSync(this);
        new ReservationScheduler(this).ensureChannel();
        requestNotificationPermission();
        account = db.getLastAccount();
        if (account == null || account.token == null || account.token.isEmpty()) {
            showLogin();
        } else {
            showMain();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BeijingTime.syncIfNeeded(this);
        if (account != null && content != null) {
            if (currentTab == 1) {
                localReservations.clear();
                showTab(1);
            } else if (currentTab == 2) {
                showTab(2);
            }
        }
    }

    @Override
    protected void onDestroy() {
        clockHandler.removeCallbacks(clockTicker);
        super.onDestroy();
    }

    private void showLogin() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(Ui.dp(this, 24), Ui.dp(this, 64), Ui.dp(this, 24), Ui.dp(this, 24));
        page.setBackgroundResource(R.drawable.bg_login);
        Ui.applySystemBars(page);

        TextView brand = Ui.statusPill(this, "本地安卓工具", Ui.PRIMARY_SOFT, Ui.PRIMARY);
        page.addView(brand);

        TextView title = Ui.text(this, "PU 脚本", 32, Ui.TEXT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.setMargins(0, Ui.dp(this, 18), 0, 0);
        page.addView(title, titleLp);

        TextView sub = Ui.text(this, "活动查询、预约报名、分数查看都在手机本地完成", 14, Ui.MUTED, Typeface.NORMAL);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(Ui.dp(this, 4), 1.0f);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, Ui.dp(this, 10), 0, Ui.dp(this, 22));
        page.addView(sub, subLp);

        LinearLayout card = Ui.card(this);
        card.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18));
        TextView cardTitle = Ui.text(this, "学校账号登录", 18, Ui.TEXT, Typeface.BOLD);
        TextView cardSub = Ui.text(this, "学校名称建议填写全称，账号信息只保存在本机。", 13, Ui.MUTED, Typeface.NORMAL);
        cardSub.setLineSpacing(Ui.dp(this, 4), 1.0f);
        card.addView(cardTitle);
        LinearLayout.LayoutParams cardSubLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardSubLp.setMargins(0, Ui.dp(this, 8), 0, 0);
        card.addView(cardSub, cardSubLp);

        EditText school = input("学校全称");
        EditText username = input("学校账号");
        EditText password = input("密码");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(school, inputLp());
        card.addView(username, inputLp());
        card.addView(password, inputLp());

        Button login = Ui.primaryButton(this, "登录");
        LinearLayout.LayoutParams loginLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
        loginLp.setMargins(0, Ui.dp(this, 18), 0, 0);
        card.addView(login, loginLp);

        Button switchAccount = Ui.secondaryButton(this, "选择已保存账号");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        slp.setMargins(0, Ui.dp(this, 12), 0, 0);
        card.addView(switchAccount, slp);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, 0);
        page.addView(card, cardLp);

        login.setOnClickListener(v -> login(school.getText().toString(), username.getText().toString(), password.getText().toString(), login));
        switchAccount.setOnClickListener(v -> showAccountChooser(false));
        setContentView(page);
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(Ui.fontSp(this, 16));
        e.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        e.setBackground(Ui.strokeBg(Color.rgb(250, 250, 250), Ui.LINE_STRONG, 1, 12, this));
        return e;
    }

    private LinearLayout.LayoutParams inputLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50));
        lp.setMargins(0, Ui.dp(this, 18), 0, 0);
        return lp;
    }

    private void login(String schoolName, String username, String password, Button loginButton) {
        schoolName = schoolName.trim();
        username = username.trim();
        if (schoolName.isEmpty() || username.isEmpty() || password.isEmpty()) {
            toast("请填写学校、账号和密码");
            return;
        }
        loginButton.setEnabled(false);
        loginButton.setText("正在登录...");
        ProgressDialog dialog = ProgressDialog.show(this, "", "正在登录...", true, false);
        String finalSchoolName = schoolName;
        String finalUsername = username;
        new Thread(() -> {
            try {
                List<Models.School> schools = api.getSchools();
                Models.School school = matchSchool(schools, finalSchoolName);
                if (school == null) throw new IllegalStateException("未找到学校，请输入学校全称");
                Models.Account logged = api.login(school.name, school.sid, finalUsername, password);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    loginButton.setEnabled(true);
                    loginButton.setText("登录");
                    account = logged;
                    showMain();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    loginButton.setEnabled(true);
                    loginButton.setText("登录");
                    toast("登录失败：" + loginMessage(ex));
                });
            }
        }).start();
    }

    private Models.School matchSchool(List<Models.School> schools, String name) {
        for (Models.School s : schools) {
            if (s.name.equals(name)) return s;
        }
        for (Models.School s : schools) {
            if (s.name.contains(name) || name.contains(s.name)) return s;
        }
        return null;
    }

    private String loginMessage(Exception ex) {
        String msg = message(ex);
        if (msg.contains("未找到学校")) return "未找到学校，请填写学校全称后重试。";
        if (msg.toLowerCase(Locale.ROOT).contains("timeout")) return "网络超时，请检查网络后重试。";
        if (msg.contains("401") || msg.contains("密码")) return "账号或密码可能不正确，请确认后重试。";
        return msg;
    }

    private void showMain() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        Ui.applySystemBars(root);
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        bottomNav.setBackgroundColor(Ui.SURFACE);
        bottomNav.setElevation(Ui.dp(this, 8));
        root.addView(bottomNav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 78)));
        setContentView(root);
        showTab(0);
        loadActivityTypes();
        clockHandler.postDelayed(() -> checkForUpdate(false), 900L);
    }

    private void checkForUpdate(boolean manual) {
        UpdateChecker.check(this, manual, new UpdateChecker.Callback() {
            @Override
            public void onSuccess(UpdateChecker.UpdateInfo info) {
                if (info != null && !isFinishing()) UpdateDialog.show(MainActivity.this, info);
                else if (manual) toast("当前已是最新版本");
            }

            @Override
            public void onError(Exception error) {
                if (manual) toast("检查更新失败：" + message(error));
            }
        });
    }

    private void showTab(int tab) {
        currentTab = tab;
        clockHandler.removeCallbacks(clockTicker);
        beijingHour = null;
        beijingMinute = null;
        beijingSecond = null;
        beijingSyncText = null;
        content.removeAllViews();
        buildBottomNav();
        if (tab == 0) showActivitiesPage();
        if (tab == 1) showMyActivitiesPage();
        if (tab == 2) showMinePage();
        if (tab != 1) {
            myStatusTabs = null;
            myListView = null;
            myStatusScroll = null;
        }
    }

    private void buildBottomNav() {
        bottomNav.removeAllViews();
        addNav("活动", 0, TabIconView.ICON_CARD);
        addNav("我的活动", 1, TabIconView.ICON_TROPHY);
        addNav("我的", 2, TabIconView.ICON_USER);
    }

    private void addNav(String label, int tab, int icon) {
        boolean selected = tab == currentTab;
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 4));
        item.setBackground(Ui.ripple(Ui.bg(selected ? Ui.PRIMARY_SOFT : Color.TRANSPARENT, 18, this), Color.argb(24, 255, 122, 26)));
        TabIconView iconView = new TabIconView(this, icon);
        iconView.setSelectedState(selected);
        TextView text = Ui.text(this, label, 12, selected ? Ui.PRIMARY : Ui.MUTED, selected ? Typeface.BOLD : Typeface.NORMAL);
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(this, 31), Ui.dp(this, 28));
        item.addView(iconView, ilp);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.setMargins(0, Ui.dp(this, 3), 0, 0);
        item.addView(text, tlp);
        item.setOnClickListener(v -> showTab(tab));
        item.setContentDescription(label + (selected ? "，已选择" : ""));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        bottomNav.addView(item, lp);
    }

    private void showActivitiesPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, Ui.dp(this, 18), 0, 0);
        page.setBackgroundResource(R.drawable.bg_activities);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 10));
        LinearLayout title = Ui.pageTitle(this, "二课活动", "筛选可参加活动，提前预约报名");
        top.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58)));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);

        TextView activeFilter = Ui.statusPill(this, filterSummary(), Ui.PRIMARY_SOFT, Ui.PRIMARY);
        activeFilter.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        activeFilter.setPadding(Ui.dp(this, 14), Ui.dp(this, 7), Ui.dp(this, 14), Ui.dp(this, 7));
        activeFilter.setSingleLine(true);
        activeFilter.setEllipsize(TextUtils.TruncateAt.END);
        tools.addView(activeFilter, new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f));
        ImageButton search = Ui.iconButton(this, android.R.drawable.ic_menu_search, !searchQuery.isEmpty(), "搜索活动");
        ImageButton refresh = Ui.iconButton(this, android.R.drawable.ic_popup_sync, false, "刷新活动");
        ImageButton filter = Ui.iconButton(this, android.R.drawable.ic_menu_sort_by_size, !"全部".equals(selectedStatus) || !selectedTypeId.isEmpty(), "筛选活动");
        LinearLayout.LayoutParams toolLp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
        toolLp.setMargins(Ui.dp(this, 8), 0, 0, 0);
        tools.addView(search, toolLp);
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
        refreshLp.setMargins(Ui.dp(this, 8), 0, 0, 0);
        tools.addView(refresh, refreshLp);
        LinearLayout.LayoutParams filterLp = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
        filterLp.setMargins(Ui.dp(this, 8), 0, 0, 0);
        tools.addView(filter, filterLp);
        top.addView(tools, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 40)));
        page.addView(top);

        FrameLayout listHost = new FrameLayout(this);
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setCacheColorHint(android.graphics.Color.TRANSPARENT);
        list.setPadding(0, 0, 0, Ui.dp(this, 10));
        list.setClipToPadding(false);
        ActivityAdapter adapter = new ActivityAdapter(this, new ActivityAdapter.Listener() {
            @Override
            public void onClick(Models.Activity activity) {
                openDetail(activity);
            }

            @Override
            public boolean onLongClick(Models.Activity activity) {
                showActivityFilterReason(activity);
                return true;
            }
        });
        list.setAdapter(adapter);
        listHost.addView(list, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View empty = Ui.emptyState(this, "正在加载活动", "正在连接 PU 接口并补全筛选信息");
        listHost.addView(empty, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(listHost, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(page);

        search.setOnClickListener(v -> showSearchDialog());
        filter.setOnClickListener(v -> showFilterDialog());
        refresh.setOnClickListener(v -> loadActivities(adapter, activeFilter, empty, refresh, true));
        adapter.submit(filteredActivities());
        updateActivityEmptyState(empty, adapter.getCount(), allActivities.isEmpty(), null);
        if (allActivities.isEmpty()) loadActivities(adapter, activeFilter, empty, refresh, false);
    }

    private void loadActivities(ActivityAdapter adapter, TextView summary, View empty, ImageButton refresh, boolean force) {
        if (!force && !allActivities.isEmpty()) {
            summary.setText(filterSummary());
            adapter.submit(filteredActivities());
            updateActivityEmptyState(empty, adapter.getCount(), false, null);
            return;
        }
        refresh.setEnabled(false);
        refresh.setContentDescription("正在刷新活动");
        summary.setText("正在刷新活动 · 补全详情筛选缓存");
        updateActivityEmptyState(empty, 0, true, "正在连接 PU 接口并补全筛选信息");
        new Thread(() -> {
            try {
                List<Models.Activity> loaded = api.getActivitiesWithDetails(account, (done, total) ->
                        runOnUiThread(() -> {
                            if (summary.getParent() != null) summary.setText("正在补全详情 " + done + "/" + total);
                            updateActivityEmptyState(empty, 0, true, "已读取活动列表，正在补全详情 " + done + "/" + total);
                        }));
                runOnUiThread(() -> {
                    allActivities.clear();
                    allActivities.addAll(loaded);
                    summary.setText(filterSummary());
                    adapter.submit(filteredActivities());
                    refresh.setEnabled(true);
                    refresh.setContentDescription("刷新活动");
                    updateActivityEmptyState(empty, adapter.getCount(), false, null);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    summary.setText("活动加载失败 · " + message(ex));
                    adapter.submit(filteredActivities());
                    refresh.setEnabled(true);
                    refresh.setContentDescription("刷新活动");
                    updateActivityEmptyState(empty, adapter.getCount(), false, "加载失败：" + message(ex));
                });
            }
        }).start();
    }

    private void updateActivityEmptyState(View empty, int count, boolean loading, String message) {
        if (empty == null) return;
        if (loading) {
            Ui.updateEmptyState(empty, "正在加载活动", firstNonEmpty(message, "正在连接 PU 接口"));
            empty.setVisibility(View.VISIBLE);
            return;
        }
        if (count > 0) {
            empty.setVisibility(View.GONE);
            return;
        }
        Ui.updateEmptyState(empty, message == null ? "没有符合条件的活动" : "暂时加载不到活动", firstNonEmpty(message, "换个筛选条件或点击刷新试试"));
        empty.setVisibility(View.VISIBLE);
    }

    private List<Models.Activity> filteredActivities() {
        ArrayList<Models.Activity> result = new ArrayList<>();
        Models.ActivityType type = selectedType();
        long now = BeijingTime.now(this);
        for (Models.Activity activity : allActivities) {
            if (!matchesSelectedType(activity, type)) continue;
            if ("可参加".equals(selectedStatus) && !canParticipateFromList(activity, now)) continue;
            if ("可报名".equals(selectedStatus) && !canJoinFromList(activity, now)) continue;
            if (!matchesSearch(activity)) continue;
            result.add(activity);
        }
        return result;
    }

    private String filterSummary() {
        String typeName = "全部类型";
        for (Models.ActivityType type : activityTypes) {
            if (type.id.equals(selectedTypeId)) typeName = type.name;
        }
        return selectedStatus + " · " + typeName + (searchQuery.isEmpty() ? "" : " · 搜索：" + searchQuery);
    }

    private Models.ActivityType selectedType() {
        for (Models.ActivityType type : activityTypes) {
            if (type.id.equals(selectedTypeId)) return type;
        }
        return new Models.ActivityType("", "全部类型");
    }

    private boolean matchesSelectedType(Models.Activity activity, Models.ActivityType type) {
        return type == null || type.id.isEmpty() || api.matchesType(activity, type.id, type.name);
    }

    private boolean matchesSearch(Models.Activity activity) {
        String query = searchQuery == null ? "" : searchQuery.trim();
        if (query.isEmpty()) return true;
        String source = activitySearchText(activity);
        String haystack = normalizeSearch(source);
        String compactHaystack = compactSearch(source);
        for (String rawPart : query.split("\\s+")) {
            String part = rawPart.trim();
            if (part.isEmpty()) continue;
            String normalized = normalizeSearch(part);
            String compact = compactSearch(part);
            if (isStructuredSearch(part)) {
                if (compact.isEmpty() || !compactHaystack.contains(compact)) return false;
            } else if (!normalized.isEmpty() && !haystack.contains(normalized)) {
                return false;
            }
        }
        return true;
    }

    private String activitySearchText(Models.Activity activity) {
        StringBuilder text = new StringBuilder();
        appendSearch(text, activity.id);
        appendSearch(text, activity.name);
        appendSearch(text, activity.statusName);
        appendSearch(text, activity.categoryName);
        appendSearch(text, activity.categoryId);
        appendSearch(text, activity.categoryValue);
        appendSearch(text, activity.creatorName);
        appendSearch(text, activity.address);
        appendSearch(text, activity.description);
        appendSearch(text, activity.joinStartTime);
        appendDateSearch(text, activity.joinStartTime);
        appendSearch(text, activity.joinEndTime);
        appendDateSearch(text, activity.joinEndTime);
        appendSearch(text, activity.startTime);
        appendDateSearch(text, activity.startTime);
        appendSearch(text, activity.endTime);
        appendDateSearch(text, activity.endTime);
        appendSearch(text, activity.credit);
        appendSearch(text, activity.integrity);
        appendSearch(text, activity.allowUserCount);
        appendSearch(text, activity.joinUserCount);
        appendSearch(text, activity.signInUserCount);
        appendSearch(text, activity.signOutUserCount);
        appendSearch(text, joinNames(activity.allowCollege));
        appendSearch(text, joinNames(activity.allowYear));
        appendSearch(text, joinNames(activity.allowTribe));
        appendSearch(text, activity.raw == null ? "" : activity.raw.toString());
        return text.toString();
    }

    private void appendSearch(StringBuilder builder, Object value) {
        if (value == null) return;
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) return;
        builder.append(' ').append(text);
    }

    private void appendDateSearch(StringBuilder builder, String value) {
        String compact = compactSearch(value);
        if (compact.length() < 4) return;
        appendSearch(builder, compact);
        if (compact.length() >= 8) {
            appendSearch(builder, compact.substring(0, 4) + "." + compact.substring(4, 6) + "." + compact.substring(6, 8));
            appendSearch(builder, compact.substring(4, 6) + "." + compact.substring(6, 8));
        }
    }

    private String normalizeSearch(String value) {
        if (value == null) return "";
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('，', ' ')
                .replace(',', ' ')
                .replace('；', ' ')
                .replace(';', ' ')
                .replace('：', ' ')
                .replace(':', ' ')
                .replace('/', ' ')
                .replace('-', ' ')
                .replace('.', ' ')
                .replaceAll("\\s+", " ");
    }

    private String compactSearch(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{Nd}]+", "");
    }

    private boolean isStructuredSearch(String value) {
        if (value == null) return false;
        String text = value.trim();
        if (text.isEmpty()) return false;
        return text.matches(".*\\d.*") && text.matches(".*[./:：\\-年月日].*");
    }

    private String joinNames(org.json.JSONArray arr) {
        if (arr == null || arr.length() == 0) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            String name = Models.firstText(obj, "name", "title", "label", "text", "value");
            if (name.isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(name);
        }
        return builder.toString();
    }

    private void showSearchDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), 0);
        TextView help = Ui.text(this, "可搜索活动名称、时间、活动类型、地点、简介、学分、人数、ID 等关键词。日期可输入 06.02、6月2日 或 2026-06-02。", 13, Ui.MUTED, Typeface.NORMAL);
        help.setLineSpacing(Ui.dp(this, 4), 1.0f);
        box.addView(help);
        EditText input = input("输入关键词");
        input.setSingleLine(false);
        input.setMaxLines(2);
        input.setText(searchQuery);
        input.setSelection(input.getText().length());
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58));
        ilp.setMargins(0, Ui.dp(this, 14), 0, 0);
        box.addView(input, ilp);
        new AlertDialog.Builder(this)
                .setTitle("搜索活动")
                .setView(box)
                .setNegativeButton("清空", (d, w) -> {
                    searchQuery = "";
                    showTab(0);
                })
                .setPositiveButton("搜索", (d, w) -> {
                    searchQuery = input.getText().toString().trim();
                    showTab(0);
                })
                .show();
    }

    private boolean canParticipateFromList(Models.Activity activity, long now) {
        if (activity.id == 0) return false;
        String status = activity.statusName == null ? "" : activity.statusName;
        String startValue = activity.raw == null ? "" : Models.firstText(activity.raw, "startTimeValue");
        if (status.contains("已结束") || startValue.contains("已结束")) return false;
        if (!activity.eligibleFor(account)) return false;
        return activity.beforeJoinEnd(now);
    }

    private boolean canJoinFromList(Models.Activity activity, long now) {
        return canParticipateFromList(activity, now) && !activity.isFull() && activity.inJoinWindow(now);
    }

    private void showActivityFilterReason(Models.Activity activity) {
        long now = BeijingTime.now(this);
        StringBuilder msg = new StringBuilder();
        msg.append("可参加：").append(canParticipateFromList(activity, now) ? "是" : "否").append("\n");
        msg.append("可报名：").append(canJoinFromList(activity, now) ? "是" : "否").append("\n\n");
        if (activity.isFull()) msg.append("人数已满：").append(activity.joinUserCount).append("/").append(activity.allowUserCount).append("，只影响可报名\n");
        if (!activity.eligibleFor(account)) msg.append("原因：学院/年级/部落资格不符合\n");
        String status = activity.statusName == null ? "" : activity.statusName;
        String startValue = activity.raw == null ? "" : Models.firstText(activity.raw, "startTimeValue");
        if (status.contains("已结束") || startValue.contains("已结束")) msg.append("原因：活动已结束\n");
        if (!activity.beforeJoinEnd(now)) msg.append("原因：报名已过结束时间\n");
        if (!activity.inJoinWindow(now)) msg.append("可报名时间：").append(firstNonEmpty(activity.joinStartTime, "未知")).append(" - ").append(firstNonEmpty(activity.joinEndTime, "未知")).append("\n");
        new AlertDialog.Builder(this)
                .setTitle("筛选判断")
                .setMessage(msg.toString().trim())
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showFilterDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), 0);

        String[] statuses = new String[]{"全部", "可参加", "可报名"};
        final String[] pendingStatus = new String[]{selectedStatus};
        final String[] pendingType = new String[]{selectedTypeId};
        box.addView(label("活动状态"));
        LinearLayout statusWrap = chipWrap();
        box.addView(statusWrap);

        box.addView(label("活动类型"));
        ScrollView typeScroll = new ScrollView(this);
        LinearLayout typeWrap = chipWrap();
        typeScroll.addView(typeWrap);
        box.addView(typeScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 210)));

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            statusWrap.removeAllViews();
            for (String status : statuses) {
                addChoiceChip(statusWrap, status, status.equals(pendingStatus[0]), () -> {
                    pendingStatus[0] = status;
                    refresh[0].run();
                });
            }
            typeWrap.removeAllViews();
            List<Models.ActivityType> types = activityTypes.isEmpty() ? new ArrayList<>() : activityTypes;
            if (types.isEmpty()) types.add(new Models.ActivityType("", "全部类型"));
            for (Models.ActivityType type : types) {
                addChoiceChip(typeWrap, type.name, type.id.equals(pendingType[0]), () -> {
                    pendingType[0] = type.id;
                    refresh[0].run();
                });
            }
        };
        refresh[0].run();

        new AlertDialog.Builder(this)
                .setTitle("筛选")
                .setView(box)
                .setNegativeButton("重置", (d, w) -> {
                    selectedStatus = "全部";
                    selectedTypeId = "";
                    showTab(0);
                })
                .setPositiveButton("确认", (d, w) -> {
                    selectedStatus = pendingStatus[0];
                    selectedTypeId = pendingType[0];
                    showTab(0);
                })
                .show();
    }

    private LinearLayout chipWrap() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 4));
        return wrap;
    }

    private void addChoiceChip(LinearLayout parent, String text, boolean selected, Runnable onClick) {
        TextView chip = Ui.text(this, text, 14, selected ? android.graphics.Color.WHITE : Ui.TEXT, selected ? Typeface.BOLD : Typeface.NORMAL);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        chip.setBackground(Ui.ripple(Ui.strokeBg(selected ? Ui.PRIMARY : Color.argb(248, 255, 255, 255), selected ? Ui.PRIMARY : Ui.LINE_STRONG, 1, 999, this), Color.argb(28, 255, 122, 26)));
        chip.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 38));
        lp.setMargins(0, Ui.dp(this, 7), 0, 0);
        parent.addView(chip, lp);
    }

    private TextView label(String text) {
        TextView tv = Ui.text(this, text, 15, Ui.TEXT, Typeface.BOLD);
        tv.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        return tv;
    }

    private int indexOf(String[] arr, String value) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(value)) return i;
        return 0;
    }

    private int typeIndex(String id) {
        for (int i = 0; i < activityTypes.size(); i++) if (activityTypes.get(i).id.equals(id)) return i;
        return 0;
    }

    private void loadActivityTypes() {
        activityTypes.clear();
        activityTypes.add(new Models.ActivityType("", "全部类型"));
        new Thread(() -> {
            try {
                List<Models.ActivityType> loaded = api.getActivityTypes(account);
                runOnUiThread(() -> {
                    activityTypes.clear();
                    activityTypes.addAll(loaded);
                });
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void showMyActivitiesPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14), 0);
        page.setBackgroundResource(R.drawable.bg_my_activities);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout title = Ui.pageTitle(this, "我的活动", "查看报名记录和预约执行反馈");
        top.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 58), 1f));
        page.addView(top);

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        myListView = list;

        LinearLayout statusTabs = new LinearLayout(this);
        statusTabs.setOrientation(LinearLayout.HORIZONTAL);
        statusTabs.setPadding(0, 0, 0, Ui.dp(this, 8));
        myStatusTabs = statusTabs;
        String[] tabs = myTabs();
        for (String tab : tabs) addMyStatusTab(statusTabs, tab);
        HorizontalScrollView statusScroll = new HorizontalScrollView(this);
        statusScroll.setHorizontalScrollBarEnabled(false);
        statusScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        statusScroll.addView(statusTabs);
        myStatusScroll = statusScroll;
        page.addView(statusScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54)));

        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(page);
        localReservations.clear();
        localReservations.addAll(db.reservationsForAccount(account.key()));
        renderMyActivities(list, "正在加载...");
        loadMyActivities(list);
    }

    private void addMyStatusTab(LinearLayout parent, String status) {
        boolean selected = status.equals(mySelectedStatus);
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER);
        tab.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        TextView text = Ui.text(this, status, 17, selected ? Ui.TEXT : Ui.MUTED, selected ? Typeface.BOLD : Typeface.NORMAL);
        text.setGravity(Gravity.CENTER);
        tab.addView(text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 0, 1f));
        View underline = new View(this);
        underline.setBackground(Ui.bg(selected ? Ui.PRIMARY : android.graphics.Color.TRANSPARENT, 3, this));
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 4));
        tab.addView(underline, ulp);
        tab.setOnClickListener(v -> {
            mySelectedStatus = status;
            refreshMyStatusTabs();
            if (myListView != null) renderMyActivities(myListView, null);
            v.post(() -> {
                if (myStatusScroll != null) myStatusScroll.smoothScrollTo(Math.max(0, v.getLeft() - Ui.dp(this, 32)), 0);
            });
        });
        parent.addView(tab, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void refreshMyStatusTabs() {
        if (myStatusTabs == null) return;
        myStatusTabs.removeAllViews();
        String[] tabs = myTabs();
        for (String tab : tabs) addMyStatusTab(myStatusTabs, tab);
    }

    private void loadMyActivities(LinearLayout list) {
        new Thread(() -> {
            try {
                syncServerReservations();
                ArrayList<Models.Activity> merged = new ArrayList<>();
                try {
                    mergeActivities(merged, api.getMyActivities(account, 5, 120), 5, "报名待审核");
                } catch (Exception ignored) {
                }
                try {
                    mergeActivities(merged, api.getMyActivities(account, 1, 120), 1, "未开始");
                } catch (Exception ignored) {
                }
                runOnUiThread(() -> {
                    localReservations.clear();
                    localReservations.addAll(db.reservationsForAccount(account.key()));
                    myActivities.clear();
                    myActivities.addAll(merged);
                    renderMyActivities(list, null);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> renderMyActivities(list, "加载失败: " + message(ex)));
            }
        }).start();
    }

    private String[] myTabs() {
        return new String[]{"全部", "未开始", "报名待审核", "预约待执行", "预约已执行", "预约失败", "预约已取消"};
    }

    private void syncServerReservations() {
        if (serverApi == null || !serverApi.configured() || account == null) return;
        try {
            List<Models.Reservation> remoteList = serverApi.reservations(account);
            Set<String> remoteIds = new LinkedHashSet<>();
            String serverUrl = AppSettings.serverBaseUrl(this);
            for (Models.Reservation remote : remoteList) {
                if (remote.activityId == 0) continue;
                if (remote.remoteId != null && !remote.remoteId.trim().isEmpty()) remoteIds.add(remote.remoteId);
                remote.accountKey = account.key();
                remote.sid = account.sid;
                remote.username = account.username;
                remote.executor = "server";
                remote.serverUrl = serverUrl;
                if (remote.createdAt == 0) remote.createdAt = System.currentTimeMillis();
                db.upsertReservation(remote);
            }
            for (Models.Reservation local : db.reservationsForAccount(account.key())) {
                if (!"server".equals(local.executor)) continue;
                if (local.remoteId == null || local.remoteId.trim().isEmpty()) continue;
                if (!remoteIds.contains(local.remoteId)) db.deleteReservation(local.id);
            }
        } catch (Exception ignored) {
        }
    }

    private void mergeActivities(List<Models.Activity> target, List<Models.Activity> incoming, int type, String sourceStatus) {
        for (Models.Activity activity : incoming) {
            activity.myListType = type;
            activity.myStatus = sourceStatus;
            boolean exists = false;
            for (Models.Activity item : target) {
                if (item.id != 0 && item.id == activity.id) {
                    item.fillMissingFrom(activity);
                    if (item.myListType == 0) item.myListType = type;
                    if (item.myStatus == null || item.myStatus.isEmpty()) item.myStatus = sourceStatus;
                    exists = true;
                    break;
                }
            }
            if (!exists) target.add(activity);
        }
    }

    private void renderMyActivities(LinearLayout list, String placeholder) {
        list.removeAllViews();
        if (placeholder != null) {
            list.addView(Ui.emptyState(this, placeholder, "正在同步已报名活动和本地预约记录"), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 220)));
            return;
        }
        int count = 0;
        for (Models.Reservation r : localReservations) {
            String label = reservationStatusText(r.status);
            if (!matchesMyFilter(label)) continue;
            count++;
            list.addView(reservationCard(r, label));
        }
        for (Models.Activity a : myActivities) {
            String label = myStatusLabel(a);
            if (!matchesMyFilter(label)) continue;
            count++;
            list.addView(myCard(a));
        }
        if (count == 0) {
            String title = myActivities.isEmpty() && localReservations.isEmpty() ? "暂无活动记录" : "当前状态暂无活动";
            String message = myActivities.isEmpty() && localReservations.isEmpty() ? "报名或预约后会显示在这里" : "切换上方状态筛选可以查看其他记录";
            list.addView(Ui.emptyState(this, title, message), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 220)));
        }
    }

    private boolean matchesMyFilter(String label) {
        if ("全部".equals(mySelectedStatus)) return true;
        return mySelectedStatus.equals(label);
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private View myCard(Models.Activity a) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 15), Ui.dp(this, 14), Ui.dp(this, 15), Ui.dp(this, 14));
        card.setBackground(Ui.strokeBg(Color.argb(246, 255, 255, 255), Ui.LINE_STRONG, 1, 14, this));
        card.setElevation(Ui.dp(this, 2));
        card.setOnClickListener(v -> openDetail(a));
        TextView title = Ui.text(this, a.name, 17, Ui.TEXT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView meta = Ui.statusPill(this, myStatusLabel(a) + " · " + TimeUtil.dateRange(a), Ui.PRIMARY_SOFT, Ui.PRIMARY);
        TextView address = Ui.text(this, "地址：" + firstNonEmpty(a.address, "暂无地址"), 13, Ui.MUTED, Typeface.NORMAL);
        TextView id = Ui.text(this, "ID " + a.id, 12, Ui.MUTED, Typeface.NORMAL);
            Button cancel = Ui.button(this, "取消报名", Ui.DANGER, android.graphics.Color.WHITE);
            cancel.setBackground(Ui.ripple(Ui.bg(Ui.DANGER, 14, this), Color.argb(44, 255, 255, 255)));
        cancel.setOnClickListener(v -> cancelActivity(a));
        card.addView(title);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.setMargins(0, Ui.dp(this, 10), 0, 0);
        card.addView(meta, metaLp);
        LinearLayout.LayoutParams addressLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        addressLp.setMargins(0, Ui.dp(this, 10), 0, 0);
        card.addView(address, addressLp);
        LinearLayout.LayoutParams idLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        idLp.setMargins(0, Ui.dp(this, 6), 0, 0);
        card.addView(id, idLp);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 38));
        blp.setMargins(0, Ui.dp(this, 8), 0, 0);
        card.addView(cancel, blp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        card.setLayoutParams(lp);
        return card;
    }

    private View reservationCard(Models.Reservation r, String label) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 15), Ui.dp(this, 14), Ui.dp(this, 15), Ui.dp(this, 14));
        card.setBackground(Ui.strokeBg(Color.argb(246, 255, 255, 255), Ui.LINE_STRONG, 1, 14, this));
        card.setElevation(Ui.dp(this, 2));
        card.setOnClickListener(v -> openReservationDetail(r));
        TextView title = Ui.text(this, r.activityName == null || r.activityName.isEmpty() ? "预约活动 #" + r.activityId : r.activityName, 19, Ui.TEXT, Typeface.BOLD);
        String runAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(new java.util.Date(r.runAt));
        TextView meta = Ui.statusPill(this, reservationMeta(r, label, runAt), reservationMetaBg(r), reservationMetaFg(r));
        TextView result = Ui.text(this, reservationFeedback(r), 14, "failed".equals(r.status) ? Ui.DANGER : Ui.MUTED, Typeface.NORMAL);
        result.setLineSpacing(Ui.dp(this, 4), 1.0f);
        card.addView(title);
        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaLp.setMargins(0, Ui.dp(this, 10), 0, 0);
        card.addView(meta, metaLp);
        LinearLayout.LayoutParams resultLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resultLp.setMargins(0, Ui.dp(this, 10), 0, 0);
        card.addView(result, resultLp);
        if ("pending".equals(r.status)) {
            Button cancel = Ui.button(this, "取消预约", Ui.DANGER, android.graphics.Color.WHITE);
            cancel.setBackground(Ui.ripple(Ui.bg(Ui.DANGER, 14, this), Color.argb(44, 255, 255, 255)));
            cancel.setOnClickListener(v -> {
                v.setPressed(false);
                cancelReservation(r);
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42));
            blp.setMargins(0, Ui.dp(this, 10), 0, 0);
            card.addView(cancel, blp);
        } else {
            Button delete = Ui.secondaryButton(this, "删除记录");
            delete.setOnClickListener(v -> {
                v.setPressed(false);
                deleteReservationRecord(r);
            });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 42));
            blp.setMargins(0, Ui.dp(this, 10), 0, 0);
            card.addView(delete, blp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        card.setLayoutParams(lp);
        return card;
    }

    private void openReservationDetail(Models.Reservation r) {
        Models.Activity activity = new Models.Activity();
        activity.id = r.activityId;
        activity.name = r.activityName;
        openDetail(activity);
    }

    private String reservationFeedback(Models.Reservation r) {
        String executor = reservationExecutorText(r);
        String feedback = r.lastResult == null || r.lastResult.trim().isEmpty() ? "等待到点自动报名" : r.lastResult.trim();
        if ("completed".equals(r.status)) return "执行成功：" + feedback;
        if ("failed".equals(r.status)) return "失败原因：" + feedback + "\n已重试：" + r.retryCount + "/3 次";
        if ("cancelled".equals(r.status)) return "已取消：" + feedback;
        if (r.retryCount > 0) return "最近一次失败：" + feedback + "\n已重试：" + r.retryCount + "/3 次，等待下次自动重试";
        return executor + "到点自动报名，执行结果可在这里刷新查看";
    }

    private String reservationMeta(Models.Reservation r, String label, String runAt) {
        String executor = reservationExecutorText(r);
        if ("pending".equals(r.status)) return label + " · " + executor + "将在 " + runAt + " 执行";
        if ("failed".equals(r.status)) return "预约失败 · 已重试 " + r.retryCount + "/3";
        if ("cancelled".equals(r.status)) return "预约已取消 · " + executor;
        return label + " · " + runAt;
    }

    private int reservationMetaBg(Models.Reservation r) {
        if ("pending".equals(r.status)) return Ui.PRIMARY_SOFT;
        if ("failed".equals(r.status)) return Color.rgb(255, 238, 238);
        if ("cancelled".equals(r.status)) return Color.rgb(242, 242, 242);
        return Color.rgb(238, 246, 242);
    }

    private int reservationMetaFg(Models.Reservation r) {
        if ("pending".equals(r.status)) return Ui.PRIMARY;
        if ("failed".equals(r.status)) return Ui.DANGER;
        if ("cancelled".equals(r.status)) return Ui.MUTED;
        return Ui.SUCCESS;
    }

    private String reservationExecutorText(Models.Reservation r) {
        return "server".equals(r.executor) ? "服务器" : "本地";
    }

    private void showMyStatusDialog() {
        Set<String> set = new LinkedHashSet<>();
        set.add("全部");
        set.add("预约中");
        set.add("报名中");
        set.add("未开始");
        set.add("已报名");
        set.add("已结束");
        set.add("已执行");
        set.add("预约失败");
        set.add("预约已取消");
        for (Models.Reservation r : localReservations) {
            set.add(reservationStatusText(r.status));
        }
        for (Models.Activity a : myActivities) {
            set.add(myStatusLabel(a));
        }
        String[] arr = set.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("活动状态")
                .setItems(arr, (d, which) -> {
                    mySelectedStatus = arr[which];
                    showTab(1);
                })
                .show();
    }

    private void cancelReservation(Models.Reservation r) {
        new AlertDialog.Builder(this)
                .setTitle("取消预约")
                .setMessage("确定取消这个预约任务吗？\n" + (r.activityName == null ? r.activityId : r.activityName))
                .setNegativeButton("再想想", null)
                .setPositiveButton("取消预约", (d, w) -> {
                    if ("server".equals(r.executor)) {
                        cancelServerReservation(r);
                    } else {
                        new ReservationScheduler(this).cancel(r.id);
                        localReservations.clear();
                        toast("预约已取消");
                        showTab(1);
                    }
                })
                .show();
    }

    private void cancelServerReservation(Models.Reservation r) {
        ProgressDialog dialog = ProgressDialog.show(this, "", "正在取消服务器预约...", true, false);
        new Thread(() -> {
            try {
                if (r.remoteId == null || r.remoteId.trim().isEmpty()) throw new IllegalStateException("本地记录缺少服务器任务 ID");
                serverApi.delete(r.remoteId);
                db.updateReservationStatus(r.id, "cancelled", "服务器任务已取消", r.retryCount);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    toast("服务器预约已取消");
                    showTab(1);
                });
            } catch (Exception ex) {
                String msg = message(ex);
                if (msg.contains("预约任务不存在") || msg.contains("任务不存在")) {
                    db.updateReservationStatus(r.id, "cancelled", "服务器任务已取消", r.retryCount);
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        toast("服务器预约已取消");
                        showTab(1);
                    });
                    return;
                }
                runOnUiThread(() -> {
                    dialog.dismiss();
                    toast("取消失败：" + msg);
                });
            }
        }).start();
    }

    private void deleteReservationRecord(Models.Reservation r) {
        new AlertDialog.Builder(this)
                .setTitle("删除预约记录")
                .setMessage(("server".equals(r.executor) ? "会同时删除服务器上的预约记录，不会取消已经报名的活动。\n" : "只删除本地预约记录，不会取消已经报名的活动。\n") + (r.activityName == null ? r.activityId : r.activityName))
                .setNegativeButton("再想想", null)
                .setPositiveButton("删除", (d, w) -> {
                    if ("server".equals(r.executor)) {
                        deleteServerReservationRecord(r);
                    } else {
                        db.deleteReservation(r.id);
                        localReservations.clear();
                        localReservations.addAll(db.reservationsForAccount(account.key()));
                        toast("预约记录已删除");
                        if (myListView != null) renderMyActivities(myListView, null);
                    }
                })
                .show();
    }

    private void deleteServerReservationRecord(Models.Reservation r) {
        ProgressDialog dialog = ProgressDialog.show(this, "", "正在删除服务器记录...", true, false);
        new Thread(() -> {
            try {
                if (r.remoteId == null || r.remoteId.trim().isEmpty()) throw new IllegalStateException("本地记录缺少服务器任务 ID");
                serverApi.delete(r.remoteId);
                deleteLocalReservationAfterRemoteDelete(dialog, r, "服务器预约记录已删除");
            } catch (Exception ex) {
                String msg = message(ex);
                if (msg.contains("预约任务不存在") || msg.contains("任务不存在")) {
                    deleteLocalReservationAfterRemoteDelete(dialog, r, "服务器已无该任务，本地记录已删除");
                    return;
                }
                runOnUiThread(() -> {
                    dialog.dismiss();
                    toast("删除失败：" + msg);
                });
            }
        }).start();
    }

    private void deleteLocalReservationAfterRemoteDelete(ProgressDialog dialog, Models.Reservation r, String toastText) {
        db.deleteReservation(r.id);
        runOnUiThread(() -> {
            dialog.dismiss();
            localReservations.clear();
            localReservations.addAll(db.reservationsForAccount(account.key()));
            toast(toastText);
            if (myListView != null) renderMyActivities(myListView, null);
        });
    }

    private String myStatusLabel(Models.Activity a) {
        if (a.myStatus != null && !a.myStatus.trim().isEmpty()) return a.myStatus.trim();
        if (a.myListType == 5) return "报名待审核";
        if (a.myListType == 1) return "未开始";
        if (a.statusName != null && !a.statusName.trim().isEmpty()) return a.statusName.trim();
        long now = BeijingTime.now(this);
        if (a.inJoinWindow(now)) return "报名中";
        if (a.notStarted(now)) return "未开始";
        long end = TimeUtil.parseMillis(a.endTime);
        if (end > 0 && now > end) return "已结束";
        return "已报名";
    }

    private String reservationStatusText(String status) {
        if ("pending".equals(status)) return "预约待执行";
        if ("completed".equals(status)) return "预约已执行";
        if ("failed".equals(status)) return "预约失败";
        if ("cancelled".equals(status)) return "预约已取消";
        return "预约已执行";
    }

    private void cancelActivity(Models.Activity a) {
        new AlertDialog.Builder(this)
                .setTitle("取消报名")
                .setMessage("确定取消报名该活动吗？\n" + a.name)
                .setNegativeButton("再想想", null)
                .setPositiveButton("取消报名", (d, w) -> {
                    ProgressDialog dialog = ProgressDialog.show(this, "", "正在取消...", true, false);
                    new Thread(() -> {
                        try {
                            String msg = api.cancel(account, a.id);
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                toast(msg);
                                myActivities.clear();
                                showTab(1);
                            });
                        } catch (Exception ex) {
                            runOnUiThread(() -> {
                                dialog.dismiss();
                                toast("取消失败: " + message(ex));
                            });
                        }
                    }).start();
                }).show();
    }

    private void showMinePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundResource(R.drawable.bg_mine);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Ui.dp(this, 16), Ui.dp(this, 18), Ui.dp(this, 16), Ui.dp(this, 18));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(Ui.pageTitle(this, "我的", "账号、分数和本地设置"), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(beijingClockCard());
        page.addView(header);
        clockHandler.removeCallbacks(clockTicker);
        updateBeijingClock();
        clockHandler.postDelayed(clockTicker, 1000);
        LinearLayout profile = new LinearLayout(this);
        profile.setOrientation(LinearLayout.VERTICAL);
        profile.setPadding(Ui.dp(this, 15), Ui.dp(this, 14), Ui.dp(this, 15), Ui.dp(this, 14));
        profile.setBackground(Ui.strokeBg(Color.argb(246, 255, 255, 255), Ui.LINE_STRONG, 1, 14, this));
        profile.setElevation(Ui.dp(this, 2));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.setMargins(0, Ui.dp(this, 12), 0, Ui.dp(this, 10));
        page.addView(profile, plp);
        renderMineInfo(profile);

        LinearLayout runtimeCard = runtimeStatusCard();
        LinearLayout.LayoutParams runtimeLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        runtimeLp.setMargins(0, 0, 0, Ui.dp(this, 10));
        page.addView(runtimeCard, runtimeLp);

        LinearLayout actionCard = Ui.card(this);
        actionCard.setPadding(Ui.dp(this, 15), Ui.dp(this, 14), Ui.dp(this, 15), Ui.dp(this, 15));
        TextView actionTitle = Ui.text(this, "常用操作", 16, Ui.TEXT, Typeface.BOLD);
        actionCard.addView(actionTitle);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button creditBtn = Ui.primaryButton(this, "查询分数");
        Button settingsBtn = Ui.secondaryButton(this, "设置");
        tuneMineButton(creditBtn);
        tuneMineButton(settingsBtn);
        actions.addView(creditBtn, mineHalfButtonLp(true));
        actions.addView(settingsBtn, mineHalfButtonLp(false));
        actionCard.addView(actions, mineButtonLp());

        LinearLayout accountActions = new LinearLayout(this);
        accountActions.setOrientation(LinearLayout.HORIZONTAL);
        accountActions.setGravity(Gravity.CENTER_VERTICAL);
        Button switchBtn = Ui.secondaryButton(this, "切换账号");
        Button logoutBtn = Ui.secondaryButton(this, "退出登录");
        tuneMineButton(switchBtn);
        tuneMineButton(logoutBtn);
        accountActions.addView(switchBtn, mineHalfButtonLp(true));
        accountActions.addView(logoutBtn, mineHalfButtonLp(false));
        actionCard.addView(accountActions, mineButtonLp());
        LinearLayout.LayoutParams actionCardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionCardLp.setMargins(0, 0, 0, Ui.dp(this, 10));
        page.addView(actionCard, actionCardLp);
        creditBtn.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreditActivity.class);
            intent.putExtra(DetailActivity.EXTRA_ACCOUNT_KEY, account.key());
            startActivity(intent);
        });
        settingsBtn.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        switchBtn.setOnClickListener(v -> showAccountChooser(true));
        logoutBtn.setOnClickListener(v -> {
            account = null;
            showLogin();
        });
        loadMineInfo(profile);
    }

    private LinearLayout runtimeStatusCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.text(this, "运行状态", 16, Ui.TEXT, Typeface.BOLD));
        AlarmManager alarm = (AlarmManager) getSystemService(ALARM_SERVICE);
        boolean exactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || (alarm != null && alarm.canScheduleExactAlarms());
        card.addView(runtimeStatusRow("准时提醒", exactAlarm ? "已开启" : "需要开启", exactAlarm));
        boolean serverEnabled = AppSettings.serverEnabled(this);
        card.addView(runtimeStatusRow("服务器执行", serverEnabled ? "已配置" : "未配置", serverEnabled));
        boolean timeSynced = BeijingTime.lastSyncedAt(this) > 0;
        card.addView(runtimeStatusRow("北京时间", timeSynced ? "已同步" : "等待同步", timeSynced));
        card.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        return card;
    }

    private View runtimeStatusRow(String label, String value, boolean ready) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 11), 0, 0);
        row.addView(Ui.text(this, label, 14, Ui.TEXT, Typeface.NORMAL), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView status = Ui.statusPill(this, value, ready ? Color.rgb(232, 248, 240) : Color.rgb(255, 244, 231), ready ? Ui.SUCCESS : Ui.WARNING);
        row.addView(status);
        return row;
    }

    private View beijingClockCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        card.setBackground(Ui.strokeBg(Color.argb(246, 255, 255, 255), Ui.LINE_STRONG, 1, 14, this));
        card.setElevation(Ui.dp(this, 2));
        LinearLayout labelRow = new LinearLayout(this);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        beijingSyncText = Ui.text(this, "同步中", 10, Ui.MUTED, Typeface.NORMAL);
        TextView label = Ui.text(this, "北京时间", 11, Ui.MUTED, Typeface.BOLD);
        label.setGravity(Gravity.RIGHT);
        labelRow.addView(beijingSyncText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        labelRow.addView(label);
        card.addView(labelRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout blocks = new LinearLayout(this);
        blocks.setOrientation(LinearLayout.HORIZONTAL);
        blocks.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.setMargins(0, Ui.dp(this, 5), 0, 0);
        card.addView(blocks, blp);
        beijingHour = clockBlock();
        beijingMinute = clockBlock();
        beijingSecond = clockBlock();
        blocks.addView(beijingHour);
        blocks.addView(clockColon());
        blocks.addView(beijingMinute);
        blocks.addView(clockColon());
        blocks.addView(beijingSecond);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 210), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(Ui.dp(this, 12), 0, 0, 0);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView clockBlock() {
        TextView tv = Ui.text(this, "00", 16, Ui.TEXT, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setMinWidth(Ui.dp(this, 34));
        tv.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        tv.setBackground(Ui.bg(Color.rgb(255, 247, 241), 6, this));
        return tv;
    }

    private TextView clockColon() {
        TextView tv = Ui.text(this, ":", 15, Ui.MUTED, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        return tv;
    }

    private void updateBeijingClock() {
        if (beijingHour == null || beijingMinute == null || beijingSecond == null) return;
        long now = BeijingTime.now(this);
        SimpleDateFormat time = new SimpleDateFormat("HH mm ss", Locale.CHINA);
        time.setTimeZone(BeijingTime.ZONE);
        String[] parts = time.format(new Date(now)).split(" ");
        if (parts.length == 3) {
            beijingHour.setText(parts[0]);
            beijingMinute.setText(parts[1]);
            beijingSecond.setText(parts[2]);
        }
        if (beijingSyncText != null) {
            long syncedAt = BeijingTime.lastSyncedAt(this);
            if (syncedAt <= 0) {
                beijingSyncText.setText("等待同步");
            } else {
                long age = Math.max(0, (System.currentTimeMillis() - syncedAt) / 60000L);
                beijingSyncText.setText(age == 0 ? "刚刚同步" : age + "分钟前同步");
            }
        }
    }

    private void renderMineInfo(LinearLayout profile) {
        profile.removeAllViews();
        profile.addView(infoRow("学校", account.schoolName, "SID " + account.sid));
        profile.addView(infoRow("账号", account.username, "本地已加密保存"));
        profile.addView(infoRow("学院", firstNonEmpty(account.collegeName, "正在获取学院名称"), "CID " + account.cid));
        profile.addView(infoRow("年级", firstNonEmpty(account.yearName, "正在获取年级名称"), "YID " + account.yid));
    }

    private View infoRow(String label, String value, String sub) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(this, 2), Ui.dp(this, 7), Ui.dp(this, 2), Ui.dp(this, 7));
        row.addView(Ui.text(this, label, 12, Ui.MUTED, Typeface.BOLD));
        TextView valueText = Ui.text(this, value, 15, Ui.TEXT, Typeface.NORMAL);
        valueText.setSingleLine(false);
        row.addView(valueText);
        TextView subText = Ui.text(this, sub, 11, Ui.MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(0, Ui.dp(this, 3), 0, 0);
        row.addView(subText, slp);
        return row;
    }

    private void loadMineInfo(LinearLayout profile) {
        Models.Account target = account;
        new Thread(() -> {
            String collegeName = cleanName(target.collegeName);
            String yearName = cleanName(target.yearName);
            try {
                org.json.JSONObject res = api.userInfo(target);
                org.json.JSONObject data = res.optJSONObject("data");
                org.json.JSONObject rootJson = data == null ? res : data;
                if (collegeName.isEmpty()) {
                    collegeName = findTextDeep(rootJson, "collegeName", "college", "academyName", "departmentName", "department", "facultyName", "yxmc", "cname", "orgName");
                }
                if (yearName.isEmpty()) {
                    yearName = findTextDeep(rootJson, "yearName", "gradeName", "grade", "year", "njmc", "yname", "enrollmentYear");
                }
                updateMineInfoIfChanged(target, profile, collegeName, yearName);
            } catch (Exception ignored) {
            }

            if (yearName.isEmpty() && target.yid > 0) {
                try {
                    yearName = findNameByIdDeep(api.yearList(target), target.yid);
                    updateMineInfoIfChanged(target, profile, collegeName, yearName);
                } catch (Exception ignored) {
                }
            }

            if (collegeName.isEmpty() || yearName.isEmpty()) {
                String[] found = findCollegeYearInActivities(target, allActivities);
                if (collegeName.isEmpty()) collegeName = found[0];
                if (yearName.isEmpty()) yearName = found[1];
                updateMineInfoIfChanged(target, profile, collegeName, yearName);
            }

            if (collegeName.isEmpty() || yearName.isEmpty()) {
                try {
                    List<Models.Activity> source = api.getActivities(target);
                    int checked = 0;
                    for (Models.Activity base : source) {
                        if (base.id == 0) continue;
                        Models.Activity detail = api.getActivityInfo(target, base.id);
                        base.fillMissingFrom(detail);
                        checked++;
                        if (collegeName.isEmpty()) collegeName = findNameInArray(base.allowCollege, target.cid);
                        if (yearName.isEmpty()) yearName = findNameInArray(base.allowYear, target.yid);
                        updateMineInfoIfChanged(target, profile, collegeName, yearName);
                        if ((!collegeName.isEmpty() && !yearName.isEmpty()) || checked >= 80) break;
                    }
                } catch (Exception ignored) {
                }
            }

            updateMineInfoIfChanged(target, profile, collegeName, yearName);
        }).start();
    }

    private void updateMineInfoIfChanged(Models.Account target, LinearLayout profile, String collegeName, String yearName) {
        if (target != account) return;
        boolean changed = false;
        String college = cleanName(collegeName);
        String year = cleanName(yearName);
        if (!college.isEmpty() && !college.equals(target.collegeName)) {
            target.collegeName = college;
            changed = true;
        }
        if (!year.isEmpty() && !year.equals(target.yearName)) {
            target.yearName = year;
            changed = true;
        }
        if (changed) {
            db.updateAccountNames(target);
            runOnUiThread(() -> {
                if (target == account && profile.getParent() != null) renderMineInfo(profile);
            });
        }
    }

    private String[] findCollegeYearInActivities(Models.Account target, List<Models.Activity> activities) {
        String college = "";
        String year = "";
        for (Models.Activity activity : activities) {
            if (college.isEmpty()) college = findNameInArray(activity.allowCollege, target.cid);
            if (year.isEmpty()) year = findNameInArray(activity.allowYear, target.yid);
            if (!college.isEmpty() && !year.isEmpty()) break;
        }
        return new String[]{college, year};
    }

    private String findNameInArray(org.json.JSONArray arr, long id) {
        if (arr == null || id <= 0) return "";
        for (int i = 0; i < arr.length(); i++) {
            org.json.JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            if (idMatches(obj, id)) {
                String name = nameFromObject(obj);
                if (!name.isEmpty()) return name;
            }
        }
        return "";
    }

    private String findNameByIdDeep(Object node, long id) {
        if (node == null || id <= 0) return "";
        if (node instanceof org.json.JSONObject) {
            org.json.JSONObject obj = (org.json.JSONObject) node;
            if (idMatches(obj, id)) {
                String name = nameFromObject(obj);
                if (!name.isEmpty()) return name;
            }
            org.json.JSONArray names = obj.names();
            if (names == null) return "";
            for (int i = 0; i < names.length(); i++) {
                String found = findNameByIdDeep(obj.opt(names.optString(i)), id);
                if (!found.isEmpty()) return found;
            }
        } else if (node instanceof org.json.JSONArray) {
            org.json.JSONArray arr = (org.json.JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                String found = findNameByIdDeep(arr.opt(i), id);
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }

    private boolean idMatches(org.json.JSONObject obj, long id) {
        String expected = String.valueOf(id);
        String[] keys = new String[]{"id", "cid", "yid", "value", "key"};
        for (String key : keys) {
            if (obj.has(key) && expected.equals(String.valueOf(obj.opt(key)))) return true;
        }
        return false;
    }

    private String nameFromObject(org.json.JSONObject obj) {
        return findTextDeep(obj, "name", "label", "title", "text", "collegeName", "yearName", "gradeName", "academyName", "departmentName", "yxmc", "njmc");
    }

    private String findTextDeep(Object node, String... keys) {
        if (node instanceof org.json.JSONObject) {
            org.json.JSONObject obj = (org.json.JSONObject) node;
            String direct = cleanName(Models.firstText(obj, keys));
            if (!direct.isEmpty()) return direct;
            org.json.JSONArray names = obj.names();
            if (names == null) return "";
            for (int i = 0; i < names.length(); i++) {
                String found = findTextDeep(obj.opt(names.optString(i)), keys);
                if (!found.isEmpty()) return found;
            }
        } else if (node instanceof org.json.JSONArray) {
            org.json.JSONArray arr = (org.json.JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                String found = findTextDeep(arr.opt(i), keys);
                if (!found.isEmpty()) return found;
            }
        }
        return "";
    }

    private String cleanName(String value) {
        if (value == null) return "";
        String text = value.trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text) || "0".equals(text)) return "";
        return text;
    }

    private void tuneMineButton(Button button) {
        button.setTextSize(Ui.fontSp(this, 13));
        button.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
    }

    private LinearLayout.LayoutParams mineHalfButtonLp(boolean left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1f);
        if (left) {
            lp.setMargins(0, 0, Ui.dp(this, 8), 0);
        } else {
            lp.setMargins(Ui.dp(this, 8), 0, 0, 0);
        }
        return lp;
    }

    private LinearLayout.LayoutParams mineButtonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 40));
        lp.setMargins(0, Ui.dp(this, 9), 0, 0);
        return lp;
    }

    private String firstNonEmpty(String value, String fallback) {
        String clean = cleanName(value);
        return clean.isEmpty() ? fallback : clean;
    }

    private LinearLayout.LayoutParams buttonLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 40));
        lp.setMargins(0, Ui.dp(this, 9), 0, 0);
        return lp;
    }

    private void showAccountChooser(boolean fromMain) {
        List<Models.Account> accounts = db.getAccounts();
        if (accounts.isEmpty()) {
            toast("还没有保存账号");
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), 0);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("切换账号")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .create();
        for (Models.Account item : accounts) {
            list.addView(accountChoiceCard(item, dialog));
        }
        dialog.show();
    }

    private View accountChoiceCard(Models.Account item, AlertDialog dialog) {
        boolean selected = account != null && account.key().equals(item.key());
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        card.setBackground(Ui.ripple(Ui.strokeBg(selected ? Ui.PRIMARY_SOFT : Color.argb(248, 255, 255, 255), selected ? Ui.PRIMARY : Ui.LINE_STRONG, selected ? 2 : 1, 14, this), Color.argb(28, 255, 122, 26)));
        card.setElevation(Ui.dp(this, selected ? 3 : 1));
        TextView school = Ui.text(this, firstNonEmpty(item.schoolName, "未知学校"), 16, Ui.TEXT, Typeface.BOLD);
        TextView user = Ui.text(this, item.username, 13, Ui.MUTED, Typeface.NORMAL);
        TextView meta = Ui.text(this, selected ? "当前账号" : "点击切换到这个账号", 12, selected ? Ui.PRIMARY : Ui.MUTED, selected ? Typeface.BOLD : Typeface.NORMAL);
        card.addView(school);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.setMargins(0, Ui.dp(this, 7), 0, 0);
        card.addView(user, ulp);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.setMargins(0, Ui.dp(this, 7), 0, 0);
        card.addView(meta, mlp);
        card.setOnClickListener(v -> {
            account = item;
            db.setLastAccount(account);
            allActivities.clear();
            myActivities.clear();
            dialog.dismiss();
            showMain();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Ui.dp(this, 8), 0, Ui.dp(this, 6));
        card.setLayoutParams(lp);
        return card;
    }

    private void openDetail(Models.Activity activity) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra(DetailActivity.EXTRA_ACTIVITY_ID, activity.id);
        intent.putExtra(DetailActivity.EXTRA_ACCOUNT_KEY, account.key());
        if (activity.raw != null) intent.putExtra(DetailActivity.EXTRA_ACTIVITY_JSON, activity.raw.toString());
        startActivity(intent);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 99);
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static final class TabIconView extends View {
        static final int ICON_CARD = 1;
        static final int ICON_TROPHY = 2;
        static final int ICON_USER = 3;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int icon;
        private boolean selected;

        TabIconView(android.content.Context context, int icon) {
            super(context);
            this.icon = icon;
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        void setSelectedState(boolean selected) {
            this.selected = selected;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int color = selected ? Ui.PRIMARY : Color.rgb(178, 178, 178);
            paint.setColor(color);
            paint.setStrokeWidth(Ui.dp(getContext(), 2));
            paint.setStyle(Paint.Style.STROKE);
            float w = getWidth();
            float h = getHeight();
            if (icon == ICON_CARD) drawCard(canvas, w, h);
            else if (icon == ICON_TROPHY) drawTrophy(canvas, w, h);
            else drawUser(canvas, w, h);
        }

        private void drawCard(Canvas canvas, float w, float h) {
            RectF bag = new RectF(w * 0.20f, h * 0.34f, w * 0.80f, h * 0.86f);
            canvas.drawRoundRect(bag, Ui.dp(getContext(), 5), Ui.dp(getContext(), 5), paint);
            RectF handle = new RectF(w * 0.34f, h * 0.13f, w * 0.66f, h * 0.44f);
            canvas.drawArc(handle, 180, 180, false, paint);
            canvas.drawLine(w * 0.20f, h * 0.52f, w * 0.80f, h * 0.52f, paint);
        }

        private void drawTrophy(Canvas canvas, float w, float h) {
            RectF cup = new RectF(w * 0.32f, h * 0.15f, w * 0.68f, h * 0.58f);
            canvas.drawRoundRect(cup, Ui.dp(getContext(), 6), Ui.dp(getContext(), 6), paint);
            RectF left = new RectF(w * 0.15f, h * 0.20f, w * 0.36f, h * 0.54f);
            RectF right = new RectF(w * 0.64f, h * 0.20f, w * 0.85f, h * 0.54f);
            canvas.drawArc(left, 270, -170, false, paint);
            canvas.drawArc(right, 270, 170, false, paint);
            canvas.drawLine(w * 0.50f, h * 0.58f, w * 0.50f, h * 0.76f, paint);
            canvas.drawLine(w * 0.36f, h * 0.86f, w * 0.64f, h * 0.86f, paint);
            canvas.drawLine(w * 0.42f, h * 0.76f, w * 0.58f, h * 0.76f, paint);
        }

        private void drawUser(Canvas canvas, float w, float h) {
            canvas.drawCircle(w * 0.50f, h * 0.34f, Math.min(w, h) * 0.16f, paint);
            RectF body = new RectF(w * 0.25f, h * 0.58f, w * 0.75f, h * 0.95f);
            canvas.drawArc(body, 205, 130, false, paint);
        }
    }
}
