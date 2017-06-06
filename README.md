# cordova-plugin-multi-player

This plugin provides an implementation of an Android service library which uses AAC Player. Ready to use Streaming Player Service. (Background Player Service).
This plugin is based on [cordova-plugin-streaming](https://github.com/mradosta/cordova-plugin-streaming) with a few ajusts.

## Supported Platforms

- Android


## Supported URLs

- http://xxxx:1232
- http://xxxx/abc.pls
- http://xxxx/abc.ram
- http://xxxx/abc.wax
- http://xxxx/abc.m4a
- http://xxxx/abc.mp3


## Installation

    cordova plugin add https://github.com/EltonFaust/cordova-plugin-multi-player.git


### Quick Example
```js
...
onDeviceReady: function() {
    navigator.multiPlayer.initialize(function(s) {
        console.log('SUCCESS navigator.multiPlayer.initialize');
        if (s == 'STARTED') {
            // the reproduction was successfully started
        } else if (s == 'STOPPED') {
            // the reproduction was stopped other than the notification
        }
    }, function(s) {
        console.log('ERROR navigator.multiPlayer.initialize');
    });
}
...


// volume and streamType parameters are not required

var url = 'http://hayatmix.net/;yayin.mp3.m3u';
// volume between 0 (silent) and 100 (default: 100)
var volume = 50; 
// valid constants are 'STREAM_MUSIC' and 'STREAM_ALARM' (default: STREAM_MUSIC)
var streamType = navigator.multiPlayer.STREAM_ALARM;

navigator.multiPlayer.play(function(s) {
    console.log('SUCCESS navigator.multiPlayer.play');
}, function(s) {
    console.log('ERROR navigator.multiPlayer.play');
}, url, volume, streamType);


navigator.multiPlayer.stop(function(s) {
    console.log('SUCCESS navigator.multiPlayer.stop');
}, function(s) {
    console.log('ERROR navigator.multiPlayer.stop');
});


var volume = 50; // volume between 0 (silent) and 100
navigator.multiPlayer.setVolume(function(s) {
    console.log('SUCCESS navigator.multiPlayer.setVolume');
}, function(s) {
    console.log('ERROR navigator.multiPlayer.setVolume');
}, volume);
```


## Libraries Used ##

[AAC Decoder Library](https://github.com/vbartacek/aacdecoder-android)



License
--------


    Copyright 2016 Martin Radosta.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
