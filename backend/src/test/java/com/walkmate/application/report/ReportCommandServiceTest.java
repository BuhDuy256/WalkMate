package com.walkmate.application.report;

import com.walkmate.domain.report.ReportErrorCode;
import com.walkmate.domain.report.SessionReport;
import com.walkmate.domain.report.SessionReportRepository;
import com.walkmate.domain.session.SessionErrorCode;
import com.walkmate.domain.session.SessionStatus;
import com.walkmate.domain.session.WalkSession;
import com.walkmate.domain.session.WalkSessionRepository;
import com.walkmate.domain.shared.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportCommandServiceTest {

    @Mock WalkSessionRepository   sessionRepository;
    @Mock SessionReportRepository reportRepository;

    @InjectMocks ReportCommandService service;

    private static final String SESSION_ID       = UUID.randomUUID().toString();
    private static final String REPORTER_ID      = UUID.randomUUID().toString();
    private static final String REPORTED_USER_ID = UUID.randomUUID().toString();
    private static final String REASON           = "Bad behavior";

    @BeforeEach
    void setDefaultWindows() {
        // Inject default window values (72h completed, 24h terminal)
        ReflectionTestUtils.setField(service, "completedWindowHours", 72L);
        ReflectionTestUtils.setField(service, "terminalWindowHours",  24L);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private WalkSession session(SessionStatus status, Instant endedAt) {
        return new WalkSession(
                SESSION_ID, UUID.randomUUID().toString(),
                REPORTER_ID, REPORTED_USER_ID,      // userIdA = reporter, userIdB = reported
                1.0, 1.0,
                Instant.now().minusSeconds(7200),    // scheduledStart
                Instant.now().minusSeconds(3600),    // scheduledEnd
                status,
                Instant.now().minusSeconds(7200),    // createdAt
                status == SessionStatus.ACTIVE ? Instant.now().minusSeconds(3600) : null,
                endedAt,
                null, null, null, null, null, 0L, 0.0, 0L
        );
    }

    // ── Participant & self-report guards ──────────────────────────────────────

    @Test
    void submitReport_notParticipant_throws() {
        WalkSession s = session(SessionStatus.ACTIVE, null);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(s));

        String outsider = UUID.randomUUID().toString();
        assertThatThrownBy(() -> service.submitReport(SESSION_ID, outsider, REPORTED_USER_ID, REASON, null))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(SessionErrorCode.SESSION_NOT_PARTICIPANT));
    }

    @Test
    void submitReport_selfReport_throws() {
        WalkSession s = session(SessionStatus.ACTIVE, null);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.submitReport(SESSION_ID, REPORTER_ID, REPORTER_ID, REASON, null))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_SELF_NOT_ALLOWED));
    }

    // ── Status rules ──────────────────────────────────────────────────────────

    @Test
    void submitReport_pendingSession_throws() {
        WalkSession s = session(SessionStatus.PENDING, null);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.submitReport(SESSION_ID, REPORTER_ID, REPORTED_USER_ID, REASON, null))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_SESSION_INVALID_STATUS));
    }

    @Test
    void submitReport_cancelledSession_throws() {
        WalkSession s = session(SessionStatus.CANCELLED, Instant.now().minusSeconds(60));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.submitReport(SESSION_ID, REPORTER_ID, REPORTED_USER_ID, REASON, null))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_SESSION_INVALID_STATUS));
    }

    @Test
    void submitReport_activeSession_alwaysAllowed() {
        WalkSession s = session(SessionStatus.ACTIVE, null);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(s));
        when(reportRepository.existsBySessionAndReporter(SESSION_ID, REPORTER_ID)).thenReturn(false);

        SessionReport report = service.submitReport(SESSION_ID, REPORTER_ID, REPORTED_USER_ID, REASON, null);
        assertThat(report).isNotNull();
        verify(reportRepository).save(any(SessionReport.class));
    }

    @Test
    void submitReport_noShowSession_alwaysAllowed() {
        WalkSession s = session(SessionStatus.NO_SHOW, Instant.now().minusSeconds(60));
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(s));
        when(reportRepository.existsBySessionAndReporter(SESSION_ID, REPORTER_ID)).thenReturn(false);

        SessionReport report = service.submitReport(SESSION_ID, REPORTER_ID, REPORTED_USER_ID, REASON, null);
        assertThat(report).isNotNull();
    }

    // ── COMPLETED window (default 72h) ────────────────────────────────────────

    @Test
    void submitReport_completedWithin72h_allowed() {
        Instant endedAt = Instant.now().minusSeconds(3600); // 1h ago
        WalkSession s = session(SessionStatus.COMPLETED, endedAt);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(s));
        when(reportRepository.existsBySessionAndReporter(SESSION_ID, REPORTER_ID)).thenReturn(false);

        SessionReport report = service.submitReport(SESSION_ID, REPORTER_ID, REPORTED_USER_ID, REASON, null);
        assertThat(report).isNotNull();
    }

    @Test
    void submitReport_completedAfter72h_rejected() {
        Instant endedAt = Instant.now().minusSeconds(73 * 3600L); // 73h ago
        WalkSession s = session(SessionStatus.COMPLETED, endedAt);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.submitReport(SESSION_ID, REPORTER_ID, REPORTED_USER_ID, REASON, null))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_WINDOW_EXPIRED));
    }

    // ── ABORTED window (default 24h) ──────────────────────────────────────────

    @Test
    void submitReport_abortedWithin24h_allowed() {
        Instant endedAt = Instant.now().minusSeconds(3600); // 1h ago
        WalkSession s = session(SessionStatus.ABORTED, endedAt);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(s));
        when(reportRepository.existsBySessionAndReporter(SESSION_ID, REPORTER_ID)).thenReturn(false);

        SessionReport report = service.submitReport(SESSION_ID, REPORTER_ID, REPORTED_USER_ID, REASON, null);
        assertThat(report).isNotNull();
    }

    @Test
    void submitReport_abortedAfter24h_rejected() {
        Instant endedAt = Instant.now().minusSeconds(25 * 3600L); // 25h ago
        WalkSession s = session(SessionStatus.ABORTED, endedAt);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.submitReport(SESSION_ID, REPORTER_ID, REPORTED_USER_ID, REASON, null))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_WINDOW_EXPIRED));
    }

    // ── Configurable window override ──────────────────────────────────────────

    @Test
    void submitReport_completedWindow_overrideToShorter_rejectsEarlier() {
        // Override completed window to 1 hour
        ReflectionTestUtils.setField(service, "completedWindowHours", 1L);

        Instant endedAt = Instant.now().minusSeconds(2 * 3600L); // 2h ago — within default 72h but outside 1h
        WalkSession s = session(SessionStatus.COMPLETED, endedAt);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.submitReport(SESSION_ID, REPORTER_ID, REPORTED_USER_ID, REASON, null))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_WINDOW_EXPIRED));
    }

    @Test
    void submitReport_terminalWindow_overrideToLonger_allowsReportThatDefaultWouldReject() {
        // Override terminal window to 48 hours
        ReflectionTestUtils.setField(service, "terminalWindowHours", 48L);

        Instant endedAt = Instant.now().minusSeconds(25 * 3600L); // 25h ago — rejected at 24h, allowed at 48h
        WalkSession s = session(SessionStatus.ABORTED, endedAt);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(s));
        when(reportRepository.existsBySessionAndReporter(SESSION_ID, REPORTER_ID)).thenReturn(false);

        SessionReport report = service.submitReport(SESSION_ID, REPORTER_ID, REPORTED_USER_ID, REASON, null);
        assertThat(report).isNotNull();
    }

    // ── Duplicate guard ───────────────────────────────────────────────────────

    @Test
    void submitReport_duplicate_throws() {
        WalkSession s = session(SessionStatus.ACTIVE, null);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(s));
        when(reportRepository.existsBySessionAndReporter(SESSION_ID, REPORTER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.submitReport(SESSION_ID, REPORTER_ID, REPORTED_USER_ID, REASON, null))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> assertThat(((DomainException) ex).getErrorCode())
                        .isEqualTo(ReportErrorCode.REPORT_ALREADY_SUBMITTED));
    }
}
