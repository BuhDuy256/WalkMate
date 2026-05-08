package com.walkmate.ui.auth.forgotpassword.otp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.core.designsystem.view.CountdownTimerView;
import com.walkmate.core.designsystem.view.OtpInputView;
import com.walkmate.core.designsystem.view.WalkMateButton;
import com.walkmate.ui.auth.forgotpassword.ForgotPasswordFlowViewModel;

public class OtpVerifyFragment extends Fragment {

    private OtpInputView        otpInputView;
    private WalkMateButton      btnVerify;
    private TextView            btnResend;
    private CountdownTimerView  countdownView;
    private TextView            tvSubtitle;

    private OtpVerifyViewModel      viewModel;
    private ForgotPasswordFlowViewModel flowViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_otp_verify, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        otpInputView  = view.findViewById(R.id.otp_input_view);
        btnVerify     = view.findViewById(R.id.btn_verify_otp);
        btnResend     = view.findViewById(R.id.tv_resend_otp);
        countdownView = view.findViewById(R.id.countdown_timer);
        tvSubtitle    = view.findViewById(R.id.tv_otp_subtitle);

        viewModel = new ViewModelProvider(this, new OtpVerifyViewModelFactory(requireContext()))
                .get(OtpVerifyViewModel.class);
        flowViewModel = new ViewModelProvider(requireActivity())
                .get(ForgotPasswordFlowViewModel.class);

        String email = flowViewModel.getEmail();
        if (email != null) {
            tvSubtitle.setText(getString(R.string.forgot_password_otp_subtitle, email));
        }

        view.findViewById(R.id.btn_back_otp).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        btnVerify.setOnClickListener(v ->
                viewModel.verifyOtp(flowViewModel.getEmail(), otpInputView.getOtp()));

        btnResend.setOnClickListener(v -> {
            btnResend.setEnabled(false);
            viewModel.resendOtp(flowViewModel.getEmail());
            startResendCooldown();
        });

        // Start the resend cooldown immediately (OTP was just sent when this fragment appeared)
        startResendCooldown();

        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            btnVerify.setLoading(state.isLoading());
            if (state.getError() != null) {
                Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_LONG).show();
                viewModel.consumeError();
            }
            if (state.getResetToken() != null) {
                flowViewModel.goToNewPassword(state.getResetToken());
            }
        });
    }

    private void startResendCooldown() {
        btnResend.setEnabled(false);
        countdownView.setVisibility(View.VISIBLE);
        countdownView.startCountdown(System.currentTimeMillis() + 60_000L);
        countdownView.setOnExpiredListener(() -> {
            btnResend.setEnabled(true);
            countdownView.setVisibility(View.GONE);
        });
    }
}
