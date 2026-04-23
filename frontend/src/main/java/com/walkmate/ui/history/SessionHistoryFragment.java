package com.walkmate.ui.history;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.ui.history.routereplay.RouteReplayActivity;
import com.walkmate.ui.profile.publicprofile.PublicProfileFragment;
import com.walkmate.ui.report.ReportIncidentFragment;

/**
 * Session History screen.
 *
 * Displays a list of past sessions. Tap on any session → launches
 * {@link RouteReplayActivity} for that session.
 *
 * Entry point: ProfileFragment via navigateToHistoryEvent on ProfileViewModel.
 */
public class SessionHistoryFragment extends Fragment {

    public static final String TAG = "SessionHistoryFragment";

    private SessionHistoryViewModel viewModel;
    private SessionHistoryAdapter   adapter;

    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private TextView    txtEmpty;
    private TextView    txtError;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_session_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        progressBar  = view.findViewById(R.id.progressHistory);
        recyclerView = view.findViewById(R.id.rvSessionHistory);
        txtEmpty     = view.findViewById(R.id.txtHistoryEmpty);
        txtError     = view.findViewById(R.id.txtHistoryError);
        View btnBack = view.findViewById(R.id.btnBackHistory);

        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        adapter = new SessionHistoryAdapter();
        adapter.setOnSessionSelectedListener(sessionId -> {
            Intent intent = new Intent(requireContext(), RouteReplayActivity.class);
            intent.putExtra(RouteReplayActivity.EXTRA_SESSION_ID, sessionId);
            startActivity(intent);
        });
        adapter.setOnPartnerClickListener(partnerId -> {
            Bundle args = new Bundle();
            args.putString(PublicProfileFragment.ARG_USER_ID, partnerId);
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_sessionHistory_to_publicProfileFragment, args);
        });
        adapter.setOnReportClickListener((sessionId, partnerId, status, terminalAtMs) -> {
            Bundle args = new Bundle();
            args.putString(ReportIncidentFragment.ARG_SESSION_ID,             sessionId);
            args.putString(ReportIncidentFragment.ARG_REPORTED_UID,           partnerId);
            args.putString(ReportIncidentFragment.ARG_SESSION_STATUS,         status != null ? status.name() : null);
            args.putLong(ReportIncidentFragment.ARG_SESSION_TERMINAL_AT_MS,   terminalAtMs);
            NavHostFragment.findNavController(SessionHistoryFragment.this)
                    .navigate(R.id.action_sessionHistory_to_reportIncidentFragment, args);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        WalkMateApplication app =
                (WalkMateApplication) requireActivity().getApplication();
        viewModel = new ViewModelProvider(this,
                new SessionHistoryViewModelFactory(
                        app.getWalkSessionRepository(),
                        app.getSessionManager().getUserId()))
                .get(SessionHistoryViewModel.class);

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
        viewModel.loadHistory();
    }

    // ── State rendering ───────────────────────────────────────────────────────

    private void renderState(SessionHistoryUiState state) {
        switch (state.kind) {
            case LOADING:
                progressBar.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                txtEmpty.setVisibility(View.GONE);
                txtError.setVisibility(View.GONE);
                break;

            case READY:
                progressBar.setVisibility(View.GONE);
                txtError.setVisibility(View.GONE);
                if (state.sessions == null || state.sessions.isEmpty()) {
                    recyclerView.setVisibility(View.GONE);
                    txtEmpty.setVisibility(View.VISIBLE);
                } else {
                    txtEmpty.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    adapter.setCurrentUserId(state.currentUserId);
                    adapter.submitList(state.sessions);
                }
                break;

            case ERROR:
                progressBar.setVisibility(View.GONE);
                recyclerView.setVisibility(View.GONE);
                txtEmpty.setVisibility(View.GONE);
                txtError.setVisibility(View.VISIBLE);
                txtError.setText(state.error);
                break;
        }
    }
}
