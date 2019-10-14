package com.eagle.gallery.pro.utils;


import android.os.Handler;
import android.os.Looper;

public class MainHandler {

    private static Handler mHandler = new Handler(Looper.getMainLooper());

    private MainHandler() {
    }

    public static Handler getInstance() {
        return mHandler;
    }

    public static void post(Runnable runnable) {
        mHandler.post(runnable);
    }

    public static void postDelay(Runnable runnable, long delay) {
        mHandler.postDelayed(runnable, delay);
    }

    public static void cancel(Runnable runnable) {
        mHandler.removeCallbacks(runnable);
    }

}