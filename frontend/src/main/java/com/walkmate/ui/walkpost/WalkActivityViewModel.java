package com.walkmate.ui.walkpost;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.util.ErrorMessageResolver;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkpost.PostVisibility;
import com.walkmate.domain.walkpost.WalkPost;
import com.walkmate.domain.walkpost.WalkPostRepository;

import java.util.List;

public class WalkActivityViewModel extends ViewModel {

    private final WalkPostRepository walkPostRepository;

    private final MutableLiveData<WalkActivityUiState> uiState =
            new MutableLiveData<>(WalkActivityUiState.loading());

    private final MutableLiveData<String> toastMessage = new MutableLiveData<>();

    public WalkActivityViewModel(WalkPostRepository walkPostRepository) {
        this.walkPostRepository = walkPostRepository;
    }

    public void loadMyPosts() {
        uiState.setValue(WalkActivityUiState.loading());
        walkPostRepository.getMyPosts(new DomainCallback<List<WalkPost>>() {
            @Override
            public void onSuccess(List<WalkPost> posts) {
                uiState.postValue(WalkActivityUiState.ready(posts));
            }

            @Override
            public void onError(Exception e) {
                uiState.postValue(WalkActivityUiState.error(
                        ErrorMessageResolver.resolve(e.getMessage())));
            }
        });
    }

    public void changeVisibility(String postId, PostVisibility newVisibility) {
        walkPostRepository.updateVisibility(postId, newVisibility.name(),
                new DomainCallback<WalkPost>() {
                    @Override
                    public void onSuccess(WalkPost post) {
                        loadMyPosts();
                    }

                    @Override
                    public void onError(Exception e) {
                        toastMessage.postValue(ErrorMessageResolver.resolve(e.getMessage()));
                    }
                });
    }

    public void deletePost(String postId) {
        walkPostRepository.deletePost(postId, new DomainCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                loadMyPosts();
            }

            @Override
            public void onError(Exception e) {
                toastMessage.postValue(ErrorMessageResolver.resolve(e.getMessage()));
            }
        });
    }

    public LiveData<WalkActivityUiState> getUiState() { return uiState; }
    public LiveData<String> getToastMessage()         { return toastMessage; }
}
