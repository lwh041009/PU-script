package com.pu.localapp;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class TimeUtil {
    private static final String[] PATTERNS = {
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy.MM.dd HH:mm:ss",
            "yyyy.MM.dd HH:mm",
            "yyyy-MM-dd",
            "yyyy.MM.dd"
    };

    private TimeUtil() {
    }

    static long parseMillis(String value) {
        if (value == null) return 0;
        String text = value.trim();
        if (text.isEmpty()) return 0;
        for (String pattern : PATTERNS) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.CHINA);
                format.setTimeZone(BeijingTime.ZONE);
                Date date = format.parse(text);
                if (date != null) return date.getTime();
            } catch (ParseException ignored) {
            }
        }
        return 0;
    }

    static String dateRange(Models.Activity activity) {
        String start = compactDate(activity.startTime);
        String end = compactDate(activity.endTime);
        if (start.isEmpty()) start = compactDate(activity.joinStartTime);
        if (end.isEmpty()) end = compactDate(activity.joinEndTime);
        if (start.isEmpty() && end.isEmpty()) return "时间未知";
        if (end.isEmpty() || start.equals(end)) return start;
        return start + " - " + end;
    }

    static String compactDate(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String v = value.trim();
        if (v.length() >= 10) return v.substring(0, 10).replace('-', '.');
        return v;
    }
}
