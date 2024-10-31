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
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        log("ACTION - " + action);

        if ("initialize".equals(action)) {
            synchronized (this) {
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
            }
        } else if ("connect".equals(action)) {
            // cordova.getActivity().runOnUiThread(new Runnable() {
            RadioManager.getRequestHandler().post(new Runnable() {
                public void run() {
                    synchronized (MultiPlayer.this) {
                        if (!isConnected && !isConnecting) {
                            isConnecting = true;

                            try {
                                mRadioManager.connect();
                            } catch (Exception e) {
                                log("Exception occurred during connect: ".concat(e.getMessage()));
                                isConnecting = false;
                                callbackContext.error(e.getMessage());
                                return;
                            }
                        }

                        callbackContext.success();
                    }
                }
            });

            return true;
        } else if ("disconnect".equals(action)) {
            RadioManager.getRequestHandler().post(new Runnable() {
                public void run() {
                    synchronized (MultiPlayer.this) {
                        requestedPlay = null;
                        boolean canDisconnect = isConnecting || isConnected;

                        if (canDisconnect) {
                            try {
                                isConnecting = false;
                                isConnected = false;
                                mRadioManager.disconnect();
                            } catch (Exception e) {
                                log("Exception occurred during disconnect: ".concat(e.getMessage()));
                                callbackContext.error(e.getMessage());
                                return;
                            }
                        }

                        callbackContext.success();

                        if (canDisconnect) {
                            log("RADIO STATE - DISCONNECTED...");
                            sendListenerResult("DISCONNECTED");
                        }
                    }
                }
            });

            return true;
        } else if ("play".equals(action)) {
            RadioManager.getRequestHandler().post(new Runnable() {
                public void run() {
                    synchronized (MultiPlayer.this) {
                        if (!isConnected) {
                            requestedPlay = args;

                            if (!isConnecting) {
                                isConnecting = true;

                                try {
                                    mRadioManager.connect();
                                } catch (Exception e) {
                                    log("Exception occurred during play auto connect: ".concat(e.getMessage()));
                                    isConnecting = false;
                                    requestedPlay = null;
                                    callbackContext.error(e.getMessage());
                                    return;
                                }
                            }
                        } else {
                            requestedPlay = null;

                            try {
                                mRadioManager.startRadio(args.getInt(0));
                            } catch (Exception e) {
                                log("Exception occurred during play: ".concat(e.getMessage()));
                                callbackContext.error(e.getMessage());
                                return;
                            }
                        }

                        callbackContext.success();
                    }
                }
            });

            return true;
        } else if ("stop".equals(action)) {
            RadioManager.getRequestHandler().post(new Runnable() {
                public void run() {
                    synchronized (MultiPlayer.this) {
                        requestedPlay = null;

                        if (isConnected) {
                            try {
                                mRadioManager.stopRadio();
                            } catch (Exception e) {
                                log("Exception occurred during stop: ".concat(e.getMessage()));
                                callbackContext.error(e.getMessage());
                                return;
                            }
                        }

                        callbackContext.success();
                    }
                }
            });

            return true;
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
    public void onRadioStoppedFocusTransient() {
        log("RADIO STATE - STOPPED FOCUS TRANSIENT...");
        this.sendListenerResult("STOPPED_FOCUS_TRANSIENT");
    }

    @Override
    public void onRadioStartedFocusTransient() {
        log("RADIO STATE - STARTED FOCUS TRANSIENT...");
        this.sendListenerResult("STARTED_FOCUS_TRANSIENT");
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
