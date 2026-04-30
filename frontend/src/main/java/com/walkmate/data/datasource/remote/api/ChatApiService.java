package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.response.ApiResponse;
import com.walkmate.data.datasource.remote.dto.response.chat.ChatMessageDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ChatApiService {

    @GET("api/v1/sessions/{sessionId}/chat/messages")
    Call<ApiResponse<List<ChatMessageDto>>> getChatHistory(
            @Path("sessionId") String sessionId,
            @Query("limit") int limit
    );
}
