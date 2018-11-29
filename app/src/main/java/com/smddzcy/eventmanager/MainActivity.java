package com.smddzcy.eventmanager;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import com.smddzcy.eventmanager.Models.*;
import com.smddzcy.eventmanager.SocketUtils;


public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener, GoogleMap.OnInfoWindowClickListener {

    private String username = "Anonymous";
    private static final Gson gson = new Gson();

    @Override
    public void onInfoWindowClick(Marker marker) {
        int idx = (int) marker.getTag();
        Models.Incident incident = incidents.get(idx);
        try {
            new BgMessageTask(
                    new JSONObject()
                            .put("type", "deleteIncident")
                            .put("payload", incident.id),
                    "Incident has been successfully marked as handled."
            ).execute();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    class CustomInfoWindow implements GoogleMap.InfoWindowAdapter {
        @Override
        public View getInfoWindow(Marker marker) {
            return null;
        }

        @Override
        public View getInfoContents(Marker marker) {
            int locIdx = (int) marker.getTag();
            final Models.Incident incident = incidents.get(locIdx);
            final LatLng latLng = locs.get(locIdx);

            View view = getLayoutInflater().inflate(R.layout.infowindow, null);
            TextView line1 = view.findViewById(R.id.iw_line1);
            TextView line2 = view.findViewById(R.id.iw_line2);
            TextView line3 = view.findViewById(R.id.iw_line3);
            TextView line4 = view.findViewById(R.id.iw_line4);
            Button handledBtn = view.findViewById(R.id.iw_button);

            line1.setText(String.format("Incident: %s", incident.incident));
            line2.setText(String.format("Reported by: %s", incident.reportedBy));

            Location markerLoc = new Location("Marker");
            markerLoc.setLatitude(marker.getPosition().latitude);
            markerLoc.setLongitude(marker.getPosition().longitude);

            String distance = "-";

            try {
                Location myLoc = googleMap.getMyLocation();
                Float dist = myLoc.distanceTo(markerLoc);
                if (dist > 1000) {
                    distance = String.format("%.1fkm", dist / 1000);
                } else if (dist > 1) {
                    distance = String.format("%.1fm", dist);
                } else {
                    distance = String.format("%.1fcm", dist * 100);
                }

            } catch (Exception ignored) {}


            line3.setText(String.format("Distance: %s (C: %s, %s)", distance, String.format("%.3f", latLng.latitude), String.format("%.3f", latLng.longitude)));
            line4.setText(String.format("Date: %s", incident.date));

            handledBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        new BgMessageTask(
                                new JSONObject()
                                        .put("type", "deleteIncident")
                                        .put("payload", incident.id),
                                "Incident has been successfully marked as handled."
                        ).execute();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });

            return view;
        }
    }

    private class BgMessageTask extends AsyncTask<Void, Void, String> {
        JSONObject msg;
        String successMsg;
        private Socket socket = null;
        private BufferedWriter out;
        private BufferedReader in;

        BgMessageTask(JSONObject msg, String successMsg) {
            this.msg = msg;
            this.successMsg = successMsg;
        }

        @Override
        protected String doInBackground(Void... voids) {
            return SocketUtils.sendMessage(this.msg.toString());
        }

        @Override
        protected void onPostExecute(final String result) {
            try {
                SuccessMessage msg = gson.fromJson(result, SuccessMessage.class);
                showToast(this.successMsg);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setLocsFromResponse(result);
                    }
                });
                googleMap.setOnMapLongClickListener(MainActivity.this);
                addingIncident.set(false);
            } catch (Exception e) {
                try {
                    FailMessage msg = gson.fromJson(result, FailMessage.class);
                    showToast(msg.payload);
                } catch (Exception ex) {
                    showToast("Your message couldn't be sent to the server");
                }
            }
        }
    }

    Button button;
    final AtomicBoolean addingIncident = new AtomicBoolean(false);

    Socket socket;
    BufferedReader in;
    BufferedWriter out;
    GoogleMap googleMap;

    List<Models.Incident> incidents;
    List<LatLng> locs = new ArrayList<>();
    List<Marker> markers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d("Username", getIntent().getStringExtra("username"));
        this.username = getIntent().getStringExtra("username");

        final ListenThread thread = new ListenThread();
        thread.start();
        showMap();
    }

    public void showMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(MainActivity.this);
    }

    public void showToast(String message) {
        showToast(message, Toast.LENGTH_SHORT);
    }

    public void showToast(String message, int toastLength) {
        Toast.makeText(MainActivity.this, message, toastLength).show();
    }

//    public AlertDialog showAlert(final Context ctx, String title, String message) {
//        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
//        builder.setCancelable(true);
//        builder.setMessage(message);
//        builder.setTitle(title);
//        builder.setPositiveButton("OK",
//                new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int which) {
//                    }
//                });
//        return builder.show();
//    }

    public void setLocsFromResponse(String response) {
        List<Models.Incident> payload = gson.fromJson(response, SuccessMessage.class).payload;
        if (payload == null) {
            payload = new ArrayList<>();
        }
        this.incidents = payload;
        List<LatLng> latLngs = new ArrayList<>();
        for (int i = 0; i < payload.size(); i++) {
            String[] parts = payload.get(i).location.split(",");
            latLngs.add(new LatLng(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])));
        }
        setLocs(latLngs);
    }

    public void setLocs(List<LatLng> latLngs) {
        locs = latLngs;
        showMap();
    }

    public void showIncidentDialog(final Context ctx, final LatLng latLng) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle("Report incident");

        // Set up the input
        final EditText input = new EditText(ctx);
        input.setHint("What happened?");
        builder.setView(input);

        // Set up the buttons
        String posBtnText = latLng == null ? "Select Location" : "OK";
        builder.setPositiveButton(posBtnText, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final String text = input.getText().toString();
                if (latLng == null) {
                    googleMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
                        @Override
                        public void onMapLongClick(LatLng latLng) {
                            if (addingIncident.getAndSet(true)) return;
                            try {
                                new BgMessageTask(
                                        new JSONObject()
                                                .put("type", "addIncident")
                                                .put("payload", username + ":::" + latLng.latitude + "," + latLng.longitude + ":::" + text),
                                        "Your incident has been successfully reported."
                                ).execute();
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } else {
                    if (addingIncident.getAndSet(true)) return;
                    try {
                        new BgMessageTask(
                                new JSONObject()
                                        .put("type", "addIncident")
                                        .put("payload", username + ":::" + latLng.latitude + "," + latLng.longitude + ":::" + text),
                                "Your incident has been successfully reported."
                        ).execute();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        // Add a marker for each spot,
        // and move the map's camera to the same location.
        for (Marker marker : markers) {
            marker.remove();
        }
        if (!locs.isEmpty()) {
            double maxLat = Integer.MIN_VALUE;
            double maxLng = Integer.MIN_VALUE;
            double minLat = Integer.MAX_VALUE;
            double minLng = Integer.MAX_VALUE;
            for (int i = 0; i < locs.size(); i++) {
                LatLng latLng = locs.get(i);
                Marker marker = googleMap.addMarker(new MarkerOptions().position(latLng).title(String.format("Coordinates: %s, %s", String.format("%.3f", latLng.latitude), String.format("%.3f", latLng.longitude))));
                marker.setTag(i);
                markers.add(marker);
                maxLat = Math.max(maxLat, latLng.latitude);
                maxLng = Math.max(maxLng, latLng.longitude);
                minLat = Math.min(minLat, latLng.latitude);
                minLng = Math.min(minLng, latLng.longitude);
                LatLngBounds bounds = new LatLngBounds(new LatLng(minLat, minLng), new LatLng(maxLat, maxLng));
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 50);
                googleMap.animateCamera(cameraUpdate);
            }
        }
        this.googleMap = googleMap;
        CustomInfoWindow customInfoWindow = new CustomInfoWindow();
        googleMap.setInfoWindowAdapter(customInfoWindow);
        this.googleMap.setOnMapLongClickListener(this);
        this.googleMap.setOnInfoWindowClickListener(this);
        googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                        this, R.raw.style_json));
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            googleMap.setMyLocationEnabled(true);
        } else {
            // Show rationale and request permission.
            requestLocationPermission();
        }
    }

    private void requestLocationPermission() {
        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.ACCESS_FINE_LOCATION)) {

            // Show an explanation to the user *asynchronously* -- don't block
            // this thread waiting for the user's response! After the user
            // sees the explanation, try again to request the permission.
            new AlertDialog.Builder(this)
                    .setTitle("Location Permission Needed")
                    .setMessage("This app needs the Location permission, please accept to use location functionality")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            //Prompt the user once explanation has been shown
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    99);
                        }
                    })
                    .create()
                    .show();
        } else {
            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    99);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 99: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        googleMap.setMyLocationEnabled(true);
                    }

                }
            }
        }
    }

    @Override
    public void onMapLongClick(LatLng latLng) {
        Log.d("longClick", latLng.toString());
        showIncidentDialog(MainActivity.this, latLng);
    }

    class ListenThread extends Thread {
        @Override
        public void run() {
            try {
                socket = new Socket(SocketUtils.HOST_IP, SocketUtils.HOST_PORT);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

                final String response = SocketUtils.sendMessage("{\"type\": \"listen\", \"payload\": \"\"}", socket, in, out);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setLocsFromResponse(response);
                    }
                });

                final StringBuilder data = new StringBuilder();
                String temp;
                while ((temp = in.readLine()) != null) {
                    // a small hack to use non-final values in the anonymous class
                    data.replace(0, data.length(), temp);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setLocsFromResponse(data.toString());
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
