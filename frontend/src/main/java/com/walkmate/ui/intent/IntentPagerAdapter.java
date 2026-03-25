package com.walkmate.ui.intent;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.walkmate.ui.intent.create.IntentCreateFragment;
import com.walkmate.ui.intent.matching.IntentMatchingFragment;
import com.walkmate.ui.intent.result.IntentResultFragment;

public class IntentPagerAdapter extends FragmentStateAdapter {

    public IntentPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == IntentActivity.TAB_MATCHING) {
            return new IntentMatchingFragment();
        }
        if (position == IntentActivity.TAB_RESULT) {
            return new IntentResultFragment();
        }
        return new IntentCreateFragment();
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
