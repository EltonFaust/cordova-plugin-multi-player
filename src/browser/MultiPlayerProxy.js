
var MultiPlayerProxy = (function () {
    var audioEl;
    var sourceEl;
    var streamUrl;
    var sendListenerResult;

    var isConnected = false;
    var isPlaying = false;
    var requestedInitPlaying = false;
    var requestedPlay = false;

    function MultiPlayerConstruct() { }

    function noop() { }

    function sendErrorNotInitialized(failureCallback) {
        if (streamUrl) {
            return true;
        }

        failureCallback('NOT_INITIALIZED');
        return false;
    }

    function errorListener() {
        sendListenerResult('ERROR');
    }

    function loadingListener() {
        if (!sourceEl.src) {
            return;
        }

        sendListenerResult('LOADING');
    }

    function playingListener() {
        isPlaying = true;
        requestedInitPlaying = false;
        requestedPlay = false;

        sendListenerResult('STARTED');
    }

    function pausedListener() {
        isPlaying = false;
        requestedInitPlaying = false;

        sendListenerResult('STOPPED');
    }

    MultiPlayerConstruct.prototype.initialize = function (successCallback, failureCallback, url) {
        streamUrl = url;
        audioEl = window.document.createElement('audio');
        sourceEl = window.document.createElement('source');
        audioEl.appendChild(sourceEl);

        sendListenerResult = successCallback || noop;
    };

    MultiPlayerConstruct.prototype.connect = function (successCallback, failureCallback) {
        if (!sendErrorNotInitialized(failureCallback) || isConnected) {
            return;
        }

        sourceEl.addEventListener('error', errorListener, false);
        audioEl.addEventListener('ended', errorListener, false);
        audioEl.addEventListener('playing', playingListener, false);
        audioEl.addEventListener('pause', pausedListener, false);
        audioEl.addEventListener('loadstart', loadingListener, false);

        isConnected = true;

        successCallback && successCallback();
        sendListenerResult('CONNECTED');

        if (requestedPlay) {
            requestedPlay = false;
            this.play(successCallback, failureCallback);
        }
    };

    MultiPlayerConstruct.prototype.disconnect = function (successCallback, failureCallback) {
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
            this.stop(noop, failureCallback);
        }

        successCallback && successCallback();
        sendListenerResult('DISCONNECTED');
    };

    MultiPlayerConstruct.prototype.play = function (successCallback, failureCallback) {
        if (!sendErrorNotInitialized(failureCallback)) {
            return;
        }

        if (!isConnected) {
            requestedPlay = true;
            this.connect(successCallback, failureCallback);
            return;
        }

        if (isPlaying || requestedInitPlaying) {
            successCallback && successCallback();
            return;
        }

        requestedInitPlaying = true;

        sourceEl.src = streamUrl;
        audioEl.load();

        var playPromise = audioEl.play();

        if (playPromise) {
            playPromise.catch(function () {
                requestedInitPlaying = false;
                errorListener();
            });
        }

        successCallback && successCallback();
    };

    MultiPlayerConstruct.prototype.stop = function (successCallback, failureCallback) {
        if (!sendErrorNotInitialized(failureCallback)) {
            return;
        }

        if (!isPlaying && !requestedInitPlaying) {
            successCallback && successCallback();
            return;
        }

        audioEl.pause();
        sourceEl.src = '';
        audioEl.load();
        requestedPlay = false;

        successCallback && successCallback();
    };

    return new MultiPlayerConstruct();
})();

module.exports = MultiPlayerProxy;

require('cordova/exec/proxy').add('MultiPlayer', MultiPlayerProxy);
