package com.walkmate.ui.qr.show;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.designsystem.view.AvatarInitialView;

public class ShowQrFragment extends Fragment {

    private static final String ARG_SESSION_ID   = "session_id";
    private static final String ARG_PARTNER_NAME = "partner_name";

    private static final int QR_SIZE_PX = 540; // render at 3× density for crispness

    private ShowQrViewModel viewModel;

    public static ShowQrFragment newInstance(String sessionId, String partnerName) {
        ShowQrFragment f = new ShowQrFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID,   sessionId);
        args.putString(ARG_PARTNER_NAME, partnerName);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_show_qr, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String sessionId   = requireArguments().getString(ARG_SESSION_ID);
        String partnerName = requireArguments().getString(ARG_PARTNER_NAME);

        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        ShowQrViewModelFactory factory =
                new ShowQrViewModelFactory(app.getWalkSessionRepository());
        viewModel = new ViewModelProvider(this, factory).get(ShowQrViewModel.class);

        ProgressBar    progressBar   = view.findViewById(R.id.progressBar);
        ImageView      imgQrCode     = view.findViewById(R.id.imgQrCode);
        View           cardQr        = view.findViewById(R.id.cardQr);
        LinearLayout   layoutTip     = view.findViewById(R.id.layoutTip);
        TextView       txtInstruction= view.findViewById(R.id.txtInstruction);
        TextView       txtError      = view.findViewById(R.id.txtError);
        AvatarInitialView avatarSelf = view.findViewById(R.id.avatarSelf);
        TextView       txtSelfName   = view.findViewById(R.id.txtSelfName);
        TextView       txtSessionLabel = view.findViewById(R.id.txtSessionLabel);

        // Bind self info — no cached display name; use "You" as a placeholder
        avatarSelf.bind("You", null);
        txtSelfName.setText("You");
        txtSessionLabel.setText(getString(R.string.qr_partner_label) + " · " + formatSessionId(sessionId));

        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            progressBar.setVisibility(state.isLoading() ? View.VISIBLE : View.GONE);

            if (state.isLoading()) {
                cardQr.setVisibility(View.GONE);
                layoutTip.setVisibility(View.GONE);
                txtInstruction.setVisibility(View.GONE);
                txtError.setVisibility(View.GONE);
                return;
            }

            if (state.getQrToken() != null) {
                Bitmap qrBitmap = generateQrBitmap(state.getQrToken());
                if (qrBitmap != null) {
                    imgQrCode.setImageBitmap(qrBitmap);
                }
                cardQr.setVisibility(View.VISIBLE);
                layoutTip.setVisibility(View.VISIBLE);
                txtInstruction.setVisibility(View.VISIBLE);
                txtError.setVisibility(View.GONE);
            } else if (state.getError() != null) {
                cardQr.setVisibility(View.GONE);
                layoutTip.setVisibility(View.GONE);
                txtInstruction.setVisibility(View.GONE);
                txtError.setVisibility(View.VISIBLE);
                txtError.setText(state.getError());
            }
        });

        // Tap error text to retry
        view.findViewById(R.id.txtError).setOnClickListener(v ->
                viewModel.loadQrToken(sessionId));

        viewModel.loadQrToken(sessionId);
    }

    @Nullable
    private Bitmap generateQrBitmap(String content) {
        try {
            BitMatrix matrix = new MultiFormatWriter()
                    .encode(content, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX);
            int w = matrix.getWidth();
            int h = matrix.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    pixels[y * w + x] = matrix.get(x, y) ? 0xFF1C1917 : 0xFFFFFFFF;
                }
            }
            Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bm.setPixels(pixels, 0, w, 0, 0, w, h);
            return bm;
        } catch (Exception e) {
            return null;
        }
    }

    private String formatSessionId(String rawUuid) {
        String suffix = rawUuid.length() >= 4
                ? rawUuid.substring(rawUuid.length() - 4).toUpperCase()
                : rawUuid.toUpperCase();
        return "WM-" + suffix;
    }
}
