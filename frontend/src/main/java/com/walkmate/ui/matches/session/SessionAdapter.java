package com.walkmate.ui.matches.session;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.walkmate.core.designsystem.view.ActivationWindowButtonView;
import com.walkmate.core.designsystem.view.AvatarInitialView;
import com.walkmate.core.designsystem.view.MatchCardHeaderView;

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

    public interface SessionActionListener {
        void onArriveClicked(String sessionId);
        void onCompleteClicked(WalkSession session);
        void onReportClicked(String sessionId, String partnerId);
    }

    // -------------------------------------------------------------------------

    private final List<WalkSession> items = new ArrayList<>();
    private OnChatClickListener chatListener;
    private OnCancelClickListener cancelListener;
    private SessionActionListener sessionActionListener;

    public void setOnChatClickListener(OnChatClickListener listener) {
        this.chatListener = listener;
    }

    public void setOnCancelClickListener(OnCancelClickListener listener) {
        this.cancelListener = listener;
    }

    public void setSessionActionListener(SessionActionListener listener) {
        this.sessionActionListener = listener;
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
        holder.bind(items.get(position), sessionActionListener);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.activationBtn.release();
        holder.cardHeader.cancelCountdown();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // -------------------------------------------------------------------------

    class ViewHolder extends RecyclerView.ViewHolder {

        final MatchCardHeaderView cardHeader;
        final ActivationWindowButtonView activationBtn;
        private final AvatarInitialView avatarPartner;
        private final TextView txtPartnerName;
        private final TextView txtMeetingPoint;
        private final TextView txtMeetingTime;
        private final MaterialButton btnChat;
        private final MaterialButton btnCancelSession;
        final MaterialButton btnComplete;
        private final MaterialButton btnReportIssue;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardHeader       = itemView.findViewById(R.id.cardHeader);
            activationBtn    = itemView.findViewById(R.id.activationBtn);
            avatarPartner    = itemView.findViewById(R.id.avatarPartner);
            txtPartnerName   = itemView.findViewById(R.id.txtPartnerName);
            txtMeetingPoint  = itemView.findViewById(R.id.txtMeetingPoint);
            txtMeetingTime   = itemView.findViewById(R.id.txtMeetingTime);
            btnChat          = itemView.findViewById(R.id.btnChat);
            btnCancelSession = itemView.findViewById(R.id.btnCancelSession);
            btnComplete      = itemView.findViewById(R.id.btnComplete);
            btnReportIssue   = itemView.findViewById(R.id.btnReportIssue);
        }

        void bind(WalkSession session, SessionActionListener listener) {
            // Zone 1: header — status badge, no countdown for sessions
            if (session.getStatus() == WalkSession.Status.ACTIVE) {
                cardHeader.setStatus("Walk Active", MatchCardHeaderView.STYLE_SESSION_ACTIVE);
            } else {
                cardHeader.setStatus("Ready to Walk", MatchCardHeaderView.STYLE_SESSION_PENDING);
            }
            cardHeader.hideCountdown();

            // Zone 2: partner identity — fallback to "Walking Partner" until API returns name
            String partnerName = session.getPartnerName();
            String displayName = (partnerName != null && !partnerName.isEmpty())
                    ? partnerName : "Walking Partner";
            avatarPartner.bind(displayName, null);
            txtPartnerName.setText(displayName);
            txtMeetingPoint.setText(formatMeetingPoint(
                    session.getMeetingPointLat(), session.getMeetingPointLng()));

            // Zone 3: meeting time
            txtMeetingTime.setText("🕐 " + formatScheduledTime(session.getScheduledTime()));

            btnChat.setOnClickListener(v -> {
                if (chatListener != null) chatListener.onChatClick(session);
            });
            btnCancelSession.setOnClickListener(v -> {
                if (cancelListener != null) cancelListener.onCancelClick(session);
            });

            // Zone 5: actions vary by session status
            if (session.getStatus() == WalkSession.Status.PENDING) {
                activationBtn.setVisibility(View.VISIBLE);
                activationBtn.bind(session.getScheduledTime(),
                        v -> { if (listener != null) listener.onArriveClicked(session.getSessionId()); });
                btnComplete.setVisibility(View.GONE);
                btnReportIssue.setVisibility(View.GONE);
            } else if (session.getStatus() == WalkSession.Status.ACTIVE) {
                activationBtn.setVisibility(View.GONE);
                btnComplete.setVisibility(View.VISIBLE);
                btnComplete.setEnabled(true);
                btnComplete.setText(R.string.btn_resume_walk);
                btnComplete.setOnClickListener(v -> {
                    if (listener != null) listener.onCompleteClicked(session);
                });
                btnReportIssue.setVisibility(View.VISIBLE);
                btnReportIssue.setOnClickListener(v -> {
                    if (listener != null)
                        listener.onReportClicked(session.getSessionId(), session.getPartnerId());
                });
            } else {
                activationBtn.setVisibility(View.GONE);
                btnComplete.setVisibility(View.GONE);
                btnReportIssue.setVisibility(View.GONE);
            }
        }

        private String formatMeetingPoint(double lat, double lng) {
            return String.format(Locale.getDefault(), "📍 %.2f°N, %.2f°E", lat, lng);
        }

        private String formatScheduledTime(String isoTime) {
            if (isoTime == null || isoTime.isEmpty()) return "";
            int tIndex = isoTime.indexOf('T');
            if (tIndex < 0 || tIndex + 5 > isoTime.length()) return isoTime;
            return isoTime.substring(tIndex + 1, tIndex + 6);
        }
    }
}
