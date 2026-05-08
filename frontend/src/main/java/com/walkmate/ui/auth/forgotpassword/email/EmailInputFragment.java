package com.walkmate.ui.auth.forgotpassword.email;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.walkmate.R;
import com.walkmate.core.designsystem.view.WalkMateButton;
import com.walkmate.core.designsystem.view.WalkMateInputField;
import com.walkmate.ui.auth.forgotpassword.ForgotPasswordFlowViewModel;

public class EmailInputFragment extends Fragment {

    private WalkMateInputField  fieldEmail;
    private WalkMateButton      btnSend;

    private EmailInputViewModel     viewModel;
    private ForgotPasswordFlowViewModel flowViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_email_input, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        fieldEmail = view.findViewById(R.id.field_reset_email);
        btnSend    = view.findViewById(R.id.btn_send_otp);

        viewModel = new ViewModelProvider(this, new EmailInputViewModelFactory(requireContext()))
                .get(EmailInputViewModel.class);
        flowViewModel = new ViewModelProvider(requireActivity())
                .get(ForgotPasswordFlowViewModel.class);

        view.findViewById(R.id.btn_back_email).setOnClickListener(v -> requireActivity().finish());

        btnSend.setOnClickListener(v -> viewModel.sendOtp(fieldEmail.getText()));

        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            btnSend.setLoading(state.isLoading());
            if (state.getError() != null) {
                Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_LONG).show();
                viewModel.consumeError();
            }
            if (state.isOtpSent()) {
                viewModel.consumeOtpSent();
                flowViewModel.goToOtpVerify(fieldEmail.getText().trim());
            }
        });
    }
}
