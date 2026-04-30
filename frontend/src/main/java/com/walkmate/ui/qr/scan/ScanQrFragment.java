package com.walkmate.ui.qr.scan;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.android.material.button.MaterialButton;
import com.walkmate.R;
import com.walkmate.WalkMateApplication;
import com.walkmate.core.designsystem.view.AvatarInitialView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScanQrFragment extends Fragment {

    private static final String ARG_SESSION_ID    = "session_id";
    private static final String ARG_PARTNER_NAME  = "partner_name";
    private static final String ARG_PARTNER_AVATAR= "partner_avatar";
    private static final String ARG_HOTSPOT_NAME  = "hotspot_name";
    private static final int    REQUEST_CAMERA     = 200;

    private ScanQrViewModel viewModel;

    private PreviewView    previewView;
    private MaterialButton btnStartScan;
    private LinearLayout   layoutIdlePlaceholder;
    private View           scanLine;
    private TextView       txtScanInstruction;
    private LinearLayout   layoutScanner;
    private LinearLayout   layoutVerified;
    private LinearLayout   layoutError;
    private TextView       txtVerifiedAt;
    private AvatarInitialView avatarVerifiedPartner;
    private TextView       txtVerifiedPartnerName;
    private TextView       txtVerifiedLocation;
    private MaterialButton btnContinueWalk;
    private MaterialButton btnRetry;
    private Button         btnScanAgain;
    private TextView       txtErrorMessage;
    private AvatarInitialView avatarPartnerChip;
    private TextView       txtPartnerChipName;

    private ProcessCameraProvider     cameraProvider;
    private ExecutorService           cameraExecutor;
    private BarcodeScanner            barcodeScanner;
    private final AtomicBoolean       qrDetected = new AtomicBoolean(false);

    private ObjectAnimator scanLineAnimator;

    public static ScanQrFragment newInstance(String sessionId, String partnerName,
                                             String partnerAvatar, String hotspotName) {
        ScanQrFragment f = new ScanQrFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_ID,     sessionId);
        args.putString(ARG_PARTNER_NAME,   partnerName);
        args.putString(ARG_PARTNER_AVATAR, partnerAvatar);
        args.putString(ARG_HOTSPOT_NAME,   hotspotName);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_scan_qr, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args        = requireArguments();
        String sessionId   = args.getString(ARG_SESSION_ID);
        String partnerName = args.getString(ARG_PARTNER_NAME);
        String partnerAvatar = args.getString(ARG_PARTNER_AVATAR);
        String hotspotName = args.getString(ARG_HOTSPOT_NAME);

        WalkMateApplication app = (WalkMateApplication) requireActivity().getApplication();
        ScanQrViewModelFactory factory =
                new ScanQrViewModelFactory(app.getWalkSessionRepository());
        viewModel = new ViewModelProvider(this, factory).get(ScanQrViewModel.class);
        viewModel.init(sessionId);

        bindViews(view);

        // Partner chip
        if (partnerName != null) {
            avatarPartnerChip.bind(partnerName, partnerAvatar);
            txtPartnerChipName.setText(partnerName);
        }

        // Verified state partner info
        if (partnerName != null) {
            avatarVerifiedPartner.bind(partnerName, partnerAvatar);
            txtVerifiedPartnerName.setText(partnerName);
        }
        txtVerifiedLocation.setText(hotspotName != null
                ? getString(R.string.qr_scan_verified_location) + " · " + hotspotName
                : getString(R.string.qr_scan_verified_location));

        cameraExecutor = Executors.newSingleThreadExecutor();
        barcodeScanner = BarcodeScanning.getClient(
                new BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build());

        // Observe state
        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);

        // Buttons
        btnStartScan.setOnClickListener(v -> onStartScanTapped());
        btnContinueWalk.setOnClickListener(v -> {
            viewModel.consumeVerification();
            requireActivity().finish();
        });
        btnRetry.setOnClickListener(v -> viewModel.retryVerification());
        btnScanAgain.setOnClickListener(v -> {
            stopCamera();
            viewModel.resetToIdle();
        });
    }

    private void bindViews(View view) {
        previewView           = view.findViewById(R.id.previewView);
        btnStartScan          = view.findViewById(R.id.btnStartScan);
        layoutIdlePlaceholder = view.findViewById(R.id.layoutIdlePlaceholder);
        scanLine              = view.findViewById(R.id.scanLine);
        txtScanInstruction    = view.findViewById(R.id.txtScanInstruction);
        layoutScanner         = view.findViewById(R.id.layoutScanner);
        layoutVerified        = view.findViewById(R.id.layoutVerified);
        layoutError           = view.findViewById(R.id.layoutError);
        txtVerifiedAt         = view.findViewById(R.id.txtVerifiedAt);
        avatarVerifiedPartner = view.findViewById(R.id.avatarVerifiedPartner);
        txtVerifiedPartnerName= view.findViewById(R.id.txtVerifiedPartnerName);
        txtVerifiedLocation   = view.findViewById(R.id.txtVerifiedLocation);
        btnContinueWalk       = view.findViewById(R.id.btnContinueWalk);
        btnRetry              = view.findViewById(R.id.btnRetry);
        btnScanAgain          = view.findViewById(R.id.btnScanAgain);
        txtErrorMessage       = view.findViewById(R.id.txtErrorMessage);
        avatarPartnerChip     = view.findViewById(R.id.avatarPartnerChip);
        txtPartnerChipName    = view.findViewById(R.id.txtPartnerChipName);
    }

    // ── State rendering ───────────────────────────────────────────────────────

    private void renderState(ScanQrUiState state) {
        switch (state.getPhase()) {
            case IDLE:
                stopScanLineAnimation();
                stopCamera();
                showGroup(ScanQrUiState.Phase.IDLE);
                txtScanInstruction.setText(R.string.qr_scan_instruction_idle);
                btnStartScan.setText(R.string.qr_btn_start_scan);
                btnStartScan.setEnabled(true);
                btnStartScan.setAlpha(1f);
                layoutIdlePlaceholder.setVisibility(View.VISIBLE);
                previewView.setVisibility(View.INVISIBLE);
                scanLine.setVisibility(View.GONE);
                break;

            case SCANNING:
                showGroup(ScanQrUiState.Phase.SCANNING);
                txtScanInstruction.setText(R.string.qr_scan_detecting);
                btnStartScan.setText(R.string.qr_scan_detecting);
                btnStartScan.setEnabled(false);
                btnStartScan.setAlpha(0.7f);
                layoutIdlePlaceholder.setVisibility(View.GONE);
                previewView.setVisibility(View.VISIBLE);
                scanLine.setVisibility(View.VISIBLE);
                startScanLineAnimation();
                break;

            case VERIFIED:
                stopScanLineAnimation();
                stopCamera();
                showGroup(ScanQrUiState.Phase.VERIFIED);
                txtVerifiedAt.setText(getString(R.string.qr_verified_subtitle, state.getVerifiedAt()));
                break;

            case ERROR:
                stopScanLineAnimation();
                stopCamera();
                showGroup(ScanQrUiState.Phase.ERROR);
                txtErrorMessage.setText(state.getError());
                break;
        }
    }

    private void showGroup(ScanQrUiState.Phase phase) {
        layoutScanner.setVisibility(
                (phase == ScanQrUiState.Phase.IDLE || phase == ScanQrUiState.Phase.SCANNING)
                        ? View.VISIBLE : View.GONE);
        layoutVerified.setVisibility(phase == ScanQrUiState.Phase.VERIFIED ? View.VISIBLE : View.GONE);
        layoutError.setVisibility(phase == ScanQrUiState.Phase.ERROR ? View.VISIBLE : View.GONE);
    }

    // ── Camera permission + launch ────────────────────────────────────────────

    private void onStartScanTapped() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            Toast.makeText(requireContext(),
                    "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show();
        }
    }

    // ── CameraX ───────────────────────────────────────────────────────────────

    private void startCamera() {
        qrDetected.set(false);
        viewModel.getUiState().getValue(); // ensure SCANNING state propagates first

        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(requireContext());

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
            } catch (Exception e) {
                Toast.makeText(requireContext(),
                        "Camera failed to start", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));

        // Transition to SCANNING state (shows scan line, updates button text)
        // We call the ViewModel with a placeholder — the real token comes from ML Kit.
        // Use the existing pendingToken mechanism via startScan-like signal:
        // Actually we signal SCANNING by calling onQrDetected only after we have a real value.
        // For now, manually post SCANNING to update UI:
        postScanningState();
    }

    private void postScanningState() {
        // We need to push the UI to SCANNING before we have a token.
        // Signal ViewModel that we're in scanning mode with an empty token (will be
        // replaced by actual token when ML Kit fires).
        // We do this by calling a no-token scanning signal.
        viewModel.startScanning();
    }

    private void bindCameraUseCases() {
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (qrDetected.get()) {
                imageProxy.close();
                return;
            }
            @SuppressWarnings("UnsafeOptInUsageError")
            InputImage inputImage = InputImage.fromMediaImage(
                    imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

            barcodeScanner.process(inputImage)
                    .addOnSuccessListener(barcodes -> {
                        for (Barcode barcode : barcodes) {
                            String raw = barcode.getRawValue();
                            if (raw != null && !raw.isEmpty() && qrDetected.compareAndSet(false, true)) {
                                viewModel.onQrDetected(raw);
                                break;
                            }
                        }
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        });

        cameraProvider.bindToLifecycle(
                getViewLifecycleOwner(),
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis);
    }

    private void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    // ── Scan line animation ───────────────────────────────────────────────────

    private void startScanLineAnimation() {
        if (scanLineAnimator != null && scanLineAnimator.isRunning()) return;
        scanLine.post(() -> {
            int parentHeight = ((View) scanLine.getParent()).getHeight();
            scanLineAnimator = ObjectAnimator.ofFloat(
                    scanLine, "translationY", 0f, parentHeight - scanLine.getHeight());
            scanLineAnimator.setDuration(1800);
            scanLineAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            scanLineAnimator.setRepeatMode(ObjectAnimator.REVERSE);
            scanLineAnimator.start();
        });
    }

    private void stopScanLineAnimation() {
        if (scanLineAnimator != null) {
            scanLineAnimator.cancel();
            scanLineAnimator = null;
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopScanLineAnimation();
        stopCamera();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
            barcodeScanner = null;
        }
    }
}
