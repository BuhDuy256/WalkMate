package com.walkingapp.data.api;

import com.walkingapp.model.Intent;
import com.walkingapp.model.Proposal;
import com.walkingapp.model.Session;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

  @POST("api/intents")
  Call<Intent> createIntent(@Body Intent intent);

  @GET("api/intents/user/{userId}")
  Call<Intent> getUserIntent(@Path("userId") int userId);

  @POST("api/match")
  Call<Proposal> findMatch(@Body Map<String, Integer> body);

  @GET("api/proposals/{proposalId}")
  Call<Proposal> getProposal(@Path("proposalId") int proposalId);

  @GET("api/proposals/user/{userId}")
  Call<Proposal> getUserProposal(@Path("userId") int userId);

  @PUT("api/proposals/{proposalId}/confirm")
  Call<Proposal> confirmProposal(@Path("proposalId") int proposalId, @Body Map<String, Integer> body);

  @PUT("api/proposals/{proposalId}/cancel")
  Call<Proposal> cancelProposal(@Path("proposalId") int proposalId, @Body Map<String, Integer> body);

  @GET("api/sessions/{sessionId}")
  Call<Session> getSession(@Path("sessionId") int sessionId);

  @GET("api/sessions/user/{userId}")
  Call<Session> getUserSession(@Path("userId") int userId);

  @PUT("api/sessions/{sessionId}/start")
  Call<Session> startSession(@Path("sessionId") int sessionId, @Body Map<String, Integer> body);

  @PUT("api/sessions/{sessionId}/complete")
  Call<Session> completeSession(@Path("sessionId") int sessionId, @Body Map<String, Integer> body);
}
