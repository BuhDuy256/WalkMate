package com.walkmate.ui.profile.publicprofile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.domain.review.WalkReview;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for the reviews list on PublicProfileFragment.
 * Each item shows star rating, optional comment, and date.
 */
class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.ViewHolder> {

    private List<WalkReview> items = new ArrayList<>();

    void submitList(List<WalkReview> reviews) {
        this.items = reviews != null ? reviews : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_review, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView txtStars;
        private final TextView txtDate;
        private final TextView txtComment;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            txtStars   = itemView.findViewById(R.id.txtReviewStars);
            txtDate    = itemView.findViewById(R.id.txtReviewDate);
            txtComment = itemView.findViewById(R.id.txtReviewComment);
        }

        void bind(WalkReview review) {
            txtStars.setText(buildStars(review.getRatingStars()));
            txtDate.setText(formatDate(review.getCreatedAt()));

            String comment = review.getComment();
            if (comment != null && !comment.isEmpty()) {
                txtComment.setVisibility(View.VISIBLE);
                txtComment.setText(comment);
            } else {
                txtComment.setVisibility(View.GONE);
            }
        }

        private static String buildStars(int rating) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                sb.append(i < rating ? "★" : "☆");
            }
            return sb.toString();
        }

        private static String formatDate(String iso) {
            if (iso == null || iso.length() < 10) return "";
            return iso.substring(0, 10);
        }
    }
}
