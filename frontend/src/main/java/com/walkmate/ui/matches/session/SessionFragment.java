package com.walkmate.ui.matches.session;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.walkmate.domain.walksession.WalkSession;
import com.walkmate.ui.chatroom.ChatroomActivity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.walkmate.R;
import com.walkmate.ui.matches.MatchesUiState;
import com.walkmate.ui.matches.MatchesViewModel;
import com.walkmate.ui.tracking.TrackingScreenActivity;

public class SessionFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private TextView txtEmpty;
    private SessionAdapter adapter;

    private MatchesViewModel matchesViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_session, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Shared ViewModel owned by MatchesFragment (the parent)
        matchesViewModel = new ViewModelProvider(requireParentFragment())
                .get(MatchesViewModel.class);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        recyclerView = view.findViewById(R.id.recyclerView);
        txtEmpty     = view.findViewById(R.id.txtEmpty);

        adapter = new SessionAdapter();
        adapter.setOnChatClickListener(session -> {
            Intent chatIntent = ChatroomActivity.createIntent(
                    requireContext(),
                    session.getSessionId(),
                    session.getPartnerName(),
                    session.getPartnerAvatar(),
                    session.getStatus() == WalkSession.Status.PENDING_MEET,
                    parseScheduledTimeMs(session.getScheduledTime())
            );
            startActivity(chatIntent);
        });
        adapter.setOnCancelClickListener(session ->
                showCancelReasonDialog(session.getSessionId()));
        adapter.setOnStartWalkClickListener(session -> {
            Intent intent = new Intent(requireContext(), TrackingScreenActivity.class);
            intent.putExtra(TrackingScreenActivity.EXTRA_SESSION_ID,   session.getSessionId());
            intent.putExtra(TrackingScreenActivity.EXTRA_PARTNER_NAME, session.getPartnerName());
            intent.putExtra(TrackingScreenActivity.EXTRA_MEETING_LAT,  session.getMeetingPointLat());
            intent.putExtra(TrackingScreenActivity.EXTRA_MEETING_LNG,  session.getMeetingPointLng());
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(() -> matchesViewModel.loadAll());

        matchesViewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
    }

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

    /**
     * Parses an ISO-8601 UTC string (e.g. "2026-03-29T14:00:00Z") into epoch milliseconds.
     * Returns 0 if the string is null, empty, or unparseable.
     */
    private static long parseScheduledTimeMs(String isoTime) {
        if (isoTime == null || isoTime.isEmpty()) return 0L;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(isoTime).getTime();
        } catch (ParseException e) {
            return 0L;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recyclerView.setAdapter(null);
        swipeRefresh = null;
        recyclerView = null;
        txtEmpty     = null;
        adapter      = null;
    }
}
