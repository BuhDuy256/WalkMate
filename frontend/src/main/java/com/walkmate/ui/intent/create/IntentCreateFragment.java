package com.walkmate.ui.intent.create;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.walkmate.R;
import com.walkmate.ui.intent.IntentActivity;

public class IntentCreateFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_intent_create, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MaterialButton btnFindBuddy = view.findViewById(R.id.btn_find_buddy);
        if (btnFindBuddy != null) {
            btnFindBuddy.setOnClickListener(v -> {
                if (getActivity() instanceof IntentActivity) {
                    ((IntentActivity) getActivity()).openMatchingTab();
                }
            });
        }
    }
}
