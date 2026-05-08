package com.walkmate.ui.walkpost;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.core.designsystem.view.WalkResultPostCard;
import com.walkmate.domain.walkpost.WalkPost;

public class WalkPostAdapter extends ListAdapter<WalkPost, WalkPostAdapter.ViewHolder> {

    private WalkResultPostCard.Variant variant = WalkResultPostCard.Variant.OWNER;
    private WalkResultPostCard.OnChangeVisibilityListener changeVisibilityListener;
    private WalkResultPostCard.OnDeletePostListener deletePostListener;

    public WalkPostAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setVariant(WalkResultPostCard.Variant variant) {
        this.variant = variant;
    }

    public void setOnChangeVisibilityListener(WalkResultPostCard.OnChangeVisibilityListener l) {
        this.changeVisibilityListener = l;
    }

    public void setOnDeletePostListener(WalkResultPostCard.OnDeletePostListener l) {
        this.deletePostListener = l;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        WalkResultPostCard card = new WalkResultPostCard(parent.getContext());
        card.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT));
        return new ViewHolder(card);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WalkPost post = getItem(position);
        holder.card.setOnChangeVisibilityListener(changeVisibilityListener);
        holder.card.setOnDeletePostListener(deletePostListener);
        holder.card.bind(post, variant);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final WalkResultPostCard card;

        ViewHolder(WalkResultPostCard card) {
            super(card);
            this.card = card;
        }
    }

    private static final DiffUtil.ItemCallback<WalkPost> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<WalkPost>() {
        @Override
        public boolean areItemsTheSame(@NonNull WalkPost a, @NonNull WalkPost b) {
            return a.getPostId() != null && a.getPostId().equals(b.getPostId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull WalkPost a, @NonNull WalkPost b) {
            if (a.getPostId() == null || !a.getPostId().equals(b.getPostId())) return false;
            if (a.getVisibility() != b.getVisibility()) return false;
            String capA = a.getCaption(), capB = b.getCaption();
            return capA == null ? capB == null : capA.equals(capB);
        }
    };
}
