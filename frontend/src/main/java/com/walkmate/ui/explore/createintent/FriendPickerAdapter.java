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

/** Simple adapter for the friend-picker bottom sheet. */
class FriendPickerAdapter extends RecyclerView.Adapter<FriendPickerAdapter.VH> {

    interface OnFriendClickListener {
        void onClick(String userId, String fullName);
    }

    private final List<UserSummary> items;
    private final OnFriendClickListener listener;

    FriendPickerAdapter(List<UserSummary> items, OnFriendClickListener listener) {
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
        holder.itemView.setOnClickListener(v ->
                listener.onClick(friend.getUserId(), friend.getFullName()));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final AvatarInitialView avatar;
        final TextView name;

        VH(@NonNull View v) {
            super(v);
            avatar = v.findViewById(R.id.avatarFriend);
            name   = v.findViewById(R.id.txtFriendName);
        }
    }
}
