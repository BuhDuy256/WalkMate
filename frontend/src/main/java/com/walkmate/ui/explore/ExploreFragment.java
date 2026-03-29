package com.walkmate.ui.explore;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.walkmate.R;

/**
 * Tab 1: Explore.
 * Full-screen map + intent-creation bottom sheet flow.
 *
 * STUB — Phase A only. Full implementation replaces this in Phase B.
 * Do not add logic here; this class exists solely so MainActivity compiles.
 */
public class ExploreFragment extends Fragment {

    public static final String TAG = "ExploreFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_explore, container, false);
    }
}
