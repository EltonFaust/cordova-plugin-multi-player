package com.eltonfaust.multiplayer;

import android.util.Log;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

public class MultiPlayer extends CordovaPlugin implements RadioListener {
    private static final String LOG_TAG = "MultiPlayer";

    private RadioManager mRadioManager = null;
    private CallbackContext connectionCallbackContext;
    private boolean isConnecting = false;
    private boolean isConnected = false;
    private JSONArray requestedPlay = null;

    @Override
    public synchronized boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        log("ACTION - " + action);

        if ("initialize".equals(action)) {
            try {
                this.mRadioManager = RadioManager.with(this.cordova.getActivity(), this);
                this.mRadioManager.setStreamURL(args.getString(0));
                this.mRadioManager.setAutoKillNotification(args.getBoolean(1));

                this.connectionCallbackContext = callbackContext;

                PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
                pluginResult.setKeepCallback(true);

                callbackContext.sendPluginResult(pluginResult);
            } catch (Exception e) {
                log("Exception occurred during initialize: ".concat(e.getMessage()));
                callbackContext.error(e.getMessage());
            }
        } else if ("connect".equals(action)) {
            if (!this.isConnected && !this.isConnecting) {
                this.isConnecting = true;

                try {
                    this.mRadioManager.connect();
                } catch (Exception e) {
                    log("Exception occurred during connect: ".concat(e.getMessage()));
                    this.isConnecting = false;
                    callbackContext.error(e.getMessage());
                    return true;
                }
            }

            callbackContext.success();
        } else if ("disconnect".equals(action)) {
            this.requestedPlay = null;
            boolean canDisconnect = this.isConnecting || this.isConnected;

            if (canDisconnect) {
                try {
                    this.isConnecting = false;
                    this.isConnected = false;
                    this.mRadioManager.disconnect();
                } catch (Exception e) {
                    log("Exception occurred during disconnect: ".concat(e.getMessage()));
                    callbackContext.error(e.getMessage());
                    return true;
                }
            }

            callbackContext.success();

            if (canDisconnect) {
                log("RADIO STATE - DISCONNECTED...");
                this.sendListenerResult("DISCONNECTED");
            }
        } else if ("play".equals(action)) {
            if (!this.isConnected) {
                this.requestedPlay = args;

                if (!this.isConnecting) {
                    this.isConnecting = true;

                    try {
                        this.mRadioManager.connect();
                    } catch (Exception e) {
                        log("Exception occurred during play auto connect: ".concat(e.getMessage()));
                        this.isConnecting = false;
                        this.requestedPlay = null;
                        callbackContext.error(e.getMessage());
                        return true;
                    }
                }
            } else {
                this.requestedPlay = null;

                try {
                    this.mRadioManager.startRadio(args.getInt(0));
                } catch (Exception e) {
                    log("Exception occurred during play: ".concat(e.getMessage()));
                    callbackContext.error(e.getMessage());
                    return true;
                }
            }

            callbackContext.success();
        } else if ("stop".equals(action)) {
            this.requestedPlay = null;

            if (this.isConnected) {
                try {
                    this.mRadioManager.stopRadio();
                } catch (Exception e) {
                    log("Exception occurred during stop: ".concat(e.getMessage()));
                    callbackContext.error(e.getMessage());
                    return true;
                }
            }

            callbackContext.success();
        } else {
            log("Called invalid action: " + action);
            return false;
        }

        return true;
    }

    @Override
    public void onRadioLoading() {
        log("RADIO STATE - LOADING...");
        this.sendListenerResult("LOADING");
    }

    @Override
    public void onRadioConnected() {
        this.isConnecting = false;
        this.isConnected = true;

        log("RADIO STATE - CONNECTED...");
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
    public void onRadioDisconnected() {
        this.isConnecting = false;
        this.isConnected = false;
        this.requestedPlay = null;

        log("RADIO STATE - DISCONNECTED...");
        this.sendListenerResult("DISCONNECTED");
    }

    @Override
    public void onRadioStarted() {
        log("RADIO STATE - PLAYING...");
        this.sendListenerResult("STARTED");
    }

    @Override
    public void onRadioStopped() {
        log("RADIO STATE - STOPPED...");
        this.sendListenerResult("STOPPED");
    }

    @Override
    public void onRadioStoppedFocusLoss() {
        log("RADIO STATE - STOPPED FOCUS LOSS...");
        this.sendListenerResult("STOPPED_FOCUS_LOSS");
    }

    @Override
    public void onError() {
        log("RADIO STATE - ERROR...");
        this.sendListenerResult("ERROR");
    }

    @Override
    public void onDestroy() {
        if (this.mRadioManager != null) {
            this.mRadioManager.disconnect();
        }
    }

    private void sendListenerResult(String result) {
        if (this.connectionCallbackContext != null) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);

            pluginResult.setKeepCallback(true);
            this.connectionCallbackContext.sendPluginResult(pluginResult);
        }
    }

    /**
     * Logger
     * @param log
     */
    private void log(String log) {
        Log.v(LOG_TAG, "Plugin : " + log);
    }
}
