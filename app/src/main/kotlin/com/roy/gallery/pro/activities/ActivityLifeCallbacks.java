package com.roy.gallery.pro.activities;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;


public class ActivityLifeCallbacks implements Application.ActivityLifecycleCallbacks {

    private int activityCount;

    private long totalTime = 0;
    private long startTime = 0;

    private long openTime = 0;

    @Override
    public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (activityCount <= 0) {
            openTime = System.currentTimeMillis();
        }
        activityCount++;
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        startTime = System.currentTimeMillis();

    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        totalTime = totalTime + (System.currentTimeMillis() - startTime);
        startTime = 0;
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        activityCount--;
        if (activityCount <= 0) {
            totalTime = 0;
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    public int getAppCount() {
        return activityCount;
    }

    public void setAppCount(int appCount) {
        this.activityCount = appCount;
    }

    public long getTotalTime() {
        return totalTime < 60 * 1000 || totalTime > 24 * 60 * 60 * 1000l ? 60 * 1000 : totalTime;
    }

}
