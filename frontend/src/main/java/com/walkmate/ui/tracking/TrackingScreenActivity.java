package com.walkmate.ui.tracking;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.walkmate.frontend.R;

import java.util.List;

public class TrackingScreenActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private Polyline currentPolyline;
    private boolean isFirstLocationRendered = false;
    private TrackingViewModel viewModel;
    
    private TextView tvDistance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tracking_screen);

        tvDistance = findViewById(R.id.tv_distance);
        FloatingActionButton fabCenterCamera = findViewById(R.id.fab_center_camera);

        viewModel = new ViewModelProvider(this).get(TrackingViewModel.class);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // --- CẮM CỨNG SESSION ID TẠM THỜI ĐỂ TEST KIẾN TRÚC VẼ MAP ---
        // Khi app thực tế chạy, ID này sẽ được truyền qua Intent giống Service
        // Ví dụ: viewModel.startTrackingSession(getIntent().getStringExtra("SESSION_ID"));
        String mockSessionId = "test-session-123";
        viewModel.startTrackingSession(mockSessionId);
        
        // Tự động bật luôn luồng thu thập GPS ngầm (Service) với đúng SessionID này
        android.content.Intent serviceIntent = new android.content.Intent(this, com.walkmate.core.service.WalkTrackerService.class);
        serviceIntent.putExtra("SESSION_ID", mockSessionId);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        fabCenterCamera.setOnClickListener(v -> viewModel.setCameraFollow(true));
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setCompassEnabled(false);

        // 💥 Bật dấu chấm xanh hiển thị vị trí theo thời gian thực của thuật toán Google
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
            mMap.getUiSettings().setMyLocationButtonEnabled(false); // Ẩn nút mặc định vì mình đã làm cái FAB nổi đẹp hơn
        }

        // 1. Lắng nghe user vuốt bản đồ để TẮT cờ follow
        mMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                viewModel.setCameraFollow(false);
            }
        });

        // 2. Lắng nghe State để vẽ đường và dời Camera
        viewModel.getUiState().observe(this, state -> {
            if (state != null && state.getPathPoints() != null && !state.getPathPoints().isEmpty()) {
                List<LatLng> points = state.getPathPoints();
                drawPolyline(points);
                
                Boolean isFollowing = viewModel.getCameraFollowState().getValue();
                if (Boolean.TRUE.equals(isFollowing) || !isFirstLocationRendered) {
                    LatLng latestPoint = points.get(points.size() - 1);
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latestPoint, 17.5f));
                    isFirstLocationRendered = true; 
                }

                tvDistance.setText(String.format("%.2f km", state.getTotalDistanceMeters() / 1000f));
            }
        });
    }

    private void drawPolyline(List<LatLng> points) {
        if (currentPolyline == null) {
            PolylineOptions polylineOptions = new PolylineOptions()
                    .color(Color.parseColor("#4285F4")) 
                    .width(16f)                         
                    .geodesic(true)                     
                    .addAll(points);
            currentPolyline = mMap.addPolyline(polylineOptions);
        } else {
            currentPolyline.setPoints(points);
        }
    }
}
