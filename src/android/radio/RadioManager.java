package com.eltonfaust.multiplayer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.IBinder;
import android.util.Log;
import android.app.Activity;

import java.util.ArrayList;
import java.util.List;

public class RadioManager implements IRadioManager {
    private static final String LOG_TAG = "MultiPlayer";

    /**
     * Streaming url to listen
     */
    private static int streamType = AudioManager.STREAM_MUSIC;

    /**
     * Streaming url to listen
     */
    private static String streamURL = null;

    /**
     * Auto kill service music control notification
     */
    private static boolean autoKillNotification = false;

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
     * Context
     */
    private Context mAppContext;

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
        this.mAppContext = mContext.getApplicationContext();

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
     * Singleton
     * @param mContext
     * @param mRadioListener
     * @return
     */
    public static RadioManager with(Context mContext, RadioListener mRadioListener) {
        RadioManager instance = RadioManager.with(mContext);
        instance.setListener(mRadioListener);

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
    public void setAutoKillNotification(boolean autoKillNotification) {
        this.autoKillNotification = autoKillNotification;
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

        ((Activity) this.mContext).setVolumeControlStream(this.streamType);

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
        this.mRadioListenerQueue.add(mRadioListener);

        if (this.isServiceConnected) {
            this.mService.registerListener(mRadioListener);
        }
    }

    /**
     * Register listener to listen radio service actions
     * @param mRadioListener
     */
    @Override
    public void setListener(RadioListener mRadioListener) {
        this.mRadioListenerQueue.clear();
        this.mRadioListenerQueue.add(mRadioListener);

        if (this.isServiceConnected) {
            this.mService.setListener(mRadioListener);
        }
    }

    /**
     * Unregister listeners
     * @param mRadioListener
     */
    @Override
    public void unregisterListener(RadioListener mRadioListener) {
        log("Register unregistered.");
        this.mRadioListenerQueue.remove(mRadioListener);

        if (this.isServiceConnected) {
            this.mService.unregisterListener(mRadioListener);
        }
    }

    /**
     * Connect radio player service
     */
    @Override
    public void connect() {
        log("Requested to connect service.");
        Intent intent = new Intent(this.mAppContext, RadioPlayerService.class);
        this.mAppContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Disconnect radio player service
     */
    @Override
    public void disconnect() {
        log("Requested to disconnect service.");

        if (this.isServiceConnected) {
            this.mAppContext.unbindService(mServiceConnection);
            this.mService = null;
            this.isServiceConnected = false;
        }
    }

    /**
     * Connection
     */
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder binder) {
            log("Service Connected.");

            RadioManager.this.mService = ((RadioPlayerService.LocalBinder) binder).getService();
            RadioManager.this.mService.setStreamURL(RadioManager.this.streamURL);
            RadioManager.this.mService.setAutoKillNotification(RadioManager.this.autoKillNotification);
            RadioManager.this.isServiceConnected = true;

            for (RadioListener mRadioListener : RadioManager.this.mRadioListenerQueue) {
                RadioManager.this.mService.registerListener(mRadioListener);
                mRadioListener.onRadioConnected();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            log("Service Disconnected.");

            if (RadioManager.this.isServiceConnected) {
                for (RadioListener mRadioListener : RadioManager.this.mRadioListenerQueue) {
                    mRadioListener.onRadioConnected();

                    try {
                        RadioManager.this.mService.registerListener(mRadioListener);
                    } catch (Exception e) {
                        log("Can't complete service Disconnected.");
                    }
                }
            }

            RadioManager.this.mService = null;
            RadioManager.this.isServiceConnected = false;
        }
    };

    /**
     * Logger
     * @param log
     */
    private void log(String log) {
        Log.v(LOG_TAG, "RadioManager : " + log);
    }
}
