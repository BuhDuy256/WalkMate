package com.walkmate.ui.admin.reports;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.domain.report.AdminReport;

import java.util.ArrayList;
import java.util.List;

public class AdminReportAdapter extends RecyclerView.Adapter<AdminReportAdapter.VH> {

    public interface OnReportClickListener {
        void onReportClick(String reportId);
    }

    private final List<AdminReport>    items    = new ArrayList<>();
    private final OnReportClickListener listener;

    public AdminReportAdapter(OnReportClickListener listener) {
        this.listener = listener;
    }

    public void setReports(List<AdminReport> reports) {
        items.clear();
        if (reports != null) items.addAll(reports);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_report, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {

        private final TextView txtReportId;
        private final TextView txtReportedUser;
        private final TextView txtStatusBadge;
        private final TextView txtReason;
        private final TextView txtReporterName;
        private final TextView txtCreatedAt;
        private final View     btnReview;

        VH(@NonNull View itemView) {
            super(itemView);
            txtReportId    = itemView.findViewById(R.id.txtReportId);
            txtReportedUser = itemView.findViewById(R.id.txtReportedUser);
            txtStatusBadge  = itemView.findViewById(R.id.txtStatusBadge);
            txtReason       = itemView.findViewById(R.id.txtReason);
            txtReporterName = itemView.findViewById(R.id.txtReporterName);
            txtCreatedAt    = itemView.findViewById(R.id.txtCreatedAt);
            btnReview       = itemView.findViewById(R.id.btnReview);
        }

        void bind(AdminReport report, OnReportClickListener listener) {
            Context ctx = itemView.getContext();

            txtReportId.setText(shortId(report.getReportId()));
            txtReportedUser.setText(report.getReportedUserName());
            txtReason.setText(reasonLabel(report.getReason()));
            txtReporterName.setText("by " + report.getReporterName());
            txtCreatedAt.setText(formatDate(report.getCreatedAt()));

            switch (report.getStatus()) {
                case PENDING:
                    txtStatusBadge.setText("PENDING");
                    txtStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_pending);
                    btnReview.setVisibility(View.VISIBLE);
                    break;
                case APPROVED:
                    txtStatusBadge.setText("APPROVED");
                    txtStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_approved);
                    btnReview.setVisibility(View.GONE);
                    break;
                case REJECTED:
                    txtStatusBadge.setText("REJECTED");
                    txtStatusBadge.setBackgroundResource(R.drawable.bg_status_badge_rejected);
                    btnReview.setVisibility(View.GONE);
                    break;
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onReportClick(report.getReportId());
            });
            if (btnReview != null) {
                btnReview.setOnClickListener(v -> {
                    if (listener != null) listener.onReportClick(report.getReportId());
                });
            }
        }

        private String shortId(String id) {
            if (id == null || id.length() < 8) return id;
            return "#" + id.substring(0, 8).toUpperCase();
        }

        private String reasonLabel(AdminReport.Reason reason) {
            if (reason == null) return "Other";
            switch (reason) {
                case SAFETY_CONCERN: return "Safety Concern";
                case MISCONDUCT:     return "Misconduct";
                case EMERGENCY:      return "Emergency";
                default:             return "Other";
            }
        }

        private String formatDate(String iso) {
            if (iso == null || iso.length() < 10) return "";
            return iso.substring(0, 10);
        }
    }
}
