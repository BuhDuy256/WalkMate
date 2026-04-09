package com.walkmate.ui.history;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

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

    private OnSessionSelectedListener listener;
    private Map<String, String> partnerNames = Collections.emptyMap();

    public SessionHistoryAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnSessionSelectedListener(OnSessionSelectedListener listener) {
        this.listener = listener;
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
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView txtDate;
        private final TextView txtPartner;
        private final TextView txtStatus;
        private final TextView txtDistance;
        private final TextView txtDuration;

        ViewHolder(View itemView) {
            super(itemView);
            txtDate     = itemView.findViewById(R.id.txtSessionDate);
            txtPartner  = itemView.findViewById(R.id.txtSessionPartner);
            txtStatus   = itemView.findViewById(R.id.txtSessionStatus);
            txtDistance = itemView.findViewById(R.id.txtSessionDistance);
            txtDuration = itemView.findViewById(R.id.txtSessionDuration);
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
