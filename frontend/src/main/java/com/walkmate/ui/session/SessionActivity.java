package com.walkmate.ui.session;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.walkmate.R;
import com.walkmate.data.local.db.WalkSessionDatabase;
import com.walkmate.data.local.entity.SessionPointLocalEntity;
import com.walkmate.data.remote.ApiClient;
import com.walkmate.data.remote.SessionApi;
import com.walkmate.data.repository.SessionRepository;
import com.walkmate.data.repository.SessionRepositoryImpl;
import com.walkmate.data.worker.SessionPointSyncWorker;
import com.walkmate.tracking.LocationTrackingService;
import com.walkmate.tracking.TrackingCommand;
import com.walkmate.tracking.TrackingServiceContract;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String DEMO_SESSION_ID = "00000000-0000-0000-0000-000000000001";

    private SessionViewModel viewModel;
    private SessionRepository repository;

    private GoogleMap googleMap;
    private Polyline routePolyline;

    private Button btnStart;
    private Button btnPause;
    private Button btnResume;
    private Button btnEnd;
    private Button btnCancel;
    private Button btnAbort;
    private TextView tvStatus;
    private TextView tvDistance;
    private TextView tvDuration;

    private long handledCommandVersion = -1L;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fine = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                if (Boolean.TRUE.equals(fine)) {
                    viewModel.activate();
                } else {
                    Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btnStart);
        btnPause = findViewById(R.id.btnPause);
        btnResume = findViewById(R.id.btnResume);
        btnEnd = findViewById(R.id.btnEnd);
        btnCancel = findViewById(R.id.btnCancel);
        btnAbort = findViewById(R.id.btnAbort);
        tvStatus = findViewById(R.id.tvStatus);
        tvDistance = findViewById(R.id.tvDistance);
        tvDuration = findViewById(R.id.tvDuration);

        ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
        WalkSessionDatabase db = WalkSessionDatabase.getInstance(this);
        SessionApi api = ApiClient.sessionApi();
        repository = new SessionRepositoryImpl(api, db.sessionLocalDao(), db.sessionPointLocalDao(), ioExecutor);

        viewModel = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                return (T) new SessionViewModel(repository);
            }
        }).get(SessionViewModel.class);

        viewModel.bindSession(DEMO_SESSION_ID);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        repository.observePoints(DEMO_SESSION_ID).observe(this, points -> {
            renderPolyline(points);
            viewModel.onPointsUpdated(points);
        });

        btnStart.setOnClickListener(v -> {
            if (ensureLocationPermission()) {
                viewModel.activate();
            }
        });
        btnPause.setOnClickListener(v -> viewModel.pause());
        btnResume.setOnClickListener(v -> viewModel.resume());
        btnEnd.setOnClickListener(v -> viewModel.complete());
        btnCancel.setOnClickListener(v -> viewModel.cancel("User cancelled"));
        btnAbort.setOnClickListener(v -> viewModel.abort("Emergency"));

        viewModel.getUiState().observe(this, this::render);
    }

    private boolean ensureLocationPermission() {
        boolean fine = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!fine) {
            permissionLauncher.launch(new String[] { Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION });
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            boolean fg = ContextCompat.checkSelfPermission(this,
                    Manifest.permission.FOREGROUND_SERVICE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (!fg) {
                permissionLauncher.launch(new String[] { Manifest.permission.FOREGROUND_SERVICE_LOCATION });
                return false;
            }
        }
        return true;
    }

    private void render(SessionUiState state) {
        tvStatus.setText(state.status.name());
        tvDistance.setText(getString(R.string.distance_format, state.distanceMeters));
        tvDuration.setText(getString(R.string.duration_format, state.durationSeconds));

        btnStart.setVisibility(state.status == SessionScreenStatus.IDLE ? View.VISIBLE : View.GONE);
        btnCancel.setVisibility(state.status == SessionScreenStatus.IDLE ? View.VISIBLE : View.GONE);

        btnPause.setVisibility(state.status == SessionScreenStatus.TRACKING_ACTIVE ? View.VISIBLE : View.GONE);
        btnResume.setVisibility(state.status == SessionScreenStatus.TRACKING_PAUSED ? View.VISIBLE : View.GONE);
        btnEnd.setVisibility((state.status == SessionScreenStatus.TRACKING_ACTIVE
                || state.status == SessionScreenStatus.TRACKING_PAUSED) ? View.VISIBLE : View.GONE);
        btnAbort.setVisibility((state.status == SessionScreenStatus.TRACKING_ACTIVE
                || state.status == SessionScreenStatus.TRACKING_PAUSED) ? View.VISIBLE : View.GONE);

        if (state.errorMessage != null) {
            Toast.makeText(this, state.errorMessage, Toast.LENGTH_SHORT).show();
        }

        executeCommand(state);
    }

    private void executeCommand(SessionUiState state) {
        if (handledCommandVersion == state.commandVersion) {
            return;
        }
        handledCommandVersion = state.commandVersion;

        Intent intent = new Intent(this, LocationTrackingService.class);
        TrackingCommand command = state.pendingCommand;

        if (command == TrackingCommand.START) {
            intent.setAction(TrackingServiceContract.ACTION_START);
            intent.putExtra(TrackingServiceContract.EXTRA_SESSION_ID, DEMO_SESSION_ID);
            intent.putExtra(TrackingServiceContract.EXTRA_NEXT_ORDER, viewModel.getNextPointOrder());
            ContextCompat.startForegroundService(this, intent);
        } else if (command == TrackingCommand.PAUSE) {
            intent.setAction(TrackingServiceContract.ACTION_PAUSE);
            startService(intent);
        } else if (command == TrackingCommand.RESUME) {
            intent.setAction(TrackingServiceContract.ACTION_RESUME);
            startService(intent);
        } else if (command == TrackingCommand.STOP) {
            intent.setAction(TrackingServiceContract.ACTION_STOP);
            startService(intent);
        }

        if (state.status == SessionScreenStatus.TRACKING_ACTIVE
                || state.status == SessionScreenStatus.TRACKING_PAUSED) {
            SessionPointSyncWorker.enqueueNow(this, DEMO_SESSION_ID);
        }
    }

    private void renderPolyline(List<SessionPointLocalEntity> points) {
        if (googleMap == null || points == null || points.isEmpty()) {
            return;
        }

        List<LatLng> latLngs = new ArrayList<>();
        for (SessionPointLocalEntity point : points) {
            latLngs.add(new LatLng(point.lat, point.lng));
        }

        if (routePolyline == null) {
            routePolyline = googleMap.addPolyline(new PolylineOptions()
                    .width(10f)
                    .color(0xFF34C759)
                    .addAll(latLngs));
        } else {
            routePolyline.setPoints(latLngs);
        }

        LatLng last = latLngs.get(latLngs.size() - 1);
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(last, 17f));
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
    }
}
