package com.walkmate.ui.qr;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.walkmate.ui.qr.scan.ScanQrFragment;
import com.walkmate.ui.qr.show.ShowQrFragment;

public class QrVerifyPagerAdapter extends FragmentStateAdapter {

    private final String sessionId;
    private final String partnerName;
    private final String partnerAvatar;
    private final String hotspotName;

    public QrVerifyPagerAdapter(@NonNull FragmentActivity activity,
                                String sessionId, String partnerName,
                                String partnerAvatar, String hotspotName) {
        super(activity);
        this.sessionId    = sessionId;
        this.partnerName  = partnerName;
        this.partnerAvatar= partnerAvatar;
        this.hotspotName  = hotspotName;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            return ShowQrFragment.newInstance(sessionId, partnerName);
        }
        return ScanQrFragment.newInstance(sessionId, partnerName, partnerAvatar, hotspotName);
    }

    @Override
    public int getItemCount() { return 2; }
}
