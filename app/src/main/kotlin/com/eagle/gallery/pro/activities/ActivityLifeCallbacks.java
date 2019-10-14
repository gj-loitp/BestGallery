package com.eagle.gallery.pro.activities;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;


public class ActivityLifeCallbacks implements Application.ActivityLifecycleCallbacks {

    private int activityCount;

    private long totalTime = 0;
    private long startTime = 0;

    private long openTime = 0;

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
        if (activityCount <= 0) {
            openTime = System.currentTimeMillis();
        }
        activityCount++;
    }

    @Override
    public void onActivityResumed(Activity activity) {
        startTime = System.currentTimeMillis();

    }

    @Override
    public void onActivityPaused(Activity activity) {
        totalTime = totalTime + (System.currentTimeMillis() - startTime);
        startTime = 0;
    }

    @Override
    public void onActivityStopped(Activity activity) {
        activityCount--;
        if (activityCount <= 0) {
            totalTime = 0;
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
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
