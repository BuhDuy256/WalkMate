package com.walkmate.ui.history;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.walkmate.R;
import com.walkmate.core.designsystem.view.AvatarInitialView;
import com.walkmate.domain.walksession.ParticipantSummary;
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.domain.walksession.WalkSession;

import java.util.Locale;

/**
 * RecyclerView adapter for the Session History list.
 *
 * Button visibility per UX invariant:
 * ACTIVE global → both buttons hidden (silent wait)
 * COMPLETED global:
 * caller≠NO_SHOW + partner=COMPLETED → "Leave a Review" + "Report"
 * caller≠NO_SHOW + partner≠COMPLETED → "Report" only
 * caller=NO_SHOW (any partner) → both hidden
 */
public class SessionHistoryAdapter
        extends ListAdapter<SessionSummary, SessionHistoryAdapter.ViewHolder> {

    public interface OnSessionSelectedListener {
        void onSessionSelected(String sessionId);
    }

    public interface OnPartnerClickListener {
        void onPartnerClick(String partnerId);
    }

    public interface OnReviewClickListener {
        void onReviewClick(String sessionId);
    }

    public interface OnReportClickListener {
        void onReportClick(String sessionId, String partnerId, long terminalAtMs);
    }

    private OnSessionSelectedListener sessionSelectedListener;
    private OnPartnerClickListener partnerClickListener;
    private OnReviewClickListener reviewClickListener;
    private OnReportClickListener reportClickListener;
    private String currentUserId = "";

    public SessionHistoryAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnSessionSelectedListener(OnSessionSelectedListener l) {
        this.sessionSelectedListener = l;
    }

    public void setOnPartnerClickListener(OnPartnerClickListener l) {
        this.partnerClickListener = l;
    }

    public void setOnReviewClickListener(OnReviewClickListener l) {
        this.reviewClickListener = l;
    }

    public void setOnReportClickListener(OnReportClickListener l) {
        this.reportClickListener = l;
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId != null ? userId : "";
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SessionSummary summary = getItem(position);
        holder.bind(summary, currentUserId);

        holder.itemView.setOnClickListener(v -> {
            if (sessionSelectedListener != null) {
                sessionSelectedListener.onSessionSelected(summary.getSessionId());
            }
        });

        String partnerId = summary.getPartnerId(currentUserId);
        holder.txtParticipant2Name.setOnClickListener(v -> {
            if (partnerClickListener != null && partnerId != null) {
                partnerClickListener.onPartnerClick(partnerId);
            }
        });
        if (holder.avatarPartner != null) {
            holder.avatarPartner.setOnClickListener(v -> {
                if (partnerClickListener != null && partnerId != null) {
                    partnerClickListener.onPartnerClick(partnerId);
                }
            });
        }

        // ── Button visibility per UX invariant ────────────────────────────────
        WalkSession.Status global = summary.getStatus();

        holder.btnReview.setVisibility(View.GONE);
        holder.btnReport.setVisibility(View.GONE);
        holder.dividerAction.setVisibility(View.GONE);

        if (global == WalkSession.Status.COMPLETED) {
            ParticipantSummary caller = summary.getCallerParticipant(currentUserId);
            ParticipantSummary partner = summary.getPartnerParticipant(currentUserId);

            WalkSession.Status callerStatus = caller != null ? caller.getUserStatus() : null;
            WalkSession.Status partnerStatus = partner != null ? partner.getUserStatus() : null;

            if (callerStatus != WalkSession.Status.NO_SHOW) {
                holder.dividerAction.setVisibility(View.VISIBLE);

                if (partnerStatus == WalkSession.Status.COMPLETED) {
                    holder.btnReview.setVisibility(View.VISIBLE);
                    holder.btnReview.setOnClickListener(v -> {
                        if (reviewClickListener != null) {
                            reviewClickListener.onReviewClick(summary.getSessionId());
                        }
                    });
                }

                holder.btnReport.setVisibility(View.VISIBLE);
                holder.btnReport.setOnClickListener(v -> {
                    if (reportClickListener != null) {
                        reportClickListener.onReportClick(
                                summary.getSessionId(),
                                partnerId,
                                summary.getTerminalAtMs());
                    }
                });
            }
            // callerStatus == NO_SHOW → both buttons stay GONE
        }
        // global ACTIVE / PENDING / CANCELLED → both buttons stay GONE
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {

        final TextView txtDate;
        final TextView txtStatus;
        final TextView txtHotspotName;
        // Participant 2 row = partner (shown at top)
        final TextView txtParticipant2Name;
        final TextView txtParticipant2Status;
        final TextView txtParticipant2Distance;
        final TextView txtParticipant2Duration;
        final AvatarInitialView avatarPartner;
        // Participant 1 row = you (shown at bottom)
        final TextView txtParticipant1Name;
        final TextView txtParticipant1Status;
        final TextView txtParticipant1Distance;
        final TextView txtParticipant1Duration;
        final AvatarInitialView avatarSelf;
        // Actions
        final View dividerAction;
        final MaterialButton btnReview;
        final MaterialButton btnReport;

        ViewHolder(View itemView) {
            super(itemView);
            txtDate = itemView.findViewById(R.id.txtSessionDate);
            txtStatus = itemView.findViewById(R.id.txtSessionStatus);
            txtHotspotName = itemView.findViewById(R.id.txtHotspotName);
            txtParticipant2Name = itemView.findViewById(R.id.txtParticipant2Name);
            txtParticipant2Status = itemView.findViewById(R.id.txtParticipant2Status);
            txtParticipant2Distance = itemView.findViewById(R.id.txtParticipant2Distance);
            txtParticipant2Duration = itemView.findViewById(R.id.txtParticipant2Duration);
            avatarPartner = itemView.findViewById(R.id.avatarPartner);
            txtParticipant1Name = itemView.findViewById(R.id.txtParticipant1Name);
            txtParticipant1Status = itemView.findViewById(R.id.txtParticipant1Status);
            txtParticipant1Distance = itemView.findViewById(R.id.txtParticipant1Distance);
            txtParticipant1Duration = itemView.findViewById(R.id.txtParticipant1Duration);
            avatarSelf = itemView.findViewById(R.id.avatarSelf);
            dividerAction = itemView.findViewById(R.id.dividerAction);
            btnReview = itemView.findViewById(R.id.btnReview);
            btnReport = itemView.findViewById(R.id.btnReport);
        }

        void bind(SessionSummary summary, String currentUserId) {
            txtDate.setText(formatDate(summary.getScheduledStart()));
            applyStatusBadge(txtStatus, summary.getStatus());

            String hotspot = summary.getHotspotName();
            txtHotspotName.setText(hotspot != null && !hotspot.isEmpty() ? hotspot : "—");

            // Partner is always shown in the top row (participant2 views)
            ParticipantSummary partner = summary.getPartnerParticipant(currentUserId);
            if (partner != null) {
                String partnerName = partner.getFullName() != null && !partner.getFullName().isEmpty()
                        ? partner.getFullName()
                        : "Unknown";
                txtParticipant2Name.setText(partnerName);
                txtParticipant2Status.setText(formatUserStatus(partner.getUserStatus()));
                txtParticipant2Distance.setText(formatDistance(partner.getDistanceKm()));
                txtParticipant2Duration.setText(formatDuration(partner.getDurationMinutes()));
                avatarPartner.bind(partnerName, partner.getAvatarUrl());
            }

            // Caller ("You") is always shown in the bottom row (participant1 views)
            ParticipantSummary caller = summary.getCallerParticipant(currentUserId);
            if (caller != null) {
                txtParticipant1Name.setText("You");
                txtParticipant1Status.setText(formatUserStatus(caller.getUserStatus()));
                txtParticipant1Distance.setText(formatDistance(caller.getDistanceKm()));
                txtParticipant1Duration.setText(formatDuration(caller.getDurationMinutes()));
                if (avatarSelf != null) {
                    String callerName = caller.getFullName() != null && !caller.getFullName().isEmpty()
                            ? caller.getFullName()
                            : "Y";
                    avatarSelf.setBackground(
                            ContextCompat.getDrawable(avatarSelf.getContext(), R.drawable.bg_circle_orange));
                    avatarSelf.setInitialTextColor(Color.WHITE);
                    avatarSelf.bind(callerName, caller.getAvatarUrl());
                }
            }
        }

        private static void applyStatusBadge(TextView badge, WalkSession.Status status) {
            String label;
            int bgColor;
            int textColor;
            switch (status != null ? status : WalkSession.Status.PENDING) {
                case ACTIVE:
                    label = "ACTIVE";
                    bgColor = 0xFFFFF7ED;
                    textColor = 0xFFF97316;
                    break;
                case COMPLETED:
                    label = "COMPLETED";
                    bgColor = 0xFFF0FDF4;
                    textColor = 0xFF16A34A;
                    break;
                case CANCELLED:
                    label = "CANCELLED";
                    bgColor = 0xFFFEF2F2;
                    textColor = 0xFFEF4444;
                    break;
                default:
                    label = status != null ? status.name() : "PENDING";
                    bgColor = 0xFFF3F2F0;
                    textColor = 0xFF78716C;
            }
            badge.setText(label);
            badge.setTextColor(textColor);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(100f);
            bg.setColor(bgColor);
            badge.setBackground(bg);
        }

        private static String formatDate(String iso) {
            if (iso == null || iso.length() < 10)
                return "—";
            return iso.substring(0, 10);
        }

        private static String formatUserStatus(WalkSession.Status status) {
            if (status == null)
                return "";
            switch (status) {
                case ACTIVE:
                    return "Walking...";
                case COMPLETED:
                    return "Completed";
                case NO_SHOW:
                    return "No Show";
                case PENDING:
                    return "Waiting...";
                default:
                    return "";
            }
        }

        private static String formatDistance(double km) {
            return String.format(Locale.getDefault(), "%.2f km", km);
        }

        private static String formatDuration(int minutes) {
            if (minutes < 60)
                return minutes + " min";
            return (minutes / 60) + "h " + (minutes % 60) + "m";
        }
    }

    // ── DiffUtil ──────────────────────────────────────────────────────────────

    private static final DiffUtil.ItemCallback<SessionSummary> DIFF_CALLBACK = new DiffUtil.ItemCallback<SessionSummary>() {
        @Override
        public boolean areItemsTheSame(@NonNull SessionSummary a,
                @NonNull SessionSummary b) {
            return a.getSessionId().equals(b.getSessionId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull SessionSummary a,
                @NonNull SessionSummary b) {
            return a.getSessionId().equals(b.getSessionId())
                    && a.getStatus() == b.getStatus();
        }
    };
}
