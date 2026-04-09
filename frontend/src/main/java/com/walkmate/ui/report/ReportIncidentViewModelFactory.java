package com.walkmate.ui.report;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.domain.walksession.WalkSessionRepository;

/**
 * Manual DI factory for ReportIncidentViewModel.
 */
public class ReportIncidentViewModelFactory implements ViewModelProvider.Factory {

    private final WalkSessionRepository sessionRepo;

    public ReportIncidentViewModelFactory(WalkSessionRepository sessionRepo) {
        this.sessionRepo = sessionRepo;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ReportIncidentViewModel.class)) {
            return (T) new ReportIncidentViewModel(sessionRepo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
