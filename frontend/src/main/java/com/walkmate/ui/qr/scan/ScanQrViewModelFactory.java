package com.walkmate.ui.qr.scan;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.walksession.WalkSessionRepository;

public class ScanQrViewModelFactory implements ViewModelProvider.Factory {

    private final WalkSessionRepository repository;

    public ScanQrViewModelFactory(WalkSessionRepository repository) {
        this.repository = repository;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new ScanQrViewModel(repository);
    }
}
