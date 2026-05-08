package com.walkmate.ui.auth.forgotpassword;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class ForgotPasswordFlowViewModel extends ViewModel {

    public enum Step { EMAIL_INPUT, OTP_VERIFY, NEW_PASSWORD, DONE }

    private String email;
    private String resetToken;

    private final MutableLiveData<Step> currentStep = new MutableLiveData<>(Step.EMAIL_INPUT);

    public LiveData<Step> getCurrentStep() { return currentStep; }

    public String getEmail()      { return email; }
    public String getResetToken() { return resetToken; }

    public void goToOtpVerify(String email) {
        this.email = email;
        currentStep.setValue(Step.OTP_VERIFY);
    }

    public void goToNewPassword(String resetToken) {
        this.resetToken = resetToken;
        currentStep.setValue(Step.NEW_PASSWORD);
    }

    public void goToDone() {
        currentStep.setValue(Step.DONE);
    }
}
