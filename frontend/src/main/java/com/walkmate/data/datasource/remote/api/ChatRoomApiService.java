package com.walkmate.data.datasource.remote.api;

import com.walkmate.data.datasource.remote.dto.request.chatroom.SendMessageRequestDto;
import com.walkmate.data.datasource.remote.dto.response.chatroom.ChatMessageDto;
import com.walkmate.data.datasource.remote.dto.response.chatroom.ChatRoomDto;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Retrofit interface for ChatRoom API endpoints.
 *
 * Requires authenticated client (ApiClient.buildAuthenticatedRetrofit).
 */
public interface ChatRoomApiService {

    @GET("sessions/{sessionId}/chatroom")
    Call<ChatRoomDto> getChatRoom(@Path("sessionId") String sessionId);

    @POST("sessions/{sessionId}/chatroom/messages")
    Call<ChatMessageDto> sendMessage(
            @Path("sessionId") String sessionId,
            @Body SendMessageRequestDto body);
}
