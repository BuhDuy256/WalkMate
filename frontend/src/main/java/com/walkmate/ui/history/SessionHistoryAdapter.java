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
import com.walkmate.domain.walksession.SessionSummary;
import com.walkmate.domain.walksession.WalkSession;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * RecyclerView adapter for the Session History list.
 *
 * Each item shows: formatted date, partner placeholder, status badge,
 * distance, duration. Tap → {@link OnSessionSelectedListener#onSessionSelected(String)}.
 */
public class SessionHistoryAdapter
        extends ListAdapter<SessionSummary, SessionHistoryAdapter.ViewHolder> {

    public interface OnSessionSelectedListener {
        void onSessionSelected(String sessionId);
    }

    /** Callback invoked when the partner name is tapped on a session history row. */
    public interface OnPartnerClickListener {
        void onPartnerClick(String partnerId);
    }

    /** Callback for "Report" action on COMPLETED/NO_SHOW history items (GAP-15). */
    public interface OnReportClickListener {
        void onReportClick(String sessionId, String partnerId,
                           WalkSession.Status status, long terminalAtMs);
    }

    private OnSessionSelectedListener listener;
    private OnPartnerClickListener partnerClickListener;
    private OnReportClickListener reportClickListener;
    private Map<String, String> partnerNames = Collections.emptyMap();

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

    /**
     * Updates the partnerId → display name map and redraws visible items.
     * Called by the Fragment each time the ViewModel posts an enriched state.
     */
    public void setPartnerNames(Map<String, String> names) {
        this.partnerNames = names != null ? names : Collections.emptyMap();
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
        holder.bind(summary, partnerNames);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSessionSelected(summary.getSessionId());
        });
        holder.txtPartner.setOnClickListener(v -> {
            if (partnerClickListener != null && summary.getPartnerId() != null) {
                partnerClickListener.onPartnerClick(summary.getPartnerId());
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
                            summary.getPartnerId(),
                            status,
                            summary.getTerminalAtMs());
                }
            });
        }
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {

        final TextView txtDate;
        final TextView txtPartner;
        private final TextView txtStatus;
        private final TextView txtDistance;
        private final TextView txtDuration;
        final MaterialButton btnReport;

        ViewHolder(View itemView) {
            super(itemView);
            txtDate     = itemView.findViewById(R.id.txtSessionDate);
            txtPartner  = itemView.findViewById(R.id.txtSessionPartner);
            txtStatus   = itemView.findViewById(R.id.txtSessionStatus);
            txtDistance = itemView.findViewById(R.id.txtSessionDistance);
            txtDuration = itemView.findViewById(R.id.txtSessionDuration);
            btnReport   = itemView.findViewById(R.id.btnReport);
        }

        void bind(SessionSummary summary, Map<String, String> partnerNames) {
            txtDate.setText(formatDate(summary.getScheduledStart()));
            String partnerId = summary.getPartnerId();
            String displayName = (partnerId != null && partnerNames.containsKey(partnerId))
                    ? partnerNames.get(partnerId)
                    : (partnerId != null ? "Partner: " + partnerId : "Unknown partner");
            txtPartner.setText(displayName);
            txtStatus.setText(formatStatus(summary.getStatus()));
            txtDistance.setText(String.format(Locale.getDefault(),
                    "%.2f km", summary.getTotalDistanceKm()));
            txtDuration.setText(formatDuration(summary.getDurationMinutes()));
        }

        private static String formatDate(String iso) {
            if (iso == null || iso.length() < 10) return "—";
            return iso.substring(0, 10); // "YYYY-MM-DD"
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
