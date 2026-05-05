package com.walkmate.ui.admin.reports;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.report.AdminReport;
import com.walkmate.domain.report.AdminReportRepository;
import com.walkmate.domain.shared.DomainCallback;

public class AdminReportDetailViewModel extends ViewModel {

    private final MutableLiveData<AdminReportDetailUiState> uiState = new MutableLiveData<>();
    private final AdminReportRepository reportRepo;

    public AdminReportDetailViewModel(AdminReportRepository reportRepo) {
        this.reportRepo = reportRepo;
    }

    public LiveData<AdminReportDetailUiState> getUiState() {
        return uiState;
    }

    public void loadReport(String reportId) {
        uiState.postValue(AdminReportDetailUiState.loading());
        reportRepo.getReportById(reportId, new DomainCallback<AdminReport>() {
            @Override
            public void onSuccess(AdminReport report) {
                uiState.postValue(AdminReportDetailUiState.loaded(report));
            }

            @Override
            public void onError(Exception e) {
                String msg = e.getMessage();
                uiState.postValue(AdminReportDetailUiState.error(
                        msg != null ? msg : "Failed to load report"));
            }
        });
    }

    public void resolveReport(String reportId, String resolution, String note) {
        AdminReportDetailUiState current = uiState.getValue();
        AdminReport currentReport = current != null ? current.getReport() : null;
        uiState.postValue(AdminReportDetailUiState.processing(currentReport));

        reportRepo.resolveReport(reportId, resolution, note, new DomainCallback<AdminReport>() {
            @Override
            public void onSuccess(AdminReport report) {
                uiState.postValue(AdminReportDetailUiState.resolved(report));
            }

            @Override
            public void onError(Exception e) {
                String msg = e.getMessage();
                uiState.postValue(AdminReportDetailUiState.error(
                        msg != null ? msg : "Failed to resolve report"));
            }
        });
    }

    public void consumeError() {
        AdminReportDetailUiState current = uiState.getValue();
        if (current != null && current.getError() != null) {
            uiState.postValue(AdminReportDetailUiState.loaded(current.getReport()));
        }
    }
}
