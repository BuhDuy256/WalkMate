package com.walkmate.ui.gamification.leaderboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.core.designsystem.view.AvatarInitialView;
import com.walkmate.domain.gamification.LeaderboardEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClicked(String userId);
    }

    private final List<LeaderboardEntry> items = new ArrayList<>();
    @Nullable private String             myUserId;
    @Nullable private OnItemClickListener clickListener;

    public void setMyUserId(@Nullable String myUserId) {
        this.myUserId = myUserId;
    }

    public void setOnItemClickListener(@Nullable OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void submitList(List<LeaderboardEntry> entries) {
        items.clear();
        if (entries != null) items.addAll(entries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), myUserId, clickListener);
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView         txtRank;
        private final AvatarInitialView avatar;
        private final TextView         txtName;
        private final TextView         txtPoints;
        private final TextView         txtDistance;
        private final TextView         txtSessions;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtRank     = itemView.findViewById(R.id.txtRank);
            avatar      = itemView.findViewById(R.id.avatar);
            txtName     = itemView.findViewById(R.id.txtName);
            txtPoints   = itemView.findViewById(R.id.txtPoints);
            txtDistance = itemView.findViewById(R.id.txtDistance);
            txtSessions = itemView.findViewById(R.id.txtSessions);
        }

        void bind(LeaderboardEntry entry, @Nullable String myUserId,
                  @Nullable OnItemClickListener listener) {
            txtRank.setText(String.format(Locale.getDefault(), "#%d", entry.getRank()));

            String displayName = (entry.getFullName() != null && !entry.getFullName().isEmpty())
                    ? entry.getFullName()
                    : entry.getUserId();
            avatar.bind(displayName, null);
            txtName.setText(displayName);

            txtPoints.setText(String.format(Locale.getDefault(), "%d pts",
                    entry.getTotalPoints()));
            txtDistance.setText(String.format(Locale.getDefault(), "%.1f km",
                    entry.getTotalDistanceKm()));
            txtSessions.setText(String.format(Locale.getDefault(), "%d walks",
                    entry.getCompletedSessions()));

            // Highlight the current user's row
            boolean isMe = myUserId != null && myUserId.equals(entry.getUserId());
            itemView.setBackgroundColor(isMe
                    ? ContextCompat.getColor(itemView.getContext(), R.color.bg_warm_light)
                    : ContextCompat.getColor(itemView.getContext(), R.color.bg_white));

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onItemClicked(entry.getUserId());
            });
        }
    }
}
