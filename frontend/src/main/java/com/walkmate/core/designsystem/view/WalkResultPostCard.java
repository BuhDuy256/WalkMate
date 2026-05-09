package com.walkmate.core.designsystem.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.walkmate.R;
import com.walkmate.core.util.GlideHelper;
import com.walkmate.domain.walkpost.PostVisibility;
import com.walkmate.domain.walkpost.WalkPost;

import java.text.DecimalFormat;

public class WalkResultPostCard extends CardView {

    public enum Variant { OWNER, VIEWER }

    public interface OnChangeVisibilityListener {
        void onChangeVisibility(String postId);
    }

    public interface OnDeletePostListener {
        void onDeletePost(String postId);
    }

    private AvatarInitialView imgAuthorAvatar;
    private TextView txtAuthorName;
    private TextView txtCreatedAt;
    private VisibilityChipView chipVisibility;
    private ImageView btnOverflow;
    private ImageView imgRouteMap;
    private TextView txtRouteNoData;
    private TextView txtHotspotName;
    private LinearLayout layoutStats;
    private WalkMateStatColumn statDistance;
    private WalkMateStatColumn statDuration;
    private LinearLayout layoutCompanion;
    private TextView txtCompanionName;
    private TextView txtCaption;

    private OnChangeVisibilityListener changeVisibilityListener;
    private OnDeletePostListener deletePostListener;

    public WalkResultPostCard(@NonNull Context context) {
        super(context);
        init(context);
    }

    public WalkResultPostCard(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public WalkResultPostCard(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_walk_result_post_card, this, true);

        imgAuthorAvatar  = findViewById(R.id.img_author_avatar);
        txtAuthorName    = findViewById(R.id.txt_author_name);
        txtCreatedAt     = findViewById(R.id.txt_created_at);
        chipVisibility   = findViewById(R.id.chip_visibility);
        btnOverflow      = findViewById(R.id.btn_overflow);
        imgRouteMap      = findViewById(R.id.img_route_map);
        txtRouteNoData   = findViewById(R.id.txt_route_no_data);
        txtHotspotName   = findViewById(R.id.txt_hotspot_name);
        layoutStats      = findViewById(R.id.layout_stats);
        statDistance     = findViewById(R.id.stat_distance);
        statDuration     = findViewById(R.id.stat_duration);
        layoutCompanion  = findViewById(R.id.layout_companion);
        txtCompanionName = findViewById(R.id.txt_companion_name);
        txtCaption       = findViewById(R.id.txt_caption);
    }

    public void bind(WalkPost post, Variant variant) {
        imgAuthorAvatar.bind(
                post.getAuthorName() != null ? post.getAuthorName() : "",
                post.getAuthorAvatarUrl());
        txtAuthorName.setText(post.getAuthorName());
        txtCreatedAt.setText(formatDate(post.getCreatedAt()));
        txtHotspotName.setText(post.getHotspotName());

        bindRouteMap(post);

        if (post.isShowStats()) {
            layoutStats.setVisibility(View.VISIBLE);
            DecimalFormat df = new DecimalFormat("#.##");
            statDistance.bind(df.format(post.getDistanceKm()), "km");
            long minutes = post.getDurationSeconds() / 60;
            statDuration.bind(String.valueOf(minutes), "min");
        } else {
            layoutStats.setVisibility(View.GONE);
        }

        if (post.isShowCompanion() && post.getCompanionName() != null) {
            layoutCompanion.setVisibility(View.VISIBLE);
            txtCompanionName.setText(post.getCompanionName());
        } else {
            layoutCompanion.setVisibility(View.GONE);
        }

        String caption = post.getCaption();
        if (caption != null && !caption.isEmpty()) {
            txtCaption.setText(caption);
            txtCaption.setVisibility(View.VISIBLE);
        } else {
            txtCaption.setVisibility(View.GONE);
        }

        if (variant == Variant.OWNER) {
            chipVisibility.setVisibility(View.VISIBLE);
            chipVisibility.setVisibility(post.getVisibility());
            btnOverflow.setVisibility(View.VISIBLE);
            btnOverflow.setOnClickListener(v -> showOverflowMenu(v, post.getPostId()));
        } else {
            chipVisibility.setVisibility(View.GONE);
            btnOverflow.setVisibility(View.GONE);
        }
    }

    /**
     * Renders the route map section.
     *
     *   showRouteMap = false             → both views GONE
     *   showRouteMap = true
     *     + url non-empty               → ImageView (Glide loads READY route image)
     *     + url null, status null        → ImageView with placeholder drawable
     *                                       (used in create-post live preview)
     *     + url null, status NO_ROUTE    → TextView "No route recorded"
     *     + url null, status PENDING     → TextView "Route preview is being generated…"
     *     + url null, status FAILED/other→ TextView "Route preview unavailable"
     */
    private void bindRouteMap(WalkPost post) {
        if (!post.isShowRouteMap()) {
            imgRouteMap.setVisibility(View.GONE);
            txtRouteNoData.setVisibility(View.GONE);
            return;
        }

        String url    = post.getRoutePreviewUrl();
        String status = post.getRoutePreviewStatus();

        if (url != null && !url.isEmpty()) {
            imgRouteMap.setVisibility(View.VISIBLE);
            txtRouteNoData.setVisibility(View.GONE);
            GlideHelper.load(imgRouteMap, url, R.drawable.ic_route_map_placeholder);
            return;
        }

        // No URL — behaviour depends on the server-reported status:
        //   PENDING   → text (should not appear in Walk Activity since generation is synchronous,
        //               but shown as safety net)
        //   NO_ROUTE  → clear "No route recorded" label
        //   FAILED    → "Route preview unavailable"
        //   null      → placeholder drawable (used in create-post live preview where no status exists yet)
        if (status == null || status.isEmpty()) {
            imgRouteMap.setVisibility(View.VISIBLE);
            GlideHelper.load(imgRouteMap, null, R.drawable.ic_route_map_placeholder);
            txtRouteNoData.setVisibility(View.GONE);
        } else if ("NO_ROUTE".equals(status)) {
            imgRouteMap.setVisibility(View.GONE);
            txtRouteNoData.setVisibility(View.VISIBLE);
            txtRouteNoData.setText("No route recorded");
        } else if ("PENDING".equals(status)) {
            imgRouteMap.setVisibility(View.GONE);
            txtRouteNoData.setVisibility(View.VISIBLE);
            txtRouteNoData.setText("Route preview is being generated…");
        } else {
            // FAILED or any unknown value
            imgRouteMap.setVisibility(View.GONE);
            txtRouteNoData.setVisibility(View.VISIBLE);
            txtRouteNoData.setText("Route preview unavailable");
        }
    }

    public void setOnChangeVisibilityListener(OnChangeVisibilityListener listener) {
        this.changeVisibilityListener = listener;
    }

    public void setOnDeletePostListener(OnDeletePostListener listener) {
        this.deletePostListener = listener;
    }

    private void showOverflowMenu(View anchor, String postId) {
        PopupMenu popup = new PopupMenu(getContext(), anchor);
        popup.getMenu().add(0, 1, 0, "Change Visibility");
        popup.getMenu().add(0, 2, 1, "Delete");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1 && changeVisibilityListener != null) {
                changeVisibilityListener.onChangeVisibility(postId);
                return true;
            }
            if (item.getItemId() == 2 && deletePostListener != null) {
                deletePostListener.onDeletePost(postId);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "";
        try {
            return isoDate.substring(0, 10);
        } catch (Exception e) {
            return isoDate;
        }
    }
}
