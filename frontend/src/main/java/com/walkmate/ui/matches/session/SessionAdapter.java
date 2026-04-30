package com.walkmate.ui.matches.session;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.walkmate.core.designsystem.view.ActivationWindowButtonView;
import com.walkmate.core.designsystem.view.AvatarInitialView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.walkmate.R;
import com.walkmate.domain.walksession.WalkSession;

import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    public interface OnVerifyQrClickListener {
        void onVerifyQrClick(WalkSession session);
    }

    public interface SessionActionListener {
        void onArriveClicked(String sessionId);
        void onCompleteClicked(WalkSession session);
        void onReportClicked(String sessionId, String partnerId);
    }

    // -------------------------------------------------------------------------

    private final List<WalkSession> items = new ArrayList<>();
    private OnChatClickListener     chatListener;
    private OnCancelClickListener   cancelListener;
    private SessionActionListener   sessionActionListener;
    private OnVerifyQrClickListener verifyQrListener;

    public void setOnChatClickListener(OnChatClickListener listener) {
        this.chatListener = listener;
    }

    public void setOnCancelClickListener(OnCancelClickListener listener) {
        this.cancelListener = listener;
    }

    public void setSessionActionListener(SessionActionListener listener) {
        this.sessionActionListener = listener;
    }

    public void setOnVerifyQrClickListener(OnVerifyQrClickListener listener) {
        this.verifyQrListener = listener;
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
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // -------------------------------------------------------------------------

    class ViewHolder extends RecyclerView.ViewHolder {

        final ActivationWindowButtonView activationBtn;
        private final AvatarInitialView avatarPartner;
        private final TextView txtPartnerName;
        private final TextView txtMeetingPoint;
        private final TextView txtStatusBadge;
        private final TextView txtSessionLocation;
        private final TextView txtMeetingTime;
        private final MaterialButton btnChat;
        private final MaterialButton btnCancelSession;
        final MaterialButton btnComplete;
        private final MaterialButton btnReportIssue;
        private final View           lblStartYourWalk;
        private final LinearLayout   bannerVerifyPartner;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            activationBtn       = itemView.findViewById(R.id.activationBtn);
            avatarPartner       = itemView.findViewById(R.id.avatarPartner);
            txtPartnerName      = itemView.findViewById(R.id.txtPartnerName);
            txtMeetingPoint     = itemView.findViewById(R.id.txtMeetingPoint);
            txtStatusBadge      = itemView.findViewById(R.id.txtStatusBadge);
            txtSessionLocation  = itemView.findViewById(R.id.txtSessionLocation);
            txtMeetingTime      = itemView.findViewById(R.id.txtMeetingTime);
            btnChat             = itemView.findViewById(R.id.btnChat);
            btnCancelSession    = itemView.findViewById(R.id.btnCancelSession);
            btnComplete         = itemView.findViewById(R.id.btnComplete);
            btnReportIssue      = itemView.findViewById(R.id.btnReportIssue);
            lblStartYourWalk    = itemView.findViewById(R.id.lblStartYourWalk);
            bannerVerifyPartner = itemView.findViewById(R.id.bannerVerifyPartner);
        }

        void bind(WalkSession session, SessionActionListener listener) {
            WalkSession.Status callerStatus = session.getCallerStatus();

            // Partner identity
            String partnerName = session.getPartnerName();
            String displayName = (partnerName != null && !partnerName.isEmpty())
                    ? partnerName : session.getPartnerId();
            avatarPartner.bind(displayName, session.getPartnerAvatar());
            txtPartnerName.setText(displayName);
            String hotspotDisplay = session.getHotspotName();
            txtMeetingPoint.setText(hotspotDisplay != null && !hotspotDisplay.isEmpty()
                    ? hotspotDisplay
                    : formatCoords(session.getMeetingPointLat(), session.getMeetingPointLng()));

            // Status badge text
            if (callerStatus == WalkSession.Status.ACTIVE) {
                txtStatusBadge.setText("Walk Active");
            } else {
                txtStatusBadge.setText("Matched ✓");
            }

            // Location + time strip
            String hotspot = session.getHotspotName();
            txtSessionLocation.setText(hotspot != null && !hotspot.isEmpty()
                    ? hotspot
                    : formatCoords(session.getMeetingPointLat(), session.getMeetingPointLng()));
            String start = formatIsoTime(session.getScheduledTime());
            String end   = formatIsoTime(session.getScheduledEnd());
            txtMeetingTime.setText(end.isEmpty() ? start : start + " – " + end);

            btnChat.setOnClickListener(v -> {
                if (chatListener != null) chatListener.onChatClick(session);
            });
            btnCancelSession.setOnClickListener(v -> {
                if (cancelListener != null) cancelListener.onCancelClick(session);
            });

            // Primary actions driven by caller status
            if (callerStatus == WalkSession.Status.PENDING) {
                lblStartYourWalk.setVisibility(View.VISIBLE);
                bannerVerifyPartner.setVisibility(View.VISIBLE);
                bannerVerifyPartner.setOnClickListener(v -> {
                    if (verifyQrListener != null) verifyQrListener.onVerifyQrClick(session);
                });
                activationBtn.setVisibility(View.VISIBLE);
                activationBtn.bind(session.getScheduledTime(),
                        v -> { if (listener != null) listener.onArriveClicked(session.getSessionId()); });
                btnComplete.setVisibility(View.GONE);
                btnReportIssue.setVisibility(View.GONE);
            } else if (callerStatus == WalkSession.Status.ACTIVE) {
                txtPartnerName.setText("Walking with " + displayName);
                txtStatusBadge.setText("● LIVE");

                lblStartYourWalk.setVisibility(View.GONE);
                bannerVerifyPartner.setVisibility(View.VISIBLE);
                bannerVerifyPartner.setOnClickListener(v -> {
                    if (verifyQrListener != null) verifyQrListener.onVerifyQrClick(session);
                });
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
                lblStartYourWalk.setVisibility(View.GONE);
                bannerVerifyPartner.setVisibility(View.GONE);
                activationBtn.setVisibility(View.GONE);
                btnComplete.setVisibility(View.GONE);
                btnReportIssue.setVisibility(View.GONE);
            }
        }

        private String formatCoords(double lat, double lng) {
            if (lat == 0 && lng == 0) return "—";
            return String.format(Locale.getDefault(), "%.2f°N, %.2f°E", lat, lng);
        }

        private String formatIsoTime(String isoTime) {
            if (isoTime == null || isoTime.isEmpty()) return "";
            try {
                ZonedDateTime local = ZonedDateTime.parse(isoTime)
                        .withZoneSameInstant(ZoneId.systemDefault());
                return String.format(Locale.getDefault(), "%02d:%02d",
                        local.getHour(), local.getMinute());
            } catch (Exception e) {
                return "";
            }
        }
    }
}
