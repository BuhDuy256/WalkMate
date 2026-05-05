package com.walkmate.data.repository;

import android.content.Context;
import android.util.Log;

import com.walkmate.core.util.ErrorParser;
import com.walkmate.data.datasource.remote.api.AdminReportApiService;
import com.walkmate.data.datasource.remote.api.ApiClient;
import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.datasource.remote.dto.request.report.ResolveReportRequest;
import com.walkmate.data.datasource.remote.dto.response.ApiError;
import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.report.AdminReportResponse;
import com.walkmate.data.mapper.AdminReportMapper;
import com.walkmate.domain.report.AdminReport;
import com.walkmate.domain.report.AdminReportRepository;
import com.walkmate.domain.shared.DomainCallback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Response;

public class AdminReportRepositoryImpl implements AdminReportRepository {

    private static final String TAG = "AdminReportRepo";

    private final AdminReportApiService apiService;
    private final AdminReportMapper     mapper;
    private final ExecutorService       executor = Executors.newCachedThreadPool();

    public AdminReportRepositoryImpl(Context context) {
        SessionManager sessionManager = new SessionManager(context);
        this.apiService = ApiClient.buildAuthenticatedRetrofit(sessionManager, ApiClient.getAuthApiService())
                .create(AdminReportApiService.class);
        this.mapper = new AdminReportMapper();
    }

    @Override
    public void getAllReports(DomainCallback<List<AdminReport>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<AdminReportResponse>>> resp =
                        apiService.getReports(null).execute();
                handleListResponse(resp, callback);
            } catch (IOException e) {
                Log.e(TAG, "getAllReports network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getReportsByStatus(String status, DomainCallback<List<AdminReport>> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<List<AdminReportResponse>>> resp =
                        apiService.getReports(status).execute();
                handleListResponse(resp, callback);
            } catch (IOException e) {
                Log.e(TAG, "getReportsByStatus network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void getReportById(String reportId, DomainCallback<AdminReport> callback) {
        executor.execute(() -> {
            try {
                Response<ApiResponse<AdminReportResponse>> resp =
                        apiService.getReport(reportId).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(mapper.toDomain(resp.body().getData()));
                } else {
                    ApiError err = ErrorParser.extractApiError(resp, "REPORT_FETCH_FAILED");
                    callback.onError(new Exception(err.getCode()));
                }
            } catch (IOException e) {
                Log.e(TAG, "getReportById network error", e);
                callback.onError(e);
            }
        });
    }

    @Override
    public void resolveReport(String reportId, String resolution, String note,
                              DomainCallback<AdminReport> callback) {
        executor.execute(() -> {
            try {
                ResolveReportRequest body = new ResolveReportRequest(resolution, note);
                Response<ApiResponse<AdminReportResponse>> resp =
                        apiService.resolveReport(reportId, body).execute();
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    callback.onSuccess(mapper.toDomain(resp.body().getData()));
                } else {
                    ApiError err = ErrorParser.extractApiError(resp, "REPORT_RESOLVE_FAILED");
                    callback.onError(new Exception(err.getCode()));
                }
            } catch (IOException e) {
                Log.e(TAG, "resolveReport network error", e);
                callback.onError(e);
            }
        });
    }

    private void handleListResponse(Response<ApiResponse<List<AdminReportResponse>>> resp,
                                    DomainCallback<List<AdminReport>> callback) {
        if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
            List<AdminReportResponse> data = resp.body().getData();
            if (data == null) {
                callback.onSuccess(Collections.emptyList());
                return;
            }
            List<AdminReport> result = new ArrayList<>(data.size());
            for (AdminReportResponse dto : data) result.add(mapper.toDomain(dto));
            callback.onSuccess(result);
        } else {
            ApiError err = ErrorParser.extractApiError(resp, "REPORTS_FETCH_FAILED");
            callback.onError(new Exception(err.getCode()));
        }
    }
}
