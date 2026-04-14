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

            // Always fire the listener so tapping any notification (read or unread)
            // triggers navigation. The fragment decides whether to call markRead.
            if (listener != null) {
                itemView.setOnClickListener(v -> listener.onMarkRead(notification));
            } else {
                itemView.setOnClickListener(null);
            }
        }

        private String labelFor(Notification.Type type) {
            switch (type) {
                case PROPOSAL_RECEIVED:        return "New Walk Proposal";
                case INVITE_SENT:              return "Private Invite Sent";
                case PROPOSAL_ACCEPTED:        return "Proposal Accepted";
                case SESSION_CONFIRMED:        return "Walk Confirmed!";
                case SESSION_ACTIVE:           return "Your Walk Has Started";
                case REVIEW_REQUESTED:         return "Leave a Review";
                case FRIEND_REQUEST_RECEIVED:  return "New Friend Request";
                case FRIEND_REQUEST_ACCEPTED:  return "Friend Request Accepted";
                case FRIEND_REQUEST_DECLINED:  return "Friend Request Declined";
                default:                       return "Notification";
            }
        }

        private String bodyFor(Notification notification) {
            switch (notification.getType()) {
                case PROPOSAL_RECEIVED:       return "You have a new walk match proposal. Tap to review it.";
                case INVITE_SENT:             return "Your private invite was sent. Tap to track it.";
                case PROPOSAL_ACCEPTED:       return "Your partner accepted the proposal!";
                case SESSION_CONFIRMED:       return "Both of you accepted — your walk session is confirmed.";
                case SESSION_ACTIVE:          return "Both partners have arrived. Your walk is underway!";
                case REVIEW_REQUESTED:        return "Your walk ended. Please rate your experience.";
                case FRIEND_REQUEST_RECEIVED: return "Someone sent you a friend request. Tap to review it.";
                case FRIEND_REQUEST_ACCEPTED: return "Your friend request was accepted!";
                case FRIEND_REQUEST_DECLINED: return "Your friend request was declined.";
                default:                      return "";
            }
        }
    }
}
