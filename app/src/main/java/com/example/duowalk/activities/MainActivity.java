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
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;

import java.util.Arrays;

/**
 * Main map screen:
 * - Shows Google Map.
 * - Lets the user search for a place (Places Autocomplete) and focuses the map on it.
 * - shows the user's current location (blue dot).
 *
 * Key behavior:
 * - We never call googleMap.clear() when searching, because it removes *everything* (including the blue dot).
 * - We keep a reference to the last search marker and remove only that marker when searching again.
 * - If user has already searched a place, we do NOT auto-recenter the camera back to the user's location.
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    /** GoogleMap instance returned asynchronously in onMapReady(). */
    private GoogleMap googleMap;

    /** Fused location provider to fetch last known device location. */
    private FusedLocationProviderClient fusedClient;

    /**
     * True only when we send the user to system Settings because permission
     * was permanently denied ("Don't ask again").
     *
     * On returning from Settings (onResume), we re-check permission and act.
     */
    private boolean returnedFromSettings = false;

    /**
     * True after onMapReady() ran at least once, meaning googleMap is safe to use.
     * Helps avoid calling map APIs too early.
     */
    private boolean mapReady = false;

    /**
     * Default map position (Tel Aviv) so the user sees a real map immediately
     * even before we have location permission / location data.
     */
    private static final LatLng DEFAULT_LOCATION = new LatLng(32.0853, 34.7818);
    private static final float DEFAULT_ZOOM = 12f;

    /**
     * We keep only the last "searched place" marker, so that each new search replaces it.
     * This avoids googleMap.clear(), which would remove the location layer and any future overlays.
     */
    private Marker searchMarker;

    /**
     * If the user explicitly searched for a place, we should not "steal the camera"
     * by automatically re-centering on the user's last known location afterward.
     */
    private boolean userPickedPlace = false;

    /**
     * If the user picks a place from the autocomplete UI before the map is ready,
     * we store the selection here and apply it once onMapReady() is called.
     */
    private LatLng pendingSearchLatLng = null;
    private String pendingSearchName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Places SDK (uses your Maps API key)
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        }

        // Setup location provider (for last known location)
        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        // Setup Places autocomplete search UI
        setupAutocomplete();

        // Setup the map fragment asynchronously
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Navigation button listeners
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

        /**
         * Configures the Places Autocomplete fragment:
         * - Requests the place fields we need (name + lat/lng).
         * - When the user chooses a place, focus the map on it and add a marker.
         *
         * Important:
         * - Place selection can happen before onMapReady(), so we handle the pending case.
         */
        private void setupAutocomplete() {
            AutocompleteSupportFragment autocompleteFragment =
                    (AutocompleteSupportFragment) getSupportFragmentManager()
                            .findFragmentById(R.id.autocomplete_fragment);

            if (autocompleteFragment == null) return;

            // Fields we need from the selected place
            autocompleteFragment.setPlaceFields(Arrays.asList(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG
            ));

            autocompleteFragment.setHint("Search places...");

            autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
                @Override
                public void onPlaceSelected(@NonNull Place place) {
                    LatLng latLng = place.getLatLng();
                    if (latLng == null) return;

                    // User action: from now on do not auto-center to user's location
                    userPickedPlace = true;

                    // If map isn't ready yet, store the selection and apply in onMapReady()
                    if (googleMap == null) {
                        pendingSearchLatLng = latLng;
                        pendingSearchName = place.getName();
                        return;
                    }

                    // Map is ready -> focus now
                    moveMapToPlace(latLng, place.getName());
                }

                @Override
                public void onError(@NonNull Status status) {
                    String message = status.getStatusMessage();

                    if (status.isCanceled()) {
                        // User dismissed or cleared the autocomplete – not an actual error
                        android.util.Log.d("PlacesAutocomplete", "Autocomplete canceled by user");
                        return;
                    }
                    android.util.Log.e(
                            "PlacesAutocomplete",
                            "Place search error: " + (message != null ? message : "unknown error")
                    );
                }

            });
        }

    /**
     * Called when the Google Map is ready to be used.
     * - Sets an initial camera position (DEFAULT_LOCATION).
     * - Applies any pending search that happened before the map loaded.
     * - Requests location permission (or enables location if already granted).
     */
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        mapReady = true;

        // Shows map immediately
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM));

        // If user searched before map was ready, apply it now
        if (pendingSearchLatLng != null) {
            moveMapToPlace(pendingSearchLatLng, pendingSearchName);
            pendingSearchLatLng = null;
            pendingSearchName = null;
        }

        // If permission is missing -> request it
        if (!PermissionsUtils.hasPermissions(this, PermissionsUtils.locationForegroundPermissions())) {
            requestLocationPermission();
            return;
        }

        // Permission already granted -> enable blue dot and maybe center
        enableMyLocationAndMaybeCenter();
    }

    /**
     * When returning to this activity:
     * - If user went to Settings because they permanently denied permission, re-check it now.
     * - Also handle the normal case where permission might have been granted outside the flow.
     */
    @Override
    protected void onResume() {
        super.onResume();

        // Returned from Settings flow: re-check permission and act
        if (returnedFromSettings) {
            returnedFromSettings = false;

            if (PermissionsUtils.hasPermissions(this, PermissionsUtils.locationForegroundPermissions())) {
                enableMyLocationAndMaybeCenter();
            } else {
                Toast.makeText(this, "Location permission is required to use the map.", Toast.LENGTH_LONG).show();
                requestLocationPermission();
            }
            return;
        }

        // Normal resume: if map is ready and permission is granted, ensure location layer is enabled
        if (mapReady && googleMap != null &&
                PermissionsUtils.hasPermissions(this, PermissionsUtils.locationForegroundPermissions())) {
            enableMyLocationAndMaybeCenter();
        }
    }

    /**
     * Requests foreground location permissions from the user.
     * (Implementation is delegated to your PermissionsUtils helper.)
     */
    private void requestLocationPermission() {
        PermissionsUtils.requestMissingPermissions(
                this,
                PermissionsUtils.REQ_LOCATION_FOREGROUND,
                PermissionsUtils.locationForegroundPermissions()
        );
    }

    /**
     * Enables the My Location layer (blue dot).
     *
     * Camera behavior:
     * - If the user has already searched a place, do NOT auto-center on the user's location.
     * - Otherwise, animate to the last known location if available.
     */
    private void enableMyLocationAndMaybeCenter() {
        if (googleMap == null) return;

        // Double-check runtime permission before enabling My Location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Show the blue "My Location" dot
        googleMap.setMyLocationEnabled(true);

        // If the user already chose a place, do not override their camera position
        if (userPickedPlace) return;

        fusedClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) {
                // No last known location: keep default view
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM));
                return;
            }

            // User might have searched while we were waiting for the async callback
            if (userPickedPlace) return;

            // Center camera on user's last known location
            LatLng me = new LatLng(location.getLatitude(), location.getLongitude());
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(me, DEFAULT_ZOOM));
        });
    }

    /**
     * Focuses the map on the given place and shows a marker.
     *
     * Important:
     * - We remove only the previous search marker instead of calling googleMap.clear().
     * - googleMap.clear() would remove everything (blue dot, overlays, polylines, etc.).
     */
    private void moveMapToPlace(@NonNull LatLng latLng, String name) {
        if (googleMap == null) return;

        // Replace the previous search marker (if any)
        if (searchMarker != null) {
            searchMarker.remove();
            searchMarker = null;
        }

        // Add the new marker
        searchMarker = googleMap.addMarker(new MarkerOptions()
                .position(latLng)
                .title(name != null ? name : "Selected place"));

        // Focus camera immediately on the selected place
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM));

        //Shows the info bubble so the user immediately sees what was selected (the location name)
        if (searchMarker != null) {
            searchMarker.showInfoWindow();
        }
    }

    /**
     * Receives the user's permission decision for the foreground location request.
     *
     * Cases:
     * - Granted -> enable location layer.
     * - Denied but can ask again -> show short message and request again.
     * - Permanently denied -> send the user to Settings.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionsUtils.REQ_LOCATION_FOREGROUND) {

            // Permission granted.
            if (PermissionsUtils.allGranted(grantResults)) {
                enableMyLocationAndMaybeCenter();
                return;
            }

            // Permission denied: check if we can ask again (rationale shown) or it's permanent denial.
            boolean canAskAgain = PermissionsUtils.shouldShowRationale(
                    this,
                    PermissionsUtils.locationForegroundPermissions()
            );

            if (canAskAgain) {
                Toast.makeText(this, "Location permission is required to use the map.", Toast.LENGTH_SHORT).show();
                requestLocationPermission();
            } else {
                Toast.makeText(this, "Location permission is required. Please enable it in Settings.", Toast.LENGTH_LONG).show();
                returnedFromSettings = true;
                PermissionsUtils.openAppSettings(this);
            }
        }
    }
}
