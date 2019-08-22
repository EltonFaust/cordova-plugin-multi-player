package com.eltonfaust.multiplayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by mertsimsek on 03/07/15.
 */
public class RadioManager implements IRadioManager {

    /**
     * Logging enable/disable
     */
    private static boolean isLogging = false;

    /**
     * Streaming url to listen
     */
    private static int streamType = AudioManager.STREAM_MUSIC;

    /**
     * Streaming url to listen
     */
    private static String streamURL;

    /**
     * Singleton
     */
    private static RadioManager instance = null;

    /**
     * RadioPlayerService
     */
    private static RadioPlayerService mService;

    /**
     * Context
     */
    private Context mContext;

    /**
     * Listeners
     */
    private List<RadioListener> mRadioListenerQueue;

    /**
     * Service connected/Disconnected lock
     */
    private boolean isServiceConnected;

    /**
     * Private constructor because of Singleton pattern
     * @param mContext
     */
    private RadioManager(Context mContext) {
        this.mContext = mContext;
        this.mRadioListenerQueue = new ArrayList<RadioListener>();
        this.isServiceConnected = false;
    }

    /**
     * Singleton
     * @param mContext
     * @return
     */
    public static RadioManager with(Context mContext) {
        if (instance == null) {
            instance = new RadioManager(mContext);
        }

        return instance;
    }

    /**
     * get current service instance
     * @return RadioPlayerService
     */
    public static RadioPlayerService getService() {
        return mService;
    }

    @Override
    public void setStreamURL(String streamURL) {
        this.streamURL = streamURL;
    }

    @Override
    public void startRadio() {
        this.startRadio(-1);
    }

    @Override
    public void startRadio(int streamType) {
        if (streamType != -1) {
            this.streamType = streamType;
        }

        this.mContext.setVolumeControlStream(this.streamType);

        this.mService.play(this.streamType);
    }

    /**
     * Stop Radio Streaming
     */
    @Override
    public void stopRadio() {
        this.mService.stop();
    }

    /**
     * Check if radio is playing
     * @return
     */
    @Override
    public boolean isPlaying() {
        log("IsPlaying : " + this.mService.isPlaying());
        return this.mService.isPlaying();
    }

    /**
     * Register listener to listen radio service actions
     * @param mRadioListener
     */
    @Override
    public void registerListener(RadioListener mRadioListener) {
        if (this.isServiceConnected) {
            this.mService.registerListener(mRadioListener);
        } else {
            this.mRadioListenerQueue.add(mRadioListener);
        }
    }

    /**
     * Unregister listeners
     * @param mRadioListener
     */
    @Override
    public void unregisterListener(RadioListener mRadioListener) {
        log("Register unregistered.");
        this.mService.unregisterListener(mRadioListener);
    }

    /**
     * Set/Unset Logging
     * @param logging
     */
    @Override
    public void setLogging(boolean logging) {
        this.isLogging = logging;
    }

    /**
     * Connect radio player service
     */
    @Override
    public void connect() {
        log("Requested to connect service.");
        Intent intent = new Intent(this.mContext, RadioPlayerService.class);
        this.mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Disconnect radio player service
     */
    @Override
    public void disconnect() {
        log("Service Disconnected.");
        this.mContext.unbindService(mServiceConnection);
    }

    /**
     * Connection
     */
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder binder) {
            log("Service Connected.");

            RadioManager.this.mService = ((RadioPlayerService.LocalBinder) binder).getService();
            RadioManager.this.mService.setLogging(RadioManager.this.isLogging);
            RadioManager.this.mService.setStreamURL(RadioManager.this.streamURL);
            RadioManager.this.isServiceConnected = true;

            if (!RadioManager.this.mRadioListenerQueue.isEmpty()) {
                for (RadioListener mRadioListener : RadioManager.this.mRadioListenerQueue) {
                    registerListener(mRadioListener);
                    mRadioListener.onRadioConnected();
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };

    /**
     * Logger
     * @param log
     */
    private void log(String log) {
        if (this.isLogging) {
            Log.v("RadioManager", "RadioManagerLog : " + log);
        }
    }
}
