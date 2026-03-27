package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.hotspot.HotspotResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface HotspotApiService {

    @GET("api/v1/hotspots")
    Call<ApiResponse<List<HotspotResponse>>> getHotspots();

    @GET("api/v1/hotspots/{id}")
    Call<ApiResponse<HotspotResponse>> getHotspotById(@Path("id") String id);
}
