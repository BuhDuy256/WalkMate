package com.walkmate.ui.intent.matching;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.walkmate.R;
import com.walkmate.ui.intent.IntentActivity;

public class IntentMatchingFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_intent_matching, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvGoResult = view.findViewById(R.id.tv_go_result);
        TextView tvSkip = view.findViewById(R.id.tv_skip_scanning);
        View btnBack = view.findViewById(R.id.btn_back_from_matching);

        View.OnClickListener openResult = v -> {
            if (getActivity() instanceof IntentActivity) {
                ((IntentActivity) getActivity()).openResultTab();
            }
        };

        if (tvGoResult != null) {
            tvGoResult.setOnClickListener(openResult);
        }
        if (tvSkip != null) {
            tvSkip.setOnClickListener(openResult);
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getActivity() instanceof IntentActivity) {
                    ((IntentActivity) getActivity()).openCreateTab();
                }
            });
        }
    }
}
