# cordova-plugin-multi-player

This plugin provides an implementation of an Android service library which uses Google's ExoPlayer. Ready to use Streaming Player Service. (Background Player Service).
This plugin has part of its code base on [cordova-plugin-exoplayer](https://github.com/frontyard/cordova-plugin-exoplayer), with a focus on audio streaming and keeping the audio active on background.

## Supported Platforms

- Android
- Browser (new)
- iOS

## Installation

```sh
cordova plugin add cordova-plugin-multi-player
```

### Optional dependency

It's recommended to use the plugin [cordova-plugin-music-controls2](https://github.com/ghenry22/cordova-plugin-music-controls2), since instead of creating a generic notification, to inform the user that there is a service in background, it uses the music controls plugin notification to do so.
```sh
cordova plugin add cordova-plugin-music-controls2
```

### Quick Example
```js
...
onDeviceReady: function() {
    var url = 'http://hayatmix.net/;yayin.mp3.m3u';

    navigator.multiPlayer.initialize(
        function (s) {
            console.log('SUCCESS navigator.multiPlayer.initialize');
            if (s == 'CONNECTED') {
                // the service responsible for playing was connected
            } else if (s == 'DISCONNECTED') {
                // the service responsible for playing was disconnected
            } else if (s == 'LOADING') {
                // the media is loading (called once every play call, not called on buffering content)
            } else if (s == 'STARTED') {
                // the media was successfully started playing
            } else if (s == 'STOPPED') {
                // the media was stopped
            } else if (s == 'STOPPED_FOCUS_TRANSIENT') {
                // the media was stopped after other app requested focus temporarily (Android/iOS only)
            } else if (s == 'STARTED_FOCUS_TRANSIENT') {
                // the media was auto started after regained facus (Android/iOS only)
            } else if (s == 'STOPPED_FOCUS_LOSS') {
                // the media was stopped after other app requested focus (Android/iOS only)
            } else if (s == 'ERROR') {
                // the media raised an error
            }
        },
        function (e) {
            console.log('ERROR navigator.multiPlayer.initialize');
        },
        // streaming url
        url,
        // Android Only (optional):
        //   on android 11+ usign MusicControls plugin, disconnect may not end the service and/or notification,
        //   this flag force cancel the MusicControls notification when the service is destroyed, enabling to terminate the process properlly
        true,
        // Browser only (optional):
        // timeout when the stream stall (in ms), will stop the stop the stream and trigger an "ERROR" event
        // this value is optional, if not provided, the stream can be stalled indefinitely
        5000
    );
}
...

// valid constants are 'STREAM_MUSIC' and 'STREAM_ALARM' (default: STREAM_MUSIC)
var streamType = navigator.multiPlayer.STREAM_ALARM;

// streamType parameter is not required
navigator.multiPlayer.play(function (s) {
    console.log('SUCCESS navigator.multiPlayer.play');
}, function (e) {
    console.log('ERROR navigator.multiPlayer.play');
}, streamType);

navigator.multiPlayer.stop(function (s) {
    console.log('SUCCESS navigator.multiPlayer.stop');
}, function (e) {
    console.log('ERROR navigator.multiPlayer.stop');
});

// initialize the service responsible for playing the stream
// this is not required, since its automatically connected when is called the play action
navigator.multiPlayer.connect(function (s) {
    console.log('SUCCESS navigator.multiPlayer.connect');
}, function (e) {
    console.log('ERROR navigator.multiPlayer.connect');
});

// close the service responsible for playing the stream
// this is not required, since its automatically disconnected when the app is closed
navigator.multiPlayer.disconnect(function (s) {
    console.log('SUCCESS navigator.multiPlayer.disconnect');
}, function (e) {
    console.log('ERROR navigator.multiPlayer.disconnect');
});
```

## Log Debug
```sh
adb logcat -s "LOG" -s "MultiPlayer"
```

## Libraries Used ##

[ExoPlayer Library](https://github.com/google/ExoPlayer)
