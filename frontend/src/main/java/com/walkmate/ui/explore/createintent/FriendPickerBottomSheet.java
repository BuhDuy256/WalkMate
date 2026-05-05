package com.walkmate.ui.explore.createintent;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.walkmate.R;
import com.walkmate.domain.social.UserSummary;

import java.util.List;

/**
 * Bottom sheet that shows the user's friend list for private-walk invite selection.
 * Callers set the friend list and listen for actions via the two listener callbacks:
 *   - {@link OnFriendSelectedListener} — fired when "Invite Walk" is tapped.
 *   - {@link OnViewProfileListener}   — fired when "View Profile" is tapped.
 */
public class FriendPickerBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "FriendPickerBottomSheet";

    public interface OnFriendSelectedListener {
        void onFriendSelected(String userId, String fullName);
    }

    public interface OnViewProfileListener {
        void onViewProfile(String userId);
    }

    private OnFriendSelectedListener friendSelectedListener;
    private OnViewProfileListener    viewProfileListener;
    private List<UserSummary>        friends;
    /** Guards against a redundant bindData() call after a selection triggers a LiveData update. */
    private boolean selectionMade = false;

    private ProgressBar  progressBar;
    private TextView     txtEmpty;
    private RecyclerView recyclerView;

    public static FriendPickerBottomSheet newInstance() {
        return new FriendPickerBottomSheet();
    }

    public void setOnFriendSelectedListener(OnFriendSelectedListener listener) {
        this.friendSelectedListener = listener;
    }

    public void setOnViewProfileListener(OnViewProfileListener listener) {
        this.viewProfileListener = listener;
    }

    public void setFriends(List<UserSummary> friends, boolean isLoading) {
        this.friends = friends;
        // Skip rebind after a selection is made — the sheet is already dismissing
        // and recreating the adapter at that point causes visible jank.
        if (!selectionMade && getView() != null) {
            bindData(isLoading);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_friend_picker, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        progressBar  = view.findViewById(R.id.progressFriendPicker);
        txtEmpty     = view.findViewById(R.id.txtFriendPickerEmpty);
        recyclerView = view.findViewById(R.id.rvFriendPicker);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        bindData(friends == null);
    }

    private void bindData(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            txtEmpty.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);
            return;
        }

        progressBar.setVisibility(View.GONE);

        if (friends == null || friends.isEmpty()) {
            txtEmpty.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            return;
        }

        txtEmpty.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        recyclerView.setAdapter(new FriendPickerAdapter(friends,
                new FriendPickerAdapter.OnFriendActionListener() {
                    @Override
                    public void onInvite(String userId, String fullName) {
                        selectionMade = true;
                        if (friendSelectedListener != null) {
                            friendSelectedListener.onFriendSelected(userId, fullName);
                        }
                        dismiss();
                    }

                    @Override
                    public void onViewProfile(String userId) {
                        selectionMade = true;
                        if (viewProfileListener != null) {
                            viewProfileListener.onViewProfile(userId);
                        }
                        dismiss();
                    }
                }));
    }
}
