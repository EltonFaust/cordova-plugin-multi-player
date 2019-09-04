package com.eltonfaust.multiplayer;

public interface IRadioManager {
    void setStreamURL(String streamURL);
    void startRadio();
    void startRadio(int streamType);
    void stopRadio();

    boolean isPlaying();

    void setListener(RadioListener mRadioListener);
    void registerListener(RadioListener mRadioListener);
    void unregisterListener(RadioListener mRadioListener);

    void connect();
    void disconnect();
}
