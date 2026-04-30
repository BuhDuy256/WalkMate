package com.walkmate.ui.qr.show;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.walksession.WalkSessionRepository;

public class ShowQrViewModelFactory implements ViewModelProvider.Factory {

    private final WalkSessionRepository repository;

    public ShowQrViewModelFactory(WalkSessionRepository repository) {
        this.repository = repository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new ShowQrViewModel(repository);
    }
}
