package com.pu.localapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

final class ActivityAdapter extends BaseAdapter {
    interface Listener {
        void onClick(Models.Activity activity);

        default boolean onLongClick(Models.Activity activity) {
            return false;
        }
    }

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
        row.setPadding(Ui.dp(context, 14), Ui.dp(context, 7), Ui.dp(context, 14), Ui.dp(context, 11));
        row.setClipToPadding(false);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(context, 14), Ui.dp(context, 13), Ui.dp(context, 14), Ui.dp(context, 12));
        card.setBackground(cardBg());
        card.setElevation(Ui.dp(context, 2));
        card.setTranslationZ(0);
        card.setOnClickListener(v -> listener.onClick(activity));
        card.setOnLongClickListener(v -> listener.onLongClick(activity));
        row.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.TOP);
        card.addView(top);

        FrameLayout imageWrap = new FrameLayout(context);
        LinearLayout.LayoutParams iwlp = new LinearLayout.LayoutParams(Ui.dp(context, 104), Ui.dp(context, 104));
        imageWrap.setLayoutParams(iwlp);
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setAdjustViewBounds(false);
        image.setPadding(Ui.dp(context, 6), Ui.dp(context, 10), Ui.dp(context, 6), Ui.dp(context, 6));
        image.setBackground(Ui.bg(Color.WHITE, 12, context));
        imageWrap.addView(image, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView status = statusBadge(displayStatus(activity));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT);
        imageWrap.addView(status, slp);
        top.addView(imageWrap);
        imageLoader.load(image, activity.coverUrl);

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        blp.setMargins(Ui.dp(context, 14), Ui.dp(context, 3), 0, 0);
        body.setLayoutParams(blp);
        top.addView(body);

        TextView title = Ui.text(context, activity.name.isEmpty() ? "未知活动" : activity.name, 17, Ui.TEXT, Typeface.BOLD);
        title.setMaxLines(2);
        title.setGravity(Gravity.LEFT);
        title.setLineSpacing(Ui.dp(context, 2), 1.0f);
        body.addView(title);

        TextView joinState = Ui.text(context, joinStateText(activity), 13, Ui.MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams jlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        jlp.setMargins(0, Ui.dp(context, 12), 0, Ui.dp(context, 8));
        joinState.setLayoutParams(jlp);
        body.addView(joinState);

        LinearLayout chips = new LinearLayout(context);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        TextView credit = statChip("A+ " + creditText(activity.credit), Ui.PRIMARY_SOFT, Ui.PRIMARY);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(0, 0, Ui.dp(context, 12), 0);
        chips.addView(credit, clp);
        body.addView(chips);

        View line = new DashedLine(context);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 1));
        llp.setMargins(0, Ui.dp(context, 14), 0, Ui.dp(context, 11));
        card.addView(line, llp);

        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView count = Ui.text(context, "已报名 " + activity.joinUserCount + "/" + activity.allowUserCount, 13, Ui.MUTED, Typeface.NORMAL);
        TextView date = Ui.text(context, TimeUtil.dateRange(activity), 13, Ui.MUTED, Typeface.NORMAL);
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
        TextView badge = Ui.text(context, text, 12, Color.WHITE, Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(Ui.dp(context, 9), Ui.dp(context, 5), Ui.dp(context, 9), Ui.dp(context, 5));
        badge.setBackground(Ui.bg(statusColor(text), 999, context));
        return badge;
    }

    private TextView statChip(String text, int bg, int fg) {
        TextView chip = Ui.text(context, text, 14, fg, Typeface.BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(Ui.dp(context, 10), Ui.dp(context, 5), Ui.dp(context, 10), Ui.dp(context, 5));
        chip.setMinWidth(Ui.dp(context, 46));
        chip.setBackground(Ui.bg(bg, 7, context));
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
        if (activity.statusName != null && !activity.statusName.isEmpty()) return activity.statusName;
        return "报名状态未知";
    }

    private String creditText(String value) {
        if (value == null || value.trim().isEmpty()) return "0";
        try {
            float n = Float.parseFloat(value.trim());
            return String.format(java.util.Locale.CHINA, "%.2f", n);
        } catch (Exception ignored) {
            return value.trim();
        }
    }

    private String countText(String value) {
        if (value == null || value.trim().isEmpty()) return "0";
        return value.trim();
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
