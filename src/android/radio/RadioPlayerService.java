package com.eltonfaust.multiplayer;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.RemoteViews;

import com.spoledge.aacdecoder.MultiPlayer;
import com.spoledge.aacdecoder.PlayerCallback;

import java.util.ArrayList;
import java.util.List;


/**
 * Created by mertsimsek on 01/07/15.
 */
public class RadioPlayerService extends Service implements PlayerCallback {

    /**
     * Logging control variable
     */
    private static boolean isLogging = false;

    /**
     * Radio buffer and decode capacity(DEFAULT VALUES)
     */
    private final int AUDIO_BUFFER_CAPACITY_MS = 800;
    private final int AUDIO_DECODE_CAPACITY_MS = 400;

    /**
     * Stream url suffix
     */
    private final String SUFFIX_PLS = ".pls";
    private final String SUFFIX_RAM = ".ram";
    private final String SUFFIX_WAX = ".wax";

    /**
     * State enum for Radio Player state (IDLE, PLAYING, STOPPED, INTERRUPTED)
     */
    public enum State {
        IDLE,
        PLAYING,
        STOPPED,
    }

    List<RadioListener> mListenerList;

    /**
     * Radio State
     */
    private State mRadioState;

    /**
     * Current radio URL
     */
    private String mRadioUrl;

    /**
     * Current radio Volume
     */
    private int mRadioVolume = 100;

    /**
     * Current radio Stream Type
     */
    private int mRadioStreamType = -1;

    /**
     * Stop action. If another mediaplayer will start.It needs
     * to send broadcast to stop this service.
     */
    public static final String ACTION_MEDIAPLAYER_STOP = "co.mobiwise.library.ACTION_STOP_MEDIAPLAYER";

    /**
     * AAC Radio Player
     */
    private MultiPlayer mRadioPlayer;

    /**
     * Android audio track
     */
    private AudioTrack audioTrack;

    /**
     * Will be controlled on incoming calls and stop and start player.
     */
    private TelephonyManager mTelephonyManager;

    /**
     * While current radio playing, if you give another play command with different
     * source, you need to stop it first. This value is responsible for control
     * after radio stopped.
     */
    private boolean isSwitching;

    /**
     * Incoming calls interrupt radio if it is playing.
     * Check if this is true or not after hang up;
     */
    private boolean isInterrupted;

    /**
     * If play method is called repeatedly, AAC Decoder will be failed.
     * play and stop methods will be turned mLock = true when they called,
     *
     * @onRadioStarted and @onRadioStopped methods will be release lock.
     */
    private boolean mLock;

    /**
     * Binder
     */
    public final IBinder mLocalBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mLocalBinder;
    }

    /**
     * Binder
     */
    public class LocalBinder extends Binder {
        public RadioPlayerService getService() {
            return RadioPlayerService.this;
        }
    }

    /**
     * Service called
     *
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mListenerList = new ArrayList<RadioListener>();

        mRadioState = State.IDLE;
        isSwitching = false;
        isInterrupted = false;
        mLock = false;
        getPlayer();

        mTelephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);

        if (mTelephonyManager != null) {
            mTelephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    /**
     * Play url if different from previous streaming url.
     *
     * @param mRadioUrl
     */
    public void play(String mRadioUrl, int volume, int streamType) {
        sendBroadcast(new Intent(ACTION_MEDIAPLAYER_STOP));

        notifyRadioLoading();

        if (volume != -1) {
            this.mRadioVolume = this.parseVolume(volume);
        }

        this.mRadioStreamType = streamType;

        if (checkSuffix(mRadioUrl)) {
            decodeStremLink(mRadioUrl);
        } else {
            this.mRadioUrl = mRadioUrl;
            isSwitching = false;
            isInterrupted = false;
            this.start();
        }
    }

    public void play(String mRadioUrl, int volume) {
        this.play(mRadioUrl, volume, this.mRadioStreamType);
    }

    public void play(String mRadioUrl) {
        this.play(mRadioUrl, -1, this.mRadioStreamType);
    }

    public void stop() {
        this.isInterrupted = false;
        this.finish();
    }

    private void start() {
        if (isPlaying()) {
            log("Switching Radio");
            isSwitching = true;
            stop();
        } else if (!mLock) {
            log("Play requested.");
            mLock = true;
            getPlayer().playAsync(this.mRadioUrl);
        }
    }

    private void finish() {
        if (!mLock && mRadioState != State.STOPPED) {
            log("Stop requested.");
            mLock = true;
            getPlayer().stop();
        }
    }

    public void setVolume(int volume) {
        this.mRadioVolume = this.parseVolume(volume);
        this.updateTrackVolume();
    }

    private void updateTrackVolume() {
        if (this.audioTrack != null) {
            this.audioTrack.setVolume(this.mRadioVolume * 0.01f);
        }
    }

    @Override
    public void playerStarted() {
        mRadioState = State.PLAYING;
        mLock = false;

        log("Player started. State : " + mRadioState);

        if (isInterrupted) {
            isInterrupted = false;
        } else {
            notifyRadioStarted();
        }
    }

    public boolean isPlaying() {
        if (State.PLAYING == mRadioState) {
            return true;
        }

        return false;
    }

    @Override
    public void playerPCMFeedBuffer(boolean b, int i, int i1) {
        //Empty
    }

    @Override
    public void playerStopped(int i) {
        mRadioState = State.STOPPED;
        mLock = false;

        log("Player stopped. State : " + mRadioState);

        if (isSwitching) {
            play(mRadioUrl);
        } else {
            if (isInterrupted) {
                isInterrupted = false;
            } else {
                notifyRadioStopped();
            }
        }
    }

    @Override
    public void playerException(Throwable throwable) {
        mLock = false;
        isInterrupted = false;
        mRadioPlayer = null;
        getPlayer();
        notifyErrorOccured();
        log("ERROR OCCURED.");
    }

    @Override
    public void playerMetadata(String s, String s2) {
        notifyMetaDataChanged(s, s2);
    }

    @Override
    public void playerAudioTrackCreated(AudioTrack audioTrack) {
        this.audioTrack = audioTrack;
        this.updateTrackVolume();
    }

    public void registerListener(RadioListener mListener) {
        mListenerList.add(mListener);
    }

    public void unregisterListener(RadioListener mListener) {
        mListenerList.remove(mListener);
    }

    private void notifyRadioStarted() {
        for (RadioListener mRadioListener : mListenerList) {
            mRadioListener.onRadioStarted();
        }
    }

    private void notifyRadioStopped() {
        for (RadioListener mRadioListener : mListenerList) {
            mRadioListener.onRadioStopped();
        }
    }

    private void notifyMetaDataChanged(String s, String s2) {
        for (RadioListener mRadioListener : mListenerList) {
            mRadioListener.onMetaDataReceived(s, s2);
        }
    }

    private void notifyRadioLoading() {
        for (RadioListener mRadioListener : mListenerList) {
            mRadioListener.onRadioLoading();
        }
    }

    private void notifyErrorOccured(){
        for (RadioListener mRadioListener : mListenerList) {
            mRadioListener.onError();
        }
    }

    /**
     * Return AAC player. If it is not initialized, creates and returns.
     *
     * @return MultiPlayer
     */
    private MultiPlayer getPlayer() {
        try {
            java.net.URL.setURLStreamHandlerFactory(new java.net.URLStreamHandlerFactory() {
                public java.net.URLStreamHandler createURLStreamHandler(String protocol) {
                    Log.d("LOG", "Asking for stream handler for protocol: '" + protocol + "'");

                    if ("icy".equals(protocol)) {
                        return new com.spoledge.aacdecoder.IcyURLStreamHandler();
                    }

                    return null;
                }
            });
        } catch (Throwable t) {
            Log.w("LOG", "Cannot set the ICY URLStreamHandler - maybe already set ? - " + t);
        }

        if (mRadioPlayer == null) {
            mRadioPlayer = new MultiPlayer(this, AUDIO_BUFFER_CAPACITY_MS, AUDIO_DECODE_CAPACITY_MS);
            mRadioPlayer.setResponseCodeCheckEnabled(false);
            mRadioPlayer.setPlayerCallback(this);
            mRadioPlayer.setStreamType(this.mRadioStreamType);
        }

        return mRadioPlayer;
    }

    PhoneStateListener phoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (state == TelephonyManager.CALL_STATE_RINGING) {
                /**
                 * Stop radio and set interrupted if it is playing on incoming call.
                 */
                if (isPlaying()) {
                    isInterrupted = true;
                    finish();
                }
            } else if (state == TelephonyManager.CALL_STATE_IDLE) {
                /**
                 * Keep playing if it is interrupted.
                 */
                if (isInterrupted) {
                    start();
                }
            } else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                /**
                 * Stop radio and set interrupted if it is playing on outgoing call.
                 */
                if (isPlaying()) {
                    isInterrupted = true;
                    finish();
                }
            }

            super.onCallStateChanged(state, incomingNumber);
        }
    };

    /**
     * Check supported suffix
     *
     * @param streamUrl
     * @return
     */
    public boolean checkSuffix(String streamUrl) {
        if (
            streamUrl.contains(SUFFIX_PLS) ||
            streamUrl.contains(SUFFIX_RAM) ||
            streamUrl.contains(SUFFIX_WAX)
        ) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Enable/Disable log
     *
     * @param logging
     */
    public void setLogging(boolean logging) {
        isLogging = logging;
    }

    /**
     * Logger
     *
     * @param log
     */
    private void log(String log) {
        if (isLogging) {
            Log.v("RadioManager", "RadioPlayerService : " + log);
        }
    }

    /**
     * If stream link is a file, then we
     * call stream decoder to get HTTP stream link
     * from that file.
     *
     * @param streamLink
     */
    private void decodeStremLink(String streamLink) {
        new StreamLinkDecoder(streamLink) {
            @Override
            protected void onPostExecute(String s) {
                super.onPostExecute(s);
                play(s);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private int parseVolume(int volume) {
        if (volume > 100) {
            return 100;
        } else if(volume < 0) {
            return 0;
        }

        return volume;
    }
}
