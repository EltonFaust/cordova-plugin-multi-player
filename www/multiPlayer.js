var multiPlayer = function(){};

multiPlayer.prototype.initialize = function(successCallback, failureCallback) {
    cordova.exec(successCallback, failureCallback, 'RadioPlugin', 'initialize', [ ]);
};

multiPlayer.prototype.play = function(successCallback, failureCallback, url, volume, streamType) {
    if (typeof volume == "undefined") {
        volume = 100;
    }

    if (typeof streamType == "undefined") {
        streamType = -1;
    }

    cordova.exec(successCallback, failureCallback, 'RadioPlugin', 'play', [url, volume, streamType]);
};

multiPlayer.prototype.stop = function(successCallback, failureCallback) {
    cordova.exec(successCallback, failureCallback, 'RadioPlugin', 'stop', []);
};

multiPlayer.prototype.setVolume = setVolume: function(successCallback, failureCallback, volume) {
    cordova.exec(successCallback, failureCallback, 'RadioPlugin', 'setvolume', [volume]);
};

// https://developer.android.com/reference/android/media/AudioManager.html
multiPlayer.STREAM_MUSIC = 3;
multiPlayer.STREAM_ALARM = 4;

module.exports = multiPlayer;
