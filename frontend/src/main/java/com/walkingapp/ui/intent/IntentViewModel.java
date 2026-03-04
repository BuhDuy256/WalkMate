package com.walkingapp.ui.intent;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.walkingapp.data.repository.WalkingRepository;
import com.walkingapp.model.Intent;
import com.walkingapp.model.Proposal;

public class IntentViewModel extends ViewModel {
  private final WalkingRepository repository;
  private final MediatorLiveData<WalkingRepository.Result<Intent>> intentResult;
  private final MediatorLiveData<WalkingRepository.Result<Proposal>> matchResult;

  public IntentViewModel() {
    this.repository = new WalkingRepository();
    this.intentResult = new MediatorLiveData<>();
    this.matchResult = new MediatorLiveData<>();
  }

  public LiveData<WalkingRepository.Result<Intent>> getIntentResult() {
    return intentResult;
  }

  public LiveData<WalkingRepository.Result<Proposal>> getMatchResult() {
    return matchResult;
  }

  public void createIntent(int userId, String walkType, String startAt, int flexMinutes, double lat, double lng,
      int radiusM) {
    Intent intent = new Intent(walkType, startAt, flexMinutes, lat, lng, radiusM);
    intent.setUserId(userId);
    LiveData<WalkingRepository.Result<Intent>> source = repository.createIntent(intent);
    intentResult.addSource(source, result -> {
      intentResult.setValue(result);
      intentResult.removeSource(source);
    });
  }

  public void findMatch(int userId) {
    android.util.Log.d("IntentViewModel", "findMatch called with userId: " + userId);
    LiveData<WalkingRepository.Result<Proposal>> source = repository.findMatch(userId);
    matchResult.addSource(source, result -> {
      android.util.Log.d("IntentViewModel", "findMatch source emitted result: " +
          (result != null ? result.getStatus() : "NULL"));
      matchResult.setValue(result);
      matchResult.removeSource(source);
    });
  }

  public void loadUserIntent(int userId) {
    LiveData<WalkingRepository.Result<Intent>> source = repository.getUserIntent(userId);
    intentResult.addSource(source, result -> {
      intentResult.setValue(result);
      intentResult.removeSource(source);
    });
  }
}
