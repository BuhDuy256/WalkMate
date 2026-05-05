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
import com.walkmate.domain.social.UserSummary;

/**
 * RecyclerView adapter for the Friends tab.
 * Each card shows avatar, name, and three action buttons.
 */
public class FriendsAdapter extends ListAdapter<UserSummary, FriendsAdapter.ViewHolder> {

    public interface ActionListener {
        void onInviteWalk(String userId, String fullName);
        void onViewProfile(String userId);
        void onRemoveFriend(String userId, String displayName);
    }

    private ActionListener listener;

    public FriendsAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setActionListener(ActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AvatarInitialView avatarView;
        private final TextView          txtName;
        private final TextView          txtTrustScore;
        private final WalkMateButton    btnInviteWalk;
        private final WalkMateButton    btnViewProfile;
        private final WalkMateButton    btnRemoveFriend;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarView      = itemView.findViewById(R.id.avatarFriend);
            txtName         = itemView.findViewById(R.id.txtFriendName);
            txtTrustScore   = itemView.findViewById(R.id.txtFriendTrustScore);
            btnInviteWalk   = itemView.findViewById(R.id.btnFriendInviteWalk);
            btnViewProfile  = itemView.findViewById(R.id.btnFriendViewProfile);
            btnRemoveFriend = itemView.findViewById(R.id.btnFriendRemove);
        }

        void bind(UserSummary user, ActionListener listener) {
            avatarView.bind(user.getFullName(), user.getAvatarUrl());
            txtName.setText(user.getFullName());
            txtTrustScore.setText("Trust: " + user.getTrustScore());

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onViewProfile(user.getUserId());
            });
            btnInviteWalk.setOnClickListener(v -> {
                if (listener != null) listener.onInviteWalk(user.getUserId(), user.getFullName());
            });
            btnViewProfile.setOnClickListener(v -> {
                if (listener != null) listener.onViewProfile(user.getUserId());
            });
            btnRemoveFriend.setOnClickListener(v -> {
                if (listener != null) listener.onRemoveFriend(user.getUserId(), user.getFullName());
            });
        }
    }

    private static final DiffUtil.ItemCallback<UserSummary> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<UserSummary>() {
                @Override
                public boolean areItemsTheSame(@NonNull UserSummary a, @NonNull UserSummary b) {
                    return a.getUserId().equals(b.getUserId());
                }
                @Override
                public boolean areContentsTheSame(@NonNull UserSummary a, @NonNull UserSummary b) {
                    return a.getFullName().equals(b.getFullName())
                            && a.getFriendshipStatus().equals(b.getFriendshipStatus());
                }
            };
}
