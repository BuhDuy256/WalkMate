package com.walkmate.ui.intent.result;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.walkmate.R;
import com.walkmate.ui.intent.IntentActivity;

public class IntentResultFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_intent_result, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        View btnBack = view.findViewById(R.id.btn_back_from_result);
        View btnScan = view.findViewById(R.id.btn_scan_again);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getActivity() instanceof IntentActivity) {
                    ((IntentActivity) getActivity()).openMatchingTab();
                }
            });
        }

        if (btnScan != null) {
            btnScan.setOnClickListener(v -> {
                if (getActivity() instanceof IntentActivity) {
                    ((IntentActivity) getActivity()).openMatchingTab();
                }
            });
        }
    }
}
