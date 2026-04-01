package com.walkmate.ui.notification;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.domain.notification.Notification;

import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    public interface OnReadListener {
        void onMarkRead(Notification notification);
    }

    private final List<Notification> items = new ArrayList<>();
    private OnReadListener readListener;

    public void setOnReadListener(OnReadListener listener) {
        this.readListener = listener;
    }

    public void submitList(List<Notification> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Notification notification = items.get(position);
        holder.bind(notification, readListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView txtTitle;
        private final TextView txtBody;
        private final View     unreadDot;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTitle  = itemView.findViewById(R.id.txt_notification_title);
            txtBody   = itemView.findViewById(R.id.txt_notification_body);
            unreadDot = itemView.findViewById(R.id.view_unread_dot);
        }

        void bind(Notification notification, OnReadListener listener) {
            txtTitle.setText(labelFor(notification.getType()));
            txtBody.setText(bodyFor(notification));
            unreadDot.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);

            if (!notification.isRead() && listener != null) {
                itemView.setOnClickListener(v -> listener.onMarkRead(notification));
            } else {
                itemView.setOnClickListener(null);
            }
        }

        private String labelFor(Notification.Type type) {
            switch (type) {
                case PROPOSAL_RECEIVED: return "New Walk Proposal";
                case SESSION_CONFIRMED: return "Walk Confirmed!";
                case SESSION_ACTIVE:    return "Your Walk Has Started";
                case REVIEW_REQUESTED:  return "Leave a Review";
                default:                return "Notification";
            }
        }

        private String bodyFor(Notification notification) {
            switch (notification.getType()) {
                case PROPOSAL_RECEIVED: return "You have a new walk match proposal. Tap to review it.";
                case SESSION_CONFIRMED: return "Both of you accepted — your walk session is confirmed.";
                case SESSION_ACTIVE:    return "Both partners have arrived. Your walk is underway!";
                case REVIEW_REQUESTED:  return "Your walk ended. Please rate your experience.";
                default:                return "";
            }
        }
    }
}
