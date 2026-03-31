package com.walkmate.ui.matches.proposal;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.walkmate.R;
import com.walkmate.core.designsystem.view.AvatarInitialView;
import com.walkmate.core.designsystem.view.TagChipGroup;
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

        private final AvatarInitialView avatarPartner;
        private final TextView txtName;
        private final TextView txtAge;
        private final TextView txtTrustScore;
        private final TextView txtTimeWindow;
        private final TagChipGroup chipGroupTags;
        private final MaterialButton btnPass;
        private final MaterialButton btnAccept;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarPartner = itemView.findViewById(R.id.avatarPartner);
            txtName       = itemView.findViewById(R.id.txtName);
            txtAge        = itemView.findViewById(R.id.txtAge);
            txtTrustScore = itemView.findViewById(R.id.txtTrustScore);
            txtTimeWindow = itemView.findViewById(R.id.txtTimeWindow);
            chipGroupTags = itemView.findViewById(R.id.chipGroupTags);
            btnPass       = itemView.findViewById(R.id.btnPass);
            btnAccept     = itemView.findViewById(R.id.btnAccept);
        }

        void bind(WalkProposal proposal) {
            String name = proposal.getMatchedUserName();
            avatarPartner.bind(name, null);

            txtName.setText(name);
            txtAge.setText("· " + proposal.getMatchedUserAge() + " tuổi");
            txtTrustScore.setText(itemView.getContext().getString(
                    R.string.proposal_trust_format, proposal.getTrustScore()));

            // Common time window
            txtTimeWindow.setText(
                    formatTime(proposal.getOverlappingTimeStart())
                    + "  –  "
                    + formatTime(proposal.getOverlappingTimeEnd()));

            chipGroupTags.setTags(proposal.getOverlappingTags());

            // Buttons
            btnPass.setOnClickListener(v -> {
                if (passListener != null) passListener.onPassClick(proposal);
            });
            btnAccept.setOnClickListener(v -> {
                if (acceptListener != null) acceptListener.onAcceptClick(proposal);
            });
        }

        private String formatTime(float hourFloat) {
            int hour   = (int) Math.floor(hourFloat);
            int minute = Math.round((hourFloat - hour) * 60f);
            if (minute >= 60) { hour++; minute = 0; }
            return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
        }
    }
}
