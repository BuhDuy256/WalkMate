package com.walkmate.ui.social.friends;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class FriendsPagerAdapter extends FragmentStateAdapter {

    public static final int TAB_FRIENDS   = 0;
    public static final int TAB_INCOMING  = 1;
    public static final int TAB_OUTGOING  = 2;
    private static final int TAB_COUNT    = 3;

    public FriendsPagerAdapter(@NonNull FragmentManager fm, @NonNull Lifecycle lifecycle) {
        super(fm, lifecycle);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case TAB_FRIENDS:  return new FriendListFragment();
            case TAB_INCOMING: return new IncomingRequestsFragment();
            case TAB_OUTGOING: return new OutgoingRequestsFragment();
            default: throw new IllegalStateException("Unknown tab position: " + position);
        }
    }

    @Override
    public int getItemCount() {
        return TAB_COUNT;
    }
}
