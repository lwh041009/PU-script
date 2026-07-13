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

final class PuApi {
    interface Progress {
        void onProgress(int done, int total);
    }

    private static final String BASE_URL = "https://apis.pocketuni.net";
    private final Context context;
    private final AppDb db;
    private final Object tokenRefreshLock = new Object();

    PuApi(Context context) {
        this.context = context.getApplicationContext();
        db = new AppDb(this.context);
    }

    List<Models.School> getSchools() throws Exception {
        JSONObject res = request("GET", "/uc/school/list", null, null, false);
        JSONArray arr = res.optJSONObject("data") == null ? null : res.optJSONObject("data").optJSONArray("list");
        ArrayList<Models.School> schools = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null) schools.add(Models.School.fromJson(obj));
            }
        }
        return schools;
    }

    Models.Account login(String schoolName, long sid, String username, String password) throws Exception {
        JSONObject body = new JSONObject();
        body.put("userName", username);
        body.put("password", password);
        body.put("sid", sid);
        body.put("device", "pc");
        JSONObject res = request("POST", "/uc/user/login", body, null, false);
        if (res.optInt("code", -1) != 0) {
            throw new IllegalStateException(res.optString("msg", res.optString("message", "登录失败")));
        }
        JSONObject data = res.optJSONObject("data");
        JSONObject baseUser = data == null ? null : data.optJSONObject("baseUserInfo");
        Models.Account account = new Models.Account();
        account.schoolName = schoolName;
        account.sid = sid;
        account.username = username;
        account.password = password;
        account.token = data == null ? "" : data.optString("token");
        account.cid = baseUser == null ? 0 : baseUser.optLong("cid");
        account.yid = baseUser == null ? 0 : baseUser.optLong("yid");
        if (baseUser != null) {
            account.collegeName = Models.firstText(baseUser, "collegeName", "college", "academyName", "departmentName", "department", "facultyName", "yxmc", "cname", "orgName");
            account.yearName = Models.firstText(baseUser, "yearName", "gradeName", "grade", "year", "njmc", "yname", "enrollmentYear");
        }
        return db.upsertAccount(account);
    }

    Models.Account refreshToken(Models.Account account) throws Exception {
        return login(account.schoolName, account.sid, account.username, account.password);
    }

    List<Models.ActivityType> getActivityTypes(Models.Account account) throws Exception {
        JSONObject body = new JSONObject();
        body.put("key", "eventFilter");
        body.put("puType", 0);
        JSONObject res = authedRequest("POST", "/apis/mapping/data", body, account, false);
        ArrayList<Models.ActivityType> types = new ArrayList<>();
        types.add(new Models.ActivityType("", "全部类型"));
        JSONObject data = res.optJSONObject("data");
        collectTypes(data, types);
        return types;
    }

    List<Models.Activity> getActivities(Models.Account account, String typeId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("sort", 0);
        body.put("page", 1);
        body.put("puType", 0);
        body.put("limit", 300);
        body.put("status", 1);
        if (typeId != null && !typeId.isEmpty() && !typeId.startsWith("name:")) {
            body.put("categorys", typeId);
        }
        JSONObject res = authedRequest("POST", "/apis/activity/list", body, account, false);
        JSONArray arr = res.optJSONObject("data") == null ? null : res.optJSONObject("data").optJSONArray("list");
        return Models.activitiesFromArray(arr);
    }

    List<Models.Activity> getActivities(Models.Account account) throws Exception {
        return getActivities(account, "");
    }

    Models.Activity getActivityInfo(Models.Account account, long activityId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("id", activityId);
        JSONObject res = authedRequest("POST", "/apis/activity/info", body, account, false);
        JSONObject data = res.optJSONObject("data");
        JSONObject baseInfo = data == null ? null : data.optJSONObject("baseInfo");
        if (baseInfo == null) baseInfo = data;
        if (baseInfo == null) throw new IllegalStateException("活动详情为空");
        Models.Activity activity = Models.Activity.fromJson(baseInfo);
        activity.id = activity.id == 0 ? activityId : activity.id;
        activity.detailLoaded = true;
        return activity;
    }

    Models.SignStats getActivitySignStats(Models.Account account, long activityId) throws Exception {
        Models.SignStats stats = new Models.SignStats();
        int page = 1;
        int limit = 100;
        int total = -1;
        int signIn = 0;
        int signOut = 0;
        int seen = 0;
        do {
            JSONObject body = new JSONObject();
            body.put("id", activityId);
            body.put("activityId", activityId);
            body.put("page", page);
            body.put("limit", limit);
            JSONObject res = authedRequest("POST", "/apis/activity/member", body, account, false);
            JSONObject data = res.optJSONObject("data");
            if (data == null) break;
            JSONObject pageInfo = data.optJSONObject("pageInfo");
            if (pageInfo != null) total = pageInfo.optInt("count", total);
            JSONArray list = data.optJSONArray("list");
            if (list == null || list.length() == 0) break;
            seen += list.length();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) continue;
                String signName = Models.firstText(item, "signName", "signStatusName", "statusName");
                if (isSignedIn(signName)) signIn++;
                if (isSignedOut(signName)) signOut++;
            }
            if (total > 0 && seen >= total) break;
            page++;
        } while (page <= 20);
        stats.memberCount = total > 0 ? total : seen;
        stats.signInCount = signIn;
        stats.signOutCount = signOut;
        return stats;
    }

    private boolean isSignedIn(String signName) {
        if (signName == null) return false;
        String text = signName.trim();
        if (text.isEmpty() || text.contains("未签到")) return false;
        return text.contains("已签到") || text.contains("未签退") || text.contains("已签退");
    }

    private boolean isSignedOut(String signName) {
        return signName != null && signName.trim().contains("已签退");
    }

    List<Models.Activity> getActivitiesWithDetails(Models.Account account, Progress progress) throws Exception {
        List<Models.Activity> source = getActivities(account);
        if (source.isEmpty()) return source;
        int total = source.size();
        int chunkSize = 12;
        for (int start = 0; start < source.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, source.size());
            ArrayList<Thread> threads = new ArrayList<>();
            for (int i = start; i < end; i++) {
                Models.Activity base = source.get(i);
                Thread thread = new Thread(() -> {
                    if (base.id != 0) {
                        try {
                            Models.Activity detail = getActivityInfo(account, base.id);
                            base.fillMissingFrom(detail);
                            base.detailLoaded = true;
                        } catch (Exception ignored) {
                        }
                    }
                });
                threads.add(thread);
                thread.start();
            }
            for (Thread thread : threads) thread.join();
            if (progress != null) progress.onProgress(Math.min(end, total), total);
        }
        return source;
    }

    List<Models.Activity> getEligibleActivities(Models.Account account, String typeId, boolean joinOpenOnly) throws Exception {
        List<Models.Activity> source = getActivities(account, typeId);
        ArrayList<Models.Activity> candidates = new ArrayList<>();
        for (Models.Activity a : source) {
            if (a.id == 0 || a.isFull()) continue;
            String status = a.statusName == null ? "" : a.statusName;
            String startValue = Models.firstText(a.raw, "startTimeValue");
            if (status.contains("已结束") || startValue.contains("已结束")) continue;
            candidates.add(a);
        }

        ArrayList<Models.Activity> result = new ArrayList<>();
        long now = BeijingTime.now(context);
        for (Models.Activity candidate : candidates) {
            try {
                Models.Activity detail = getActivityInfo(account, candidate.id);
                if (!matchesType(detail, typeId, "")) continue;
                if (!detail.eligibleFor(account) || detail.isFull()) continue;
                long joinStart = TimeUtil.parseMillis(detail.joinStartTime);
                long joinEnd = TimeUtil.parseMillis(detail.joinEndTime);
                if (joinStart <= 0) continue;
                if (joinOpenOnly) {
                    if (!(now >= joinStart - 60000L && (joinEnd == 0 || now <= joinEnd))) continue;
                } else {
                    if (now >= joinStart - 60000L) continue;
                }
                result.add(detail);
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    List<Models.Activity> getMyActivities(Models.Account account, int type, int limit) throws Exception {
        JSONObject body = new JSONObject();
        body.put("type", type);
        body.put("page", 1);
        body.put("limit", limit);
        JSONObject res = authedRequest("POST", "/apis/activity/myList/new", body, account, false);
        JSONArray arr = res.optJSONObject("data") == null ? null : res.optJSONObject("data").optJSONArray("list");
        return Models.activitiesFromArray(arr);
    }

    List<Models.Activity> getMyActivities(Models.Account account, int limit) throws Exception {
        JSONObject body = new JSONObject();
        body.put("page", 1);
        body.put("limit", limit);
        JSONObject res = authedRequest("POST", "/apis/activity/myList/new", body, account, false);
        JSONArray arr = res.optJSONObject("data") == null ? null : res.optJSONObject("data").optJSONArray("list");
        return Models.activitiesFromArray(arr);
    }

    String join(Models.Account account, long activityId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("activityId", activityId);
        JSONObject res = authedRequest("POST", "/apis/activity/join", body, account, true);
        ensureActionResult(res, "报名");
        return responseMessage(res, "报名请求已提交");
    }

    String cancel(Models.Account account, long activityId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("activityId", activityId);
        JSONObject res = authedRequest("POST", "/apis/activity/cancel", body, account, false);
        ensureActionResult(res, "取消报名");
        return responseMessage(res, "取消报名请求已提交");
    }

    JSONObject userInfo(Models.Account account) throws Exception {
        return authedRequest("POST", "/apis/user/pc-info", null, account, false);
    }

    JSONObject yearList(Models.Account account) throws Exception {
        return authedRequest("GET", "/apis/mapping/year-list", null, account, false);
    }

    JSONObject mappingData(Models.Account account, String key) throws Exception {
        JSONObject body = new JSONObject();
        body.put("key", key);
        body.put("puType", 0);
        return authedRequest("POST", "/apis/mapping/data", body, account, false);
    }

    JSONObject activityCredit(Models.Account account) throws Exception {
        return authedRequest("GET", "/apis/credit/activity?puType=0", null, account, false);
    }

    JSONObject applyCredit(Models.Account account) throws Exception {
        return authedRequest("GET", "/apis/credit/apply?puType=0&year=0", null, account, false);
    }

    private JSONObject authedRequest(String method, String endpoint, JSONObject body, Models.Account account, boolean xSign) throws Exception {
        String tokenBeforeRequest = account == null || account.token == null ? "" : account.token;
        JSONObject res = request(method, endpoint, body, account, xSign);
        if (res.optInt("code", 0) == 401) {
            synchronized (tokenRefreshLock) {
                String currentToken = account == null || account.token == null ? "" : account.token;
                if (account != null && currentToken.equals(tokenBeforeRequest)) {
                    Models.Account refreshed = refreshToken(account);
                    account.token = refreshed.token;
                }
            }
            res = request(method, endpoint, body, account, xSign);
        }
        return res;
    }

    private JSONObject request(String method, String endpoint, JSONObject body, Models.Account account, boolean xSign) throws Exception {
        long requestStart = System.currentTimeMillis();
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + endpoint).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        conn.setRequestProperty("DNT", "1");
        conn.setRequestProperty("Origin", "https://class.pocketuni.net");
        conn.setRequestProperty("Referer", "https://class.pocketuni.net/");
        conn.setRequestProperty("Sec-Fetch-Dest", "empty");
        conn.setRequestProperty("Sec-Fetch-Mode", "cors");
        conn.setRequestProperty("Sec-Fetch-Site", "same-site");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0");
        conn.setRequestProperty("Content-Type", "application/json");
        if (account != null && account.token != null && !account.token.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + account.token + ":" + account.sid);
            if (xSign) conn.setRequestProperty("X-Sign", XSign.generate(BeijingTime.now(context)));
        } else {
            conn.setRequestProperty("Authorization", "Bearer :0");
        }
        if ("POST".equals(method)) {
            conn.setDoOutput(true);
            byte[] bytes = (body == null ? "{}" : body.toString()).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }
        }
        int code = conn.getResponseCode();
        BeijingTime.updateFromHttpDate(context, conn.getHeaderField("Date"), requestStart, System.currentTimeMillis());
        InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String text = readAll(stream);
        if (text.isEmpty()) {
            JSONObject empty = new JSONObject();
            empty.put("code", code);
            empty.put("msg", "空响应");
            return empty;
        }
        JSONObject json = new JSONObject(text);
        json.put("_httpCode", code);
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

    private String responseMessage(JSONObject res, String fallback) {
        String text = cleanMessage(res.optString("msg"));
        if (text.isEmpty()) text = cleanMessage(res.optString("message"));
        if (text.isEmpty()) text = cleanMessage(res.optString("data"));
        if (text.isEmpty()) text = cleanMessage(res.optString("error"));
        if (text.isEmpty()) text = fallback;
        int code = res.optInt("code", 0);
        int httpCode = res.optInt("_httpCode", 200);
        if ((code != 0 || httpCode >= 400) && fallback.endsWith("失败") && fallback.equals(text)) {
            text = fallback + "，服务器未返回具体原因";
        }
        return text;
    }

    private String cleanMessage(String value) {
        if (value == null) return "";
        String text = value.trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text) || "{}".equals(text) || "[]".equals(text)) return "";
        return text;
    }

    private void ensureActionResult(JSONObject res, String action) {
        int code = res.optInt("code", 0);
        if (code != 0) {
            String msg = responseMessage(res, action + "失败");
            throw new IllegalStateException(msg);
        }
    }

    private void collectTypes(JSONObject data, List<Models.ActivityType> out) {
        if (data == null) return;
        collectEventFilterGroups(data, out);
        if (out.size() == 1) {
            addFallbackTypes(out);
        }
    }

    private void collectEventFilterGroups(JSONObject data, List<Models.ActivityType> out) {
        JSONArray keys = data.names();
        if (keys == null) return;
        for (int i = 0; i < keys.length(); i++) {
            String key = keys.optString(i);
            Object value = data.opt(key);
            if (value instanceof JSONArray) {
                JSONArray arr = (JSONArray) value;
                for (int j = 0; j < arr.length(); j++) {
                    JSONObject item = arr.optJSONObject(j);
                    if (item == null) continue;
                    String groupName = Models.firstText(item, "name", "title", "label");
                    if (groupName.contains("分类") || groupName.contains("类型")) {
                        collectFirstChildArray(item, out);
                    }
                }
            } else if (value instanceof JSONObject) {
                collectEventFilterGroups((JSONObject) value, out);
            }
        }
    }

    private void collectFirstChildArray(JSONObject group, List<Models.ActivityType> out) {
        JSONArray keys = group.names();
        if (keys == null) return;
        for (int i = 0; i < keys.length(); i++) {
            Object value = group.opt(keys.optString(i));
            if (!(value instanceof JSONArray)) continue;
            JSONArray arr = (JSONArray) value;
            if (arr.length() == 0) continue;
            for (int j = 0; j < arr.length(); j++) {
                Object item = arr.opt(j);
                String name;
                String id;
                if (item instanceof JSONObject) {
                    JSONObject obj = (JSONObject) item;
                    name = Models.firstText(obj, "name", "label", "title", "text", "value");
                    id = Models.firstText(obj, "id", "key", "value");
                    String valueText = Models.firstText(obj, "value", "id", "key", "name", "label", "title");
                    if (id.isEmpty()) id = "name:" + name;
                    if (valueText.isEmpty()) valueText = name;
                    if (!name.isEmpty() && !"全部".equals(name) && !containsType(out, id, name)) {
                        out.add(new Models.ActivityType(id, name, valueText));
                    }
                    continue;
                } else {
                    name = String.valueOf(item);
                    id = "name:" + name;
                }
                if (!name.isEmpty() && !"全部".equals(name) && !containsType(out, id, name)) {
                    out.add(new Models.ActivityType(id, name, name));
                }
            }
            return;
        }
    }

    private void addFallbackTypes(List<Models.ActivityType> out) {
        String[] names = new String[]{"思想政治", "社会实践", "文化艺术", "学术科技", "社会工作"};
        for (String name : names) {
            if (!containsType(out, "name:" + name, name)) out.add(new Models.ActivityType("name:" + name, name, name));
        }
    }

    boolean matchesType(Models.Activity activity, String typeId, String typeName) {
        if (typeId == null || typeId.isEmpty()) return true;
        String expected = typeId.startsWith("name:") ? typeId.substring(5) : typeName;
        if (expected == null) expected = "";
        if (activity.categoryId > 0 && String.valueOf(activity.categoryId).equals(typeId)) return true;
        if (activity.categoryValue != null && (activity.categoryValue.equals(typeId) || activity.categoryValue.equals(expected))) return true;
        String category = activity.categoryName == null ? "" : activity.categoryName;
        String title = activity.name == null ? "" : activity.name;
        String mapped = mapCategory(expected);
        String shortName = shortCategory(expected);
        return !expected.isEmpty()
                && (category.contains(expected)
                || category.contains(mapped)
                || category.contains(shortName)
                || title.contains(expected)
                || title.contains(mapped)
                || title.contains(shortName));
    }

    private String mapCategory(String name) {
        if ("思政".equals(name)) return "思想政治";
        if ("实践".equals(name)) return "社会实践";
        if ("文艺".equals(name)) return "文化艺术";
        if ("科技".equals(name)) return "学术科技";
        if ("技能".equals(name)) return "社会工作";
        return name;
    }

    private String shortCategory(String name) {
        if ("思想政治".equals(name)) return "思政";
        if ("社会实践".equals(name)) return "实践";
        if ("文化艺术".equals(name)) return "文艺";
        if ("学术科技".equals(name)) return "科技";
        if ("社会工作".equals(name)) return "技能";
        return name;
    }

    private boolean containsType(List<Models.ActivityType> out, String id, String name) {
        for (Models.ActivityType type : out) {
            if (type.id.equals(id) && type.name.equals(name)) return true;
        }
        return false;
    }
}
