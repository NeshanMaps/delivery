package org.neshan.delivery.activity;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.carto.core.ScreenBounds;
import com.carto.core.ScreenPos;
import com.carto.styles.AnimationStyle;
import com.carto.styles.AnimationStyleBuilder;
import com.carto.styles.AnimationType;
import com.carto.styles.MarkerStyle;
import com.carto.styles.MarkerStyleBuilder;
import com.carto.utils.BitmapUtils;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.neshan.common.model.LatLng;
import org.neshan.common.model.LatLngBounds;
import org.neshan.delivery.R;
import org.neshan.delivery.adapter.SearchAdapter;
import org.neshan.delivery.database_helper.AssetDatabaseHelper;
import org.neshan.delivery.model.Driver;
import org.neshan.mapsdk.MapView;
import org.neshan.mapsdk.model.Marker;
import org.neshan.servicessdk.search.NeshanSearch;
import org.neshan.servicessdk.search.model.Item;
import org.neshan.servicessdk.search.model.NeshanSearchResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements SearchAdapter.OnSearchItemListener {

    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = 10000;
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int NEAR_MOTORS_DISTANCE_KILOMETERS = 2;

    private MapView map;
    private LinearLayout layoutSearching;
    private Button btnSubmitSource;
    private Button btnSubmitDestination;
    private AppCompatEditText txtSearch;
    private RecyclerView recyclerView;

    private SQLiteDatabase db;

    private List<Marker> driverMarkers = new ArrayList<>();
    private FusedLocationProviderClient fusedLocationClient;
    private SettingsClient settingsClient;
    private LocationCallback locationCallback;
    private Location userLocation;
    private LocationRequest locationRequest;
    private LocationSettingsRequest locationSettingsRequest;
    private Marker userMarker;
    private BottomSheetDialog driverInfoBottomSheetDialog;
    private BottomSheetDialog travelDetailBottomSheetDialog;
    private List<Driver> drivers;
    private LatLng sourceLatLng;
    private LatLng destinationLatLng;
    private Marker sourceMarker;
    private Marker destinationMarker;
    private SearchAdapter adapter;
    private List<Item> items;
    private Handler handler = new Handler();
    private Runnable runnable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();

        initLayoutReferences();

        getMyFakeLocation();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                drivers = getNearDrivers();
            }
        }, 3000);

        btnSubmitSource.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sourceLatLng = map.getCameraTargetPosition();
                sourceMarker = addPin(sourceLatLng, R.drawable.source_marker);
                btnSubmitSource.setVisibility(View.GONE);
                btnSubmitDestination.setVisibility(View.VISIBLE);
            }
        });

        btnSubmitDestination.setOnClickListener(view -> {
            destinationLatLng = map.getCameraTargetPosition();
            destinationMarker = addPin(destinationLatLng, R.drawable.destination_marker);
            showLatLngsInCamera(sourceLatLng, destinationLatLng);
            btnSubmitDestination.setVisibility(View.GONE);
            showTravelDetailBottomSheetDialog();
        });

        txtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (runnable != null) {
                    handler.removeCallbacks(runnable);
                }
                runnable = new Runnable() {
                    @Override
                    public void run() {
                        search(map.getCameraTargetPosition(), editable.toString());
                    }
                };

                handler.postDelayed(runnable, 1000);
            }
        });

    }

    private void search(LatLng searchPosition, String text) {
        //TODO: Replace API-KEY with your api key
        new NeshanSearch.Builder("API-KEY")
                .setLocation(searchPosition)
                .setTerm(text)
                .build().call(new Callback<NeshanSearchResult>() {
                    @Override
                    public void onResponse(Call<NeshanSearchResult> call, Response<NeshanSearchResult> response) {
                        if (response.code() == 403) {
                            Toast.makeText(MainActivity.this, getString(R.string.connection_error), Toast.LENGTH_LONG).show();
                            return;
                        }
                        if (response.body() != null) {
                            NeshanSearchResult result = response.body();
                            items = result.getItems();
                            adapter.updateList(items);
                        }
                    }

                    @Override
                    public void onFailure(Call<NeshanSearchResult> call, Throwable t) {
                        Toast.makeText(MainActivity.this, getString(R.string.connection_error), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showLatLngsInCamera(LatLng sourceLatLng, LatLng destinationLatLng) {
        double minLat;
        double minLng;
        double maxLat;
        double maxLng;
        LatLng northEast;
        LatLng southWest;
        minLat = Math.min(sourceLatLng.getLatitude(), destinationLatLng.getLatitude());
        minLng = Math.min(sourceLatLng.getLongitude(), destinationLatLng.getLongitude());
        maxLat = Math.max(sourceLatLng.getLatitude(), destinationLatLng.getLatitude());
        maxLng = Math.max(sourceLatLng.getLongitude(), destinationLatLng.getLongitude());

        northEast = new LatLng(maxLat, maxLng);
        southWest = new LatLng(minLat, minLng);

        map.moveToCameraBounds(
                new LatLngBounds(northEast, southWest),
                new ScreenBounds(
                        new ScreenPos(0, 0),
                        new ScreenPos(map.getWidth(), map.getHeight())
                ),
                true, 0.25f);
    }

    private Marker addPin(LatLng location, int drawable) {
        AnimationStyleBuilder animStBl = new AnimationStyleBuilder();
        animStBl.setFadeAnimationType(AnimationType.ANIMATION_TYPE_SMOOTHSTEP);
        animStBl.setSizeAnimationType(AnimationType.ANIMATION_TYPE_SPRING);
        animStBl.setPhaseInDuration(0.5f);
        animStBl.setPhaseOutDuration(0.5f);
        AnimationStyle animSt = animStBl.buildStyle();

        MarkerStyleBuilder markStCr = new MarkerStyleBuilder();
        markStCr.setSize(50f);
        markStCr.setAnchorPointX(0);
        markStCr.setAnchorPointY(-1.5f);
        markStCr.setBitmap(BitmapUtils.createBitmapFromAndroidBitmap(BitmapFactory.decodeResource(getResources(), drawable)));
        markStCr.setAnimationStyle(animSt);
        MarkerStyle markSt = markStCr.buildStyle();

        Marker marker = new Marker(location, markSt);

        map.addMarker(marker);
        return marker;
    }

    private Driver randomDriver() {
        return drivers.get(new Random().nextInt((drivers.size() - 2) + 1) + 1);
    }

    private void initLayoutReferences() {
        initViews();

        initMap();
    }

    private void initMap() {
        map.getSettings().setZoomControlsEnabled(false);
    }

    private void initViews() {
        map = findViewById(R.id.mapview);
        layoutSearching = findViewById(R.id.layout_searching);
        btnSubmitSource = findViewById(R.id.btn_submit_source);
        btnSubmitDestination = findViewById(R.id.btn_submit_destination);
        txtSearch = findViewById(R.id.txt_search);
        recyclerView = findViewById(R.id.recycler_view);
        items = new ArrayList<>();
        adapter = new SearchAdapter(items, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private List<Driver> getNearDrivers() {
        List<Driver> drivers = new ArrayList<>();
        AssetDatabaseHelper myDbHelper = new AssetDatabaseHelper(this);

        try {
            myDbHelper.createDataBase();
        } catch (IOException ioe) {
            throw new Error("Unable to create database");
        }

        try {
            db = myDbHelper.openDataBase();
        } catch (SQLException sqle) {
            sqle.printStackTrace();
        }


        // creating a cursor and query all rows of points table
        Cursor cursor = db.rawQuery("select * from driver", null);

        //reading all points and adding a marker for each one
        if (cursor.moveToFirst()) {
            // variable for creating bound
            // min = south-west
            // max = north-east
            double minLat = Double.MAX_VALUE;
            double minLng = Double.MAX_VALUE;
            double maxLat = Double.MIN_VALUE;
            double maxLng = Double.MIN_VALUE;
            while (!cursor.isAfterLast()) {
                String driverId = cursor.getString(cursor.getColumnIndex("id"));
                String driverName = cursor.getString(cursor.getColumnIndex("name"));
                double lng = cursor.getDouble(cursor.getColumnIndex("lng"));
                double lat = cursor.getDouble(cursor.getColumnIndex("lat"));
                LatLng LatLng = new LatLng(lat, lng);

                // validating min and max
                minLat = Math.min(LatLng.getLatitude(), minLat);
                minLng = Math.min(LatLng.getLongitude(), minLng);
                maxLat = Math.max(LatLng.getLatitude(), maxLat);
                maxLng = Math.max(LatLng.getLongitude(), maxLng);

                Location driverLocation = new Location("");
                driverLocation.setLatitude(lat);
                driverLocation.setLongitude(lng);

                if (driverLocation.distanceTo(userLocation) / 1000 <= NEAR_MOTORS_DISTANCE_KILOMETERS) {
                    Driver driver = new Driver();
                    driver.setLat(lat).setLng(lng).setId(driverId).setName(driverName);
                    drivers.add(driver);
                }

                driverMarkers.add(addMarker(LatLng));

                cursor.moveToNext();
            }

            map.moveToCameraBounds(
                    new LatLngBounds(new LatLng(maxLat, maxLng), new LatLng(minLat, minLng)),
                    new ScreenBounds(
                            new ScreenPos(0, 0),
                            new ScreenPos(map.getWidth(), map.getHeight())
                    ),
                    true, 0.25f);

            Log.i("BOUND", "getDBPoints: " + minLat + " " + minLng + "----" + maxLat + " " + maxLng);
        }
        cursor.close();
        return drivers;
    }

    private void getMyFakeLocation() {
        userLocation = new Location("");
        userLocation.setLatitude(35.701433073);
        userLocation.setLongitude(51.337892468);
        LatLng userLatLng = new LatLng(userLocation.getLatitude(), userLocation.getLongitude());
        addUserMarker(userLatLng);
        map.moveCamera(userLatLng, .5f);
    }

    private void getMyLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        settingsClient = LocationServices.getSettingsClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                // location is received
                userLocation = locationResult.getLastLocation();

                onLocationChange();
                stopLocationUpdates();
            }
        };

        locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        locationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(locationRequest);
        locationSettingsRequest = builder.build();

    }

    public void stopLocationUpdates() {
        fusedLocationClient
                .removeLocationUpdates(locationCallback)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                    }
                });
    }

    private void onLocationChange() {
        if (userLocation != null) {
            addUserMarker(new LatLng(userLocation.getLatitude(), userLocation.getLongitude()));
        }
    }

    private void addUserMarker(LatLng loc) {
        if (userMarker != null) {
            map.removeMarker(userMarker);
        }
        MarkerStyleBuilder markStCr = new MarkerStyleBuilder();
        markStCr.setSize(30f);
        markStCr.setBitmap(com.carto.utils.BitmapUtils.createBitmapFromAndroidBitmap(BitmapFactory.decodeResource(getResources(), org.neshan.mapsdk.R.drawable.ic_marker)));
        MarkerStyle markSt = markStCr.buildStyle();

        userMarker = new Marker(loc, markSt);

        map.addMarker(userMarker);
    }

    private Marker addMarker(LatLng loc) {
        AnimationStyleBuilder animStBl = new AnimationStyleBuilder();
        animStBl.setFadeAnimationType(AnimationType.ANIMATION_TYPE_SMOOTHSTEP);
        animStBl.setSizeAnimationType(AnimationType.ANIMATION_TYPE_SPRING);
        animStBl.setPhaseInDuration(0.5f);
        animStBl.setPhaseOutDuration(0.5f);
        AnimationStyle animSt = animStBl.buildStyle();

        MarkerStyleBuilder markStCr = new MarkerStyleBuilder();
        markStCr.setSize(30f);
        markStCr.setBitmap(BitmapUtils.createBitmapFromAndroidBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.scooter_small)));
        markStCr.setAnimationStyle(animSt);
        MarkerStyle markSt = markStCr.buildStyle();

        Marker marker = new Marker(loc, markSt);

        map.addMarker(marker);
        return marker;
    }

    private void showDriverInfoBottomSheetDialog(Driver driver) {
        driverInfoBottomSheetDialog = new BottomSheetDialog(map.getContext());
        driverInfoBottomSheetDialog.setContentView(R.layout.driver_bottom_sheet);

        AppCompatTextView lblName = driverInfoBottomSheetDialog.findViewById(R.id.lbl_name);
        Button btnCancel = driverInfoBottomSheetDialog.findViewById(R.id.btn_cancel);

        lblName.setText(driver.getName());

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                driverInfoBottomSheetDialog.dismiss();
                btnSubmitSource.setVisibility(View.VISIBLE);
                if (sourceMarker != null) {
                    map.removeMarker(sourceMarker);
                }
                if (destinationMarker != null) {
                    map.removeMarker(destinationMarker);
                }
            }
        });

        driverInfoBottomSheetDialog.show();

        travelDetailBottomSheetDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                travelDetailBottomSheetDialog.dismiss();
                btnSubmitSource.setVisibility(View.VISIBLE);
                if (sourceMarker != null) {
                    map.removeMarker(sourceMarker);
                }
                if (destinationMarker != null) {
                    map.removeMarker(destinationMarker);
                }
            }
        });
    }

    private void showTravelDetailBottomSheetDialog() {
        travelDetailBottomSheetDialog = new BottomSheetDialog(map.getContext());
        travelDetailBottomSheetDialog.setContentView(R.layout.travel_detail_bottom_sheet);

        AppCompatTextView lblPrice = travelDetailBottomSheetDialog.findViewById(R.id.lbl_price);
        Button btnRequest = travelDetailBottomSheetDialog.findViewById(R.id.btn_request);
        lblPrice.setText("300,000 " + "ریال");


        btnRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                travelDetailBottomSheetDialog.dismiss();
                layoutSearching.setVisibility(View.VISIBLE);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        layoutSearching.setVisibility(View.GONE);
                        showDriverInfoBottomSheetDialog(randomDriver());
                    }
                }, 5000);
            }
        });

        travelDetailBottomSheetDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                travelDetailBottomSheetDialog.dismiss();
                btnSubmitSource.setVisibility(View.VISIBLE);
                if (sourceMarker != null) {
                    map.removeMarker(sourceMarker);
                }
                if (destinationMarker != null) {
                    map.removeMarker(destinationMarker);
                }
            }
        });

        travelDetailBottomSheetDialog.show();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private boolean checkLocationPermission() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void getLocationPermission() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onSeachItemClick(LatLng latLng) {
        closeKeyBoard();
        adapter.updateList(new ArrayList<Item>());
        map.setZoom(16f, 0);
        map.moveCamera(latLng, 0);
    }

    private void closeKeyBoard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }
}