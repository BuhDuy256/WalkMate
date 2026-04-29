package com.walkmate.ui.home;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecentMatesHomeAdapter
        extends ListAdapter<HomeDashboardUiState.RecentMateSnapshot, RecentMatesHomeAdapter.ViewHolder> {

    // Deterministic avatar colors cycling through a pleasing palette
    private static final int[] AVATAR_COLORS = {
        0xFF7C3AED, // purple
        0xFF0EA5E9, // sky blue
        0xFF10B981, // emerald
        0xFFF97316, // orange
        0xFFE11D48, // rose
        0xFF8B5CF6, // violet
    };

    // Per-position resolved location names (populated async by HomeFragment)
    private final List<String> locationNames = new ArrayList<>();

    public RecentMatesHomeAdapter() {
        super(DIFF_CALLBACK);
    }

    @Override
    public void submitList(List<HomeDashboardUiState.RecentMateSnapshot> list) {
        locationNames.clear();
        if (list != null) {
            for (int i = 0; i < list.size(); i++) locationNames.add(null);
        }
        super.submitList(list);
    }

    /** Called by HomeFragment after async geocoding resolves a position's location. */
    public void updateLocationAtPosition(int position, String locationName) {
        if (position < 0 || position >= locationNames.size()) return;
        locationNames.set(position, locationName);
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_mate_home, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HomeDashboardUiState.RecentMateSnapshot mate = getItem(position);
        String resolvedLocation = position < locationNames.size() ? locationNames.get(position) : null;
        holder.bind(mate, resolvedLocation);
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {

        final FrameLayout  avatarContainer;
        final TextView     txtInitials;
        final TextView     txtMateName;
        final LinearLayout rowMateLocation;
        final TextView     txtMateLocation;
        final TextView     txtMateTime;

        ViewHolder(View itemView) {
            super(itemView);
            avatarContainer = itemView.findViewById(R.id.avatarMate);
            txtInitials     = itemView.findViewById(R.id.txtInitials);
            txtMateName     = itemView.findViewById(R.id.txtMateName);
            rowMateLocation = itemView.findViewById(R.id.rowMateLocation);
            txtMateLocation = itemView.findViewById(R.id.txtMateLocation);
            txtMateTime     = itemView.findViewById(R.id.txtMateTime);
        }

        void bind(HomeDashboardUiState.RecentMateSnapshot mate, String locationName) {
            String name = mate.partnerName != null ? mate.partnerName : "?";
            txtMateName.setText(name);
            txtInitials.setText(initials(name));
            txtMateTime.setText(relativeTime(mate.scheduledAtMs));

            int color = avatarColor(name);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            float radius = itemView.getResources().getDisplayMetrics().density * 12;
            bg.setCornerRadius(radius);
            bg.setColor(color);
            avatarContainer.setBackground(bg);

            if (locationName != null && !locationName.isEmpty()) {
                txtMateLocation.setText(locationName);
                rowMateLocation.setVisibility(View.VISIBLE);
            } else {
                rowMateLocation.setVisibility(View.GONE);
            }
        }

        private static String initials(String fullName) {
            if (fullName == null || fullName.trim().isEmpty()) return "?";
            String[] parts = fullName.trim().split("\\s+");
            if (parts.length >= 2) {
                return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0))
                        .toUpperCase(Locale.ROOT);
            }
            return parts[0].substring(0, Math.min(2, parts[0].length()))
                    .toUpperCase(Locale.ROOT);
        }

        private static int avatarColor(String name) {
            if (name == null || name.isEmpty()) return AVATAR_COLORS[0];
            return AVATAR_COLORS[Math.abs(name.hashCode()) % AVATAR_COLORS.length];
        }

        private static String relativeTime(long atMs) {
            if (atMs == 0) return "";
            long diff = System.currentTimeMillis() - atMs;
            long days = diff / (1000L * 60 * 60 * 24);
            if (days <= 0) return "Today";
            if (days == 1) return "Yesterday";
            return days + " days ago";
        }
    }

    // ── DiffUtil ──────────────────────────────────────────────────────────────

    private static final DiffUtil.ItemCallback<HomeDashboardUiState.RecentMateSnapshot> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<HomeDashboardUiState.RecentMateSnapshot>() {
                @Override
                public boolean areItemsTheSame(@NonNull HomeDashboardUiState.RecentMateSnapshot a,
                                               @NonNull HomeDashboardUiState.RecentMateSnapshot b) {
                    return a.partnerName != null && a.partnerName.equals(b.partnerName)
                            && a.scheduledAtMs == b.scheduledAtMs;
                }
                @Override
                public boolean areContentsTheSame(@NonNull HomeDashboardUiState.RecentMateSnapshot a,
                                                  @NonNull HomeDashboardUiState.RecentMateSnapshot b) {
                    return areItemsTheSame(a, b);
                }
            };
}
