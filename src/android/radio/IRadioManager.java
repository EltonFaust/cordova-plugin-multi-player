package com.eltonfaust.multiplayer;

public interface IRadioManager {
    void setStreamUrl(String streamURL);
    void startRadio();
    void startRadio(int streamType);
    void stopRadio();

    boolean isPlaying();

    void registerListener(RadioListener mRadioListener);
    void unregisterListener(RadioListener mRadioListener);

    void setLogging(boolean logging);

    void connect();
    void disconnect();
}
