package com.walkmate.ui.explore.createintent;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.core.designsystem.view.AvatarInitialView;
import com.walkmate.domain.social.UserSummary;

import java.util.List;

/** Adapter for the friend-picker bottom sheet used in the Create Intent flow. */
class FriendPickerAdapter extends RecyclerView.Adapter<FriendPickerAdapter.VH> {

    interface OnFriendActionListener {
        void onInvite(String userId, String fullName);
        void onViewProfile(String userId);
    }

    private final List<UserSummary> items;
    private final OnFriendActionListener listener;

    FriendPickerAdapter(List<UserSummary> items, OnFriendActionListener listener) {
        this.items    = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_friend_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        UserSummary friend = items.get(position);
        holder.avatar.bind(friend.getFullName(), friend.getAvatarUrl());
        holder.name.setText(friend.getFullName());

        // "Remove" is not applicable in the picker context.
        holder.btnRemove.setVisibility(View.GONE);

        holder.btnInvite.setOnClickListener(v ->
                listener.onInvite(friend.getUserId(), friend.getFullName()));
        holder.btnViewProfile.setOnClickListener(v ->
                listener.onViewProfile(friend.getUserId()));

        // Tapping anywhere else on the card also invites the friend.
        holder.itemView.setOnClickListener(v ->
                listener.onInvite(friend.getUserId(), friend.getFullName()));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final AvatarInitialView avatar;
        final TextView          name;
        final View              btnInvite;
        final View              btnViewProfile;
        final View              btnRemove;

        VH(@NonNull View v) {
            super(v);
            avatar         = v.findViewById(R.id.avatarFriend);
            name           = v.findViewById(R.id.txtFriendName);
            btnInvite      = v.findViewById(R.id.btnFriendInviteWalk);
            btnViewProfile = v.findViewById(R.id.btnFriendViewProfile);
            btnRemove      = v.findViewById(R.id.btnFriendRemove);
        }
    }
}
