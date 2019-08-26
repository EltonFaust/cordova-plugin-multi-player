# cordova-plugin-multi-player

This plugin provides an implementation of an Android service library which uses Google's ExoPlayer. Ready to use Streaming Player Service. (Background Player Service).
This plugin has part of its code base on [cordova-plugin-exoplayer](https://github.com/frontyard/cordova-plugin-exoplayer), with a focus on audio streaming and keeping the audio active on background.

## Supported Platforms

- Android
- iOS


## Installation

    cordova plugin add cordova-plugin-multi-player


### Quick Example
```js
...
onDeviceReady: function() {
    var url = 'http://hayatmix.net/;yayin.mp3.m3u';

    navigator.multiPlayer.initialize(function(s) {
        console.log('SUCCESS navigator.multiPlayer.initialize');
        if (s == 'CONNECTED') {
            // the service responsible for playing was connected
        } else if (s == 'LOADING') {
            // the media is loading (called once every play call, not called on buffering content)
        } else if (s == 'STARTED') {
            // the media was successfully started playing
        } else if (s == 'STOPPED') {
            // the media was stopped
        } else if (s == 'STOPPED_FOCUS_LOSS') {
            // the media was stopped after other app requested focus
        } else if (s == 'ERROR') {
            // the media raised an error
        }
    }, function(s) {
        console.log('ERROR navigator.multiPlayer.initialize');
    }, url);
}
...

// valid constants are 'STREAM_MUSIC' and 'STREAM_ALARM' (default: STREAM_MUSIC)
var streamType = navigator.multiPlayer.STREAM_ALARM;

// streamType parameter is not required
navigator.multiPlayer.play(function(s) {
    console.log('SUCCESS navigator.multiPlayer.play');
}, function(s) {
    console.log('ERROR navigator.multiPlayer.play');
}, streamType);


navigator.multiPlayer.stop(function(s) {
    console.log('SUCCESS navigator.multiPlayer.stop');
}, function(s) {
    console.log('ERROR navigator.multiPlayer.stop');
});
```

## Log Debug

    adb logcat -s "LOG" -s "RadioPlugin" -s "RadioManager" -s "AACPlayer" -s "BufferReader" -s "FlashAACInputStream" -s "IcyInputStream" -s "MP3Player" -s "MultiPlayer" -s "PCMFeed"

## Libraries Used ##

[ExoPlayer Library](https://github.com/google/ExoPlayer)


