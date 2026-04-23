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
import com.walkmate.domain.walksession.ParticipantSummary;
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.domain.walksession.WalkSession;

import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for the Session History list.
 *
 * Each card shows the session date, global status, and two participant rows
 * (name + distance + duration). The caller's row is labelled "You".
 */
public class SessionHistoryAdapter
        extends ListAdapter<SessionSummary, SessionHistoryAdapter.ViewHolder> {

    public interface OnSessionSelectedListener {
        void onSessionSelected(String sessionId);
    }

    public interface OnPartnerClickListener {
        void onPartnerClick(String partnerId);
    }

    public interface OnReportClickListener {
        void onReportClick(String sessionId, String partnerId,
                           WalkSession.Status status, long terminalAtMs);
    }

    private OnSessionSelectedListener listener;
    private OnPartnerClickListener partnerClickListener;
    private OnReportClickListener reportClickListener;
    private String currentUserId = "";

    public SessionHistoryAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnSessionSelectedListener(OnSessionSelectedListener listener) {
        this.listener = listener;
    }

    public void setOnPartnerClickListener(OnPartnerClickListener listener) {
        this.partnerClickListener = listener;
    }

    public void setOnReportClickListener(OnReportClickListener listener) {
        this.reportClickListener = listener;
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
            if (listener != null) listener.onSessionSelected(summary.getSessionId());
        });

        // Wire partner name click for profile navigation
        String partnerId = summary.getPartnerId(currentUserId);
        holder.txtParticipant2Name.setOnClickListener(v -> {
            if (partnerClickListener != null && partnerId != null) {
                partnerClickListener.onPartnerClick(partnerId);
            }
        });

        // Report button — visible for reportable terminal statuses only
        WalkSession.Status status = summary.getStatus();
        boolean reportable = status == WalkSession.Status.COMPLETED
                || status == WalkSession.Status.NO_SHOW;
        holder.btnReport.setVisibility(reportable ? View.VISIBLE : View.GONE);
        if (reportable) {
            holder.btnReport.setOnClickListener(v -> {
                if (reportClickListener != null) {
                    reportClickListener.onReportClick(
                            summary.getSessionId(),
                            partnerId,
                            status,
                            summary.getTerminalAtMs());
                }
            });
        }
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {

        final TextView txtDate;
        final TextView txtStatus;
        final TextView txtParticipant1Name;
        final TextView txtParticipant1Distance;
        final TextView txtParticipant1Duration;
        final TextView txtParticipant2Name;
        final TextView txtParticipant2Distance;
        final TextView txtParticipant2Duration;
        final MaterialButton btnReport;

        ViewHolder(View itemView) {
            super(itemView);
            txtDate               = itemView.findViewById(R.id.txtSessionDate);
            txtStatus             = itemView.findViewById(R.id.txtSessionStatus);
            txtParticipant1Name   = itemView.findViewById(R.id.txtParticipant1Name);
            txtParticipant1Distance = itemView.findViewById(R.id.txtParticipant1Distance);
            txtParticipant1Duration = itemView.findViewById(R.id.txtParticipant1Duration);
            txtParticipant2Name   = itemView.findViewById(R.id.txtParticipant2Name);
            txtParticipant2Distance = itemView.findViewById(R.id.txtParticipant2Distance);
            txtParticipant2Duration = itemView.findViewById(R.id.txtParticipant2Duration);
            btnReport             = itemView.findViewById(R.id.btnReport);
        }

        void bind(SessionSummary summary, String currentUserId) {
            txtDate.setText(formatDate(summary.getScheduledStart()));
            txtStatus.setText(formatStatus(summary.getStatus()));

            List<ParticipantSummary> participants = summary.getParticipants();
            if (participants != null && participants.size() >= 2) {
                bindParticipantRow(participants.get(0), currentUserId,
                        txtParticipant1Name, txtParticipant1Distance, txtParticipant1Duration);
                bindParticipantRow(participants.get(1), currentUserId,
                        txtParticipant2Name, txtParticipant2Distance, txtParticipant2Duration);
            } else if (participants != null && participants.size() == 1) {
                bindParticipantRow(participants.get(0), currentUserId,
                        txtParticipant1Name, txtParticipant1Distance, txtParticipant1Duration);
                txtParticipant2Name.setText("—");
                txtParticipant2Distance.setText("");
                txtParticipant2Duration.setText("");
            }
        }

        private void bindParticipantRow(ParticipantSummary p, String currentUserId,
                                         TextView nameView, TextView distView, TextView durView) {
            String displayName = p.getParticipantId().equals(currentUserId)
                    ? "You"
                    : (p.getFullName() != null && !p.getFullName().isEmpty()
                            ? p.getFullName()
                            : "Unknown");
            nameView.setText(displayName);
            distView.setText(String.format(Locale.getDefault(), "%.2f km", p.getDistanceKm()));
            durView.setText(formatDuration(p.getDurationMinutes()));
        }

        private static String formatDate(String iso) {
            if (iso == null || iso.length() < 10) return "—";
            return iso.substring(0, 10);
        }

        private static String formatStatus(WalkSession.Status status) {
            if (status == null) return "Unknown";
            switch (status) {
                case COMPLETED: return "Completed";
                case CANCELLED: return "Cancelled";
                case ABORTED:   return "Aborted";
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
