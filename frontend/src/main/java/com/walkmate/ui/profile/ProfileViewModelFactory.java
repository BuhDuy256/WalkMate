package com.walkmate.ui.profile;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.data.datasource.remote.api.SessionManager;
import com.walkmate.data.repository.UserRepositoryImpl;
import com.walkmate.domain.gamification.GamificationRepository;
import com.walkmate.domain.report.AdminReportRepository;
import com.walkmate.domain.review.ReviewRepository;
import com.walkmate.domain.user.UserProfileRepository;
import com.walkmate.domain.user.UserRepository;

public class ProfileViewModelFactory implements ViewModelProvider.Factory {

    private final UserProfileRepository  profileRepo;
    private final UserRepository         userRepository;
    private final GamificationRepository gamificationRepo;
    private final ReviewRepository       reviewRepo;
    private final SessionManager         sessionManager;
    private final AdminReportRepository  adminReportRepo;

    public ProfileViewModelFactory(UserProfileRepository profileRepo,
                                   Context context,
                                   GamificationRepository gamificationRepo,
                                   ReviewRepository reviewRepo,
                                   AdminReportRepository adminReportRepo) {
        this.profileRepo      = profileRepo;
        this.userRepository   = new UserRepositoryImpl(context.getApplicationContext());
        this.gamificationRepo = gamificationRepo;
        this.reviewRepo       = reviewRepo;
        this.sessionManager   = new SessionManager(context.getApplicationContext());
        this.adminReportRepo  = adminReportRepo;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ProfileViewModel.class)) {
            return (T) new ProfileViewModel(profileRepo, userRepository, gamificationRepo,
                    reviewRepo, sessionManager, adminReportRepo);
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
