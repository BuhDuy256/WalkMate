package com.walkmate.ui.matches.finding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.walkmate.R;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.ui.matches.MatchesPagerAdapter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FindingAdapter extends RecyclerView.Adapter<FindingAdapter.ViewHolder> {

    public interface OnCancelClickListener {
        void onCancelClick(WalkIntent intent);
    }

    public interface OnIntentActionListener {
        void onViewProposalClicked(String intentId);
        void onIntentExpired();
    }

    private final List<WalkIntent> items = new ArrayList<>();
    private OnCancelClickListener cancelListener;
    private OnIntentActionListener actionListener;

    public void setOnCancelClickListener(OnCancelClickListener listener) {
        this.cancelListener = listener;
    }

    public void setOnIntentActionListener(OnIntentActionListener listener) {
        this.actionListener = listener;
    }

    public void setItems(List<WalkIntent> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_finding_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // -------------------------------------------------------------------------

    class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView     txtHotspotName;
        private final TextView     txtCreatedAt;
        private final LinearLayout layoutStatusBadge;
        private final TextView     txtStatusBadge;
        private final TextView     txtTimeWindow;
        private final TextView     txtAgeRange;
        private final MaterialButton btnFindMatch;
        private final MaterialButton btnCancelIntent;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtHotspotName    = itemView.findViewById(R.id.txtHotspotName);
            txtCreatedAt      = itemView.findViewById(R.id.txtCreatedAt);
            layoutStatusBadge = itemView.findViewById(R.id.layoutStatusBadge);
            txtStatusBadge    = itemView.findViewById(R.id.txtStatusBadge);
            txtTimeWindow     = itemView.findViewById(R.id.txtTimeWindow);
            txtAgeRange       = itemView.findViewById(R.id.txtAgeRange);
            btnFindMatch      = itemView.findViewById(R.id.btnFindMatch);
            btnCancelIntent   = itemView.findViewById(R.id.btnCancelIntent);
        }

        void bind(WalkIntent intent) {
            // Location name
            String hotspotName = intent.getHotspotName();
            if (hotspotName != null && !hotspotName.isEmpty()) {
                txtHotspotName.setText(hotspotName);
            } else {
                String id = intent.getHotspotId();
                txtHotspotName.setText(id.length() > 12 ? id.substring(0, 8) + "…" : id);
            }

            // Created at (relative time)
            txtCreatedAt.setText(formatRelativeTime(intent.getCreatedAt()));

            // Time window
            txtTimeWindow.setText(
                    formatTime(intent.getTimeStart()) + " – " + formatTime(intent.getTimeEnd()));

            // Age range
            txtAgeRange.setText(itemView.getContext().getString(
                    R.string.age_range_format, intent.getAgeMin(), intent.getAgeMax()));

            // Expiry check
            boolean isExpired = false;
            if (intent.getExpiresAt() != null) {
                isExpired = Instant.parse(intent.getExpiresAt()).toEpochMilli()
                        <= System.currentTimeMillis();
            }

            // Status badge
            if (intent.isMatching()) {
                txtStatusBadge.setText("Matched 🔒");
            } else {
                txtStatusBadge.setText("Finding…");
            }

            // Actions
            if (intent.isMatching()) {
                btnFindMatch.setVisibility(View.VISIBLE);
                btnFindMatch.setOnClickListener(v -> {
                    if (actionListener != null) actionListener.onViewProposalClicked(intent.getId());
                });
                btnCancelIntent.setVisibility(View.GONE);
            } else {
                btnFindMatch.setVisibility(View.GONE);
                btnCancelIntent.setVisibility(isExpired ? View.GONE : View.VISIBLE);
                if (!isExpired) {
                    btnCancelIntent.setOnClickListener(v -> {
                        if (cancelListener != null) cancelListener.onCancelClick(intent);
                    });
                }
            }
        }

        private String formatTime(float hourFloat) {
            int hour   = (int) Math.floor(hourFloat);
            int minute = Math.round((hourFloat - hour) * 60f);
            if (minute >= 60) { hour++; minute = 0; }
            return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
        }

        private String formatRelativeTime(String isoTime) {
            if (isoTime == null || isoTime.isEmpty()) return "";
            try {
                Instant created = Instant.parse(isoTime);
                Instant now = Instant.now();
                long hours = ChronoUnit.HOURS.between(created, now);
                if (hours < 1) {
                    long minutes = ChronoUnit.MINUTES.between(created, now);
                    return minutes <= 1 ? "Just now" : minutes + "m ago";
                }
                if (hours < 24) return hours + "h ago";
                long days = ChronoUnit.DAYS.between(created, now);
                return days + "d ago";
            } catch (Exception e) {
                return "";
            }
        }
    }
}
