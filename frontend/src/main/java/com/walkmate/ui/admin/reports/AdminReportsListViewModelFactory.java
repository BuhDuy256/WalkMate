package com.walkmate.ui.admin.reports;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.report.AdminReportRepository;

public class AdminReportsListViewModelFactory implements ViewModelProvider.Factory {

    private final AdminReportRepository reportRepo;

    public AdminReportsListViewModelFactory(AdminReportRepository reportRepo) {
        this.reportRepo = reportRepo;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(AdminReportsListViewModel.class)) {
            return (T) new AdminReportsListViewModel(reportRepo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
