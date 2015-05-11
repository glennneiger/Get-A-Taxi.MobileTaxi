package com.getataxi.mobiletaxi;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.provider.Settings;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.getataxi.mobiletaxi.comm.RestClientManager;
import com.getataxi.mobiletaxi.comm.models.LoginUserDM;
import com.getataxi.mobiletaxi.utils.DeviceState;
import com.getataxi.mobiletaxi.utils.UserPreferencesManager;

import org.apache.http.HttpStatus;

import java.io.IOException;
import java.text.ParseException;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.mime.TypedByteArray;


public class StartupActivity extends Activity {
    private Context context;
    private Button proceedButton;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup);
        context = this;
        this.proceedButton = (Button)findViewById(R.id.proceed_to_order_btn);
        boolean devicesReady = true;

        if(!DeviceState.isNetworkAvailable(context)){
            devicesReady = false;
            DeviceState.showSettingsAlert(
                    R.string.network_disconnected_title,
                    R.string.network_disconnected_message,
                    Settings.ACTION_WIRELESS_SETTINGS,
                    context
            );
        }

        if (!DeviceState.isPositioningAvailable(context)){
            devicesReady = false;
            DeviceState.showSettingsAlert(
                    R.string.positioning_unavailable_title,
                    R.string.positioning_unavailable_message,
                    Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                    context
            );
        }


        if(devicesReady) {
            proceedWithStartup();
        }else {
            proceedButton.setVisibility(View.VISIBLE);
            proceedButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    proceedWithStartup();
                }
            });
        }
    }

    private void proceedWithStartup() {

        try {
            Log.e("DATE: ", UserPreferencesManager.tokenDateFormat.parse("Thu, 09 Apr 2015 20:48:26 GMT").toString());
        } catch (ParseException e) {
            e.printStackTrace();
        }

        // Check for login credentials
        if(UserPreferencesManager.checkForLoginCredentials(context)){
            LoginUserDM loginUserDM = UserPreferencesManager.getLoginData(context);
            // Check if still logged-in
            if(UserPreferencesManager.isLoggedIn(context) && !UserPreferencesManager.tokenHasExpired(loginUserDM)){
                Intent orderMap = new Intent(context, OrderMap.class);
                orderMap.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(orderMap);
            }else{
                String grantType = "password";
//                RestClientManager manager = new RestClientManager(context);
//                manager.login(loginUserDM, grantType);
                //        RestClientManager.login(loginUserDM, grantType, context); // static

                RestClientManager.login(loginUserDM, grantType, new Callback<LoginUserDM>() {
                    @Override
                    public void success(LoginUserDM loginUserDM, Response response) {
                        int status = response.getStatus();
                        if (status == HttpStatus.SC_OK) {
                            try {
                                String s = getResources().getString(R.string.token_renew);
                                Toast.makeText(context, String.format(s, loginUserDM.email), Toast.LENGTH_LONG).show();
                                UserPreferencesManager.saveLoginData(loginUserDM, context);

                                Intent orderMap = new Intent(context, OrderMap.class);
                                orderMap.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                                context.startActivity(orderMap);

                            } catch (IllegalStateException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        String errorJson = new String(((TypedByteArray) error.getResponse().getBody()).getBytes());
                        Toast.makeText(context, errorJson, Toast.LENGTH_LONG).show();
                    }
                });

            }
        } else  {
            // No login credentials, check for stored registration
            Resources res = getResources();
            if (UserPreferencesManager.checkForRegistration(context)){
                // Stored credentials found, suggest login
                Toast.makeText(context, res.getString(R.string.please_login), Toast.LENGTH_LONG).show();
                Intent loginIntent = new Intent(context, LoginActivity.class);
                loginIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(loginIntent);
            } else {
                // No stored credentials at all, suggesting new registration
                Toast.makeText(context, res.getString(R.string.please_register), Toast.LENGTH_LONG).show();
                Intent registerIntent = new Intent(context, LoginActivity.class);
                registerIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(registerIntent);
            }
        }
    }

}
