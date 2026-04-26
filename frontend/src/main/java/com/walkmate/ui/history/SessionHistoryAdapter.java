package com.walkmate.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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

import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for the Session History list.
 *
 * Button visibility per UX invariant:
 *   ACTIVE global          → both buttons hidden (silent wait)
 *   COMPLETED global:
 *     caller=COMPLETED + partner=COMPLETED → "Leave a Review"
 *     caller=COMPLETED + partner=NO_SHOW  → "Report"
 *     caller=NO_SHOW (any partner)        → both hidden
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
    private OnPartnerClickListener    partnerClickListener;
    private OnReviewClickListener     reviewClickListener;
    private OnReportClickListener     reportClickListener;
    private String currentUserId = "";

    public SessionHistoryAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnSessionSelectedListener(OnSessionSelectedListener l) { this.sessionSelectedListener = l; }
    public void setOnPartnerClickListener(OnPartnerClickListener l)        { this.partnerClickListener = l; }
    public void setOnReviewClickListener(OnReviewClickListener l)          { this.reviewClickListener = l; }
    public void setOnReportClickListener(OnReportClickListener l)          { this.reportClickListener = l; }

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

        if (global == WalkSession.Status.COMPLETED) {
            ParticipantSummary caller  = summary.getCallerParticipant(currentUserId);
            ParticipantSummary partner = summary.getPartnerParticipant(currentUserId);

            WalkSession.Status callerStatus  = caller  != null ? caller.getUserStatus()  : null;
            WalkSession.Status partnerStatus = partner != null ? partner.getUserStatus() : null;

            if (callerStatus == WalkSession.Status.COMPLETED) {
                if (partnerStatus == WalkSession.Status.COMPLETED) {
                    holder.btnReview.setVisibility(View.VISIBLE);
                    holder.btnReview.setOnClickListener(v -> {
                        if (reviewClickListener != null) {
                            reviewClickListener.onReviewClick(summary.getSessionId());
                        }
                    });
                } else if (partnerStatus == WalkSession.Status.NO_SHOW) {
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
            }
            // callerStatus == NO_SHOW → both buttons stay GONE
        }
        // global ACTIVE / PENDING / CANCELLED → both buttons stay GONE
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {

        final TextView     txtDate;
        final TextView     txtStatus;
        final TextView     txtParticipant1Name;
        final TextView     txtParticipant1Status;
        final TextView     txtParticipant1Distance;
        final TextView     txtParticipant1Duration;
        final TextView        txtParticipant2Name;
        final TextView        txtParticipant2Status;
        final TextView        txtParticipant2Distance;
        final TextView        txtParticipant2Duration;
        final AvatarInitialView avatarPartner;
        final MaterialButton  btnReview;
        final MaterialButton  btnReport;

        ViewHolder(View itemView) {
            super(itemView);
            txtDate                 = itemView.findViewById(R.id.txtSessionDate);
            txtStatus               = itemView.findViewById(R.id.txtSessionStatus);
            txtParticipant1Name     = itemView.findViewById(R.id.txtParticipant1Name);
            txtParticipant1Status   = itemView.findViewById(R.id.txtParticipant1Status);
            txtParticipant1Distance = itemView.findViewById(R.id.txtParticipant1Distance);
            txtParticipant1Duration = itemView.findViewById(R.id.txtParticipant1Duration);
            txtParticipant2Name     = itemView.findViewById(R.id.txtParticipant2Name);
            txtParticipant2Status   = itemView.findViewById(R.id.txtParticipant2Status);
            txtParticipant2Distance = itemView.findViewById(R.id.txtParticipant2Distance);
            txtParticipant2Duration = itemView.findViewById(R.id.txtParticipant2Duration);
            avatarPartner           = itemView.findViewById(R.id.avatarPartner);
            btnReview               = itemView.findViewById(R.id.btnReview);
            btnReport               = itemView.findViewById(R.id.btnReport);
        }

        void bind(SessionSummary summary, String currentUserId) {
            txtDate.setText(formatDate(summary.getScheduledStart()));
            txtStatus.setText(formatStatus(summary.getStatus()));

            List<ParticipantSummary> participants = summary.getParticipants();
            if (participants != null && participants.size() >= 2) {
                bindParticipantRow(participants.get(0), currentUserId,
                        txtParticipant1Name, txtParticipant1Status,
                        txtParticipant1Distance, txtParticipant1Duration);
                bindParticipantRow(participants.get(1), currentUserId,
                        txtParticipant2Name, txtParticipant2Status,
                        txtParticipant2Distance, txtParticipant2Duration);
            } else if (participants != null && participants.size() == 1) {
                bindParticipantRow(participants.get(0), currentUserId,
                        txtParticipant1Name, txtParticipant1Status,
                        txtParticipant1Distance, txtParticipant1Duration);
                txtParticipant2Name.setText("—");
                txtParticipant2Status.setText("");
                txtParticipant2Distance.setText("");
                txtParticipant2Duration.setText("");
            }

            // Bind partner avatar
            if (avatarPartner != null) {
                ParticipantSummary partner = summary.getPartnerParticipant(currentUserId);
                if (partner != null) {
                    String name = partner.getFullName() != null ? partner.getFullName() : "?";
                    avatarPartner.bind(name, partner.getAvatarUrl());
                }
            }
        }

        private void bindParticipantRow(ParticipantSummary p, String currentUserId,
                                         TextView nameView, TextView statusView,
                                         TextView distView, TextView durView) {
            String displayName = p.getParticipantId().equals(currentUserId)
                    ? "You"
                    : (p.getFullName() != null && !p.getFullName().isEmpty()
                            ? p.getFullName()
                            : "Unknown");
            nameView.setText(displayName);
            statusView.setText(formatUserStatus(p.getUserStatus()));
            distView.setText(String.format(Locale.getDefault(), "%.2f km", p.getDistanceKm()));
            durView.setText(formatDuration(p.getDurationMinutes()));
        }

        private static String formatDate(String iso) {
            if (iso == null || iso.length() < 10) return "—";
            return iso.substring(0, 10);
        }

        private static String formatUserStatus(WalkSession.Status status) {
            if (status == null) return "";
            switch (status) {
                case ACTIVE:    return "Walking...";
                case COMPLETED: return "Completed";
                case NO_SHOW:   return "No Show";
                case PENDING:   return "Waiting...";
                default:        return "";
            }
        }

        private static String formatStatus(WalkSession.Status status) {
            if (status == null) return "Unknown";
            switch (status) {
                case COMPLETED: return "Completed";
                case CANCELLED: return "Cancelled";
                case NO_SHOW:   return "No Show";
                case ACTIVE:    return "Active";
                case PENDING:   return "Pending";
                default:        return status.name();
            }
        }

        private static String formatDuration(int minutes) {
            if (minutes < 60) return minutes + " min";
            return (minutes / 60) + "h " + (minutes % 60) + "m";
        }
    }

    // ── DiffUtil ──────────────────────────────────────────────────────────────

    private static final DiffUtil.ItemCallback<SessionSummary> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<SessionSummary>() {
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
