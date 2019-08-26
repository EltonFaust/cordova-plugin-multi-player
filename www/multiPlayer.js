var multiPlayer = function() {};

multiPlayer.prototype.initialize = function(successCallback, failureCallback, url) {
    cordova.exec(successCallback, failureCallback, 'RadioPlugin', 'initialize', [ url ]);
};

multiPlayer.prototype.connect = function(successCallback, failureCallback) {
    cordova.exec(successCallback, failureCallback, 'RadioPlugin', 'connect', []);
};

multiPlayer.prototype.play = function(successCallback, failureCallback, streamType) {
    if (typeof streamType == 'undefined') {
        streamType = -1;
    }

    cordova.exec(successCallback, failureCallback, 'RadioPlugin', 'play', [ streamType ]);
};

multiPlayer.prototype.stop = function(successCallback, failureCallback) {
    cordova.exec(successCallback, failureCallback, 'RadioPlugin', 'stop', []);
};

// https://developer.android.com/reference/android/media/AudioManager.html
multiPlayer.STREAM_MUSIC = 3;
multiPlayer.STREAM_ALARM = 4;

module.exports = new multiPlayer();
