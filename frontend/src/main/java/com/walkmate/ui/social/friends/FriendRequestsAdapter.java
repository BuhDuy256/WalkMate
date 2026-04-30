package com.walkmate.ui.social.friends;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.core.designsystem.view.AvatarInitialView;
import com.walkmate.core.designsystem.view.WalkMateButton;
import com.walkmate.domain.social.FriendRequest;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * RecyclerView adapter shared by IncomingRequestsFragment and OutgoingRequestsFragment.
 *
 * showActions controls which buttons are rendered:
 *   true  → Incoming tab (Accept + Decline visible, sent subtitle hidden)
 *   false → Outgoing/Sent tab (Cancel visible, "Pending · Sent X ago" subtitle shown)
 */
public class FriendRequestsAdapter extends ListAdapter<FriendRequest, FriendRequestsAdapter.ViewHolder> {

    public interface ActionListener {
        void onAccept(String requestId);
        void onDecline(String requestId);
        void onCancel(String requestId);
        void onViewProfile(String userId);
    }

    private final boolean showActions;
    private ActionListener listener;

    public FriendRequestsAdapter(boolean showActions) {
        super(DIFF_CALLBACK);
        this.showActions = showActions;
    }

    public void setActionListener(ActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend_request_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), showActions, listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AvatarInitialView avatarView;
        private final TextView          txtName;
        private final TextView          txtMutualFriends;
        private final TextView          txtSentSubtitle;
        private final WalkMateButton    btnAccept;
        private final WalkMateButton    btnDecline;
        private final WalkMateButton    btnCancel;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarView       = itemView.findViewById(R.id.avatarRequest);
            txtName          = itemView.findViewById(R.id.txtRequestName);
            txtMutualFriends = itemView.findViewById(R.id.txtMutualFriends);
            txtSentSubtitle  = itemView.findViewById(R.id.txtSentSubtitle);
            btnAccept        = itemView.findViewById(R.id.btnRequestAccept);
            btnDecline       = itemView.findViewById(R.id.btnRequestDecline);
            btnCancel        = itemView.findViewById(R.id.btnRequestCancel);
        }

        void bind(FriendRequest request, boolean showActions, ActionListener listener) {
            String name = request.getSenderName();
            avatarView.bind(name, request.getSenderAvatarUrl());
            txtName.setText(name);

            String viewableUserId = showActions ? request.getSenderId() : request.getReceiverId();
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onViewProfile(viewableUserId);
            });

            if (showActions) {
                // Incoming: Accept + Decline; subtitle hidden (mutual friends not yet available)
                btnAccept.setVisibility(View.VISIBLE);
                btnDecline.setVisibility(View.VISIBLE);
                btnCancel.setVisibility(View.GONE);
                txtMutualFriends.setVisibility(View.GONE);
                txtSentSubtitle.setVisibility(View.GONE);

                btnAccept.setOnClickListener(v -> {
                    if (listener != null) listener.onAccept(request.getRequestId());
                });
                btnDecline.setOnClickListener(v -> {
                    if (listener != null) listener.onDecline(request.getRequestId());
                });
            } else {
                // Outgoing/Sent: Cancel; show "Pending · Sent X time ago"
                btnAccept.setVisibility(View.GONE);
                btnDecline.setVisibility(View.GONE);
                btnCancel.setVisibility(View.VISIBLE);
                txtMutualFriends.setVisibility(View.GONE);

                String sentAgo = formatSentAgo(request.getCreatedAt());
                if (sentAgo != null) {
                    txtSentSubtitle.setText("Pending · Sent " + sentAgo);
                    txtSentSubtitle.setVisibility(View.VISIBLE);
                } else {
                    txtSentSubtitle.setText("Pending");
                    txtSentSubtitle.setVisibility(View.VISIBLE);
                }

                btnCancel.setOnClickListener(v -> {
                    if (listener != null) listener.onCancel(request.getRequestId());
                });
            }
        }

        private static String formatSentAgo(String createdAt) {
            if (createdAt == null || createdAt.isEmpty()) return null;
            long millis = parseIsoMillis(createdAt);
            CharSequence span = DateUtils.getRelativeTimeSpanString(
                    millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE);
            return span != null ? span.toString() : null;
        }

        private static long parseIsoMillis(String iso) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            try {
                Date d = sdf.parse(iso);
                if (d != null) return d.getTime();
            } catch (ParseException ignored) {}
            // Fallback: try without literal Z (e.g. with milliseconds)
            try {
                SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                sdf2.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = sdf2.parse(iso);
                if (d != null) return d.getTime();
            } catch (ParseException ignored) {}
            return System.currentTimeMillis();
        }
    }

    private static final DiffUtil.ItemCallback<FriendRequest> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<FriendRequest>() {
                @Override
                public boolean areItemsTheSame(@NonNull FriendRequest a, @NonNull FriendRequest b) {
                    return a.getRequestId().equals(b.getRequestId());
                }
                @Override
                public boolean areContentsTheSame(@NonNull FriendRequest a, @NonNull FriendRequest b) {
                    return a.getStatus().equals(b.getStatus());
                }
            };
}
