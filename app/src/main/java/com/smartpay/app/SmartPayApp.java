package com.smartpay.app;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * SmartPayApp - Application class to enforce application-wide configurations,
 * including forcing light mode (MODE_NIGHT_NO) globally across the entire app.
 */
public class SmartPayApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // Force light mode so texts and surfaces remain clearly readable regardless of system dark mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        // Create OS notification channel early so it is ready before any notification fires
        NotificationHelper.createChannel(this);
    }
}
