package com.walkmate.ui.badge;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Multi-view-type adapter for the badge screen.
 *
 * View types:
 *   TYPE_PROGRESS_CARD  — overall progress banner (one per list, position 0)
 *   TYPE_RARITY_LEGEND  — rarity legend row (position 1)
 *   TYPE_CATEGORY_HEADER — section heading (badge category name)
 *   TYPE_BADGE_ITEM     — individual badge card
 */
public class BadgeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_PROGRESS_CARD   = 0;
    private static final int TYPE_RARITY_LEGEND   = 1;
    private static final int TYPE_CATEGORY_HEADER = 2;
    private static final int TYPE_BADGE_ITEM      = 3;

    // ── Flat item list ────────────────────────────────────────────────────────

    private static final class Row {
        final int type;
        // used by TYPE_PROGRESS_CARD
        int earnedCount, totalCount;
        // used by TYPE_CATEGORY_HEADER
        String categoryTitle;
        int categoryEarned, categoryTotal;
        // used by TYPE_BADGE_ITEM
        BadgeUiState.BadgeItem badge;

        Row(int type) { this.type = type; }
    }

    private final List<Row> rows = new ArrayList<>();

    // ── Public API ────────────────────────────────────────────────────────────

    public void submitState(BadgeUiState state, String filter) {
        rows.clear();

        if (state == null || state.getCategories() == null) {
            notifyDataSetChanged();
            return;
        }

        // Progress card
        Row progressRow = new Row(TYPE_PROGRESS_CARD);
        progressRow.earnedCount = state.getEarnedCount();
        progressRow.totalCount  = state.getTotalCount();
        rows.add(progressRow);

        // Rarity legend
        rows.add(new Row(TYPE_RARITY_LEGEND));

        // Categories + badge items (filtered)
        for (BadgeUiState.BadgeCategory cat : state.getCategories()) {
            List<BadgeUiState.BadgeItem> filtered = new ArrayList<>();
            for (BadgeUiState.BadgeItem b : cat.badges) {
                if ("all".equals(filter)) {
                    filtered.add(b);
                } else if ("earned".equals(filter) && b.earned) {
                    filtered.add(b);
                } else if ("locked".equals(filter) && !b.earned) {
                    filtered.add(b);
                }
            }
            if (filtered.isEmpty()) continue;

            int catEarned = 0;
            for (BadgeUiState.BadgeItem b : filtered) if (b.earned) catEarned++;

            Row header = new Row(TYPE_CATEGORY_HEADER);
            header.categoryTitle  = cat.title;
            header.categoryEarned = catEarned;
            header.categoryTotal  = filtered.size();
            rows.add(header);

            for (BadgeUiState.BadgeItem b : filtered) {
                Row r = new Row(TYPE_BADGE_ITEM);
                r.badge = b;
                rows.add(r);
            }
        }

        notifyDataSetChanged();
    }

    // ── Adapter overrides ─────────────────────────────────────────────────────

    @Override public int getItemViewType(int position) { return rows.get(position).type; }
    @Override public int getItemCount()                { return rows.size(); }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_PROGRESS_CARD:
                return new ProgressCardVH(inf.inflate(R.layout.item_badge_progress_card, parent, false));
            case TYPE_RARITY_LEGEND:
                return new RarityLegendVH(inf.inflate(R.layout.item_badge_rarity_legend, parent, false));
            case TYPE_CATEGORY_HEADER:
                return new CategoryHeaderVH(inf.inflate(R.layout.item_badge_category_header, parent, false));
            default:
                return new BadgeItemVH(inf.inflate(R.layout.item_badge_card, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        switch (row.type) {
            case TYPE_PROGRESS_CARD:
                ((ProgressCardVH) holder).bind(row.earnedCount, row.totalCount);
                break;
            case TYPE_CATEGORY_HEADER:
                ((CategoryHeaderVH) holder).bind(row.categoryTitle,
                        row.categoryEarned, row.categoryTotal);
                break;
            case TYPE_BADGE_ITEM:
                ((BadgeItemVH) holder).bind(row.badge);
                break;
            // TYPE_RARITY_LEGEND is static — no bind needed
        }
    }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    static class ProgressCardVH extends RecyclerView.ViewHolder {
        private final TextView  txtPct;
        private final TextView  txtSubtitle;
        private final ProgressBar progressBar;

        ProgressCardVH(@NonNull View v) {
            super(v);
            txtPct      = v.findViewById(R.id.txtProgressPct);
            txtSubtitle = v.findViewById(R.id.txtProgressSubtitle);
            progressBar = v.findViewById(R.id.progressBarOverall);
        }

        void bind(int earned, int total) {
            int pct = total == 0 ? 0 : Math.round((earned * 100f) / total);
            txtPct.setText(pct + "%");
            txtSubtitle.setText(earned + " / " + total + " badges");
            progressBar.setMax(100);
            progressBar.setProgress(pct);
        }
    }

    static class RarityLegendVH extends RecyclerView.ViewHolder {
        RarityLegendVH(@NonNull View v) { super(v); }
    }

    static class CategoryHeaderVH extends RecyclerView.ViewHolder {
        private final TextView txtTitle;
        private final TextView txtCount;

        CategoryHeaderVH(@NonNull View v) {
            super(v);
            txtTitle = v.findViewById(R.id.txtCategoryTitle);
            txtCount = v.findViewById(R.id.txtCategoryCount);
        }

        void bind(String title, int earned, int total) {
            txtTitle.setText(title);
            txtCount.setText(earned + "/" + total);
        }
    }

    static class BadgeItemVH extends RecyclerView.ViewHolder {
        private final View      accentBar;
        private final View      shimmerStrip;
        private final TextView  txtName;
        private final TextView  txtRarity;
        private final TextView  txtDescription;
        private final TextView  txtEarnedDate;
        private final TextView  txtProgressLabel;
        private final ProgressBar progressBar;
        private final ImageView lockIcon;

        BadgeItemVH(@NonNull View v) {
            super(v);
            accentBar        = v.findViewById(R.id.viewAccentBar);
            shimmerStrip     = v.findViewById(R.id.viewShimmerStrip);
            txtName          = v.findViewById(R.id.txtBadgeName);
            txtRarity        = v.findViewById(R.id.txtBadgeRarity);
            txtDescription   = v.findViewById(R.id.txtBadgeDescription);
            txtEarnedDate    = v.findViewById(R.id.txtEarnedDate);
            txtProgressLabel = v.findViewById(R.id.txtProgressLabel);
            progressBar      = v.findViewById(R.id.progressBarBadge);
            lockIcon         = (ImageView) v.findViewById(R.id.viewLockIcon);
        }

        void bind(BadgeUiState.BadgeItem badge) {
            int rarityColor = rarityColor(badge.rarity, itemView);

            // Accent bar
            accentBar.setBackgroundColor(badge.earned
                    ? rarityColor
                    : ContextCompat.getColor(itemView.getContext(), R.color.badge_locked_accent));

            // Shimmer strip (earned only)
            shimmerStrip.setVisibility(badge.earned ? View.VISIBLE : View.GONE);

            // Name
            txtName.setText(badge.displayName);
            txtName.setTextColor(badge.earned
                    ? ContextCompat.getColor(itemView.getContext(), R.color.text_dark)
                    : ContextCompat.getColor(itemView.getContext(), R.color.badge_locked_name));

            // Rarity chip
            txtRarity.setText(capitalize(badge.rarity));
            txtRarity.setTextColor(badge.earned
                    ? rarityColor
                    : ContextCompat.getColor(itemView.getContext(), R.color.badge_locked_rarity_text));
            txtRarity.setBackgroundTintList(ColorStateList.valueOf(
                    badge.earned
                            ? rarityBg(badge.rarity, itemView)
                            : ContextCompat.getColor(itemView.getContext(), R.color.badge_locked_rarity_bg)));

            // Description
            txtDescription.setText(badge.description);
            txtDescription.setTextColor(badge.earned
                    ? ContextCompat.getColor(itemView.getContext(), R.color.text_label)
                    : ContextCompat.getColor(itemView.getContext(), R.color.badge_locked_desc));

            // Lock icon
            lockIcon.setVisibility(badge.earned ? View.GONE : View.VISIBLE);

            // Earned date / progress
            if (badge.earned) {
                txtEarnedDate.setVisibility(badge.earnedDate != null ? View.VISIBLE : View.GONE);
                if (badge.earnedDate != null) txtEarnedDate.setText("Earned " + badge.earnedDate);
                txtProgressLabel.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
            } else {
                txtEarnedDate.setVisibility(View.GONE);
                if (badge.progressLabel != null) {
                    txtProgressLabel.setText(badge.progressLabel);
                    txtProgressLabel.setVisibility(View.VISIBLE);
                    progressBar.setMax(100);
                    progressBar.setProgress(badge.progressPct);
                    progressBar.setProgressTintList(ColorStateList.valueOf(rarityColor));
                    progressBar.setVisibility(View.VISIBLE);
                } else {
                    txtProgressLabel.setVisibility(View.GONE);
                    progressBar.setVisibility(View.GONE);
                }
            }

            // Card opacity
            itemView.setAlpha(badge.earned ? 1f : 0.75f);
        }

        private static int rarityColor(String rarity, View v) {
            if (rarity == null) return ContextCompat.getColor(v.getContext(), R.color.badge_common_color);
            switch (rarity) {
                case "rare":      return ContextCompat.getColor(v.getContext(), R.color.badge_rare_color);
                case "epic":      return ContextCompat.getColor(v.getContext(), R.color.badge_epic_color);
                case "legendary": return ContextCompat.getColor(v.getContext(), R.color.orange_primary);
                default:          return ContextCompat.getColor(v.getContext(), R.color.badge_common_color);
            }
        }

        private static int rarityBg(String rarity, View v) {
            if (rarity == null) return ContextCompat.getColor(v.getContext(), R.color.badge_common_bg);
            switch (rarity) {
                case "rare":      return ContextCompat.getColor(v.getContext(), R.color.badge_rare_bg);
                case "epic":      return ContextCompat.getColor(v.getContext(), R.color.badge_epic_bg);
                case "legendary": return ContextCompat.getColor(v.getContext(), R.color.bg_warm_light);
                default:          return ContextCompat.getColor(v.getContext(), R.color.badge_common_bg);
            }
        }

        private static String capitalize(String s) {
            if (s == null || s.isEmpty()) return "";
            return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
        }
    }
}
