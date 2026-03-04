package com.walkingapp.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.walkingapp.data.api.ApiClient;
import com.walkingapp.data.api.ApiService;
import com.walkingapp.model.Intent;
import com.walkingapp.model.Proposal;
import com.walkingapp.model.Session;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WalkingRepository {
  private final ApiService apiService;

  public WalkingRepository() {
    this.apiService = ApiClient.getApiService();
  }

  public LiveData<Result<Intent>> createIntent(Intent intent) {
    MutableLiveData<Result<Intent>> result = new MutableLiveData<>();
    result.setValue(Result.loading());

    apiService.createIntent(intent).enqueue(new Callback<Intent>() {
      @Override
      public void onResponse(Call<Intent> call, Response<Intent> response) {
        android.util.Log.d("WalkingRepository", "createIntent response: code=" + response.code() +
            ", successful=" + response.isSuccessful() +
            ", body=" + (response.body() != null ? "not null" : "NULL"));

        if (response.isSuccessful() && response.body() != null) {
          android.util.Log.d("WalkingRepository", "Intent created successfully, setting success");
          result.setValue(Result.success(response.body()));
        } else if (response.isSuccessful() && response.body() == null) {
          android.util.Log.e("WalkingRepository", "Response successful but body is NULL - Gson parsing failed!");
          result.setValue(Result.error("Response body is null (parsing error)"));
        } else {
          String error = "Failed to create intent. Code: " + response.code();
          try {
            if (response.errorBody() != null) {
              error += ", Error: " + response.errorBody().string();
            }
          } catch (Exception e) {
            error += ", Parse error: " + e.getMessage();
          }
          android.util.Log.e("WalkingRepository", error);
          result.setValue(Result.error(error));
        }
      }

      @Override
      public void onFailure(Call<Intent> call, Throwable t) {
        result.setValue(Result.error("Network error: " + t.getMessage()));
      }
    });

    return result;
  }

  public LiveData<Result<Intent>> getUserIntent(int userId) {
    MutableLiveData<Result<Intent>> result = new MutableLiveData<>();
    result.setValue(Result.loading());

    apiService.getUserIntent(userId).enqueue(new Callback<Intent>() {
      @Override
      public void onResponse(Call<Intent> call, Response<Intent> response) {
        if (response.isSuccessful() && response.body() != null) {
          result.setValue(Result.success(response.body()));
        } else {
          result.setValue(Result.error("No intent found"));
        }
      }

      @Override
      public void onFailure(Call<Intent> call, Throwable t) {
        result.setValue(Result.error(t.getMessage()));
      }
    });

    return result;
  }

  public LiveData<Result<Proposal>> findMatch(int userId) {
    android.util.Log.d("WalkingRepository", "findMatch called with userId: " + userId);
    MutableLiveData<Result<Proposal>> result = new MutableLiveData<>();
    result.setValue(Result.loading());

    Map<String, Integer> body = new HashMap<>();
    body.put("user_id", userId);

    android.util.Log.d("WalkingRepository", "Enqueuing findMatch API call");
    apiService.findMatch(body).enqueue(new Callback<Proposal>() {
      @Override
      public void onResponse(Call<Proposal> call, Response<Proposal> response) {
        android.util.Log.d("WalkingRepository", "findMatch response: code=" + response.code() +
            ", successful=" + response.isSuccessful() +
            ", body=" + (response.body() != null ? "not null" : "NULL"));
        if (response.isSuccessful() && response.body() != null) {
          result.setValue(Result.success(response.body()));
        } else {
          String error = "No match found. Code: " + response.code();
          try {
            if (response.errorBody() != null) {
              error = response.errorBody().string();
            }
          } catch (Exception e) {
            error += ", Parse error: " + e.getMessage();
          }
          android.util.Log.e("WalkingRepository", "findMatch error: " + error);
          result.setValue(Result.error(error));
        }
      }

      @Override
      public void onFailure(Call<Proposal> call, Throwable t) {
        android.util.Log.e("WalkingRepository", "findMatch network failure: " + t.getMessage());
        result.setValue(Result.error("Network error: " + t.getMessage()));
      }
    });

    return result;
  }

  public LiveData<Result<Proposal>> getUserProposal(int userId) {
    MutableLiveData<Result<Proposal>> result = new MutableLiveData<>();
    result.setValue(Result.loading());

    apiService.getUserProposal(userId).enqueue(new Callback<Proposal>() {
      @Override
      public void onResponse(Call<Proposal> call, Response<Proposal> response) {
        if (response.isSuccessful() && response.body() != null) {
          result.setValue(Result.success(response.body()));
        } else {
          result.setValue(Result.error("No proposal found"));
        }
      }

      @Override
      public void onFailure(Call<Proposal> call, Throwable t) {
        result.setValue(Result.error(t.getMessage()));
      }
    });

    return result;
  }

  public LiveData<Result<Proposal>> confirmProposal(int proposalId, int userId) {
    MutableLiveData<Result<Proposal>> result = new MutableLiveData<>();
    result.setValue(Result.loading());

    Map<String, Integer> body = new HashMap<>();
    body.put("user_id", userId);

    apiService.confirmProposal(proposalId, body).enqueue(new Callback<Proposal>() {
      @Override
      public void onResponse(Call<Proposal> call, Response<Proposal> response) {
        if (response.isSuccessful() && response.body() != null) {
          result.setValue(Result.success(response.body()));
        } else {
          result.setValue(Result.error("Failed to confirm proposal"));
        }
      }

      @Override
      public void onFailure(Call<Proposal> call, Throwable t) {
        result.setValue(Result.error(t.getMessage()));
      }
    });

    return result;
  }

  public LiveData<Result<Proposal>> cancelProposal(int proposalId, int userId) {
    MutableLiveData<Result<Proposal>> result = new MutableLiveData<>();
    result.setValue(Result.loading());

    Map<String, Integer> body = new HashMap<>();
    body.put("user_id", userId);

    apiService.cancelProposal(proposalId, body).enqueue(new Callback<Proposal>() {
      @Override
      public void onResponse(Call<Proposal> call, Response<Proposal> response) {
        if (response.isSuccessful() && response.body() != null) {
          result.setValue(Result.success(response.body()));
        } else {
          result.setValue(Result.error("Failed to cancel proposal"));
        }
      }

      @Override
      public void onFailure(Call<Proposal> call, Throwable t) {
        result.setValue(Result.error(t.getMessage()));
      }
    });

    return result;
  }

  public LiveData<Result<Session>> getUserSession(int userId) {
    MutableLiveData<Result<Session>> result = new MutableLiveData<>();
    result.setValue(Result.loading());

    apiService.getUserSession(userId).enqueue(new Callback<Session>() {
      @Override
      public void onResponse(Call<Session> call, Response<Session> response) {
        if (response.isSuccessful() && response.body() != null) {
          result.setValue(Result.success(response.body()));
        } else {
          result.setValue(Result.error("No session found"));
        }
      }

      @Override
      public void onFailure(Call<Session> call, Throwable t) {
        result.setValue(Result.error(t.getMessage()));
      }
    });

    return result;
  }

  public LiveData<Result<Session>> startSession(int sessionId, int userId) {
    MutableLiveData<Result<Session>> result = new MutableLiveData<>();
    result.setValue(Result.loading());

    Map<String, Integer> body = new HashMap<>();
    body.put("user_id", userId);

    apiService.startSession(sessionId, body).enqueue(new Callback<Session>() {
      @Override
      public void onResponse(Call<Session> call, Response<Session> response) {
        if (response.isSuccessful() && response.body() != null) {
          result.setValue(Result.success(response.body()));
        } else {
          result.setValue(Result.error("Failed to start session"));
        }
      }

      @Override
      public void onFailure(Call<Session> call, Throwable t) {
        result.setValue(Result.error(t.getMessage()));
      }
    });

    return result;
  }

  public LiveData<Result<Session>> completeSession(int sessionId, int userId) {
    MutableLiveData<Result<Session>> result = new MutableLiveData<>();
    result.setValue(Result.loading());

    Map<String, Integer> body = new HashMap<>();
    body.put("user_id", userId);

    apiService.completeSession(sessionId, body).enqueue(new Callback<Session>() {
      @Override
      public void onResponse(Call<Session> call, Response<Session> response) {
        if (response.isSuccessful() && response.body() != null) {
          result.setValue(Result.success(response.body()));
        } else {
          result.setValue(Result.error("Failed to complete session"));
        }
      }

      @Override
      public void onFailure(Call<Session> call, Throwable t) {
        result.setValue(Result.error(t.getMessage()));
      }
    });

    return result;
  }

  public static class Result<T> {
    public enum Status {
      LOADING, SUCCESS, ERROR
    }

    private final Status status;
    private final T data;
    private final String error;

    private Result(Status status, T data, String error) {
      this.status = status;
      this.data = data;
      this.error = error;
    }

    public static <T> Result<T> loading() {
      return new Result<>(Status.LOADING, null, null);
    }

    public static <T> Result<T> success(T data) {
      return new Result<>(Status.SUCCESS, data, null);
    }

    public static <T> Result<T> error(String error) {
      return new Result<>(Status.ERROR, null, error);
    }

    public Status getStatus() {
      return status;
    }

    public T getData() {
      return data;
    }

    public String getError() {
      return error;
    }
  }
}
