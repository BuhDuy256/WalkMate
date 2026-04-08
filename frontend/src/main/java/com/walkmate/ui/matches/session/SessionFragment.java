package com.walkmate.ui.matches.session;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.walkmate.R;
import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.ui.matches.MatchesUiState;
import com.walkmate.ui.matches.MatchesViewModel;
import com.walkmate.ui.tracking.TrackingScreenActivity;

public class SessionFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private TextView txtEmpty;
    private SessionAdapter adapter;

    private MatchesViewModel matchesViewModel;

    // ── Polling ───────────────────────────────────────────────────────────────

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            matchesViewModel.loadAll();
            pollHandler.postDelayed(this, 15_000L);
        }
    };

    private void startActivationPolling() {
        pollHandler.removeCallbacks(pollRunnable);
        pollHandler.postDelayed(pollRunnable, 15_000L);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_session, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Shared ViewModel scoped to Activity — same instance as MatchesFragment.
        matchesViewModel = new ViewModelProvider(requireActivity())
                .get(MatchesViewModel.class);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        recyclerView = view.findViewById(R.id.recyclerView);
        txtEmpty     = view.findViewById(R.id.txtEmpty);

        adapter = new SessionAdapter();
        adapter.setOnChatClickListener(session ->
                Toast.makeText(requireContext(),
                        R.string.session_chat_coming_soon, Toast.LENGTH_SHORT).show());
        adapter.setOnCancelClickListener(session ->
                showCancelReasonDialog(session.getSessionId()));
        adapter.setSessionActionListener(new SessionAdapter.SessionActionListener() {
            @Override
            public void onArriveClicked(String sessionId) {
                matchesViewModel.activateSession(sessionId);
            }

            @Override
            public void onAbortClicked(String sessionId) {
                showCancelReasonDialog(sessionId); // re-uses cancel dialog as abort proxy
            }

            @Override
            public void onCompleteClicked(String sessionId) {
                // Navigate to tracking screen where complete is handled properly
                startActivity(new Intent(requireContext(), TrackingScreenActivity.class)
                        .putExtra(TrackingScreenActivity.EXTRA_SESSION_ID, sessionId));
            }
        });
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> matchesViewModel.loadAll());

        matchesViewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);

        matchesViewModel.getActivationResultEvent().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;
            matchesViewModel.consumeActivationResult();
            if (result.session != null && result.session.getStatus() == WalkSession.Status.ACTIVE) {
                // Case B: both activated — launch tracking screen
                startActivity(new Intent(requireContext(), TrackingScreenActivity.class)
                        .putExtra(TrackingScreenActivity.EXTRA_SESSION_ID,   result.session.getSessionId())
                        .putExtra(TrackingScreenActivity.EXTRA_PARTNER_NAME, result.session.getPartnerName())
                        .putExtra(TrackingScreenActivity.EXTRA_MEETING_LAT,  result.session.getMeetingPointLat())
                        .putExtra(TrackingScreenActivity.EXTRA_MEETING_LNG,  result.session.getMeetingPointLng()));
            } else if (result.session != null) {
                // Case A: only this user activated — show waiting toast and start polling
                showWaitingForPartnerUi();
                startActivationPolling();
            } else if ("SESSION_ACTIVATION_WINDOW_CLOSED".equals(result.errorCode)) {
                Toast.makeText(requireContext(),
                        "Activation window has closed.", Toast.LENGTH_SHORT).show();
            } else if (result.errorCode != null) {
                Toast.makeText(requireContext(), result.errorCode, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Periodic background refresh while the fragment is visible (30 s)
        pollHandler.postDelayed(pollRunnable, 30_000L);
    }

    @Override
    public void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(pollRunnable);
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void showWaitingForPartnerUi() {
        Toast.makeText(requireContext(),
                R.string.session_waiting_for_partner, Toast.LENGTH_LONG).show();
    }

    // ── State rendering ───────────────────────────────────────────────────────

    private void renderState(MatchesUiState state) {
        swipeRefresh.setRefreshing(state.isLoading());

        adapter.setItems(state.getActiveSessions());

        boolean empty = !state.isLoading() && state.getActiveSessions().isEmpty();
        txtEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (state.getError() != null) {
            Toast.makeText(requireContext(), state.getError(), Toast.LENGTH_SHORT).show();
            matchesViewModel.consumeError();
        }
    }

    private void showCancelReasonDialog(String sessionId) {
        String[] reasons = {
                getString(R.string.cancel_reason_busy),
                getString(R.string.cancel_reason_no_contact),
                getString(R.string.cancel_reason_other)
        };

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.cancel_session_title)
                .setItems(reasons, (dialog, which) ->
                        matchesViewModel.cancelSession(sessionId, reasons[which]))
                .setNegativeButton(R.string.btn_keep_session, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        pollHandler.removeCallbacks(pollRunnable);
        recyclerView.setAdapter(null);
        swipeRefresh = null;
        recyclerView = null;
        txtEmpty     = null;
        adapter      = null;
    }
}
