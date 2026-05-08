package com.walkmate.ui.auth.forgotpassword;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.ui.auth.forgotpassword.email.EmailInputFragment;
import com.walkmate.ui.auth.forgotpassword.newpassword.NewPasswordFragment;
import com.walkmate.ui.auth.forgotpassword.otp.OtpVerifyFragment;

public class ForgotPasswordActivity extends AppCompatActivity {

    private ForgotPasswordFlowViewModel flowViewModel;

    // Skips the first LiveData emission (which replays current value on observe).
    // This prevents duplicate fragment adds on rotation when the ViewModel already
    // holds a non-initial step.
    private boolean skipInitialEmission = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        flowViewModel = new ViewModelProvider(this).get(ForgotPasswordFlowViewModel.class);

        if (savedInstanceState == null) {
            showFragment(new EmailInputFragment(), false);
        }

        flowViewModel.getCurrentStep().observe(this, step -> {
            if (skipInitialEmission) {
                skipInitialEmission = false;
                return; // Fragment manager already restored state; skip replay.
            }
            switch (step) {
                case OTP_VERIFY:
                    showFragment(new OtpVerifyFragment(), true);
                    break;
                case NEW_PASSWORD:
                    showFragment(new NewPasswordFragment(), true);
                    break;
                case DONE:
                    finish();
                    break;
                default:
                    break;
            }
        });
    }

    private void showFragment(Fragment fragment, boolean addToBackStack) {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container_forgot, fragment);
        if (addToBackStack) tx.addToBackStack(null);
        tx.commit();
    }
}
