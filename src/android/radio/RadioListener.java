package com.eltonfaust.multiplayer;

public interface RadioListener {
    void onRadioLoading();
    void onRadioConnected();
    void onRadioDisconnected();
    void onRadioStarted();
    void onRadioStopped();
    void onRadioStoppedFocusLoss();
    void onRadioStoppedFocusTransient();
    void onRadioStartedFocusTransient();
    void onError();
}
