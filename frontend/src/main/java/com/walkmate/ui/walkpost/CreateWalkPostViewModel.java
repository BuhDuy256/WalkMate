package com.walkmate.ui.walkpost;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.util.ErrorMessageResolver;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.walkpost.PostVisibility;
import com.walkmate.domain.walkpost.WalkPost;
import com.walkmate.domain.walkpost.WalkPostRepository;

public class CreateWalkPostViewModel extends ViewModel {

    private final WalkPostRepository walkPostRepository;

    private final MutableLiveData<CreateWalkPostUiState> uiState =
            new MutableLiveData<>(CreateWalkPostUiState.idle());

    public CreateWalkPostViewModel(WalkPostRepository walkPostRepository) {
        this.walkPostRepository = walkPostRepository;
    }

    public void submit(
            String sessionId,
            String rawCaption,
            PostVisibility visibility,
            boolean showRouteMap,
            boolean showStats,
            boolean showCompanion) {

        String caption = rawCaption != null ? rawCaption.trim() : null;
        if (caption != null && caption.isEmpty()) caption = null;
        if (caption != null && caption.length() > 150) {
            uiState.setValue(CreateWalkPostUiState.error("Caption must be 150 characters or fewer."));
            return;
        }

        uiState.setValue(CreateWalkPostUiState.loading());

        walkPostRepository.createPost(
                sessionId, caption, visibility, showCompanion, showRouteMap, showStats,
                new DomainCallback<WalkPost>() {
                    @Override
                    public void onSuccess(WalkPost post) {
                        uiState.postValue(CreateWalkPostUiState.success());
                    }

                    @Override
                    public void onError(Exception e) {
                        uiState.postValue(CreateWalkPostUiState.error(
                                ErrorMessageResolver.resolve(e.getMessage())));
                    }
                });
    }

    public LiveData<CreateWalkPostUiState> getUiState() {
        return uiState;
    }
}
