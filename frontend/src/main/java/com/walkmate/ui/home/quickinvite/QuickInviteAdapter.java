package com.walkmate.ui.home.quickinvite;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.core.util.GlideHelper;
import com.walkmate.R;
import com.walkmate.ui.home.HomeDashboardUiState.QuickInviteUser;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for the horizontal "Rủ rê nhanh" invite list on the
 * Home Dashboard.
 *
 * Each item shows a circular avatar (loaded via Glide) and the user's name.
 * The Fragment calls submitList() inside renderState() whenever the LiveData
 * delivers a new HomeDashboardUiState.
 */
public class QuickInviteAdapter extends RecyclerView.Adapter<QuickInviteAdapter.ViewHolder> {

    private List<QuickInviteUser> items = new ArrayList<>();

    /** Called by HomeFragment.renderState() to refresh the list. */
    public void submitList(List<QuickInviteUser> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_quick_invite, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final ImageView imgAvatar;
        private final TextView txtName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.imgInviteAvatar);
            txtName   = itemView.findViewById(R.id.txtInviteName);
        }

        void bind(QuickInviteUser user) {
            txtName.setText(user.displayName);

            GlideHelper.loadCircle(imgAvatar, user.avatarUrl);
        }
    }
}
