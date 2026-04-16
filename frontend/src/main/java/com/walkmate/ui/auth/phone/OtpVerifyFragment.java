package com.walkmate.ui.auth.phone;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.core.designsystem.view.OtpInputView;
import com.walkmate.core.designsystem.view.WalkMateButton;
import com.walkmate.ui.main.MainActivity;

/**
 * Second step of phone OTP flow: enter the 6-digit code.
 * Shares PhoneOtpViewModel with PhoneInputFragment via Activity scope.
 */
public class OtpVerifyFragment extends Fragment {

    private OtpInputView otpInputView;
    private WalkMateButton btnVerify;
    private TextView tvResend;
    private TextView tvError;
    private ImageView btnBack;

    private PhoneOtpViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_otp_verify, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        otpInputView = view.findViewById(R.id.otp_input_view);
        btnVerify    = view.findViewById(R.id.btn_verify_otp);
        tvResend     = view.findViewById(R.id.tv_resend);
        tvError      = view.findViewById(R.id.tv_otp_error);
        btnBack      = view.findViewById(R.id.btn_back);

        // Re-use the same ViewModel instance from PhoneInputFragment (Activity scope)
        viewModel = new ViewModelProvider(requireActivity()).get(PhoneOtpViewModel.class);

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        btnVerify.setOnClickListener(v ->
                viewModel.verifyOtp(otpInputView.getOtp()));

        tvResend.setOnClickListener(v -> {
            if (viewModel.getUiState().getValue() != null
                    && viewModel.getUiState().getValue().getResendCooldownSeconds() == 0) {
                otpInputView.clear();
                viewModel.resendOtp();
            }
        });

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
    }

    private void renderState(PhoneOtpUiState state) {
        if (state == null) return;

        btnVerify.setLoading(state.isLoading());
        otpInputView.setEnabled(!state.isLoading());

        // Error display
        if (state.getError() != null) {
            tvError.setText(state.getError());
            tvError.setVisibility(View.VISIBLE);
            viewModel.consumeError();
        } else {
            tvError.setVisibility(View.GONE);
        }

        // Resend cooldown
        int cooldown = state.getResendCooldownSeconds();
        if (cooldown > 0) {
            tvResend.setText(getString(R.string.otp_resend_format, cooldown));
            tvResend.setAlpha(0.5f);
            tvResend.setClickable(false);
        } else {
            tvResend.setText(R.string.otp_resend_ready);
            tvResend.setAlpha(1f);
            tvResend.setClickable(true);
        }

        // Navigate on success
        if (state.isSuccess()) {
            Intent intent = new Intent(requireActivity(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
    }
}
