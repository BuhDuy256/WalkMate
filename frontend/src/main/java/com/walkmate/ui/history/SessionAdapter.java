package com.walkmate.ui.history;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;

import java.util.List;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {

    private List<Session> sessionList;

    public SessionAdapter(List<Session> sessionList) {
        this.sessionList = sessionList;
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        Session session = sessionList.get(position);

        holder.tvDateDay.setText(session.getDay());
        holder.tvDateMonth.setText(session.getMonth());
        holder.ivAvatar.setImageResource(session.getAvatarResId());
        holder.tvName.setText(session.getPartnerName());
        holder.tvStatusBadge.setText(session.getStatus());
        holder.tvLocationAndType.setText("📍 " + session.getLocation() + "    🌿 " + session.getType());
        holder.tvDuration.setText("⏱ " + session.getDuration());
        holder.tvDistance.setText("📍 " + session.getDistance());
        holder.tvSteps.setText("👟 " + session.getSteps());
        
        // Setup Rating Stars
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < session.getRating(); i++) {
            stars.append("★ ");
        }
        for (int i = session.getRating(); i < 5; i++) {
            stars.append("☆ ");
        }
        holder.tvRatingStars.setText(stars.toString().trim());

        // Setup Alert Message
        if (session.getAlertMessage() != null && !session.getAlertMessage().isEmpty()) {
            holder.cvAlertMessage.setVisibility(View.VISIBLE);
            holder.tvAlertMessage.setText(session.getAlertMessage());
        } else {
            holder.cvAlertMessage.setVisibility(View.GONE);
        }

        // Setup colors based on Status
        switch (session.getStatus()) {
            case "Completed":
                holder.viewStatusIndicator.setBackgroundColor(Color.parseColor("#34C759")); // Green
                holder.cvStatusBadge.setCardBackgroundColor(Color.parseColor("#E8F8EE")); // Light green
                holder.tvStatusBadge.setTextColor(Color.parseColor("#34C759"));
                break;
            case "Cancelled":
                holder.viewStatusIndicator.setBackgroundColor(Color.parseColor("#E0E0E0")); // Grey
                holder.cvStatusBadge.setCardBackgroundColor(Color.parseColor("#F5F5F5")); // Light grey
                holder.tvStatusBadge.setTextColor(Color.parseColor("#5C5C5C"));
                break;
            case "No-show":
                holder.viewStatusIndicator.setBackgroundColor(Color.parseColor("#D32F2F")); // Red
                holder.cvStatusBadge.setCardBackgroundColor(Color.parseColor("#FFF0F0")); // Light red
                holder.tvStatusBadge.setTextColor(Color.parseColor("#D32F2F"));
                break;
            default:
                holder.viewStatusIndicator.setBackgroundColor(Color.parseColor("#FF8A4C")); // Orange
                holder.cvStatusBadge.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
                holder.tvStatusBadge.setTextColor(Color.parseColor("#FF8A4C"));
                break;
        }
    }

    @Override
    public int getItemCount() {
        return sessionList.size();
    }

    public static class SessionViewHolder extends RecyclerView.ViewHolder {
        View viewStatusIndicator;
        TextView tvDateDay, tvDateMonth, tvName, tvStatusBadge, tvLocationAndType;
        TextView tvDuration, tvDistance, tvSteps, tvRatingStars, tvAlertMessage;
        ImageView ivAvatar;
        CardView cvStatusBadge, cvAlertMessage;

        public SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            viewStatusIndicator = itemView.findViewById(R.id.viewStatusIndicator);
            tvDateDay = itemView.findViewById(R.id.tvDateDay);
            tvDateMonth = itemView.findViewById(R.id.tvDateMonth);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvName);
            cvStatusBadge = itemView.findViewById(R.id.cvStatusBadge);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            tvLocationAndType = itemView.findViewById(R.id.tvLocationAndType);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            tvDistance = itemView.findViewById(R.id.tvDistance);
            tvSteps = itemView.findViewById(R.id.tvSteps);
            tvRatingStars = itemView.findViewById(R.id.tvRatingStars);
            cvAlertMessage = itemView.findViewById(R.id.cvAlertMessage);
            tvAlertMessage = itemView.findViewById(R.id.tvAlertMessage);
        }
    }
}
