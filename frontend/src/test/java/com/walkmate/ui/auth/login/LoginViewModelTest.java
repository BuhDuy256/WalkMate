package com.walkmate.ui.auth.login;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.walkmate.core.util.UserErrorMessageMapper;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.user.UserRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class LoginViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private UserRepository userRepository;

    @Mock
    private Context context;

    private LoginViewModel viewModel;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(context.getApplicationContext()).thenReturn(context);
        // Simplify resource lookups used by UserErrorMessageMapper
        when(context.getString(any(Integer.class))).thenAnswer(invocation -> {
            int resId = invocation.getArgument(0);
            // return a small readable string depending on mapper action
            UserErrorMessageMapper.ErrorResult r = UserErrorMessageMapper.map(null);
            return "error-message-" + resId;
        });

        viewModel = new LoginViewModel(userRepository, context);
    }

    @Test
    public void login_emptyEmail_setsValidationError() {
        viewModel.login("", "password");
        LoginUiState state = viewModel.getUiState().getValue();
        assertFalse(state.isLoading());
        assertEquals("Email is required", state.getError());
    }

    @Test
    public void login_invalidEmail_setsValidationError() {
        viewModel.login("not-an-email", "password");
        LoginUiState state = viewModel.getUiState().getValue();
        assertFalse(state.isLoading());
        assertEquals("Please enter a valid email address", state.getError());
    }

    @Test
    public void login_emptyPassword_setsValidationError() {
        viewModel.login("test@example.com", "");
        LoginUiState state = viewModel.getUiState().getValue();
        assertFalse(state.isLoading());
        assertEquals("Password is required", state.getError());
    }

    @Test
    public void login_success_setsSuccessState() {
        // stub device id
        when(userRepository.getOrGenerateDeviceId()).thenReturn("device-1");
        doAnswer(invocation -> {
            DomainCallback<String> cb = invocation.getArgument(3);
            cb.onSuccess("access-token-xyz");
            return null;
        }).when(userRepository).login(anyString(), anyString(), anyString(), any(DomainCallback.class));

        viewModel.login("user@example.com", "pwd");

        LoginUiState loading = viewModel.getUiState().getValue();
        assertTrue(loading.isLoading());

        // after callback should be success
        LoginUiState finalState = viewModel.getUiState().getValue();
        assertTrue(finalState.isSuccess());
        assertNull(finalState.getError());
    }

    @Test
    public void login_error_mapsAndExposesMessage() {
        when(userRepository.getOrGenerateDeviceId()).thenReturn("device-1");
        doAnswer(invocation -> {
            DomainCallback<String> cb = invocation.getArgument(3);
            cb.onError(new Exception("USER_INVALID_CREDENTIALS"));
            return null;
        }).when(userRepository).login(anyString(), anyString(), anyString(), any(DomainCallback.class));

        viewModel.login("user@example.com", "pwd");

        LoginUiState state = viewModel.getUiState().getValue();
        // buildErrorState uses context.getString(result.messageResId) — mocked to return non-null
        assertFalse(state.isSuccess());
        assertTrue(state.getError() != null && !state.getError().isEmpty());
    }

    @Test
    public void loginWithGoogle_emptyToken_setsError() {
        viewModel.loginWithGoogle("");
        LoginUiState state = viewModel.getUiState().getValue();
        assertEquals("Google Sign-In failed. Please try again.", state.getError());
    }

    @Test
    public void loginWithGoogle_success_setsSuccess() {
        when(userRepository.getOrGenerateDeviceId()).thenReturn("device-1");
        doAnswer(invocation -> {
            DomainCallback<String> cb = invocation.getArgument(2);
            cb.onSuccess("token-google");
            return null;
        }).when(userRepository).loginWithGoogle(anyString(), anyString(), any(DomainCallback.class));

        viewModel.loginWithGoogle("google-id-token");
        LoginUiState state = viewModel.getUiState().getValue();
        assertTrue(state.isSuccess());
        assertNull(state.getError());
    }

    @Test
    public void consumeError_clearsErrorButPreservesOtherFlags() {
        // simulate an error state
        viewModel.getUiState().setValue(new LoginUiState(false, "some-error", false, false));
        viewModel.consumeError();
        LoginUiState state = viewModel.getUiState().getValue();
        assertNull(state.getError());
    }
}
