package com.walkmate.ui.auth.phone;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.core.designsystem.view.WalkMateButton;
import com.walkmate.core.designsystem.view.WalkMateInputField;

/**
 * First step of phone OTP flow: enter phone number and request an OTP.
 * Hosted inside AuthActivity's auth_fragment_container.
 */
public class PhoneInputFragment extends Fragment {

    private WalkMateInputField fieldPhone;
    private WalkMateButton btnSendOtp;
    private ImageView btnBack;

    private PhoneOtpViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_phone_input, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        fieldPhone  = view.findViewById(R.id.field_phone);
        btnSendOtp  = view.findViewById(R.id.btn_send_otp);
        btnBack     = view.findViewById(R.id.btn_back);

        PhoneOtpViewModelFactory factory = new PhoneOtpViewModelFactory(requireContext());
        // Share ViewModel with OtpVerifyFragment via the Activity scope
        viewModel = new ViewModelProvider(requireActivity(), factory).get(PhoneOtpViewModel.class);

        btnBack.setOnClickListener(v -> requireActivity().onBackPressed());

        btnSendOtp.setOnClickListener(v ->
                viewModel.sendOtp(fieldPhone.getText()));

        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;

            btnSendOtp.setLoading(state.isLoading());

            if (state.getError() != null) {
                Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_LONG).show();
                viewModel.consumeError();
            }

            if (state.isOtpSent()) {
                // Navigate to OTP verify step
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.auth_fragment_container, new OtpVerifyFragment())
                        .addToBackStack(null)
                        .commit();
            }
        });
    }
}
