package com.walkmate.ui.matches;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.walkmate.R;

/**
 * Tab 2: Matches.
 * Hosts three sub-tabs: Finding / Proposal / Session via TabLayout + ViewPager2.
 *
 * STUB — Phase A only. Full implementation replaces this in Phase C.
 * Do not add logic here; this class exists solely so MainActivity compiles.
 */
public class MatchesFragment extends Fragment {

    public static final String TAG = "MatchesFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_matches, container, false);
    }
}
