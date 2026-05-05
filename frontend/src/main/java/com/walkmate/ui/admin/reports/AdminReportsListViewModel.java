package com.walkmate.ui.admin.reports;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.report.AdminReport;
import com.walkmate.domain.report.AdminReportRepository;
import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public class AdminReportsListViewModel extends ViewModel {

    private final MutableLiveData<AdminReportsListUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<String> navigateToDetailEvent = new MutableLiveData<>();

    private final AdminReportRepository reportRepo;

    public AdminReportsListViewModel(AdminReportRepository reportRepo) {
        this.reportRepo = reportRepo;
    }

    public LiveData<AdminReportsListUiState> getUiState() {
        return uiState;
    }

    public LiveData<String> getNavigateToDetailEvent() {
        return navigateToDetailEvent;
    }

    public void consumeNavigateToDetail() {
        navigateToDetailEvent.setValue(null);
    }

    public void loadReports() {
        uiState.postValue(AdminReportsListUiState.loading());
        reportRepo.getAllReports(new DomainCallback<List<AdminReport>>() {
            @Override
            public void onSuccess(List<AdminReport> reports) {
                uiState.postValue(AdminReportsListUiState.success(reports));
            }

            @Override
            public void onError(Exception e) {
                String msg = e.getMessage();
                uiState.postValue(AdminReportsListUiState.error(
                        msg != null ? msg : "Failed to load reports"));
            }
        });
    }

    public void onReportClicked(String reportId) {
        navigateToDetailEvent.postValue(reportId);
    }
}
