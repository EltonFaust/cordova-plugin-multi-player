package com.eltonfaust.multiplayer;

public interface RadioListener {
    void onRadioLoading();
    void onRadioConnected();
    void onRadioStarted();
    void onRadioStopped();
    void onRadioStoppedFocusLoss();
    void onError();
}
