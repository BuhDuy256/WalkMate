package com.walkmate.domain.report;

import com.walkmate.domain.shared.DomainCallback;

import java.util.List;

public interface AdminReportRepository {
    void getAllReports(DomainCallback<List<AdminReport>> callback);
    void getReportsByStatus(String status, DomainCallback<List<AdminReport>> callback);
    void getReportById(String reportId, DomainCallback<AdminReport> callback);
    void resolveReport(String reportId, String resolution, String note,
                       DomainCallback<AdminReport> callback);
}
