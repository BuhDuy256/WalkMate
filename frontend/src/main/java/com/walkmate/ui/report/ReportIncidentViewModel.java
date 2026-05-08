package com.walkmate.ui.report;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.util.ErrorMessageResolver;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walksession.SessionSummary;
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
            new MutableLiveData<>(ReportIncidentUiState.loading());

    private final WalkSessionRepository sessionRepo;

    public ReportIncidentViewModel(WalkSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    public LiveData<ReportIncidentUiState> getUiState() {
        return uiState;
    }

    /**
     * Checks whether the session has already been reported by fetching the
     * session summary directly. Posts {@link ReportIncidentUiState.Kind#ALREADY_REPORTED}
     * with the full snapshot so the Fragment can pre-fill reason and evidence URL.
     * Falls back to {@link ReportIncidentUiState.Kind#IDLE} on any error so the user
     * can still attempt to submit.
     */
    public void loadReportState(String sessionId) {
        uiState.postValue(ReportIncidentUiState.loading());

        sessionRepo.getSessionSummary(sessionId, new DomainCallback<SessionSummary>() {
            @Override
            public void onSuccess(SessionSummary session) {
                if (session.isReported()) {
                    SessionSummary.ReportSnapshot domainSnap = session.getReportSnapshot();
                    ReportIncidentUiState.ReportSnapshot uiSnap = domainSnap != null
                            ? new ReportIncidentUiState.ReportSnapshot(
                                    domainSnap.getReason(),
                                    domainSnap.getEvidenceUrl())
                            : null;
                    uiState.postValue(ReportIncidentUiState.alreadyReported(uiSnap));
                } else {
                    uiState.postValue(ReportIncidentUiState.idle());
                }
            }

            @Override
            public void onError(Exception e) {
                uiState.postValue(ReportIncidentUiState.idle());
            }
        });
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
                        uiState.postValue(ReportIncidentUiState.error(
                                ErrorMessageResolver.resolve(e.getMessage())));
                    }
                });
    }
}
