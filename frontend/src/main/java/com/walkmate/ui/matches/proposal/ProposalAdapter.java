package com.walkmate.ui.matches.proposal;

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
import com.google.android.material.chip.ChipGroup;
import com.walkmate.R;
import com.walkmate.domain.walkproposal.WalkProposal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProposalAdapter extends RecyclerView.Adapter<ProposalAdapter.ViewHolder> {

    // -------------------------------------------------------------------------
    // Listener interfaces
    // -------------------------------------------------------------------------

    public interface OnPassClickListener {
        void onPassClick(WalkProposal proposal);
    }

    public interface OnAcceptClickListener {
        void onAcceptClick(WalkProposal proposal);
    }

    // -------------------------------------------------------------------------

    private final List<WalkProposal> items = new ArrayList<>();
    private OnPassClickListener passListener;
    private OnAcceptClickListener acceptListener;

    public void setOnPassClickListener(OnPassClickListener listener) {
        this.passListener = listener;
    }

    public void setOnAcceptClickListener(OnAcceptClickListener listener) {
        this.acceptListener = listener;
    }

    public void setItems(List<WalkProposal> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_proposal_card, parent, false);
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

        private final TextView txtAvatarInitial;
        private final TextView txtName;
        private final TextView txtAge;
        private final TextView txtTrustScore;
        private final TextView txtTimeWindow;
        private final ChipGroup chipGroupTags;
        private final MaterialButton btnPass;
        private final MaterialButton btnAccept;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtAvatarInitial = itemView.findViewById(R.id.txtAvatarInitial);
            txtName          = itemView.findViewById(R.id.txtName);
            txtAge           = itemView.findViewById(R.id.txtAge);
            txtTrustScore    = itemView.findViewById(R.id.txtTrustScore);
            txtTimeWindow    = itemView.findViewById(R.id.txtTimeWindow);
            chipGroupTags    = itemView.findViewById(R.id.chipGroupTags);
            btnPass          = itemView.findViewById(R.id.btnPass);
            btnAccept        = itemView.findViewById(R.id.btnAccept);
        }

        void bind(WalkProposal proposal) {
            // Avatar initial (first letter of matched user's name)
            String name = proposal.getMatchedUserName();
            txtAvatarInitial.setText(name != null && !name.isEmpty()
                    ? String.valueOf(name.charAt(0)).toUpperCase(Locale.getDefault())
                    : "?");

            txtName.setText(name);
            txtAge.setText("· " + proposal.getMatchedUserAge() + " tuổi");
            txtTrustScore.setText(itemView.getContext().getString(
                    R.string.proposal_trust_format, proposal.getTrustScore()));

            // Common time window
            txtTimeWindow.setText(
                    formatTime(proposal.getOverlappingTimeStart())
                    + "  –  "
                    + formatTime(proposal.getOverlappingTimeEnd()));

            // Common interest chips
            bindTagChips(proposal.getOverlappingTags());

            // Buttons
            btnPass.setOnClickListener(v -> {
                if (passListener != null) passListener.onPassClick(proposal);
            });
            btnAccept.setOnClickListener(v -> {
                if (acceptListener != null) acceptListener.onAcceptClick(proposal);
            });
        }

        private void bindTagChips(List<String> tags) {
            chipGroupTags.removeAllViews();
            if (tags == null || tags.isEmpty()) return;

            int bgColor   = ContextCompat.getColor(chipGroupTags.getContext(), R.color.bg_tag_inactive);
            int textColor = ContextCompat.getColor(chipGroupTags.getContext(), R.color.text_label);

            for (String tag : tags) {
                Chip chip = new Chip(chipGroupTags.getContext());
                chip.setText(tag);
                chip.setClickable(false);
                chip.setFocusable(false);
                chip.setChipBackgroundColor(ColorStateList.valueOf(bgColor));
                chip.setTextColor(textColor);
                chip.setTextSize(12f);
                chip.setChipStrokeWidth(0f);
                chip.setEnsureMinTouchTargetSize(false);
                chip.setCheckedIconVisible(false);
                chip.setChipIconVisible(false);
                chipGroupTags.addView(chip);
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
