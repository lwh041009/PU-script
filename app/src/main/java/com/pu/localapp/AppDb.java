package com.pu.localapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class AppDb extends SQLiteOpenHelper {
    private static final String DB_NAME = "pu_local.db";
    private static final int DB_VERSION = 3;
    private final EncryptedPrefs encryptedPrefs;

    AppDb(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        encryptedPrefs = new EncryptedPrefs(context);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE accounts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "school_name TEXT NOT NULL," +
                "sid INTEGER NOT NULL," +
                "username TEXT NOT NULL," +
                "cid INTEGER DEFAULT 0," +
                "yid INTEGER DEFAULT 0," +
                "college_name TEXT," +
                "year_name TEXT," +
                "updated_at INTEGER NOT NULL," +
                "UNIQUE(sid, username))");
        db.execSQL("CREATE TABLE reservations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "account_key TEXT NOT NULL," +
                "sid INTEGER NOT NULL," +
                "username TEXT NOT NULL," +
                "activity_id INTEGER NOT NULL," +
                "activity_name TEXT," +
                "run_at INTEGER NOT NULL," +
                "status TEXT NOT NULL," +
                "last_result TEXT," +
                "retry_count INTEGER DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "executor TEXT DEFAULT 'local'," +
                "remote_id TEXT," +
                "server_url TEXT," +
                "UNIQUE(account_key, activity_id))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            addColumnIfMissing(db, "accounts", "college_name", "TEXT");
            addColumnIfMissing(db, "accounts", "year_name", "TEXT");
        }
        if (oldVersion < 3) {
            addColumnIfMissing(db, "reservations", "executor", "TEXT DEFAULT 'local'");
            addColumnIfMissing(db, "reservations", "remote_id", "TEXT");
            addColumnIfMissing(db, "reservations", "server_url", "TEXT");
        }
    }

    Models.Account upsertAccount(Models.Account account) {
        long now = System.currentTimeMillis();
        account.updatedAt = now;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("school_name", account.schoolName);
        values.put("sid", account.sid);
        values.put("username", account.username);
        values.put("cid", account.cid);
        values.put("yid", account.yid);
        values.put("college_name", account.collegeName);
        values.put("year_name", account.yearName);
        values.put("updated_at", now);
        long rowId = db.insertWithOnConflict("accounts", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (rowId == -1) {
            db.update("accounts", values, "sid=? AND username=?", new String[]{String.valueOf(account.sid), account.username});
            rowId = getAccountId(account.sid, account.username);
        }
        account.id = rowId;
        encryptedPrefs.put(secretKey(account.key(), "password"), account.password);
        encryptedPrefs.put(secretKey(account.key(), "token"), account.token);
        encryptedPrefs.put("last_account_key", account.key());
        return account;
    }

    void updateAccountNames(Models.Account account) {
        ContentValues values = new ContentValues();
        values.put("college_name", account.collegeName);
        values.put("year_name", account.yearName);
        getWritableDatabase().update("accounts", values, "sid=? AND username=?", new String[]{String.valueOf(account.sid), account.username});
    }

    Models.Account getLastAccount() {
        String key = encryptedPrefs.get("last_account_key", "");
        if (key.isEmpty()) return null;
        return getAccountByKey(key);
    }

    void setLastAccount(Models.Account account) {
        encryptedPrefs.put("last_account_key", account.key());
    }

    List<Models.Account> getAccounts() {
        ArrayList<Models.Account> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query("accounts", null, null, null, null, null, "updated_at DESC");
        try {
            while (c.moveToNext()) {
                list.add(readAccount(c));
            }
        } finally {
            c.close();
        }
        return list;
    }

    Models.Account getAccountByKey(String key) {
        String[] parts = key.split(":", 2);
        if (parts.length != 2) return null;
        Cursor c = getReadableDatabase().query("accounts", null, "sid=? AND username=?", new String[]{parts[0], parts[1]}, null, null, null);
        try {
            if (c.moveToFirst()) return readAccount(c);
        } finally {
            c.close();
        }
        return null;
    }

    void deleteAccount(Models.Account account) {
        getWritableDatabase().delete("accounts", "sid=? AND username=?", new String[]{String.valueOf(account.sid), account.username});
        encryptedPrefs.remove(secretKey(account.key(), "password"));
        encryptedPrefs.remove(secretKey(account.key(), "token"));
        String last = encryptedPrefs.get("last_account_key", "");
        if (account.key().equals(last)) encryptedPrefs.remove("last_account_key");
    }

    long upsertReservation(Models.Reservation reservation) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("account_key", reservation.accountKey);
        values.put("sid", reservation.sid);
        values.put("username", reservation.username);
        values.put("activity_id", reservation.activityId);
        values.put("activity_name", reservation.activityName);
        values.put("run_at", reservation.runAt);
        values.put("status", reservation.status);
        values.put("last_result", reservation.lastResult);
        values.put("retry_count", reservation.retryCount);
        values.put("created_at", reservation.createdAt == 0 ? System.currentTimeMillis() : reservation.createdAt);
        values.put("executor", reservation.executor == null || reservation.executor.trim().isEmpty() ? "local" : reservation.executor);
        values.put("remote_id", reservation.remoteId);
        values.put("server_url", reservation.serverUrl);
        long id = db.insertWithOnConflict("reservations", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (id == -1) {
            db.update("reservations", values, "account_key=? AND activity_id=?", new String[]{reservation.accountKey, String.valueOf(reservation.activityId)});
            id = getReservationId(reservation.accountKey, reservation.activityId);
        }
        return id;
    }

    void updateReservationStatus(long id, String status, String result, int retryCount) {
        ContentValues values = new ContentValues();
        values.put("status", status);
        values.put("last_result", result);
        values.put("retry_count", retryCount);
        getWritableDatabase().update("reservations", values, "id=?", new String[]{String.valueOf(id)});
    }

    void updateReservationRemote(long id, String remoteId, String serverUrl) {
        ContentValues values = new ContentValues();
        values.put("remote_id", remoteId);
        values.put("server_url", serverUrl);
        values.put("executor", "server");
        getWritableDatabase().update("reservations", values, "id=?", new String[]{String.valueOf(id)});
    }

    void deleteReservation(long id) {
        getWritableDatabase().delete("reservations", "id=?", new String[]{String.valueOf(id)});
    }

    Models.Reservation getReservation(long id) {
        Cursor c = getReadableDatabase().query("reservations", null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        try {
            if (c.moveToFirst()) return readReservation(c);
        } finally {
            c.close();
        }
        return null;
    }

    List<Models.Reservation> pendingReservations() {
        ArrayList<Models.Reservation> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query("reservations", null, "status=?", new String[]{"pending"}, null, null, "run_at ASC");
        try {
            while (c.moveToNext()) list.add(readReservation(c));
        } finally {
            c.close();
        }
        return list;
    }

    List<Models.Reservation> reservationsForAccount(String accountKey) {
        ArrayList<Models.Reservation> list = new ArrayList<>();
        Cursor c = getReadableDatabase().query("reservations", null, "account_key=?", new String[]{accountKey}, null, null, "run_at ASC");
        try {
            while (c.moveToNext()) list.add(readReservation(c));
        } finally {
            c.close();
        }
        return list;
    }

    private long getAccountId(long sid, String username) {
        Cursor c = getReadableDatabase().query("accounts", new String[]{"id"}, "sid=? AND username=?", new String[]{String.valueOf(sid), username}, null, null, null);
        try {
            if (c.moveToFirst()) return c.getLong(0);
        } finally {
            c.close();
        }
        return 0;
    }

    private long getReservationId(String accountKey, long activityId) {
        Cursor c = getReadableDatabase().query("reservations", new String[]{"id"}, "account_key=? AND activity_id=?", new String[]{accountKey, String.valueOf(activityId)}, null, null, null);
        try {
            if (c.moveToFirst()) return c.getLong(0);
        } finally {
            c.close();
        }
        return 0;
    }

    private Models.Account readAccount(Cursor c) {
        Models.Account a = new Models.Account();
        a.id = c.getLong(c.getColumnIndexOrThrow("id"));
        a.schoolName = c.getString(c.getColumnIndexOrThrow("school_name"));
        a.sid = c.getLong(c.getColumnIndexOrThrow("sid"));
        a.username = c.getString(c.getColumnIndexOrThrow("username"));
        a.cid = c.getLong(c.getColumnIndexOrThrow("cid"));
        a.yid = c.getLong(c.getColumnIndexOrThrow("yid"));
        int collegeIndex = c.getColumnIndex("college_name");
        int yearIndex = c.getColumnIndex("year_name");
        a.collegeName = collegeIndex < 0 ? "" : c.getString(collegeIndex);
        a.yearName = yearIndex < 0 ? "" : c.getString(yearIndex);
        a.updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"));
        a.password = encryptedPrefs.get(secretKey(a.key(), "password"), "");
        a.token = encryptedPrefs.get(secretKey(a.key(), "token"), "");
        return a;
    }

    private Models.Reservation readReservation(Cursor c) {
        Models.Reservation r = new Models.Reservation();
        r.id = c.getLong(c.getColumnIndexOrThrow("id"));
        r.accountKey = c.getString(c.getColumnIndexOrThrow("account_key"));
        r.sid = c.getLong(c.getColumnIndexOrThrow("sid"));
        r.username = c.getString(c.getColumnIndexOrThrow("username"));
        r.activityId = c.getLong(c.getColumnIndexOrThrow("activity_id"));
        r.activityName = c.getString(c.getColumnIndexOrThrow("activity_name"));
        r.runAt = c.getLong(c.getColumnIndexOrThrow("run_at"));
        r.status = c.getString(c.getColumnIndexOrThrow("status"));
        r.lastResult = c.getString(c.getColumnIndexOrThrow("last_result"));
        r.retryCount = c.getInt(c.getColumnIndexOrThrow("retry_count"));
        r.createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"));
        int executorIndex = c.getColumnIndex("executor");
        int remoteIdIndex = c.getColumnIndex("remote_id");
        int serverUrlIndex = c.getColumnIndex("server_url");
        r.executor = executorIndex < 0 ? "local" : c.getString(executorIndex);
        if (r.executor == null || r.executor.trim().isEmpty()) r.executor = "local";
        r.remoteId = remoteIdIndex < 0 ? "" : c.getString(remoteIdIndex);
        r.serverUrl = serverUrlIndex < 0 ? "" : c.getString(serverUrlIndex);
        return r;
    }

    private String secretKey(String accountKey, String field) {
        return "account:" + accountKey + ":" + field;
    }

    private void addColumnIfMissing(SQLiteDatabase db, String table, String column, String type) {
        Cursor c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            while (c.moveToNext()) {
                if (column.equals(c.getString(c.getColumnIndexOrThrow("name")))) return;
            }
        } finally {
            c.close();
        }
        db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
    }
}
