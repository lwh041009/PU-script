package com.pu.localapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ActivityAdapter extends BaseAdapter {
    interface Listener {
        void onClick(Models.Activity activity);

        default boolean onLongClick(Models.Activity activity) {
            return false;
        }
    }

    private static final int PU_SOFT = Color.rgb(255, 236, 240);
    private static final int PU_TEXT = Color.rgb(232, 96, 126);

    private final Context context;
    private final ImageLoader imageLoader = new ImageLoader();
    private final Listener listener;
    private List<Models.Activity> data = new ArrayList<>();

    ActivityAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    void submit(List<Models.Activity> list) {
        data = list == null ? new ArrayList<>() : list;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Object getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return data.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Models.Activity activity = data.get(position);

        FrameLayout row = new FrameLayout(context);
        row.setPadding(Ui.dp(context, 12), Ui.dp(context, 3), Ui.dp(context, 12), Ui.dp(context, 3));
        row.setClipToPadding(false);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(context, 10), Ui.dp(context, 8), Ui.dp(context, 10), Ui.dp(context, 8));
        card.setBackground(cardBg());
        card.setElevation(Ui.dp(context, 1));
        card.setTranslationZ(0);
        card.setOnClickListener(v -> listener.onClick(activity));
        card.setOnLongClickListener(v -> listener.onLongClick(activity));
        row.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);
        card.addView(top);

        FrameLayout imageWrap = new FrameLayout(context);
        LinearLayout.LayoutParams iwlp = new LinearLayout.LayoutParams(Ui.dp(context, 76), Ui.dp(context, 76));
        imageWrap.setLayoutParams(iwlp);
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(Ui.bg(Color.WHITE, 10, context));
        imageWrap.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView status = statusBadge(displayStatus(activity));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        imageWrap.addView(status, slp);
        top.addView(imageWrap);
        imageLoader.load(image, activity.coverUrl);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.setMargins(Ui.dp(context, 10), 0, 0, 0);
        body.setLayoutParams(blp);
        top.addView(body);

        TextView title = Ui.text(context, activity.name.isEmpty() ? "未知活动" : activity.name, 15, Ui.TEXT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setGravity(Gravity.LEFT);
        title.setLineSpacing(Ui.dp(context, 1), 1.0f);
        body.addView(title);

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.setMargins(0, Ui.dp(context, 6), 0, 0);
        meta.setLayoutParams(mlp);
        String joinTypeLabel = activity.joinTypeLabel();
        if (!joinTypeLabel.isEmpty()) {
            TextView audit = Ui.text(context, joinTypeLabel, 12, Ui.PRIMARY, Typeface.BOLD);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            alp.setMargins(0, 0, Ui.dp(context, 8), 0);
            meta.addView(audit, alp);
        }
        TextView joinState = Ui.text(context, joinStateText(activity), 12, Ui.MUTED, Typeface.NORMAL);
        meta.addView(joinState, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(meta);

        LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chlp.setMargins(0, Ui.dp(context, 6), 0, 0);
        chips.setLayoutParams(chlp);
        TextView credit = statChip(formatScore(activity.credit, "0.00"), Ui.PRIMARY_SOFT, Ui.PRIMARY);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, 0, Ui.dp(context, 8), 0);
        chips.addView(credit, clp);
        chips.addView(statChip(formatScore(activity.puAmount, "0"), PU_SOFT, PU_TEXT),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(chips);

        View line = new DashedLine(context);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 1));
        llp.setMargins(0, Ui.dp(context, 8), 0, Ui.dp(context, 6));
        card.addView(line, llp);

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView count = Ui.text(context, "已报名人数 " + activity.joinUserCount + "/" + activity.allowUserCount, 11, Ui.MUTED, Typeface.NORMAL);
        TextView date = Ui.text(context, TimeUtil.dateRange(activity), 11, Ui.MUTED, Typeface.NORMAL);
        date.setGravity(Gravity.RIGHT);
        footer.addView(count, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        footer.addView(date, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(footer);
        return row;
    }

    private GradientDrawable cardBg() {
        return Ui.strokeBg(Color.argb(246, 255, 255, 255), Ui.LINE_STRONG, 1, 14, context);
    }

    private TextView statusBadge(String text) {
        TextView badge = Ui.text(context, text, 10, Color.WHITE, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Ui.dp(context, 6), Ui.dp(context, 3), Ui.dp(context, 6), Ui.dp(context, 3));
        badge.setBackground(Ui.bg(statusColor(text), 8, context));
        return badge;
    }

    private TextView statChip(String text, int bg, int fg) {
        TextView chip = Ui.text(context, text, 12, fg, Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(Ui.dp(context, 7), Ui.dp(context, 3), Ui.dp(context, 7), Ui.dp(context, 3));
        chip.setMinWidth(Ui.dp(context, 40));
        chip.setBackground(Ui.bg(bg, 6, context));
        return chip;
    }

    private int statusColor(String text) {
        if ("报名中".equals(text)) return Ui.SUCCESS;
        if ("未开始".equals(text)) return Ui.PRIMARY;
        if (text != null && text.contains("结束")) return Ui.MUTED;
        return Ui.WARNING;
    }

    private String joinStateText(Models.Activity activity) {
        long now = BeijingTime.now(context);
        if (activity.inJoinWindow(now)) return "报名进行中";
        if (activity.notStarted(now)) return "报名未开始";
        if (activity.statusName != null && activity.statusName.contains("结束")) return "报名已结束";
        if (activity.statusName != null && !activity.statusName.isEmpty()) return activity.statusName;
        return "报名已结束";
    }

    private String formatScore(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try {
            float n = Float.parseFloat(value.trim());
            if (Math.abs(n - Math.round(n)) < 0.001f) return String.valueOf(Math.round(n));
            return String.format(Locale.CHINA, "%.2f", n);
        } catch (Exception ignored) {
            return value.trim();
        }
    }

    private String displayStatus(Models.Activity activity) {
        long now = BeijingTime.now(context);
        if (activity.inJoinWindow(now)) return "报名中";
        if (activity.notStarted(now)) return "未开始";
        if (activity.statusName != null && !activity.statusName.isEmpty()) return activity.statusName;
        return "活动";
    }

    private static final class DashedLine extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DashedLine(Context context) {
            super(context);
            paint.setColor(Color.rgb(232, 232, 232));
            paint.setStrokeWidth(Ui.dp(context, 1));
            paint.setStyle(Paint.Style.STROKE);
            paint.setPathEffect(new DashPathEffect(new float[]{Ui.dp(context, 4), Ui.dp(context, 6)}, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float y = getHeight() / 2f;
            canvas.drawLine(0, y, getWidth(), y, paint);
        }
    }
}
