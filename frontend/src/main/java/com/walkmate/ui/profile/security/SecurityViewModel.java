package com.walkmate.ui.profile.security;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.AccountSecurityInfo;
import com.walkmate.domain.user.UserRepository;

public class SecurityViewModel extends ViewModel {

    private final UserRepository userRepository;
    private final MutableLiveData<SecurityUiState> uiState =
            new MutableLiveData<>(SecurityUiState.initial());

    public SecurityViewModel(UserRepository userRepository) {
        this.userRepository = userRepository;
        loadSecurityInfo();
    }

    public LiveData<SecurityUiState> getUiState() { return uiState; }

    public void loadSecurityInfo() {
        uiState.setValue(SecurityUiState.initial());
        userRepository.getAccountSecurityInfo(new DomainCallback<AccountSecurityInfo>() {
            @Override
            public void onSuccess(AccountSecurityInfo info) {
                uiState.postValue(new SecurityUiState(
                        false, info.hasPassword(), info.hasGoogle(), false, null));
            }

            @Override
            public void onError(Exception error) {
                uiState.postValue(new SecurityUiState(false, false, false, false, "Failed to load security info."));
            }
        });
    }

    public void setOrChangePassword(String currentPassword, String newPassword, String confirmPassword) {
        if (newPassword == null || newPassword.trim().isEmpty()) {
            postError("New password is required.");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            postError("Passwords do not match.");
            return;
        }

        SecurityUiState current = uiState.getValue();
        boolean hasPass = current != null && current.hasPassword();
        boolean hasGoog = current != null && current.hasGoogle();

        uiState.setValue(new SecurityUiState(true, hasPass, hasGoog, false, null));

        String passToSend = hasPass ? currentPassword : null;

        userRepository.setOrChangePassword(passToSend, newPassword.trim(),
                new DomainCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        // Reload to flip hasPassword=true in UI
                        userRepository.getAccountSecurityInfo(new DomainCallback<AccountSecurityInfo>() {
                            @Override
                            public void onSuccess(AccountSecurityInfo info) {
                                uiState.postValue(new SecurityUiState(
                                        false, info.hasPassword(), info.hasGoogle(), true, null));
                            }

                            @Override
                            public void onError(Exception e) {
                                uiState.postValue(new SecurityUiState(
                                        false, true, hasGoog, true, null));
                            }
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        String msg = resolveErrorMessage(error.getMessage(), hasPass);
                        uiState.postValue(new SecurityUiState(false, hasPass, hasGoog, false, msg));
                    }
                });
    }

    public void consumeSuccess() {
        SecurityUiState s = uiState.getValue();
        if (s != null) {
            uiState.setValue(new SecurityUiState(s.isLoading(), s.hasPassword(), s.hasGoogle(), false, s.getError()));
        }
    }

    public void consumeError() {
        SecurityUiState s = uiState.getValue();
        if (s != null) {
            uiState.setValue(new SecurityUiState(s.isLoading(), s.hasPassword(), s.hasGoogle(), s.isSuccess(), null));
        }
    }

    private void postError(String message) {
        SecurityUiState current = uiState.getValue();
        boolean hasPass = current != null && current.hasPassword();
        boolean hasGoog = current != null && current.hasGoogle();
        uiState.setValue(new SecurityUiState(false, hasPass, hasGoog, false, message));
    }

    private String resolveErrorMessage(String errorCode, boolean hadExistingPassword) {
        if (errorCode == null) return "Something went wrong. Please try again.";
        switch (errorCode) {
            case "USER_INVALID_CREDENTIALS":
                return "Current password is incorrect.";
            case "USER_PASSWORD_TOO_WEAK":
                return "Password must be at least 8 characters with one uppercase letter and one number.";
            case "INVALID_USER_DATA":
                return hadExistingPassword
                        ? "Current password is required."
                        : "Invalid information provided.";
            default:
                return "Something went wrong. Please try again.";
        }
    }
}
