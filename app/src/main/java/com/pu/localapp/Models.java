package com.pu.localapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

final class Models {
    private Models() {
    }

    static class School {
        long sid;
        String name;

        static School fromJson(JSONObject obj) {
            School s = new School();
            s.sid = obj.optLong("id");
            s.name = obj.optString("name");
            return s;
        }
    }

    static class Account implements Serializable {
        long id;
        String schoolName;
        long sid;
        String username;
        String password;
        String token;
        long cid;
        long yid;
        String collegeName;
        String yearName;
        long updatedAt;

        String key() {
            return sid + ":" + username;
        }
    }

    static class Activity implements Serializable {
        long id;
        String name;
        String statusName;
        String categoryName;
        String coverUrl;
        String joinStartTime;
        String joinEndTime;
        String startTime;
        String endTime;
        String credit;
        String integrity;
        int allowUserCount;
        int joinUserCount;
        int signInUserCount = -1;
        int signOutUserCount = -1;
        int categoryId;
        String categoryValue;
        String creatorName;
        String address;
        String description;
        JSONArray allowCollege;
        JSONArray allowYear;
        JSONArray allowTribe;
        JSONObject raw;
        boolean detailLoaded;
        int myListType;
        String myStatus;

        static Activity fromJson(JSONObject obj) {
            Activity a = new Activity();
            a.raw = obj;
            a.id = obj.optLong("id", obj.optLong("activityId"));
            a.name = firstText(obj, "activityName", "name", "title");
            a.statusName = firstText(obj, "statusName", "status", "stateName");
            a.categoryName = firstText(obj, "categoryName", "category", "categoryTitle");
            if (empty(a.categoryName)) a.categoryName = objectText(obj, "category", "name", "title", "label");
            if (empty(a.categoryName)) a.categoryName = objectText(obj, "subCategory", "name", "title", "label");
            a.coverUrl = firstText(obj, "cover", "coverUrl", "image", "imageUrl", "img", "logo", "poster", "pic", "activityImg", "activityImage", "thumb");
            if (empty(a.coverUrl)) a.coverUrl = objectText(obj, "logo", "url", "name");
            if (empty(a.coverUrl)) a.coverUrl = objectText(obj, "cover", "url", "name");
            if (empty(a.coverUrl)) a.coverUrl = objectText(obj, "image", "url", "name");
            a.joinStartTime = firstText(obj, "joinStartTime", "applyStartTime", "signStartTime");
            a.joinEndTime = firstText(obj, "joinEndTime", "applyEndTime", "signEndTime");
            a.startTime = firstText(obj, "startTime", "activityStartTime");
            a.endTime = firstText(obj, "endTime", "activityEndTime");
            a.credit = firstText(obj, "credit", "score");
            a.integrity = firstText(obj, "integrity", "integrityValue", "puAmount", "signCount");
            a.allowUserCount = firstInt(obj, "allowUserCount", "limitCount", "userCount");
            a.joinUserCount = firstInt(obj, "joinUserCount", "joinedCount", "applyCount");
            a.signInUserCount = firstOptionalInt(obj, "signInUserCount", "signedUserCount", "signedCount", "signUserCount", "checkInUserCount", "checkInCount", "attendanceCount", "actualSignCount");
            a.signOutUserCount = firstOptionalInt(obj, "signOutUserCount", "signedOutUserCount", "signedOutCount", "quitUserCount", "signOutCount", "checkoutCount", "actualSignOutCount");
            a.categoryId = firstInt(obj, "categoryId", "categoryID", "category_id", "category", "categorys");
            if (a.categoryId == 0) a.categoryId = objectInt(obj, "category", "id");
            if (a.categoryId == 0) a.categoryId = objectInt(obj, "subCategory", "id");
            a.categoryValue = firstText(obj, "categoryId", "categoryID", "category_id", "categorys", "cid");
            if (empty(a.categoryValue)) a.categoryValue = objectText(obj, "category", "id");
            if (empty(a.categoryValue)) a.categoryValue = objectText(obj, "subCategory", "id");
            if (empty(a.categoryValue) && a.categoryId > 0) a.categoryValue = String.valueOf(a.categoryId);
            a.creatorName = firstText(obj, "creatorName", "sponsor", "organizer", "host");
            if (empty(a.creatorName)) a.creatorName = objectText(obj, "user", "name");
            if (empty(a.creatorName)) a.creatorName = objectText(obj, "college", "name");
            a.address = firstText(obj, "address", "location", "place");
            a.description = firstText(obj, "description", "content", "detail", "intro");
            a.allowCollege = obj.optJSONArray("allowCollege");
            a.allowYear = obj.optJSONArray("allowYear");
            a.allowTribe = obj.optJSONArray("allowTribe");
            return a;
        }

        void fillMissingFrom(Activity other) {
            if (other == null) return;
            if (id == 0) id = other.id;
            if (empty(name)) name = other.name;
            if (empty(statusName)) statusName = other.statusName;
            if (empty(categoryName)) categoryName = other.categoryName;
            if (empty(coverUrl)) coverUrl = other.coverUrl;
            if (empty(joinStartTime)) joinStartTime = other.joinStartTime;
            if (empty(joinEndTime)) joinEndTime = other.joinEndTime;
            if (empty(startTime)) startTime = other.startTime;
            if (empty(endTime)) endTime = other.endTime;
            if (empty(credit)) credit = other.credit;
            if (empty(integrity)) integrity = other.integrity;
            if (allowUserCount == 0) allowUserCount = other.allowUserCount;
            if (joinUserCount == 0) joinUserCount = other.joinUserCount;
            if (signInUserCount < 0) signInUserCount = other.signInUserCount;
            if (signOutUserCount < 0) signOutUserCount = other.signOutUserCount;
            if (categoryId == 0) categoryId = other.categoryId;
            if (empty(categoryValue)) categoryValue = other.categoryValue;
            if (empty(creatorName)) creatorName = other.creatorName;
            if (empty(address)) address = other.address;
            if (empty(description)) description = other.description;
            if (allowCollege == null) allowCollege = other.allowCollege;
            if (allowYear == null) allowYear = other.allowYear;
            if (allowTribe == null) allowTribe = other.allowTribe;
            if (raw == null) raw = other.raw;
            if (myListType == 0) myListType = other.myListType;
            if (empty(myStatus)) myStatus = other.myStatus;
        }

        boolean isFull() {
            return allowUserCount > 0 && joinUserCount >= allowUserCount;
        }

        boolean eligibleFor(Account account) {
            return matchesList(allowCollege, account.cid) && matchesList(allowYear, account.yid) && (allowTribe == null || allowTribe.length() == 0);
        }

        boolean beforeJoinEnd(long now) {
            long end = TimeUtil.parseMillis(joinEndTime);
            return end == 0 || now <= end;
        }

        boolean inJoinWindow(long now) {
            long start = TimeUtil.parseMillis(joinStartTime);
            long end = TimeUtil.parseMillis(joinEndTime);
            return start > 0 && now >= start - 60000L && (end == 0 || now <= end);
        }

        boolean notStarted(long now) {
            long start = TimeUtil.parseMillis(joinStartTime);
            return start > 0 && now < start;
        }

        private static boolean matchesList(JSONArray arr, long id) {
            if (arr == null || arr.length() == 0) return true;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null && String.valueOf(id).equals(String.valueOf(obj.optLong("id")))) {
                    return true;
                }
            }
            return false;
        }

        private static boolean empty(String value) {
            return value == null || value.trim().isEmpty();
        }
    }

    static class ActivityType {
        String id;
        String name;
        String value;

        ActivityType(String id, String name) {
            this.id = id;
            this.name = name;
            this.value = name;
        }

        ActivityType(String id, String name, String value) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.value = value == null || value.trim().isEmpty() ? this.name : value.trim();
        }
    }

    static class Reservation implements Serializable {
        long id;
        String accountKey;
        long sid;
        String username;
        long activityId;
        String activityName;
        long runAt;
        String status;
        String lastResult;
        int retryCount;
        long createdAt;
        String executor;
        String remoteId;
        String serverUrl;
    }

    static class SignStats {
        int memberCount = -1;
        int signInCount = -1;
        int signOutCount = -1;
    }

    static String firstText(JSONObject obj, String... keys) {
        for (String key : keys) {
            Object value = obj.opt(key);
            if (value != null && value != JSONObject.NULL) {
                if (value instanceof JSONObject || value instanceof JSONArray) continue;
                String text = String.valueOf(value).trim();
                if (!text.isEmpty() && !"null".equalsIgnoreCase(text)) return text;
            }
        }
        return "";
    }

    static int firstInt(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key)) return obj.optInt(key);
        }
        return 0;
    }

    static int firstOptionalInt(JSONObject obj, String... keys) {
        for (String key : keys) {
            if (obj.has(key) && !obj.isNull(key)) return obj.optInt(key);
        }
        return -1;
    }

    static String objectText(JSONObject obj, String objectKey, String... keys) {
        JSONObject child = obj.optJSONObject(objectKey);
        if (child == null) return "";
        return firstText(child, keys);
    }

    static int objectInt(JSONObject obj, String objectKey, String key) {
        JSONObject child = obj.optJSONObject(objectKey);
        return child == null ? 0 : child.optInt(key);
    }

    static List<Activity> activitiesFromArray(JSONArray arr) {
        List<Activity> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null) list.add(Activity.fromJson(obj));
        }
        return list;
    }
}
