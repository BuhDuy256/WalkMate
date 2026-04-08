package com.walkmate.ui.matches.finding;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.walkmate.R;
import com.walkmate.core.designsystem.view.CountdownTimerView;
import com.walkmate.core.designsystem.view.TagChipGroup;
import com.walkmate.domain.walkintent.WalkIntent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FindingAdapter extends RecyclerView.Adapter<FindingAdapter.ViewHolder> {

    public interface OnCancelClickListener {
        void onCancelClick(WalkIntent intent);
    }

    public interface OnIntentActionListener {
        void onFindMatchClicked(String intentId);
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
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.countdown.cancelCountdown();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // -------------------------------------------------------------------------

    class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView txtHotspotName;
        private final TextView txtTimeWindow;
        private final CountdownTimerView countdown;
        private final Chip chipDuration;
        private final Chip chipAgeRange;
        private final TagChipGroup chipGroupTags;
        private final Chip chipStatus;
        private final ImageView imgLock;
        private final MaterialButton btnFindMatch;
        private final MaterialButton btnCancelIntent;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtHotspotName  = itemView.findViewById(R.id.txtHotspotName);
            txtTimeWindow   = itemView.findViewById(R.id.txtTimeWindow);
            countdown       = itemView.findViewById(R.id.countdownTimer);
            chipDuration    = itemView.findViewById(R.id.chipDuration);
            chipAgeRange    = itemView.findViewById(R.id.chipAgeRange);
            chipGroupTags   = itemView.findViewById(R.id.chipGroupTags);
            chipStatus      = itemView.findViewById(R.id.chipStatus);
            imgLock         = itemView.findViewById(R.id.imgLock);
            btnFindMatch    = itemView.findViewById(R.id.btnFindMatch);
            btnCancelIntent = itemView.findViewById(R.id.btnCancelIntent);
        }

        void bind(WalkIntent intent) {
            txtHotspotName.setText(intent.getHotspotId());

            txtTimeWindow.setText(formatTime(intent.getTimeStart()) + "  –  " + formatTime(intent.getTimeEnd()));

            int durationMinutes = Math.round((intent.getTimeEnd() - intent.getTimeStart()) * 60f);
            chipDuration.setText(durationMinutes + " min");

            chipAgeRange.setText(itemView.getContext().getString(
                    R.string.age_range_format, intent.getAgeMin(), intent.getAgeMax()));

            chipGroupTags.setTags(intent.getTags());

            bindStatusChip(intent.getStatus());

            // Countdown timer
            if (intent.getExpiresAt() != null) {
                countdown.setVisibility(View.VISIBLE);
                countdown.startCountdown(intent.getExpiresAt());
                countdown.setOnExpiredListener(() -> {
                    if (actionListener != null) actionListener.onIntentExpired();
                });
            } else {
                countdown.setVisibility(View.GONE);
                countdown.cancelCountdown();
            }

            // OPEN vs MATCHING state
            if (intent.isMatching()) {
                btnFindMatch.setText(R.string.btn_view_proposal);
                btnFindMatch.setOnClickListener(v -> {
                    if (actionListener != null) actionListener.onViewProposalClicked(intent.getId());
                });
                btnCancelIntent.setVisibility(View.GONE);
                imgLock.setVisibility(View.VISIBLE);
            } else {
                // OPEN (default)
                btnFindMatch.setText(R.string.find_match);
                btnFindMatch.setOnClickListener(v -> {
                    if (actionListener != null) actionListener.onFindMatchClicked(intent.getId());
                });
                btnCancelIntent.setVisibility(View.VISIBLE);
                imgLock.setVisibility(View.GONE);
                btnCancelIntent.setOnClickListener(v -> {
                    if (cancelListener != null) cancelListener.onCancelClick(intent);
                });
            }
        }

        private void bindStatusChip(String status) {
            chipStatus.setText(status);
            int bgColor;
            int textColor;
            if ("CONSUMED".equals(status)) {
                bgColor   = ContextCompat.getColor(itemView.getContext(), R.color.bg_tag_inactive);
                textColor = ContextCompat.getColor(itemView.getContext(), R.color.text_label);
            } else {
                // OPEN (and any other active state)
                bgColor   = ContextCompat.getColor(itemView.getContext(), R.color.bg_warm_light);
                textColor = ContextCompat.getColor(itemView.getContext(), R.color.orange_end);
            }
            chipStatus.setChipBackgroundColor(ColorStateList.valueOf(bgColor));
            chipStatus.setTextColor(textColor);
        }

        private String formatTime(float hourFloat) {
            int hour   = (int) Math.floor(hourFloat);
            int minute = Math.round((hourFloat - hour) * 60f);
            if (minute >= 60) { hour++; minute = 0; }
            return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
        }
    }
}
