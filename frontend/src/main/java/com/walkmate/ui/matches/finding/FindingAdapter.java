package com.walkmate.ui.matches.finding;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.walkmate.R;
import com.walkmate.core.designsystem.view.TagChipGroup;
import com.walkmate.domain.walkintent.WalkIntent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FindingAdapter extends RecyclerView.Adapter<FindingAdapter.ViewHolder> {

    public interface OnCancelClickListener {
        void onCancelClick(WalkIntent intent);
    }

    private final List<WalkIntent> items = new ArrayList<>();
    private OnCancelClickListener cancelListener;

    public void setOnCancelClickListener(OnCancelClickListener listener) {
        this.cancelListener = listener;
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

        private final TextView txtHotspotName;
        private final TextView txtTimeWindow;
        private final Chip chipDuration;
        private final Chip chipAgeRange;
        private final TagChipGroup chipGroupTags;
        private final Chip chipStatus;
        private final MaterialButton btnCancelIntent;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtHotspotName  = itemView.findViewById(R.id.txtHotspotName);
            txtTimeWindow   = itemView.findViewById(R.id.txtTimeWindow);
            chipDuration    = itemView.findViewById(R.id.chipDuration);
            chipAgeRange    = itemView.findViewById(R.id.chipAgeRange);
            chipGroupTags   = itemView.findViewById(R.id.chipGroupTags);
            chipStatus      = itemView.findViewById(R.id.chipStatus);
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

            btnCancelIntent.setOnClickListener(v -> {
                if (cancelListener != null) cancelListener.onCancelClick(intent);
            });
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
