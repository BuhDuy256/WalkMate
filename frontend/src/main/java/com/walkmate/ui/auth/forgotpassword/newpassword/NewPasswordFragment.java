package com.walkmate.ui.auth.forgotpassword.newpassword;

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

public class NewPasswordFragment extends Fragment {

    private WalkMateInputField  fieldPassword;
    private WalkMateInputField  fieldConfirm;
    private WalkMateButton      btnReset;

    private NewPasswordViewModel        viewModel;
    private ForgotPasswordFlowViewModel flowViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_new_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        fieldPassword = view.findViewById(R.id.field_new_password);
        fieldConfirm  = view.findViewById(R.id.field_confirm_password);
        btnReset      = view.findViewById(R.id.btn_reset_password);

        viewModel = new ViewModelProvider(this, new NewPasswordViewModelFactory(requireContext()))
                .get(NewPasswordViewModel.class);
        flowViewModel = new ViewModelProvider(requireActivity())
                .get(ForgotPasswordFlowViewModel.class);

        btnReset.setOnClickListener(v ->
                viewModel.confirmReset(
                        flowViewModel.getResetToken(),
                        fieldPassword.getText(),
                        fieldConfirm.getText()));

        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            btnReset.setLoading(state.isLoading());
            if (state.getError() != null) {
                Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_LONG).show();
                viewModel.consumeError();
            }
            if (state.isSuccess()) {
                Toast.makeText(requireContext(),
                        R.string.forgot_password_success, Toast.LENGTH_LONG).show();
                flowViewModel.goToDone();
            }
        });
    }
}
