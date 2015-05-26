package com.getataxi.mobiletaxi.comm;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.getataxi.mobiletaxi.R;
import com.getataxi.mobiletaxi.comm.models.LoginUserDM;
import com.getataxi.mobiletaxi.utils.Constants;
import com.getataxi.mobiletaxi.utils.UserPreferencesManager;

import java.util.concurrent.ExecutionException;

import microsoft.aspnet.signalr.client.LogLevel;
import microsoft.aspnet.signalr.client.Logger;
import microsoft.aspnet.signalr.client.SignalRFuture;
import microsoft.aspnet.signalr.client.hubs.HubConnection;
import microsoft.aspnet.signalr.client.hubs.HubProxy;
import microsoft.aspnet.signalr.client.hubs.SubscriptionHandler2;


/**
 * Created by bvb on 30.4.2015 г..
 */
public class SignalRTrackingService extends Service {

    private HubConnection connection;
    private Intent broadcastIntent;
    private HubProxy proxy;
    private boolean reportLocationEnabled = false;
    int orderId;
    @Override
    public void onCreate() {
        super.onCreate();
        orderId = -1;
        // Register for Location Service broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.LOCATION_UPDATED);
        registerReceiver(locationReceiver, filter);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Toast.makeText(this, getString(R.string.tracking_started), Toast.LENGTH_LONG).show();

        Log.d("TRACKINGSERVICE", "onStartCommand");

        orderId = intent.getIntExtra(Constants.ORDER_ID, -1);
        String baseUsrl = intent.getStringExtra(Constants.BASE_URL_STORAGE);
        reportLocationEnabled = intent.getBooleanExtra(Constants.LOCATION_REPORT_ENABLED, false);

        if(orderId == -1){
            return -1;
        }

        String server =  baseUsrl + Constants.HUB_ENDPOINT;

        Log.d("TRACKINGSERVICE", server);

        Logger l  = new Logger() {
            @Override
            public void log(String s, LogLevel logLevel) {

            }
        };

        // Getting user details
        LoginUserDM loginData = UserPreferencesManager.getLoginData(getApplicationContext());

        // Prepare request
       // Request request = new Request("POST");
        connection = new HubConnection(server);
        proxy = connection.createHubProxy(Constants.TRACKING_HUB_PROXY);
        connection.setCredentials(new TokenAuthenticationCredentials(loginData.accessToken));
        //connection.prepareRequest(request);

        Log.d("TRACKINGSERVICE", "awaiting connection");
        SignalRFuture<Void> awaitConnection = connection.start();
        try {
            awaitConnection.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }

        Log.d("TRACKINGSERVICE", "invoking hub");
        proxy.invoke(Constants.HUB_CONNECT, orderId);

        Log.d("TRACKINGSERVICE", "registering callback");
        proxy.on(Constants.HUB_PEER_LOCATION_CHANGED, new SubscriptionHandler2<Double, Double>() {
            @Override
            public void run(Double lat, Double lon) {
                Log.d("TRACKINGSERVICE", "HUB_PEER_LOCATION_CHANGED");
                Location loc = new Location("void");
                loc.setLatitude(lat);
                loc.setLongitude(lon);
                broadcastIntent = new Intent(Constants.HUB_PEER_LOCATION_CHANGED_BC);
                broadcastIntent.putExtra(Constants.LOCATION, loc);
                sendBroadcast(broadcastIntent);
            }
        }, Double.class, Double.class);


        Log.d("TRACKINGSERVICE", "DONE onStartCommand()");
        return Service.START_STICKY;
                //--------------------------------------------------------------------------------
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(locationReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * The receiver for the Location Service location update broadcasts
     */
    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(Constants.LOCATION_UPDATED)) {
                if(!reportLocationEnabled){
                    return;
                }
                Bundle data = intent.getExtras();

                Location location = data.getParcelable(Constants.LOCATION);

                double lat = location.getLatitude();
                double lon = location.getLongitude();

                if ( proxy != null && orderId != -1){
                    Log.d("TRACKINGSERVICE", "HUB_MY_LOCATION_CHANGED");
                    proxy.invoke(Constants.HUB_MY_LOCATION_CHANGED, orderId, lat, lon);
                }

            }
        }
    };


    public static Thread performOnBackgroundThread(final Runnable runnable) {
        final Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch(Exception e) {
                    Log.d("Error", e.toString());
                } finally {

                }
            }
        };
        t.start();
        return t;
    }
}
