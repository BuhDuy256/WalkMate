package com.walkmate.ui.matches.session;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.walkmate.R;
import com.walkmate.domain.walksession.WalkSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.ViewHolder> {

    // -------------------------------------------------------------------------
    // Listener interfaces
    // -------------------------------------------------------------------------

    public interface OnChatClickListener {
        void onChatClick(WalkSession session);
    }

    public interface OnCancelClickListener {
        void onCancelClick(WalkSession session);
    }

    // -------------------------------------------------------------------------

    private final List<WalkSession> items = new ArrayList<>();
    private OnChatClickListener chatListener;
    private OnCancelClickListener cancelListener;

    public void setOnChatClickListener(OnChatClickListener listener) {
        this.chatListener = listener;
    }

    public void setOnCancelClickListener(OnCancelClickListener listener) {
        this.cancelListener = listener;
    }

    public void setItems(List<WalkSession> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session_card, parent, false);
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
        private final TextView txtPartnerName;
        private final TextView txtMeetingPoint;
        private final TextView txtMeetingTime;
        private final MaterialButton btnChat;
        private final MaterialButton btnCancelSession;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtAvatarInitial = itemView.findViewById(R.id.txtAvatarInitial);
            txtPartnerName   = itemView.findViewById(R.id.txtPartnerName);
            txtMeetingPoint  = itemView.findViewById(R.id.txtMeetingPoint);
            txtMeetingTime   = itemView.findViewById(R.id.txtMeetingTime);
            btnChat          = itemView.findViewById(R.id.btnChat);
            btnCancelSession = itemView.findViewById(R.id.btnCancelSession);
        }

        void bind(WalkSession session) {
            // Avatar initial
            String name = session.getPartnerName();
            txtAvatarInitial.setText(name != null && !name.isEmpty()
                    ? String.valueOf(name.charAt(0)).toUpperCase(Locale.getDefault())
                    : "?");

            txtPartnerName.setText(name);
            txtMeetingPoint.setText(formatMeetingPoint(
                    session.getMeetingPointLat(), session.getMeetingPointLng()));
            txtMeetingTime.setText("🕐 " + formatScheduledTime(session.getScheduledTime()));

            btnChat.setOnClickListener(v -> {
                if (chatListener != null) chatListener.onChatClick(session);
            });
            btnCancelSession.setOnClickListener(v -> {
                if (cancelListener != null) cancelListener.onCancelClick(session);
            });
        }

        private String formatMeetingPoint(double lat, double lng) {
            return String.format(Locale.getDefault(), "📍 %.4f°N, %.4f°E", lat, lng);
        }

        /**
         * Extracts "HH:MM" from an ISO-8601 string such as "2026-03-29T14:00:00Z".
         */
        private String formatScheduledTime(String isoTime) {
            if (isoTime == null || isoTime.isEmpty()) return "";
            int tIndex = isoTime.indexOf('T');
            if (tIndex < 0 || tIndex + 5 > isoTime.length()) return isoTime;
            return isoTime.substring(tIndex + 1, tIndex + 6); // "HH:MM"
        }
    }
}
