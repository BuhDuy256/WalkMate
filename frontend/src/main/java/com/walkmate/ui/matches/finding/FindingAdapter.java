package com.walkmate.ui.matches.finding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.walkmate.R;
import com.walkmate.core.designsystem.view.MatchCardHeaderView;
import com.walkmate.core.designsystem.view.TagChipGroup;
import com.walkmate.domain.walkintent.WalkIntent;
import com.walkmate.ui.matches.MatchesPagerAdapter;

import java.time.Instant;
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
        if (!boundHolders.contains(holder)) boundHolders.add(holder);
        holder.bind(items.get(position));
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.cardHeader.cancelCountdown();
        boundHolders.remove(holder);
    }

    public void cancelAllTimers() {
        for (ViewHolder holder : new ArrayList<>(boundHolders)) {
            holder.cardHeader.cancelCountdown();
        }
        boundHolders.clear();
    }

    private final List<ViewHolder> boundHolders = new ArrayList<>();

    @Override
    public int getItemCount() {
        return items.size();
    }

    // -------------------------------------------------------------------------

    class ViewHolder extends RecyclerView.ViewHolder {

        final MatchCardHeaderView cardHeader;
        private final TextView txtHotspotName;
        private final TextView txtTimeWindow;
        private final Chip chipDuration;
        private final Chip chipAgeRange;
        private final TagChipGroup chipGroupTags;
        private final MaterialButton btnFindMatch;
        private final MaterialButton btnCancelIntent;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardHeader      = itemView.findViewById(R.id.cardHeader);
            txtHotspotName  = itemView.findViewById(R.id.txtHotspotName);
            txtTimeWindow   = itemView.findViewById(R.id.txtTimeWindow);
            chipDuration    = itemView.findViewById(R.id.chipDuration);
            chipAgeRange    = itemView.findViewById(R.id.chipAgeRange);
            chipGroupTags   = itemView.findViewById(R.id.chipGroupTags);
            btnFindMatch    = itemView.findViewById(R.id.btnFindMatch);
            btnCancelIntent = itemView.findViewById(R.id.btnCancelIntent);
        }

        void bind(WalkIntent intent) {
            // Zone 2: hotspot display name — prefer name, fall back to abbreviated ID
            String hotspotName = intent.getHotspotName();
            if (hotspotName != null && !hotspotName.isEmpty()) {
                txtHotspotName.setText(hotspotName);
            } else {
                String id = intent.getHotspotId();
                txtHotspotName.setText(id.length() > 12 ? id.substring(0, 8) + "…" : id);
            }

            txtTimeWindow.setText(formatTime(intent.getTimeStart()) + "  –  " + formatTime(intent.getTimeEnd()));

            int durationMinutes = Math.round((intent.getTimeEnd() - intent.getTimeStart()) * 60f);
            chipDuration.setText(durationMinutes + " min");

            chipAgeRange.setText(itemView.getContext().getString(
                    R.string.age_range_format, intent.getAgeMin(), intent.getAgeMax()));

            chipGroupTags.setTags(intent.getTags());

            // Determine expiry state upfront
            long millisUntilExpiry = Long.MAX_VALUE;
            if (intent.getExpiresAt() != null) {
                millisUntilExpiry = Instant.parse(intent.getExpiresAt()).toEpochMilli()
                        - System.currentTimeMillis();
            }
            boolean isExpired = millisUntilExpiry <= 0;

            // Zone 1: header — status badge + countdown
            if (intent.isMatching()) {
                cardHeader.setStatus("Matched 🔒", MatchCardHeaderView.STYLE_MATCHING);
            } else {
                cardHeader.setStatus("Searching…", MatchCardHeaderView.STYLE_OPEN);
            }

            if (intent.getExpiresAt() != null && !isExpired) {
                cardHeader.startCountdown(intent.getExpiresAt());
                cardHeader.setOnExpiredListener(() -> {
                    if (actionListener != null) actionListener.onIntentExpired();
                });
            } else {
                cardHeader.hideCountdown();
            }

            // Zone 5: actions vary by MATCHING vs OPEN
            if (intent.isMatching()) {
                btnFindMatch.setVisibility(View.VISIBLE);
                btnFindMatch.setText(R.string.btn_view_proposal);
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
    }
}
