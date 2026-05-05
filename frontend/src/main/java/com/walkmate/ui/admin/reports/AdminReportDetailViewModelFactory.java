package com.walkmate.ui.admin.reports;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.report.AdminReportRepository;

public class AdminReportDetailViewModelFactory implements ViewModelProvider.Factory {

    private final AdminReportRepository reportRepo;

    public AdminReportDetailViewModelFactory(AdminReportRepository reportRepo) {
        this.reportRepo = reportRepo;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(AdminReportDetailViewModel.class)) {
            return (T) new AdminReportDetailViewModel(reportRepo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
