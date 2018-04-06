package com.example.myfirstapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, AdapterView.OnItemClickListener {
    private MapView mapView;
    private GoogleMap gmap;
    private AlertDialog alertDialog;
    private AutoCompleteTextView addressText;

    private static final String GEO_API_URL = "https://maps.googleapis.com/maps/api/geocode/json?address=";
    private static final String API_KEY = "AIzaSyBTEtOwfqLxrd8LgDulUlCDty8aPfd76G4";

    private static final String LOG_TAG = "Autocomplete";
    private static final String PLACES_API_BASE = "https://maps.googleapis.com/maps/api/place";
    private static final String TYPE_AUTOCOMPLETE = "/autocomplete";
    private static final String OUT_JSON = "/json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        addressText = (AutoCompleteTextView) findViewById(R.id.autoCompleteTextView);

        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(API_KEY);
        }

        mapView = findViewById(R.id.map_view);
        mapView.onCreate(mapViewBundle);
        mapView.getMapAsync(this);

        Button queryButton = (Button) findViewById(R.id.queryButton);
        queryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new RetrieveFeedTask().execute();
            }
        });
        alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setMessage("Error processing address request. Try Again.");
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        addressText.setAdapter(new GooglePlacesAutocompleteAdapter(this, R.layout.list_item));
        addressText.setOnItemClickListener(this);
    }

    class RetrieveFeedTask extends AsyncTask<Void, Void, String> {
        private double lat = 0;
        private double lng = 0;

        protected void onPreExecute() {}

        protected String doInBackground(Void... urls) {
            String address = addressText.getText().toString();
            address.replaceAll(" ", "+");

            try {
                URL url = new URL(GEO_API_URL + address + "&apiKey=" + API_KEY);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                try {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    StringBuilder stringBuilder = new StringBuilder();
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line).append("\n");
                    }
                    bufferedReader.close();
                    return stringBuilder.toString();
                }
                finally{
                    urlConnection.disconnect();
                }
            }
            catch(Exception e) {
                Log.e("ERROR", e.getMessage(), e);
                return null;
            }
        }

        protected void onPostExecute(String response) {
            if(response == null) {
                response = "THERE WAS AN ERROR";
            }
            Log.i("INFO", response);

            try {
                JSONObject jObject = new JSONObject(response);
                JSONArray jArray = jObject.getJSONArray("results");
                jObject = jArray.getJSONObject(0);
                String address = jObject.getString("formatted_address");
                JSONArray typesArray = jObject.getJSONArray("types");
                String type = typesArray.getString(0);
                jObject = jObject.getJSONObject("geometry");
                jObject = jObject.getJSONObject("location");
                lat = jObject.getDouble("lat");
                lng = jObject.getDouble("lng");
                new RetrieveWeatherTask().execute();
                moveMap(lat, lng, address, type);
            }
            catch (JSONException e) {
                alertDialog.show();
            }
        }

        class RetrieveWeatherTask extends AsyncTask<Void, Void, String> {
            private static final String WEATHER_URL = "http://api.openweathermap.org/data/2.5/weather?";
            private static final String WEATHER_KEY = "1c142b2fa25b53963df0a44488b89ae0";

            protected void onPreExecute() {}

            protected String doInBackground(Void... urls) {
                String link = WEATHER_URL + "lat=" + (int)lat + "&lon=" + (int)lng;
                try {
                    URL url = new URL(link + "&units=metric" + "&APPID=" + WEATHER_KEY);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    try {
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                        StringBuilder stringBuilder = new StringBuilder();
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            stringBuilder.append(line).append("\n");
                        }
                        bufferedReader.close();
                        return stringBuilder.toString();
                    }
                    finally{
                        urlConnection.disconnect();
                    }
                }
                catch(Exception e) {
                    Log.e("ERROR", e.getMessage(), e);
                    return null;
                }
            }

            protected void onPostExecute(String response) {
                if(response == null) {
                    response = "THERE WAS AN ERROR";
                }
                Log.i("INFO", response);

                try {
                    JSONObject jObject = new JSONObject(response);
                    jObject = jObject.getJSONObject("main");
                    double temperature = jObject.getDouble("temp");
                    Log.i("temperature:" , ""+temperature);
                    updateMarker(temperature);
                }
                catch (JSONException e) {
                    alertDialog.show();
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        Bundle mapViewBundle = outState.getBundle(API_KEY);
        if (mapViewBundle == null) {
            mapViewBundle = new Bundle();
            outState.putBundle(API_KEY, mapViewBundle);
        }

        mapView.onSaveInstanceState(mapViewBundle);
    }
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }
    @Override
    protected void onPause() {
        mapView.onPause();
        super.onPause();
    }
    @Override
    protected void onDestroy() {
        mapView.onDestroy();
        super.onDestroy();
    }
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }
    @Override
    public void onMapReady(GoogleMap googleMap) {
        gmap = googleMap;
    }

    private MarkerOptions currentMarker;

    public void moveMap(double latitude, double longitude, String address, String type)
    {
        if(type.equals("administrative_area_level_1"))
        {
            type = "state";
        }
        else if (type.equals("locality"))
        {
            type = "city";
        }

        switch(type)
        {
            case "country": gmap.setMinZoomPreference(4); gmap.setMaxZoomPreference(4); break;
            case "state": gmap.setMinZoomPreference(6); gmap.setMaxZoomPreference(6); break;
            case "city": gmap.setMinZoomPreference(12); gmap.setMaxZoomPreference(12); break;
            default: gmap.resetMinMaxZoomPreference();
        }

        LatLng coordinate = new LatLng(latitude, longitude);
        gmap.moveCamera(CameraUpdateFactory.newLatLng(coordinate));

        gmap.clear();
        currentMarker = new MarkerOptions().position(coordinate).title(address);
        gmap.addMarker(currentMarker);
    }

    public void updateMarker(double temperature)
    {
        gmap.clear();
        currentMarker.snippet(temperature + " Celsius");
        gmap.addMarker(currentMarker);
    }

    public void onItemClick(AdapterView adapterView, View view, int position, long id) {
        String str = (String) adapterView.getItemAtPosition(position);
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
    }

    public static ArrayList autocomplete(String input) {
        ArrayList resultList = null;

        HttpURLConnection conn = null;
        StringBuilder jsonResults = new StringBuilder();
        try {
            StringBuilder sb = new StringBuilder(PLACES_API_BASE + TYPE_AUTOCOMPLETE + OUT_JSON);
            sb.append("?key=" + "AIzaSyBpGt2hay98kEfG5JHe9OHijFBpqOmDpGE");
            sb.append("&components=country:us");
            sb.append("&input=" + URLEncoder.encode(input, "utf8"));

            URL url = new URL(sb.toString());
            conn = (HttpURLConnection) url.openConnection();
            InputStreamReader in = new InputStreamReader(conn.getInputStream());

            // Load the results into a StringBuilder
            int read;
            char[] buff = new char[1024];
            while ((read = in.read(buff)) != -1) {
                jsonResults.append(buff, 0, read);
            }
        } catch (MalformedURLException e) {
            Log.i(LOG_TAG, "Error processing Places API URL", e);
            return resultList;
        } catch (IOException e) {
            Log.i(LOG_TAG, "Error connecting to Places API", e);
            return resultList;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

        try {
            // Create a JSON object hierarchy from the results
            Log.i("JSON RESULT:", jsonResults.toString());
            JSONObject jsonObj = new JSONObject(jsonResults.toString());
            JSONArray predsJsonArray = jsonObj.getJSONArray("predictions");

            // Extract the Place descriptions from the results
            resultList = new ArrayList(predsJsonArray.length());
            for (int i = 0; i < predsJsonArray.length(); i++) {
                System.out.println(predsJsonArray.getJSONObject(i).getString("description"));
                System.out.println("============================================================");
                resultList.add(predsJsonArray.getJSONObject(i).getString("description"));
            }
        } catch (JSONException e) {
            Log.i(LOG_TAG, "Cannot process JSON results", e);
        }

        return resultList;
    }

    class GooglePlacesAutocompleteAdapter extends ArrayAdapter implements Filterable {
        private ArrayList resultList;

        public GooglePlacesAutocompleteAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
        }

        @Override
        public int getCount() {
            return resultList.size();
        }

        @Override
        public String getItem(int index) {
            return (String)resultList.get(index);
        }

        @Override
        public Filter getFilter() {
            Filter filter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults filterResults = new FilterResults();
                    if (constraint != null) {
                        // Retrieve the autocomplete results.
                        Log.i("CONSTRAINT:", constraint.toString());
                        resultList = autocomplete(constraint.toString());
                        Log.i("LIST:", ""+resultList.size());

                        // Assign the data to the FilterResults
                        filterResults.values = resultList;
                        filterResults.count = resultList.size();
                    }
                    return filterResults;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    Log.i("DOES THIS WORK", results.count + "");
                    if (results != null && results.count > 0) {
                        Log.i("DOES THIS WORK", "LIST CHANGED");
                        notifyDataSetChanged();
                    } else {
                        notifyDataSetInvalidated();
                    }
                }
            };
            return filter;
        }
    }
}
