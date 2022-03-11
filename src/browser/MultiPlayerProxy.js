
var MultiPlayerProxy = (function () {
    function noop() { }
    // mp3 audio, most common support
    var blankAudio = 'data:audio/mpeg;base64,SUQzBAAAAAAAI1RTU0UAAAAPAAADTGF2ZjU4Ljc2LjEwMAAAAAAAAAAAAAAA/+M4wAAAAAAAAAAAAEluZm8AAAAPAAAAAwAAAbAAqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV////////////////////////////////////////////AAAAAExhdmM1OC4xMwAAAAAAAAAAAAAAACQDkAAAAAAAAAGw9wrNaQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/+MYxAAAAANIAAAAAExBTUUzLjEwMFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV/+MYxDsAAANIAAAAAFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV/+MYxHYAAANIAAAAAFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV';

    var audioEl;
    var sourceEl;
    var streamUrl;
    var sendListenerResult = noop;

    var isConnected = false;
    var isPlaying = false;
    var requestedInitPlaying = false;
    var requestedPlay = false;

    function unloadPlayer() {
        isPlaying = false;
        requestedInitPlaying = false;
        requestedPlay = false;

        // set a blank audio to force stop loading from streaming
        sourceEl.src = blankAudio;
        audioEl.load();
        audioEl.loop = true;

        var playPromise = audioEl.play();

        if (playPromise) {
            playPromise.then(function () {
                audioEl.pause();
            }).catch(function (e) {
                audioEl.pause();
            })
        } else {
            setTimeout(function () {
                audioEl.pause();
            });
        }
    }

    function sendErrorNotInitialized(failureCallback) {
        if (streamUrl) {
            return true;
        }

        failureCallback('NOT_INITIALIZED');
        return false;
    }

    function errorListener() {
        if (!sourceEl.src || sourceEl.src == blankAudio) {
            return;
        }

        unloadPlayer();
        sendListenerResult('ERROR');
    }

    function loadingListener() {
        if (!sourceEl.src || sourceEl.src == blankAudio) {
            return;
        }

        sendListenerResult('LOADING');
    }

    function playingListener() {
        if (!sourceEl.src || sourceEl.src == blankAudio) {
            return;
        }

        isPlaying = true;
        requestedInitPlaying = false;
        requestedPlay = false;

        sendListenerResult('STARTED');
    }

    function pausedListener() {
        if (!sourceEl.src || sourceEl.src == blankAudio) {
            return;
        }

        unloadPlayer();
        sendListenerResult('STOPPED');
    }

    function initialize(successCallback, failureCallback, params) {
        streamUrl = params[0];
        audioEl = window.document.createElement('audio');
        sourceEl = window.document.createElement('source');
        audioEl.appendChild(sourceEl);
        document.body.appendChild(audioEl);

        successCallback(null, { keepCallback: true, status: cordova.callbackStatus.NO_RESULT });

        sendListenerResult = function (message) {
            (successCallback || noop)(message, { keepCallback: true });
        };
    };

    function connect(successCallback, failureCallback) {
        if (!sendErrorNotInitialized(failureCallback) || isConnected) {
            return;
        }

        sourceEl.addEventListener('error', errorListener);
        audioEl.addEventListener('ended', errorListener);
        audioEl.addEventListener('playing', playingListener);
        audioEl.addEventListener('pause', pausedListener);
        audioEl.addEventListener('loadstart', loadingListener);

        isConnected = true;

        successCallback && successCallback();
        sendListenerResult('CONNECTED');

        if (requestedPlay) {
            requestedPlay = false;
            play(successCallback, failureCallback);
        }
    };

    function disconnect(successCallback, failureCallback) {
        if (!sendErrorNotInitialized(failureCallback) || !isConnected) {
            return;
        }

        sourceEl.removeEventListener('error', errorListener);
        audioEl.removeEventListener('ended', errorListener);
        audioEl.removeEventListener('playing', playingListener);
        audioEl.removeEventListener('pause', pausedListener);
        audioEl.removeEventListener('loadstart', loadingListener);

        isConnected = false;

        if (isPlaying || requestedInitPlaying) {
            stop(noop, failureCallback);
        }

        successCallback && successCallback();
        sendListenerResult('DISCONNECTED');
    };

    function play(successCallback, failureCallback) {
        if (!sendErrorNotInitialized(failureCallback)) {
            return;
        }

        if (!isConnected) {
            requestedPlay = true;
            connect(successCallback, failureCallback);
            return;
        }

        if (isPlaying || requestedInitPlaying) {
            successCallback && successCallback();
            return;
        }

        requestedInitPlaying = true;

        sourceEl.src = streamUrl;
        audioEl.load();
        audioEl.loop = false;

        var playPromise = audioEl.play();

        if (playPromise) {
            playPromise.catch(errorListener);
        }

        successCallback && successCallback();
    };

    function stop(successCallback, failureCallback) {
        if (!sendErrorNotInitialized(failureCallback)) {
            return;
        }

        if (!isPlaying && !requestedInitPlaying) {
            successCallback && successCallback();
            return;
        }

        audioEl.pause();
        successCallback && successCallback();
    };

    return {
        initialize: initialize,
        connect: connect,
        disconnect: disconnect,
        play: play,
        stop: stop,
    };
})();

module.exports = MultiPlayerProxy;

require('cordova/exec/proxy').add('MultiPlayer', MultiPlayerProxy);
