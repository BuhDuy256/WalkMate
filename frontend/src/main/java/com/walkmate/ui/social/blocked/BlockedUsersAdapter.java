package com.walkmate.ui.social.blocked;

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
 * RecyclerView adapter for the Blocked Users screen.
 * Each row shows avatar, name, and an "Unblock" button.
 */
public class BlockedUsersAdapter extends ListAdapter<UserSummary, BlockedUsersAdapter.ViewHolder> {

    public interface ActionListener {
        void onUnblock(String userId, String displayName);
    }

    private ActionListener listener;

    public BlockedUsersAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setActionListener(ActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_blocked_user_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AvatarInitialView avatarView;
        private final TextView          txtName;
        private final WalkMateButton    btnUnblock;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarView = itemView.findViewById(R.id.avatarBlocked);
            txtName    = itemView.findViewById(R.id.txtBlockedName);
            btnUnblock = itemView.findViewById(R.id.btnUnblock);
        }

        void bind(UserSummary user, ActionListener listener) {
            avatarView.bind(user.getFullName(), user.getAvatarUrl());
            txtName.setText(user.getFullName());
            btnUnblock.setOnClickListener(v -> {
                if (listener != null) listener.onUnblock(user.getUserId(), user.getFullName());
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
                    return a.getFullName().equals(b.getFullName());
                }
            };
}
