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
 * The caller (ExploreFragment) sets the friend list and listens for selection via
 * the {@link OnFriendSelectedListener} callback.
 */
public class FriendPickerBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "FriendPickerBottomSheet";

    public interface OnFriendSelectedListener {
        void onFriendSelected(String userId, String fullName);
    }

    private OnFriendSelectedListener listener;
    private List<UserSummary> friends;

    private ProgressBar progressBar;
    private TextView txtEmpty;
    private RecyclerView recyclerView;

    public static FriendPickerBottomSheet newInstance() {
        return new FriendPickerBottomSheet();
    }

    public void setOnFriendSelectedListener(OnFriendSelectedListener listener) {
        this.listener = listener;
    }

    public void setFriends(List<UserSummary> friends, boolean isLoading) {
        this.friends = friends;
        if (getView() != null) {
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
        recyclerView.setAdapter(new FriendPickerAdapter(friends, (userId, name) -> {
            if (listener != null) listener.onFriendSelected(userId, name);
            dismiss();
        }));
    }
}
