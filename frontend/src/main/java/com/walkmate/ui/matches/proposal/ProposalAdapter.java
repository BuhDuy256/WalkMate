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
import com.walkmate.core.designsystem.view.CountdownTimerView;
import com.walkmate.core.designsystem.view.TagChipGroup;
import com.walkmate.domain.walkproposal.WalkProposal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProposalAdapter extends RecyclerView.Adapter<ProposalAdapter.ViewHolder> {

    // -------------------------------------------------------------------------
    // Listener interface
    // -------------------------------------------------------------------------

    public interface ProposalActionListener {
        void onPass(String proposalId);
        void onAccept(String proposalId);
        void onCancel(String proposalId);
        void onProposalExpired();
    }

    // -------------------------------------------------------------------------

    private final List<WalkProposal> items = new ArrayList<>();
    private ProposalActionListener actionListener;

    public void setProposalActionListener(ProposalActionListener listener) {
        this.actionListener = listener;
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

        private final AvatarInitialView avatarPartner;
        private final TextView txtName;
        private final TextView txtAge;
        private final TextView txtTrustScore;
        private final TextView txtTimeWindow;
        private final CountdownTimerView countdown;
        private final TagChipGroup chipGroupTags;
        private final TextView txtWaitingOverlay;
        private final MaterialButton btnPass;
        private final MaterialButton btnAccept;
        private final MaterialButton btnCancelProposal;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarPartner      = itemView.findViewById(R.id.avatarPartner);
            txtName            = itemView.findViewById(R.id.txtName);
            txtAge             = itemView.findViewById(R.id.txtAge);
            txtTrustScore      = itemView.findViewById(R.id.txtTrustScore);
            txtTimeWindow      = itemView.findViewById(R.id.txtTimeWindow);
            countdown          = itemView.findViewById(R.id.countdownTimer);
            chipGroupTags      = itemView.findViewById(R.id.chipGroupTags);
            txtWaitingOverlay  = itemView.findViewById(R.id.txtWaitingOverlay);
            btnPass            = itemView.findViewById(R.id.btnPass);
            btnAccept          = itemView.findViewById(R.id.btnAccept);
            btnCancelProposal  = itemView.findViewById(R.id.btnCancelProposal);
        }

        void bind(WalkProposal proposal) {
            // Partner display name (may be userId placeholder until VM enriches it)
            String displayName = proposal.getMatchedUserName() != null
                    ? proposal.getMatchedUserName() : proposal.getMatchedUserId();
            avatarPartner.bind(displayName, null);
            txtName.setText(displayName);
            txtAge.setText("· " + proposal.getMatchedUserAge() + " tuổi");
            txtTrustScore.setText(itemView.getContext().getString(
                    R.string.proposal_trust_format, proposal.getTrustScore()));

            // Common time window
            txtTimeWindow.setText(
                    formatTime(proposal.getOverlappingTimeStart())
                    + "  –  "
                    + formatTime(proposal.getOverlappingTimeEnd()));

            chipGroupTags.setTags(proposal.getOverlappingTags());

            // Countdown timer (5-minute TTL)
            countdown.startCountdown(proposal.getExpiresAt());
            countdown.setOnExpiredListener(() -> {
                if (actionListener != null) actionListener.onProposalExpired();
            });

            // Case A: I accepted, waiting for partner → hide Accept/Pass, show waiting overlay
            if (proposal.isAcceptedByMe() && proposal.getStatus() == WalkProposal.Status.PENDING) {
                txtWaitingOverlay.setVisibility(View.VISIBLE);
                btnAccept.setVisibility(View.GONE);
                btnPass.setVisibility(View.GONE);
            } else {
                txtWaitingOverlay.setVisibility(View.GONE);
                btnAccept.setVisibility(View.VISIBLE);
                btnPass.setVisibility(View.VISIBLE);
                btnPass.setOnClickListener(v -> {
                    if (actionListener != null) actionListener.onPass(proposal.getProposalId());
                });
                btnAccept.setOnClickListener(v -> {
                    if (actionListener != null) actionListener.onAccept(proposal.getProposalId());
                });
            }

            btnCancelProposal.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onCancel(proposal.getProposalId());
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
