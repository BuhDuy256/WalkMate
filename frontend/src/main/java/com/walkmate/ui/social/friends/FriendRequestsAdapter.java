package com.walkmate.ui.social.friends;

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

/**
 * RecyclerView adapter shared by IncomingRequestsFragment and OutgoingRequestsFragment.
 *
 * showActions controls whether Accept/Decline buttons are rendered:
 *   true  → Incoming tab (Accept + Decline visible)
 *   false → Outgoing tab (status label visible, no action buttons)
 */
public class FriendRequestsAdapter extends ListAdapter<FriendRequest, FriendRequestsAdapter.ViewHolder> {

    public interface ActionListener {
        void onAccept(String requestId);
        void onDecline(String requestId);
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
        private final WalkMateButton    btnAccept;
        private final WalkMateButton    btnDecline;
        private final TextView          txtPending;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarView  = itemView.findViewById(R.id.avatarRequest);
            txtName     = itemView.findViewById(R.id.txtRequestName);
            btnAccept   = itemView.findViewById(R.id.btnRequestAccept);
            btnDecline  = itemView.findViewById(R.id.btnRequestDecline);
            txtPending  = itemView.findViewById(R.id.txtRequestPending);
        }

        void bind(FriendRequest request, boolean showActions, ActionListener listener) {
            // For incoming: show sender info. For outgoing: show receiver info via senderName
            // (the adapter is generic; callers set the appropriate name on the request model).
            String name = request.getSenderName();
            avatarView.bind(name, request.getSenderAvatarUrl());
            txtName.setText(name);

            if (showActions) {
                btnAccept.setVisibility(View.VISIBLE);
                btnDecline.setVisibility(View.VISIBLE);
                txtPending.setVisibility(View.GONE);

                btnAccept.setOnClickListener(v -> {
                    if (listener != null) listener.onAccept(request.getRequestId());
                });
                btnDecline.setOnClickListener(v -> {
                    if (listener != null) listener.onDecline(request.getRequestId());
                });
            } else {
                btnAccept.setVisibility(View.GONE);
                btnDecline.setVisibility(View.GONE);
                txtPending.setVisibility(View.VISIBLE);
            }
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
