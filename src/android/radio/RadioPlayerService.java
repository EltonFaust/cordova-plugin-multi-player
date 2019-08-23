package com.eltonfaust.multiplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.service.notification.StatusBarNotification;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.RemoteViews;

import com.google.android.exoplayer2.*;
import com.google.android.exoplayer2.extractor.*;
import com.google.android.exoplayer2.source.*;
import com.google.android.exoplayer2.source.dash.*;
import com.google.android.exoplayer2.source.hls.*;
import com.google.android.exoplayer2.source.smoothstreaming.*;
import com.google.android.exoplayer2.trackselection.*;
import com.google.android.exoplayer2.ui.*;
import com.google.android.exoplayer2.upstream.*;
import com.google.android.exoplayer2.util.*;

import java.util.ArrayList;
import java.util.List;

/*
LINKS REMOVER:
https://github.com/wseemann/FFmpegMediaPlayer

https://exoplayer.dev/
https://github.com/google/ExoPlayer/tree/release-v2/library/core/src/main/java/com/google/android/exoplayer2
https://exoplayer.dev/doc/reference/com/google/android/exoplayer2/SimpleExoPlayer.html#setVolume-float-
https://github.com/nzkozar/ExoplayerExample
https://github.com/frontyard/cordova-plugin-exoplayer/blob/2.0.0/plugin.xml
https://github.com/frontyard/cordova-plugin-exoplayer/blob/2.0.0/src/android/Player.java
https://www.npmjs.com/package/cordova-plugin-exoplayer
*/

public class RadioPlayerService extends Service {

    /**
     * Logging control variable
     */
    private static boolean isLogging = false;

    /**
     * Radio buffer and decode capacity(DEFAULT VALUES)
     */
    private final int AUDIO_BUFFER_CAPACITY_MS = 800;
    private final int AUDIO_DECODE_CAPACITY_MS = 400;

    // Music Control plugin notification id
    public static final int MUSIC_CONTROL_NOTIFICATION = 7824;

    // ID for the 'foreground' notification channel
    public static final String NOTIFICATION_CHANNEL_ID = "cordova-plugin-multi-player-id";

    // ID for the 'foreground' notification
    public static final int NOTIFICATION_ID = 20190517;

    // Default title of the background notification
    private static final String NOTIFICATION_TITLE = "App is running in background";

    // Default text of the background notification
    private static final String NOTIFICATION_TEXT = "Doing heavy tasks.";

    /**
     * State enum for Radio Player state (IDLE, PLAYING, STOPPED, INTERRUPTED)
     */
    public enum State {
        IDLE,
        PLAYING,
        STOPPED,
        STOPPED_FOCUS_LOSS,
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
     * Current radio Stream Type
     */
    private int mRadioStreamType = AudioManager.STREAM_MUSIC;

    /**
     * SimpleExoPlayer
     */
    private SimpleExoPlayer mRadioPlayer = null;

    /**
     * Binder
     */
    public final IBinder mLocalBinder = new LocalBinder();

    /**
     * AudioManager
     */
    private AudioManager mAudioManager;

    /**
     * Partial wake lock to prevent the app from going to sleep when locked
     */
    private PowerManager.WakeLock wakeLock;

    /**
     * AudioFocusRequest
     */
    private AudioFocusRequest mAudioFocusRequest;

    /**
     * AudioAttributes
     */
    private AudioAttributes mAudioAttributes;

    /**
     * com.google.android.exoplayer2.audio.AudioAttributes
     */
    private com.google.android.exoplayer2.audio.AudioAttributes mPlayerAudioAttributes;


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

        this.mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);

        this.mRadioState = State.IDLE;

        Notification serviceNotification = null;
        int startWithNotificationID = 0;

        // tryes to get the Music Control notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            serviceNotification = this.getActiveNotification(MUSIC_CONTROL_NOTIFICATION);

            if (serviceNotification != null) {
                startWithNotificationID = MUSIC_CONTROL_NOTIFICATION;
            }
        }

        if (serviceNotification == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                // android 8.1 requires that the notification is assigned to a channel
                // and it not allows to initialize a hidden notification
                serviceNotification = this.createNotification();
            } else {
                // code based on plugin cordova-plugin-background-mode
                Notification.Builder notificationBuilder = new Notification.Builder(this.getApplicationContext())
                        .setContentTitle(NOTIFICATION_TITLE)
                        .setContentText(NOTIFICATION_TEXT)
                        .setOngoing(true)
                        .setPriority(Notification.PRIORITY_MIN);

                serviceNotification = notificationBuilder.build();
            }

            startWithNotificationID = NOTIFICATION_ID;
        }

        if (serviceNotification != null) {
            this.startForeground(startWithNotificationID, serviceNotification);
        }

        PowerManager powerMgr = (PowerManager) this.getSystemService(POWER_SERVICE);

        this.wakeLock = powerMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BackgroundMode");

        this.wakeLock.acquire();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.stopForeground(true);

        NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);

        this.releasePlayer();

        if (this.wakeLock != null) {
            this.wakeLock.release();
            this.wakeLock = null;
        }
    }

    public void setStreamURL(String mRadioUrl) {
        this.mRadioUrl = mRadioUrl;
    }

    /**
     * Play url if different from previous streaming url.
     *
     * @param streamType
     */
    public void play(int streamType) {
        notifyRadioLoading();

        boolean changeAudioStreamType = streamType != -1 && this.mRadioStreamType != streamType;

        SimpleExoPlayer player = this.getPlayer(streamType);

        int result = AudioManager.AUDIOFOCUS_REQUEST_FAILED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result = this.requestAudioFocus(changeAudioStreamType);
        } else {
            result = this.mAudioManager.requestAudioFocus(this.audioFocusChangeListener, this.mRadioStreamType, AudioManager.AUDIOFOCUS_GAIN);
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            player.setVolume(1f);
            player.setPlayWhenReady(true);
        }
    }

    public void play() {
        this.play(this.mRadioStreamType);
    }

    public void stop() {
        if (this.mRadioPlayer == null) {
            this.notifyRadioStopped();
            return;
        }

        if (this.mRadioState == State.PLAYING || this.mRadioState == State.STOPPED_FOCUS_LOSS) {
            this.mRadioPlayer.stop();
        }
    }

    public boolean isPlaying() {
        if (State.PLAYING == this.mRadioState) {
            return true;
        }

        return false;
    }

    public void registerListener(RadioListener mListener) {
        mListenerList.add(mListener);
    }

    public void unregisterListener(RadioListener mListener) {
        mListenerList.remove(mListener);
    }

    private void notifyRadioLoading() {
        for (RadioListener mRadioListener : mListenerList) {
            mRadioListener.onRadioLoading();
        }
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

    private void notifyRadioStoppedFocusLoss() {
        for (RadioListener mRadioListener : mListenerList) {
            mRadioListener.onRadioStoppedFocusLoss();
        }
    }

    private void notifyErrorOccured(){
        for (RadioListener mRadioListener : mListenerList) {
            mRadioListener.onError();
        }
    }

    /**
     * Return SimpleExoPlayer instance. If it is not initialized, creates and returns.
     *
     * @return SimpleExoPlayer
     */
    private SimpleExoPlayer getPlayer(int streamType) {
        boolean changeAudioStreamType = streamType != -1 && this.mRadioStreamType != streamType;

        if (streamType != -1) {
            this.mRadioStreamType = streamType;
        }

        if (this.mRadioPlayer == null) {
            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
            TrackSelector trackSelector = new DefaultTrackSelector();
            LoadControl loadControl = new DefaultLoadControl();

            this.mRadioPlayer = ExoPlayerFactory.newSimpleInstance(this.getApplicationContext(), trackSelector, loadControl);
            this.mRadioPlayer.addListener(this.playerEventListener);

            DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(getApplicationContext(), "CordovaMultiPlayer");
            ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();

            Handler mainHandler = new Handler();
            MediaSource mediaSource = new ExtractorMediaSource(Uri.parse(this.mRadioUrl), dataSourceFactory, extractorsFactory, mainHandler, null);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                this.setPlayerAudioAttributes(changeAudioStreamType);
            } else {
                this.mRadioPlayer.setAudioStreamType(this.mRadioStreamType);
            }

            this.mRadioPlayer.prepare(mediaSource);
        } else if (changeAudioStreamType) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                this.setPlayerAudioAttributes(changeAudioStreamType);
            } else {
                this.mRadioPlayer.setAudioStreamType(this.mRadioStreamType);
            }
        }

        return this.mRadioPlayer;
    }

    private SimpleExoPlayer getPlayer() {
        return this.getPlayer(-1);
    }

    private void releasePlayer() {
        if (this.mRadioPlayer != null) {
            this.mRadioState = State.STOPPED;
            this.mRadioPlayer.release();
            this.mRadioPlayer = null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.abandonAudioFocus();
        } else {
            this.mAudioManager.abandonAudioFocus(this.audioFocusChangeListener);
        }
    }

    private ExoPlayer.EventListener playerEventListener = new ExoPlayer.EventListener() {
        @Override
        public void onLoadingChanged(boolean isLoading) {
        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            log("Playback parameters changed");
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            RadioPlayerService.this.releasePlayer();
            RadioPlayerService.this.notifyErrorOccured();
            RadioPlayerService.this.log("ERROR OCCURED.");
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if (playWhenReady && playbackState == ExoPlayer.STATE_READY && RadioPlayerService.this.mRadioState != State.PLAYING) {
                // The player is only playing if the state is Player.STATE_READY and playWhenReady=true
                RadioPlayerService.this.log("Player state changed. Playing");
                RadioPlayerService.this.mRadioState = State.PLAYING;
                RadioPlayerService.this.notifyRadioStarted();
            } else if (playbackState == ExoPlayer.STATE_IDLE && RadioPlayerService.this.mRadioState == State.PLAYING) {
                // Player.STATE_IDLE: This is the initial state, the state when the player is stopped, and when playback failed.
                RadioPlayerService.this.log("Player state changed. Stopped");
                RadioPlayerService.this.releasePlayer();
                RadioPlayerService.this.notifyRadioStopped();
            } else if (playbackState == ExoPlayer.STATE_IDLE && RadioPlayerService.this.mRadioState == State.STOPPED_FOCUS_LOSS) {
                // focus loss, notify and set state to STOPPED
                RadioPlayerService.this.log("Player state changed. Stopped focus loss");
                RadioPlayerService.this.releasePlayer();
                RadioPlayerService.this.notifyRadioStoppedFocusLoss();
            } else {
                RadioPlayerService.this.log("Player state changed. ExoPlayer State: " + playbackState + ", Current state: " + RadioPlayerService.this.mRadioState);
            }
        }

        @Override
        public void onPositionDiscontinuity(int reason) {
        }

        @Override
        public void onRepeatModeChanged(int newRepeatMode) {
        }

        @Override
        public void onSeekProcessed() {
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
        }

        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest) {
        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        }
    };

    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            if (RadioPlayerService.this.mRadioPlayer == null || RadioPlayerService.this.mRadioState != State.PLAYING) {
                return;
            }

            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                RadioPlayerService.this.mRadioPlayer.setVolume(0.2f);
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                RadioPlayerService.this.mRadioPlayer.setVolume(1f);
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                RadioPlayerService.this.mRadioState = State.STOPPED_FOCUS_LOSS;
                RadioPlayerService.this.stop();
            }
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.M)
    private Notification getActiveNotification(int notificationId) {
        NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
        StatusBarNotification[] barNotifications = notificationManager.getActiveNotifications();

        for (StatusBarNotification notification: barNotifications) {
            if (notification.getId() == notificationId) {
                return notification.getNotification();
            }
        }

        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O_MR1)
    private Notification createNotification() {
        NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

        CharSequence name = "cordova-plugin-multi-player";
        String description = "cordova-plugin-multi-player notification";
        int importance = NotificationManager.IMPORTANCE_LOW;

        NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);

        notificationChannel.setDescription(description);
        notificationManager.createNotificationChannel(notificationChannel);

        Notification.Builder notificationBuilder = new Notification.Builder(this.getApplicationContext())
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setContentTitle(NOTIFICATION_TITLE)
                .setContentText(NOTIFICATION_TEXT)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_MIN);

        return notificationBuilder.build();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private int requestAudioFocus(boolean recreateAttribute) {
        if (this.mAudioAttributes == null || recreateAttribute) {
            int audioUsageType = this.mRadioStreamType == AudioManager.STREAM_ALARM ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_MEDIA;
            this.mAudioAttributes = new AudioAttributes.Builder().setUsage(audioUsageType).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        }

        if (this.mAudioFocusRequest == null || recreateAttribute) {
            this.mAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(this.mAudioAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(this.audioFocusChangeListener).build();
        }

        return this.mAudioManager.requestAudioFocus(this.mAudioFocusRequest);

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private int abandonAudioFocus() {
        if (this.mAudioFocusRequest != null) {
            return this.mAudioManager.abandonAudioFocusRequest(this.mAudioFocusRequest);
        }

        return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setPlayerAudioAttributes(boolean recreate) {
        if (this.mRadioPlayer != null) {
            if (this.mPlayerAudioAttributes == null || recreate) {
                int audioUsageType = this.mRadioStreamType == AudioManager.STREAM_ALARM ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_MEDIA;
                this.mPlayerAudioAttributes = new com.google.android.exoplayer2.audio.AudioAttributes.Builder()
                    .setUsage(audioUsageType).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
            }

            this.mRadioPlayer.setAudioAttributes(this.mPlayerAudioAttributes);
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
}
