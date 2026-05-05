package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.request.report.ResolveReportRequest;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.report.AdminReportResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface AdminReportApiService {

    @GET("api/v1/admin/reports")
    Call<ApiResponse<List<AdminReportResponse>>> getReports(
            @Query("status") String status);

    @GET("api/v1/admin/reports/{reportId}")
    Call<ApiResponse<AdminReportResponse>> getReport(
            @Path("reportId") String reportId);

    @PATCH("api/v1/admin/reports/{reportId}/resolve")
    Call<ApiResponse<AdminReportResponse>> resolveReport(
            @Path("reportId") String reportId,
            @Body ResolveReportRequest body);
}
