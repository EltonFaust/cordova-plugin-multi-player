package com.eltonfaust.multiplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
// import android.media.AudioAttributes;
// import android.media.AudioFocusRequest;
// import android.media.AudioManager;
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

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

// import com.google.android.exoplayer2.*;
// import com.google.android.exoplayer2.extractor.*;
// import com.google.android.exoplayer2.source.*;
// import com.google.android.exoplayer2.source.dash.*;
// import com.google.android.exoplayer2.source.hls.*;
// import com.google.android.exoplayer2.source.smoothstreaming.*;
// import com.google.android.exoplayer2.trackselection.*;
// import com.google.android.exoplayer2.ui.*;
// import com.google.android.exoplayer2.upstream.*;
// import com.google.android.exoplayer2.util.*;

import java.util.ArrayList;
import java.util.List;

public class RadioPlayerService extends Service {
    private static final String LOG_TAG = "MultiPlayer";

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
    private static final String NOTIFICATION_TEXT = "Playing in background";

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
     * Auto kill music controls notification on destroy
     */
    private boolean mRadioKillNotification = false;

    /**
     * Current radio Stream Type
     */
    private int mRadioStreamType = AudioManager.STREAM_MUSIC;

    /**
     * ExoPlayer
     */
    private ExoPlayer mRadioPlayer = null;

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
    // private com.google.android.exoplayer2.audio.AudioAttributes mPlayerAudioAttributes;

    private Notification serviceNotification = null;
    private int startWithNotificationID = 0;

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
        this.mListenerList = new ArrayList<RadioListener>();

        this.mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);

        this.mRadioState = State.IDLE;

        this.serviceNotification = null;
        this.startWithNotificationID = 0;

        // tries to get the Music Control notification
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                this.startForeground(startWithNotificationID, serviceNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                this.startForeground(startWithNotificationID, serviceNotification);
            }
        }

        PowerManager powerMgr = (PowerManager) this.getSystemService(POWER_SERVICE);

        this.wakeLock = powerMgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BackgroundMode");

        this.wakeLock.acquire();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.stopForeground(false);
        this.stopSelf();

        if (
            this.startWithNotificationID != 0
            && (
                this.mRadioKillNotification
                || this.startWithNotificationID == NOTIFICATION_ID
            )
        ) {
            NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(this.startWithNotificationID);
        }

        this.releasePlayer();

        if (this.wakeLock != null) {
            this.wakeLock.release();
            this.wakeLock = null;
        }

        this.log("destroy");
    }

    public void setStreamURL(String mRadioUrl) {
        this.mRadioUrl = mRadioUrl;
    }

    public void setAutoKillNotification(boolean mRadioKillNotification) {
        this.mRadioKillNotification = mRadioKillNotification;
    }

    /**
     * Play url if different from previous streaming url.
     *
     * @param streamType
     */
    public void play(int streamType) {
        notifyRadioLoading();

        boolean changeAudioStreamType = streamType != -1 && this.mRadioStreamType != streamType;

        if (streamType != -1) {
            this.mRadioStreamType = streamType;
        }

        int result = AudioManager.AUDIOFOCUS_REQUEST_FAILED;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result = this.requestAudioFocus(changeAudioStreamType);
        } else {
            result = this.mAudioManager.requestAudioFocus(this.audioFocusChangeListener, this.mRadioStreamType, AudioManager.AUDIOFOCUS_GAIN);
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            ExoPlayer player = RadioPlayerService.this.getPlayer(changeAudioStreamType);
            player.setVolume(1f);
            player.setPlayWhenReady(true);
        } else {
            this.log("Can't play streaming. Audio focus not granted");
            this.mRadioState = State.STOPPED_FOCUS_LOSS;
            this.releasePlayer();
            this.notifyRadioStoppedFocusLoss();
        }
    }

    public void play() {
        this.play(-1);
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
        this.mListenerList.add(mListener);
    }

    public void unregisterListener(RadioListener mListener) {
        this.mListenerList.remove(mListener);
    }

    public void setListener(RadioListener mListener) {
        this.mListenerList.clear();
        this.mListenerList.add(mListener);
    }

    private void notifyRadioLoading() {
        for (RadioListener mRadioListener : this.mListenerList) {
            mRadioListener.onRadioLoading();
        }
    }

    private void notifyRadioStarted() {
        for (RadioListener mRadioListener : this.mListenerList) {
            mRadioListener.onRadioStarted();
        }
    }

    private void notifyRadioStopped() {
        for (RadioListener mRadioListener : this.mListenerList) {
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
     * Return ExoPlayer instance. If it is not initialized, creates and returns.
     *
     * @return ExoPlayer
     */
    private ExoPlayer getPlayer(boolean changeAudioStreamType) {
        if (this.mRadioPlayer == null) {
            int audioUsageType = this.mRadioStreamType == AudioManager.STREAM_ALARM
                ? C.AudioUsage.USAGE_ALARM
                : C.AudioUsage.USAGE_MEDIA;

            DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this.getApplicationContext(), "CordovaMultiPlayer");
            ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();

            ExoPlayer player = new ExoPlayer.Builder(dataSourceFactory, extractorsFactory)
                .setMediaSourceFactory(
                    new DefaultMediaSourceFactory(this.getApplicationContext())
                        .setLiveTargetOffsetMs(5000)
                )
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(audioUsageType)
                        .setContentType(C.AudioContentType.AUDIO_CONTENT_TYPE_MUSIC)
                        .build()
                )
                .build();

            // Per MediaItem settings.
            MediaItem mediaItem = new MediaItem.Builder()
                .setUri(Uri.parse(this.mRadioUrl))
                .setLiveConfiguration(
                    new MediaItem.LiveConfiguration.Builder()
                        .setMaxPlaybackSpeed(1.02f)
                        .build()
                )
                .build();

            player.setMediaItem(mediaItem);
            player.addListener(this.playerEventListener);
            player.prepare();

            // DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
            // TrackSelector trackSelector = new DefaultTrackSelector();
            // LoadControl loadControl = new DefaultLoadControl();

            // this.mRadioPlayer = ExoPlayerFactory.newSimpleInstance(this.getApplicationContext(), trackSelector, loadControl);
            // this.mRadioPlayer.addListener(this.playerEventListener);

            // DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(getApplicationContext(), "CordovaMultiPlayer");
            // ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();

            // Handler mainHandler = new Handler();
            // MediaSource mediaSource = new ExtractorMediaSource(Uri.parse(this.mRadioUrl), dataSourceFactory, extractorsFactory, mainHandler, null);

            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //     this.setPlayerAudioAttributes(changeAudioStreamType);
            // } else {
            //     this.mRadioPlayer.setAudioStreamType(this.mRadioStreamType);
            // }

            // this.mRadioPlayer.prepare(mediaSource);
        } else if (changeAudioStreamType) {
            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            //     this.setPlayerAudioAttributes(changeAudioStreamType);
            // } else {
            //     this.mRadioPlayer.setAudioStreamType(this.mRadioStreamType);
            // }
        }

        return this.mRadioPlayer;
    }

    private ExoPlayer getPlayer() {
        return this.getPlayer(false);
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

    private ExoPlayer.Listener playerEventListener = new ExoPlayer.Listener() {
        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            RadioPlayerService.this.log("Playback parameters changed");
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                ExoPlayer player = RadioPlayerService.this.getPlayer();
                player.seekToDefaultPosition();
                player.prepare();
                RadioPlayerService.this.log("FELL BEHIND, RE-INITIALIZING AT THE LIVE EDGE..");
            } else {
                RadioPlayerService.this.releasePlayer();
                RadioPlayerService.this.notifyErrorOccured();
                RadioPlayerService.this.log("ERROR OCCURED.");
            }
        }

        // @Override
        // public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        //     if (playWhenReady && playbackState == ExoPlayer.STATE_READY && RadioPlayerService.this.mRadioState != State.PLAYING) {
        //         // The player is only playing if the state is Player.STATE_READY and playWhenReady=true
        //         RadioPlayerService.this.log("Player state changed. Playing");
        //         RadioPlayerService.this.mRadioState = State.PLAYING;
        //         RadioPlayerService.this.notifyRadioStarted();
        //     } else if (playbackState == ExoPlayer.STATE_IDLE && RadioPlayerService.this.mRadioState == State.PLAYING) {
        //         // Player.STATE_IDLE: This is the initial state, the state when the player is stopped, and when playback failed.
        //         RadioPlayerService.this.log("Player state changed. Stopped");
        //         RadioPlayerService.this.releasePlayer();
        //         RadioPlayerService.this.notifyRadioStopped();
        //     } else if (playbackState == ExoPlayer.STATE_IDLE && RadioPlayerService.this.mRadioState == State.STOPPED_FOCUS_LOSS) {
        //         // focus loss, notify and set state to STOPPED
        //         RadioPlayerService.this.log("Player state changed. Stopped focus loss");
        //         RadioPlayerService.this.releasePlayer();
        //         RadioPlayerService.this.notifyRadioStoppedFocusLoss();
        //     } else {
        //         RadioPlayerService.this.log("Player state changed. ExoPlayer State: " + playbackState + ", Current state: " + RadioPlayerService.this.mRadioState);
        //     }
        // }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == ExoPlayer.STATE_IDLE && RadioPlayerService.this.mRadioState == State.PLAYING) {
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
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            if (playWhenReady && reason == ExoPlayer.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST && RadioPlayerService.this.mRadioState != State.PLAYING) {
                // The player is only playing if the state is Player.STATE_READY and playWhenReady=true
                RadioPlayerService.this.log("Player state changed. Playing");
                RadioPlayerService.this.mRadioState = State.PLAYING;
                RadioPlayerService.this.notifyRadioStarted();
            }
        }
    };

    // private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
    //     public void onAudioFocusChange(int focusChange) {
    //         if (RadioPlayerService.this.mRadioPlayer == null || RadioPlayerService.this.mRadioState != State.PLAYING) {
    //             return;
    //         }

    //         if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
    //             RadioPlayerService.this.mRadioPlayer.setVolume(0.2f);
    //         } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
    //             RadioPlayerService.this.mRadioPlayer.setVolume(1f);
    //         } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
    //             RadioPlayerService.this.mRadioState = State.STOPPED_FOCUS_LOSS;
    //             RadioPlayerService.this.stop();
    //         }
    //     }
    // };

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

    // @RequiresApi(api = Build.VERSION_CODES.O)
    // private int requestAudioFocus(boolean recreateAttribute) {
    //     if (this.mAudioAttributes == null || recreateAttribute) {
    //         int audioUsageType = this.mRadioStreamType == AudioManager.STREAM_ALARM ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_MEDIA;
    //         this.mAudioAttributes = new AudioAttributes.Builder().setUsage(audioUsageType).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    //     }

    //     if (this.mAudioFocusRequest == null || recreateAttribute) {
    //         this.mAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
    //             .setAudioAttributes(this.mAudioAttributes)
    //             .setAcceptsDelayedFocusGain(false)
    //             .setOnAudioFocusChangeListener(this.audioFocusChangeListener).build();
    //     }

    //     return this.mAudioManager.requestAudioFocus(this.mAudioFocusRequest);

    // }

    // @RequiresApi(api = Build.VERSION_CODES.O)
    // private int abandonAudioFocus() {
    //     if (this.mAudioFocusRequest != null) {
    //         return this.mAudioManager.abandonAudioFocusRequest(this.mAudioFocusRequest);
    //     }

    //     return AudioManager.AUDIOFOCUS_REQUEST_FAILED;
    // }

    // @RequiresApi(api = Build.VERSION_CODES.O)
    // private void setPlayerAudioAttributes(boolean recreate) {
    //     if (this.mRadioPlayer != null) {
    //         if (this.mPlayerAudioAttributes == null || recreate) {
    //             int audioUsageType = this.mRadioStreamType == AudioManager.STREAM_ALARM ? AudioAttributes.USAGE_ALARM : AudioAttributes.USAGE_MEDIA;
    //             this.mPlayerAudioAttributes = new com.google.android.exoplayer2.audio.AudioAttributes.Builder()
    //                 .setUsage(audioUsageType).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    //         }

    //         this.mRadioPlayer.setAudioAttributes(this.mPlayerAudioAttributes);
    //     }
    // }

    /**
     * Logger
     *
     * @param log
     */
    private void log(String log) {
        Log.v(LOG_TAG, "RadioPlayerService : " + log);
    }
}
