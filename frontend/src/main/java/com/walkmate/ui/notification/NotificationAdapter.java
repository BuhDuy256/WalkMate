package com.walkmate.ui.notification;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.domain.notification.Notification;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // ── Listener ──────────────────────────────────────────────────────────────

    public interface OnReadListener {
        void onMarkRead(Notification notification);
    }

    // ── Row model ─────────────────────────────────────────────────────────────

    private static class Row {
        final String       sectionLabel;
        final Notification notification;
        final long         epochMs;

        Row(String label) {
            sectionLabel = label;
            notification = null;
            epochMs      = 0;
        }

        Row(Notification n, long epochMs) {
            sectionLabel    = null;
            this.notification = n;
            this.epochMs      = epochMs;
        }

        boolean isHeader() { return sectionLabel != null; }
    }

    // ── View types ─────────────────────────────────────────────────────────────

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_ITEM   = 1;

    // ── State ─────────────────────────────────────────────────────────────────

    private final List<Row> rows = new ArrayList<>();
    private OnReadListener readListener;

    // ── Public API ────────────────────────────────────────────────────────────

    public void setOnReadListener(OnReadListener listener) {
        this.readListener = listener;
    }

    public void submitList(List<Notification> newItems) {
        rows.clear();
        if (newItems != null) rows.addAll(buildRows(newItems));
        notifyDataSetChanged();
    }

    // ── RecyclerView.Adapter ──────────────────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isHeader() ? VIEW_TYPE_HEADER : VIEW_TYPE_ITEM;
    }

    @Override
    public int getItemCount() { return rows.size(); }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            return new HeaderViewHolder(inf.inflate(R.layout.item_notification_header, parent, false));
        }
        return new ItemViewHolder(inf.inflate(R.layout.item_notification, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (row.isHeader()) {
            ((HeaderViewHolder) holder).bind(row.sectionLabel);
        } else {
            ((ItemViewHolder) holder).bind(row.notification, row.epochMs, readListener);
        }
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtLabel;

        HeaderViewHolder(@NonNull View v) {
            super(v);
            txtLabel = (TextView) v;
        }

        void bind(String label) { txtLabel.setText(label); }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {

        private final com.google.android.material.card.MaterialCardView card;
        private final View      accentBar;
        private final ImageView imgIcon;
        private final TextView  txtTitle;
        private final TextView  txtTime;
        private final View      dotUnread;
        private final TextView  txtBody;

        ItemViewHolder(@NonNull View v) {
            super(v);
            card      = (com.google.android.material.card.MaterialCardView) v;
            accentBar = v.findViewById(R.id.view_accent_bar);
            imgIcon   = v.findViewById(R.id.img_notif_icon);
            txtTitle  = v.findViewById(R.id.txt_notification_title);
            txtTime   = v.findViewById(R.id.txt_notification_time);
            dotUnread = v.findViewById(R.id.view_unread_dot);
            txtBody   = v.findViewById(R.id.txt_notification_body);
        }

        void bind(Notification n, long epochMs, OnReadListener listener) {
            txtTitle.setText(labelFor(n.getType()));
            txtBody.setText(bodyFor(n));
            txtTime.setText(formatRelativeTime(epochMs));

            if (n.isRead()) {
                card.setCardBackgroundColor(Color.WHITE);
                card.setStrokeColor(Color.parseColor("#F3F2F0"));
                accentBar.setVisibility(View.GONE);
                dotUnread.setVisibility(View.GONE);
            } else {
                card.setCardBackgroundColor(Color.parseColor("#FFFBF7"));
                card.setStrokeColor(Color.parseColor("#FDDCB5"));
                accentBar.setVisibility(View.VISIBLE);
                dotUnread.setVisibility(View.VISIBLE);
            }

            setupIcon(imgIcon, n.getType());

            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onMarkRead(n));
            } else {
                itemView.setOnClickListener(null);
            }
        }

        private static void setupIcon(ImageView img, Notification.Type type) {
            int iconRes;
            int iconColor;
            int bgColor;

            switch (type) {
                case REVIEW_REQUESTED:
                    iconRes   = R.drawable.ic_notif_star;
                    iconColor = Color.parseColor("#F59E0B");
                    bgColor   = Color.parseColor("#FFFBEB");
                    break;
                case SESSION_ACTIVE:
                    iconRes   = R.drawable.ic_distance;
                    iconColor = Color.parseColor("#10B981");
                    bgColor   = Color.parseColor("#ECFDF5");
                    break;
                case SESSION_CONFIRMED:
                case PROPOSAL_ACCEPTED:
                    iconRes   = R.drawable.ic_notif_check_circle;
                    iconColor = Color.parseColor("#3B82F6");
                    bgColor   = Color.parseColor("#EFF6FF");
                    break;
                default:
                    iconRes   = R.drawable.ic_notif_user_plus;
                    iconColor = Color.parseColor("#8B5CF6");
                    bgColor   = Color.parseColor("#F5F3FF");
                    break;
            }

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            float r = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12,
                    img.getContext().getResources().getDisplayMetrics());
            bg.setCornerRadius(r);
            bg.setColor(bgColor);
            img.setBackground(bg);

            img.setImageResource(iconRes);
            ImageViewCompat.setImageTintList(img, ColorStateList.valueOf(iconColor));
        }

        private static String labelFor(Notification.Type type) {
            switch (type) {
                case PROPOSAL_RECEIVED:        return "New Walk Proposal";
                case INVITE_SENT:              return "Private Invite Sent";
                case PROPOSAL_ACCEPTED:        return "Proposal Accepted";
                case SESSION_CONFIRMED:        return "Walk Confirmed!";
                case SESSION_ACTIVE:           return "Your Walk Has Started";
                case REVIEW_REQUESTED:         return "Leave a Review";
                case FRIEND_REQUEST_RECEIVED:  return "New Friend Request";
                case FRIEND_REQUEST_ACCEPTED:  return "Friend Request Accepted";
                case FRIEND_REQUEST_DECLINED:  return "Friend Request Declined";
                default:                       return "Notification";
            }
        }

        private static String bodyFor(Notification n) {
            switch (n.getType()) {
                case PROPOSAL_RECEIVED:        return "You have a new walk match proposal. Tap to review it.";
                case INVITE_SENT:              return "Your private invite was sent. Tap to track it.";
                case PROPOSAL_ACCEPTED:        return "Your partner accepted the proposal!";
                case SESSION_CONFIRMED:        return "Both of you accepted — your walk session is confirmed.";
                case SESSION_ACTIVE:           return "Both partners have arrived. Your walk is underway!";
                case REVIEW_REQUESTED:         return "Your walk ended. Please rate your experience.";
                case FRIEND_REQUEST_RECEIVED:  return "Someone sent you a friend request. Tap to review it.";
                case FRIEND_REQUEST_ACCEPTED:  return "Your friend request was accepted!";
                case FRIEND_REQUEST_DECLINED:  return "Your friend request was declined.";
                default:                       return "";
            }
        }
    }

    // ── Row building helpers ──────────────────────────────────────────────────

    private static List<Row> buildRows(List<Notification> notifications) {
        List<Row> today     = new ArrayList<>();
        List<Row> yesterday = new ArrayList<>();
        List<Row> earlier   = new ArrayList<>();

        Calendar todayCal = Calendar.getInstance();
        Calendar yesCal   = Calendar.getInstance();
        yesCal.add(Calendar.DAY_OF_YEAR, -1);

        for (Notification n : notifications) {
            long epochMs = parseIsoToEpochMs(n.getCreatedAt());
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(epochMs);
            Row row = new Row(n, epochMs);

            if (isSameDay(cal, todayCal))   today.add(row);
            else if (isSameDay(cal, yesCal)) yesterday.add(row);
            else                             earlier.add(row);
        }

        List<Row> result = new ArrayList<>();
        if (!today.isEmpty())     { result.add(new Row("TODAY"));     result.addAll(today); }
        if (!yesterday.isEmpty()) { result.add(new Row("YESTERDAY")); result.addAll(yesterday); }
        if (!earlier.isEmpty())   { result.add(new Row("EARLIER"));   result.addAll(earlier); }
        return result;
    }

    private static long parseIsoToEpochMs(String createdAt) {
        if (createdAt == null || createdAt.isEmpty()) return System.currentTimeMillis();
        try {
            String s = createdAt.trim().replaceAll("\\.\\d+", ""); // strip millis
            if (s.endsWith("Z")) {
                s = s.substring(0, s.length() - 1) + "+0000";
            } else {
                s = s.replaceAll("([+\\-])(\\d{2}):(\\d{2})$", "$1$2$3"); // +07:00 → +0700
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = sdf.parse(s);
            return d != null ? d.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static String formatRelativeTime(long epochMs) {
        long diffMs  = System.currentTimeMillis() - epochMs;
        long diffMin = diffMs / 60_000L;
        if (diffMin < 1)  return "Just now";
        if (diffMin < 60) return diffMin + "m ago";
        long diffHr = diffMin / 60;
        if (diffHr < 24)  return diffHr + "h ago";
        long diffDay = diffHr / 24;
        if (diffDay == 1) return "Yesterday";
        return diffDay + "d ago";
    }

    private static boolean isSameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR)        == b.get(Calendar.YEAR)
            && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
