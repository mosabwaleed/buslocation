package info.androidhive.androidlocation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;
import java.util.ArrayList;
import java.util.Calendar;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = MainActivity.class.getSimpleName();
    @BindView(R.id.btn_start_location_updates)
    Button btnStartUpdates;
    // for test
    private GoogleMap mMap;
    private Handler handler = new Handler();
    double lat;
    double lng;
    EditText ebusnumber,eroundnumber;
    Spinner city;
    Button stop;
    ArrayList<String> busnodb = new ArrayList<>() ;
    FirebaseDatabase firebaseDatabase;
    FirebaseFirestore firestore;
    String citystr , days , message = "";

    public double getLat() {
        return lat;
    }
    public void setLat(double lat) {
        this.lat = lat;
    }
    public double getLng() {
        return lng;
    }
    public void setLng(double lng) {
        this.lng = lng;
    }
    // location last updated time
    private String mLastUpdateTime;
    // location updates interval - 10sec
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    // fastest updates interval - 5 sec
    // location updates will be received if another app is requesting the locations
    // than your app can handle
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = 500;
    private static final int REQUEST_CHECK_SETTINGS = 100;
    // bunch of location related apis
    private FusedLocationProviderClient mFusedLocationClient;
    private SettingsClient mSettingsClient;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
    private LocationCallback mLocationCallback;
    private Location mCurrentLocation;
    // boolean flag to toggle the ui
    private Boolean mRequestingLocationUpdates;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
//        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        ebusnumber = findViewById(R.id.busnumber);
        eroundnumber = findViewById(R.id.roundnumber);
        city = findViewById(R.id.city);
        stop = findViewById(R.id.stop);
        firebaseDatabase = FirebaseDatabase.getInstance();
        firestore = FirebaseFirestore.getInstance();
        // initialize the necessary libraries
        init();
        // restore the values from saved instance state
        restoreValuesFromBundle(savedInstanceState);
    }
    private void init() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                // location is received
                mCurrentLocation = locationResult.getLastLocation();
                //mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());

                updateLocationUI();
            }
        };

        mRequestingLocationUpdates = false;

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
    }
    /**
     * Restoring values from saved instance state
     */
    private void restoreValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("is_requesting_updates")) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean("is_requesting_updates");
            }

            if (savedInstanceState.containsKey("last_known_location")) {
                mCurrentLocation = savedInstanceState.getParcelable("last_known_location");
            }

            if (savedInstanceState.containsKey("last_updated_on")) {
                mLastUpdateTime = savedInstanceState.getString("last_updated_on");
            }
        }

        updateLocationUI();
    }
    /**
     * Update the UI displaying the location data
     * and toggling the buttons
     */
    private void updateLocationUI() {
        if (mCurrentLocation != null) {
            setLat(mCurrentLocation.getLatitude());
            setLng(mCurrentLocation.getLongitude());
            //txtLocationResult.setText("Lat: " + mCurrentLocation.getLatitude() + ", " +"Lng: " + mCurrentLocation.getLongitude());


            // giving a blink animation on TextView
            //txtLocationResult.setAlpha(0);
            //txtLocationResult.animate().alpha(1).setDuration(300);

            // location last updated time

        }

        toggleButtons();
    }
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("is_requesting_updates", mRequestingLocationUpdates);
        outState.putParcelable("last_known_location", mCurrentLocation);
        outState.putString("last_updated_on", mLastUpdateTime);

    }
    private void toggleButtons() {
        String buno = ebusnumber.getText().toString();
        String rono = eroundnumber.getText().toString();
        if (mRequestingLocationUpdates&&!buno.matches("") && !rono.matches("")) {
            btnStartUpdates.setEnabled(true);

        } else {
            btnStartUpdates.setEnabled(false);

        }
    }
    private void startLocationUpdates() {
        mSettingsClient
                .checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.i(TAG, "All location settings are satisfied.");

                        //Toast.makeText(getApplicationContext(), "Started location updates!", Toast.LENGTH_SHORT).show();

                        //noinspection MissingPermission
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                mLocationCallback, Looper.myLooper());

                        updateLocationUI();
                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings ");
                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the
                                    // result in onActivityResult().
                                    ResolvableApiException rae = (ResolvableApiException) e;
                                    rae.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException sie) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                Log.e(TAG, errorMessage);

                                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }

                        updateLocationUI();
                    }
                });
    }
    @OnClick(R.id.btn_start_location_updates)
    public void startLocationButtonClick() {
        busnodb.clear();
        firebaseDatabase.getReference("buses").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot dscity : dataSnapshot.getChildren()){
                    for(DataSnapshot dsno : dscity.getChildren()){
                        busnodb.add(dsno.getKey());
                    }
                }
                String buno = ebusnumber.getText().toString();
                final String rono = eroundnumber.getText().toString();

                if (city.getSelectedItem().toString().equalsIgnoreCase("zarqa")){
                    citystr = "";
                }
                else if(city.getSelectedItem().toString().equalsIgnoreCase("university mosque")){
                    citystr = "mosq";
                }
                else {
                    citystr = city.getSelectedItem().toString();
                }
                Calendar calendar = Calendar.getInstance();
                int day = calendar.get(Calendar.DAY_OF_WEEK);

                switch (day) {
                    case Calendar.SUNDAY:
                    case Calendar.TUESDAY:
                    case Calendar.THURSDAY:
                        days = "stuth";
                        break;
                    case Calendar.MONDAY:
                    case Calendar.WEDNESDAY:
                        days = "mw";
                        break;
                    default:
                        days = "";
                        break;
                }
                if (!citystr.matches("")&&!days.matches("")) {
                    firestore.collection(citystr).document(days).addSnapshotListener((documentSnapshot, e) -> {
                        message = "Your Round started at " + documentSnapshot.get(rono);
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    });
                }
                if (!buno.matches("") && !rono.matches("")){
                    if(!busnodb.contains("bus number " + buno)) {
                        ebusnumber.setVisibility(View.GONE);
                        eroundnumber.setVisibility(View.GONE);
                        btnStartUpdates.setVisibility(View.GONE);
                        stop.setVisibility(View.VISIBLE);
                        city.setVisibility(View.GONE);
                        // Requesting ACCESS_FINE_LOCATION using Dexter library
                        Dexter.withActivity(MainActivity.this)
                                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                                .withListener(new PermissionListener() {
                                    @Override
                                    public void onPermissionGranted(PermissionGrantedResponse response) {
                                        mRequestingLocationUpdates = true;
                                        startLocationUpdates();
                                        onMapReady(mMap);
                                    }

                                    @Override
                                    public void onPermissionDenied(PermissionDeniedResponse response) {
                                        if (response.isPermanentlyDenied()) {
                                            // open device settings when the permission is
                                            // denied permanently
                                            openSettings();
                                        }
                                    }

                                    @Override
                                    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                                        token.continuePermissionRequest();
                                    }
                                }).check();
                    }
                    else {
                        Toast.makeText(MainActivity.this,"the bus number is already exist",Toast.LENGTH_LONG).show();
                        btnStartUpdates.setEnabled(true);
                    }
                }
                else{
                    Toast.makeText(MainActivity.this,"please fill bus number and round number",Toast.LENGTH_LONG).show();
                    btnStartUpdates.setEnabled(true);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
            }
        });

    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.e(TAG, "User agreed to make required location settings changes.");
                        // Nothing to do. startLocationupdates() gets called in onResume again.
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.e(TAG, "User chose not to make required location settings changes.");
                        mRequestingLocationUpdates = false;
                        break;
                }
                break;
        }
    }
    private void openSettings() {
        Intent intent = new Intent();
        intent.setAction(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package",
                BuildConfig.APPLICATION_ID, null);
        intent.setData(uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    @Override
    public void onResume() {
        super.onResume();
        // Resuming location updates depending on button state and
        // allowed permissions
        if (mRequestingLocationUpdates && checkPermissions()) {
            startLocationUpdates();
        }
        updateLocationUI();
    }
    private boolean checkPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }
    @Override
    protected void onPause() {
        super.onPause();
    }
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        String buno = ebusnumber.getText().toString();
        String rono = eroundnumber.getText().toString();
        mMap.setMyLocationEnabled(true);
        if (!buno.matches("") && !rono.matches("")) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    mMap.clear();
                    DatabaseReference lat = firebaseDatabase.getReference("buses").child(city.getSelectedItem().toString()).child("bus number " + ebusnumber.getText().toString()).child("round number " + eroundnumber.getText().toString()).child("lat");
                    lat.setValue(getLat() + "");
                    DatabaseReference lng = firebaseDatabase.getReference("buses").child(city.getSelectedItem().toString()).child("bus number " + ebusnumber.getText().toString()).child("round number " + eroundnumber.getText().toString()).child("lng");
                    lng.setValue(getLng() + "");
                    LatLng buspos = new LatLng(getLat(), getLng());
                    mMap.addMarker(new MarkerOptions().position(buspos));
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(buspos));
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(buspos, 15f));
                    handler.postDelayed(this, 1000);
                }
            };
            handler.postDelayed(runnable, 1000);
        }
        else{
            Toast.makeText(MainActivity.this,"please fill bus number and round number",Toast.LENGTH_LONG).show();
            btnStartUpdates.setEnabled(true);
        }
    }
    public void Stop(View view) {
        firebaseDatabase.getReference("buses").child(city.getSelectedItem().toString()).child("bus number " + ebusnumber.getText().toString()).child("round number " + eroundnumber.getText().toString()).child("lat").removeValue().addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                firebaseDatabase.getReference("buses").child(city.getSelectedItem().toString()).child("bus number " + ebusnumber.getText().toString()).child("round number " + eroundnumber.getText().toString()).child("lng").removeValue().addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        ebusnumber.setVisibility(View.VISIBLE);
                        eroundnumber.setVisibility(View.VISIBLE);
                        btnStartUpdates.setVisibility(View.VISIBLE);
                        stop.setVisibility(View.GONE);
                        city.setVisibility(View.VISIBLE);
                        ebusnumber.setText("");
                        eroundnumber.setText("");
                        handler.removeCallbacksAndMessages(null);
                    }
                });
            }
        });
    }

}
