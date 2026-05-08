package com.walkmate.ui.auth.forgotpassword.newpassword;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.R;
import com.walkmate.core.util.UserErrorMessageMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserRepository;

public class NewPasswordViewModel extends ViewModel {

    private final UserRepository userRepository;
    private final Context        appContext;

    private final MutableLiveData<NewPasswordUiState> uiState =
            new MutableLiveData<>(NewPasswordUiState.initial());

    public NewPasswordViewModel(UserRepository userRepository, Context appContext) {
        this.userRepository = userRepository;
        this.appContext     = appContext.getApplicationContext();
    }

    public LiveData<NewPasswordUiState> getUiState() { return uiState; }

    public void confirmReset(String resetToken, String password, String confirmPassword) {
        if (password == null || password.isEmpty()) {
            uiState.setValue(new NewPasswordUiState(false, "Password is required", false));
            return;
        }
        if (!password.equals(confirmPassword)) {
            uiState.setValue(new NewPasswordUiState(false,
                    appContext.getString(R.string.forgot_password_passwords_mismatch), false));
            return;
        }

        uiState.setValue(new NewPasswordUiState(true, null, false));

        userRepository.confirmPasswordReset(resetToken, password, new DomainCallback<Void>() {
            @Override public void onSuccess(Void result) {
                uiState.postValue(new NewPasswordUiState(false, null, true));
            }
            @Override public void onError(Exception error) {
                uiState.postValue(buildError(error.getMessage()));
            }
        });
    }

    public void consumeError() {
        NewPasswordUiState s = uiState.getValue();
        if (s != null) uiState.setValue(new NewPasswordUiState(s.isLoading(), null, s.isSuccess()));
    }

    private NewPasswordUiState buildError(String errorCode) {
        UserErrorMessageMapper.ErrorResult r = UserErrorMessageMapper.map(errorCode);
        return new NewPasswordUiState(false, appContext.getString(r.messageResId), false);
    }
}
