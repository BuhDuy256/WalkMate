package com.walkingapp.ui.session;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;

import com.walkingapp.data.repository.WalkingRepository;
import com.walkingapp.model.Proposal;
import com.walkingapp.model.Session;

public class SessionViewModel extends ViewModel {
  private final WalkingRepository repository;
  private final MediatorLiveData<WalkingRepository.Result<Proposal>> proposalResult;
  private final MediatorLiveData<WalkingRepository.Result<Session>> sessionResult;

  public SessionViewModel() {
    this.repository = new WalkingRepository();
    this.proposalResult = new MediatorLiveData<>();
    this.sessionResult = new MediatorLiveData<>();
  }

  public LiveData<WalkingRepository.Result<Proposal>> getProposalResult() {
    return proposalResult;
  }

  public LiveData<WalkingRepository.Result<Session>> getSessionResult() {
    return sessionResult;
  }

  public void loadUserProposal(int userId) {
    LiveData<WalkingRepository.Result<Proposal>> source = repository.getUserProposal(userId);
    proposalResult.addSource(source, result -> {
      proposalResult.setValue(result);
      proposalResult.removeSource(source);
    });
  }

  public void confirmProposal(int proposalId, int userId) {
    LiveData<WalkingRepository.Result<Proposal>> source = repository.confirmProposal(proposalId, userId);
    proposalResult.addSource(source, result -> {
      proposalResult.setValue(result);
      proposalResult.removeSource(source);
    });
  }

  public void cancelProposal(int proposalId, int userId) {
    LiveData<WalkingRepository.Result<Proposal>> source = repository.cancelProposal(proposalId, userId);
    proposalResult.addSource(source, result -> {
      proposalResult.setValue(result);
      proposalResult.removeSource(source);
    });
  }

  public void loadUserSession(int userId) {
    LiveData<WalkingRepository.Result<Session>> source = repository.getUserSession(userId);
    sessionResult.addSource(source, result -> {
      sessionResult.setValue(result);
      sessionResult.removeSource(source);
    });
  }

  public void startSession(int sessionId, int userId) {
    LiveData<WalkingRepository.Result<Session>> source = repository.startSession(sessionId, userId);
    sessionResult.addSource(source, result -> {
      sessionResult.setValue(result);
      sessionResult.removeSource(source);
    });
  }

  public void completeSession(int sessionId, int userId) {
    LiveData<WalkingRepository.Result<Session>> source = repository.completeSession(sessionId, userId);
    sessionResult.addSource(source, result -> {
      sessionResult.setValue(result);
      sessionResult.removeSource(source);
    });
  }
}
