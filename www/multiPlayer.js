var MultiPlayer = (function () {
    function MultiPlayerConstruct() {
        // https://developer.android.com/reference/android/media/AudioManager.html
        this.STREAM_MUSIC = 3;
        this.STREAM_ALARM = 4;
    }

    MultiPlayerConstruct.prototype.initialize = function (successCallback, failureCallback, url) {
        cordova.exec(successCallback, failureCallback, 'MultiPlayer', 'initialize', [ url ]);
    };

    MultiPlayerConstruct.prototype.connect = function (successCallback, failureCallback) {
        cordova.exec(successCallback, failureCallback, 'MultiPlayer', 'connect', []);
    };

    MultiPlayerConstruct.prototype.disconnect = function (successCallback, failureCallback) {
        cordova.exec(successCallback, failureCallback, 'MultiPlayer', 'disconnect', []);
    };

    MultiPlayerConstruct.prototype.play = function (successCallback, failureCallback, streamType) {
        if (typeof streamType == 'undefined') {
            streamType = -1;
        }

        cordova.exec(successCallback, failureCallback, 'MultiPlayer', 'play', [ streamType ]);
    };

    MultiPlayerConstruct.prototype.stop = function(successCallback, failureCallback) {
        cordova.exec(successCallback, failureCallback, 'MultiPlayer', 'stop', []);
    };

    MultiPlayerConstruct.prototype.getDuration = function(successCallback, failureCallback) {
        cordova.exec(successCallback, failureCallback, 'MultiPlayer', 'getDuration', []);
    };

    return new MultiPlayerConstruct();
})();

module.exports = MultiPlayer;
