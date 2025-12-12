package com.example.duowalk.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.duowalk.R;
import com.example.duowalk.utils.PermissionsUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedClient;

    // True only when we sent the user to Settings because permission was permanently denied
    private boolean returnedFromSettings = false;

    // To know we can safely interact with the map in onResume()
    private boolean mapReady = false;

    // Default location (Tel Aviv) so you see a real map even before location permission
    private static final LatLng DEFAULT_LOCATION = new LatLng(32.0853, 34.7818);
    private static final float DEFAULT_ZOOM = 12f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // navigation listeners
        findViewById(R.id.btn_steps).setOnClickListener(v ->
                startActivity(new Intent(this, StepsActivity.class)));

        findViewById(R.id.btn_leaderboard).setOnClickListener(v ->
                startActivity(new Intent(this, LeaderboardActivity.class)));

        findViewById(R.id.btn_friends).setOnClickListener(v ->
                startActivity(new Intent(this, FriendsActivity.class)));

        findViewById(R.id.btn_profile).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        mapReady = true;

        // Always show some map immediately (even before location permission)
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM));

        // If permission is missing -> request
        if (!PermissionsUtils.hasPermissions(this, PermissionsUtils.locationForegroundPermissions())) {
            requestLocationPermission();
            return;
        }

        // Permission already granted
        enableMyLocationAndCenter();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If we returned from Settings, re-check and act
        if (returnedFromSettings) {
            returnedFromSettings = false;

            if (PermissionsUtils.hasPermissions(this, PermissionsUtils.locationForegroundPermissions())) {
                enableMyLocationAndCenter();
            } else {
                Toast.makeText(this, "Location permission is required to use the map.", Toast.LENGTH_LONG).show();
                requestLocationPermission();
            }
        } else {
            // Also handle the normal case: map is ready and user granted permission outside the flow
            if (mapReady && googleMap != null &&
                    PermissionsUtils.hasPermissions(this, PermissionsUtils.locationForegroundPermissions())) {
                enableMyLocationAndCenter();
            }
        }
    }

    private void requestLocationPermission() {
        PermissionsUtils.requestMissingPermissions(
                this,
                PermissionsUtils.REQ_LOCATION_FOREGROUND,
                PermissionsUtils.locationForegroundPermissions()
        );
    }

    private void enableMyLocationAndCenter() {
        if (googleMap == null) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        googleMap.setMyLocationEnabled(true);

        fusedClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) {
                // If no last known location, at least keep the default map view visible
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM));
                return;
            }
            LatLng me = new LatLng(location.getLatitude(), location.getLongitude());
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(me, 16f));
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionsUtils.REQ_LOCATION_FOREGROUND) {

            if (PermissionsUtils.allGranted(grantResults)) {
                enableMyLocationAndCenter();
                return;
            }

            // Denied
            boolean canAskAgain = PermissionsUtils.shouldShowRationale(
                    this,
                    PermissionsUtils.locationForegroundPermissions()
            );

            if (canAskAgain) {
                Toast.makeText(this, "Location permission is required to use the map.", Toast.LENGTH_SHORT).show();
                requestLocationPermission(); // ask again
            } else {
                Toast.makeText(this, "Location permission is required. Please enable it in Settings.", Toast.LENGTH_LONG).show();
                returnedFromSettings = true;
                PermissionsUtils.openAppSettings(this);
            }
        }
    }
}
