package com.eltonfaust.multiplayer;

import org.json.JSONArray;
import org.json.JSONException;

import android.content.Intent;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import java.util.List;

public class RadioPlugin extends CordovaPlugin implements RadioListener {
    private static final String LOG_TAG = "RadioPlugin";

    private RadioManager mRadioManager = null;
    private CallbackContext connectionCallbackContext;
    private boolean isConnecting = false;
    private boolean isConnected = false;
    private JSONArray requestedPlay = null;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("initialize".equals(action)) {
            try {
                this.mRadioManager = RadioManager.with(this.cordova.getActivity());
                this.mRadioManager.registerListener(this);
                this.mRadioManager.setLogging(true);
                this.mRadioManager.setStreamURL(args.getString(0));

                this.connectionCallbackContext = callbackContext;

                PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
                pluginResult.setKeepCallback(true);

                callbackContext.sendPluginResult(pluginResult);
                return true;
            } catch (Exception e) {
                Log.e(LOG_TAG, "Exception occurred: ".concat(e.getMessage()));
                callbackContext.error(e.getMessage());
                return false;
            }
        } else if ("connect".equals(action)) {
            if (!this.isConnected && !this.isConnecting) {
                this.isConnecting = true;
                this.mRadioManager.connect();
            }

            callbackContext.success();
            return true;
        } else if ("play".equals(action)) {
            if (!this.isConnected) {
                if (!this.isConnecting) {
                    this.isConnecting = true;
                    this.mRadioManager.connect();
                }

                this.requestedPlay = args;
            } else {
                this.requestedPlay = null;
                this.mRadioManager.startRadio(args.getInt(0));
            }

            callbackContext.success();
            return true;
        } else if ("stop".equals(action)) {
            this.requestedPlay = null;

            if (this.isConnected) {
               this.mRadioManager.stopRadio();
            }

            callbackContext.success();
            return true;
        }

        Log.e(LOG_TAG, "Called invalid action: " + action);
        return false;
    }

    @Override
    public void onRadioLoading() {
        Log.e(LOG_TAG, "RADIO STATE : LOADING...");
        this.sendListenerResult("LOADING");
    }

    @Override
    public void onRadioConnected() {
        this.isConnecting = false;
        this.isConnected = true;

        Log.e(LOG_TAG, "RADIO STATE : CONNECTED...");
        this.sendListenerResult("CONNECTED");

        if (this.requestedPlay != null) {
            try {
                this.mRadioManager.startRadio(this.requestedPlay.getInt(0));
            } catch(JSONException e) {
            }

            this.requestedPlay = null;
        }
    }

    @Override
    public void onRadioStarted() {
        Log.e(LOG_TAG, "RADIO STATE: PLAYING...");
        this.sendListenerResult("STARTED");
    }

    @Override
    public void onRadioStopped() {
        Log.e(LOG_TAG, "RADIO STATE: STOPPED...");
        this.sendListenerResult("STOPPED");
    }

    @Override
    public void onRadioStoppedFocusLoss() {
        Log.e(LOG_TAG, "RADIO STATE: STOPPED FOCUS LOSS...");
        this.sendListenerResult("STOPPED_FOCUS_LOSS");
    }

    @Override
    public void onError() {
        this.sendListenerResult("ERROR");
    }

    private void sendListenerResult(String result) {
        if (this.connectionCallbackContext != null) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);

            pluginResult.setKeepCallback(true);
            this.connectionCallbackContext.sendPluginResult(pluginResult);
        }
    }
}
