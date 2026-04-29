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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProposalAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_CARD   = 1;

    // ── Listener ────────────────────────────────────────────────────────────────

    public interface ProposalActionListener {
        void onPass(String proposalId, boolean isPrivateInvite);
        void onAccept(String proposalId);
        void onCancel(String proposalId);
        void onProposalExpired();
        void onViewProfile(String userId);
    }

    // ── Data ─────────────────────────────────────────────────────────────────────

    /** Flat list mixing GroupHeader and WalkProposal in display order. */
    private final List<Object> displayItems = new ArrayList<>();
    private ProposalActionListener actionListener;

    public void setProposalActionListener(ProposalActionListener listener) {
        this.actionListener = listener;
    }

    public void setItems(List<WalkProposal> proposals) {
        displayItems.clear();
        if (proposals != null && !proposals.isEmpty()) {
            // Group by hotspot name (preserving order of first occurrence)
            Map<String, List<WalkProposal>> groups = new LinkedHashMap<>();
            for (WalkProposal p : proposals) {
                String key = p.getHotspotName() != null ? p.getHotspotName() : "—";
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
            }
            for (Map.Entry<String, List<WalkProposal>> entry : groups.entrySet()) {
                displayItems.add(new GroupHeader(entry.getKey(), entry.getValue().size()));
                displayItems.addAll(entry.getValue());
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return displayItems.get(position) instanceof GroupHeader
                ? VIEW_TYPE_HEADER : VIEW_TYPE_CARD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_proposal_header, parent, false);
            return new HeaderViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_proposal_card, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((GroupHeader) displayItems.get(position));
        } else {
            ((CardViewHolder) holder).bind((WalkProposal) displayItems.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return displayItems.size();
    }

    // ── GroupHeader model ─────────────────────────────────────────────────────────

    static class GroupHeader {
        final String hotspotName;
        final int count;
        GroupHeader(String hotspotName, int count) {
            this.hotspotName = hotspotName;
            this.count = count;
        }
    }

    // ── Header ViewHolder ─────────────────────────────────────────────────────────

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtGroupHotspot;
        private final TextView txtGroupCount;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            txtGroupHotspot = itemView.findViewById(R.id.txtGroupHotspot);
            txtGroupCount   = itemView.findViewById(R.id.txtGroupCount);
        }

        void bind(GroupHeader header) {
            txtGroupHotspot.setText(header.hotspotName);
            txtGroupCount.setText("⚡ " + header.count + " new");
        }
    }

    // ── Card ViewHolder ───────────────────────────────────────────────────────────

    class CardViewHolder extends RecyclerView.ViewHolder {

        private final AvatarInitialView avatarPartner;
        private final TextView txtName;
        private final TextView txtAge;
        private final TagChipGroup chipGroupTags;
        private final TextView txtTimeWindow;
        private final TextView txtMeetingLocation;
        private final TextView txtWaitingOverlay;
        private final MaterialButton btnPass;
        private final MaterialButton btnAccept;
        private final MaterialButton btnCancelProposal;

        CardViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarPartner      = itemView.findViewById(R.id.avatarPartner);
            txtName            = itemView.findViewById(R.id.txtName);
            txtAge             = itemView.findViewById(R.id.txtAge);
            chipGroupTags      = itemView.findViewById(R.id.chipGroupTags);
            txtTimeWindow      = itemView.findViewById(R.id.txtTimeWindow);
            txtMeetingLocation = itemView.findViewById(R.id.txtMeetingLocation);
            txtWaitingOverlay  = itemView.findViewById(R.id.txtWaitingOverlay);
            btnPass            = itemView.findViewById(R.id.btnPass);
            btnAccept          = itemView.findViewById(R.id.btnAccept);
            btnCancelProposal  = itemView.findViewById(R.id.btnCancelProposal);
        }

        void bind(WalkProposal proposal) {
            String displayName = proposal.getMatchedUserName() != null
                    ? proposal.getMatchedUserName() : proposal.getMatchedUserId();

            avatarPartner.bind(displayName, proposal.getMatchedUserAvatarUrl());
            txtName.setText(displayName);

            View.OnClickListener profileClick = v -> {
                if (actionListener != null && proposal.getMatchedUserId() != null) {
                    actionListener.onViewProfile(proposal.getMatchedUserId());
                }
            };
            avatarPartner.setOnClickListener(profileClick);
            txtName.setOnClickListener(profileClick);

            txtAge.setText(proposal.getMatchedUserAge() + " yrs");

            chipGroupTags.setTags(proposal.getOverlappingTags());

            txtTimeWindow.setText(
                    formatTime(proposal.getOverlappingTimeStart())
                    + " – "
                    + formatTime(proposal.getOverlappingTimeEnd()));

            String hotspot = proposal.getHotspotName();
            txtMeetingLocation.setText(hotspot != null ? hotspot : "—");

            boolean waiting = proposal.isCurrentUserAccepted()
                    && proposal.getStatus() == WalkProposal.Status.PENDING;

            if (waiting) {
                txtWaitingOverlay.setVisibility(View.VISIBLE);
                btnAccept.setVisibility(View.GONE);
                btnPass.setVisibility(View.GONE);
            } else {
                txtWaitingOverlay.setVisibility(View.GONE);
                btnAccept.setVisibility(View.VISIBLE);
                btnPass.setVisibility(View.VISIBLE);
                btnPass.setOnClickListener(v -> {
                    if (actionListener != null)
                        actionListener.onPass(proposal.getProposalId(), proposal.isPrivateInvite());
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
