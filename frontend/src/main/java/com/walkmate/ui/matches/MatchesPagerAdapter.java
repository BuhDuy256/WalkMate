package com.walkmate.ui.matches;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.walkmate.ui.matches.finding.FindingFragment;
import com.walkmate.ui.matches.proposal.ProposalFragment;
import com.walkmate.ui.matches.session.SessionFragment;

public class MatchesPagerAdapter extends FragmentStateAdapter {

    public static final int TAB_FINDING  = 0;
    public static final int TAB_PROPOSAL = 1;
    public static final int TAB_SESSION  = 2;
    private static final int TAB_COUNT   = 3;

    public MatchesPagerAdapter(@NonNull FragmentManager fm, @NonNull Lifecycle lifecycle) {
        super(fm, lifecycle);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case TAB_FINDING:  return new FindingFragment();
            case TAB_PROPOSAL: return new ProposalFragment();
            case TAB_SESSION:  return new SessionFragment();
            default: throw new IllegalStateException("Unknown tab position: " + position);
        }
    }

    @Override
    public int getItemCount() {
        return TAB_COUNT;
    }
}
