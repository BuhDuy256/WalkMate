package com.walkmate.ui.report;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.WalkSessionRepository;

/**
 * ViewModel for the Report Incident screen.
 *
 * Validates input and calls {@link WalkSessionRepository#reportSession}.
 * Posts {@link ReportIncidentUiState#submitted()} on success so the Fragment
 * can show a Toast and pop back.
 */
public class ReportIncidentViewModel extends ViewModel {

    private final MutableLiveData<ReportIncidentUiState> uiState =
            new MutableLiveData<>(ReportIncidentUiState.idle());

    private final WalkSessionRepository sessionRepo;

    public ReportIncidentViewModel(WalkSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    public LiveData<ReportIncidentUiState> getUiState() {
        return uiState;
    }

    /**
     * Validates inputs then submits the incident report.
     *
     * @param sessionId      the session being reported
     * @param reportedUserId the partner's userId
     * @param reason         must not be null or blank
     * @param evidenceUrl    optional URL to evidence (may be null)
     */
    public void submitReport(String sessionId, String reportedUserId,
                             String reason, String evidenceUrl) {
        if (reason == null || reason.trim().isEmpty()) {
            uiState.postValue(ReportIncidentUiState.error("Please select a reason."));
            return;
        }
        if (reportedUserId == null || reportedUserId.trim().isEmpty()) {
            uiState.postValue(ReportIncidentUiState.error("Reported user ID is missing."));
            return;
        }

        uiState.postValue(ReportIncidentUiState.loading());

        sessionRepo.reportSession(sessionId, reportedUserId, reason.trim(),
                evidenceUrl != null && !evidenceUrl.trim().isEmpty()
                        ? evidenceUrl.trim() : null,
                new DomainCallback<Void>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        uiState.postValue(ReportIncidentUiState.submitted());
                    }

                    @Override
                    public void onError(Exception e) {
                        String msg = e.getMessage();
                        uiState.postValue(ReportIncidentUiState.error(
                                (msg != null && !msg.isEmpty()) ? msg : "Failed to submit report"));
                    }
                });
    }
}
