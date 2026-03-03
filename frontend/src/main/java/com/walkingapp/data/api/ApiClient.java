package com.walkingapp.data.api;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
  private static final String BASE_URL = "http://10.0.2.2:8000/";
  private static Retrofit retrofit = null;
  private static ApiService apiService = null;

  public static ApiService getApiService() {
    if (apiService == null) {
      retrofit = createRetrofit();
      apiService = retrofit.create(ApiService.class);
    }
    return apiService;
  }

  private static Retrofit createRetrofit() {
    HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
    loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

    OkHttpClient client = new OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build();

    return new Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build();
  }
}
