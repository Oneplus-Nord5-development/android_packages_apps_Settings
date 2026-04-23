/*
 * Copyright (C) 2026 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.display;

import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.app.IActivityTaskManager;
import android.app.Service;
import android.app.TaskStackListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.SparseArray;

public class PerAppRefreshRateService extends Service {

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final TaskStackListener mTaskStackListener = new TaskStackListener() {
        @Override
        public void onTaskStackChanged() {
            mHandler.post(PerAppRefreshRateService.this::updateRefreshRateForFocusedTask);
        }
    };
    private final BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent == null ? null : intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                clearOverride();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                updateRefreshRateForFocusedTask();
            }
        }
    };
    private PerAppRefreshRateManager mManager;
    private IActivityTaskManager mActivityTaskManager;

    private String mActivePackageName;
    private int mActiveUserId = UserHandle.USER_NULL;
    private int mAppliedRefreshRate;
    private boolean mOverrideApplied;
    private final SparseArray<RefreshRateDefaults> mDefaultRefreshRates = new SparseArray<>();

    @Override
    public void onCreate() {
        super.onCreate();

        mManager = new PerAppRefreshRateManager(this);
        if (!mManager.isFeatureEnabled()) {
            stopSelf();
            return;
        }

        mActivityTaskManager = ActivityTaskManager.getService();
        registerScreenStateReceiver();
        try {
            mActivityTaskManager.registerTaskStackListener(mTaskStackListener);
        } catch (RemoteException e) {
            stopSelf();
            return;
        }

        updateRefreshRateForFocusedTask();
    }

    @Override
    public void onDestroy() {
        unregisterScreenStateReceiver();
        try {
            if (mActivityTaskManager != null) {
                mActivityTaskManager.unregisterTaskStackListener(mTaskStackListener);
            }
        } catch (RemoteException e) {
            // Ignore and continue restoring defaults.
        }
        clearOverride();
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mHandler.post(this::updateRefreshRateForFocusedTask);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void updateRefreshRateForFocusedTask() {
        final RootTaskInfo info;
        try {
            info = mActivityTaskManager != null ? mActivityTaskManager.getFocusedRootTaskInfo()
                    : null;
        } catch (RemoteException e) {
            clearOverride();
            return;
        }

        if (info == null || info.topActivity == null) {
            clearOverride();
            return;
        }

        final String packageName = info.topActivity.getPackageName();
        final int userId = info.userId;
        final int refreshRate = mManager.getRefreshRateForPackage(packageName, userId);
        if (refreshRate <= 0) {
            clearOverride();
            return;
        }

        if (mOverrideApplied && userId == mActiveUserId && refreshRate == mAppliedRefreshRate
                && packageName.equals(mActivePackageName)) {
            return;
        }

        applyOverride(packageName, userId, refreshRate);
    }

    private void applyOverride(String packageName, int userId, int refreshRate) {
        if (mOverrideApplied && (userId != mActiveUserId
                || !packageName.equals(mActivePackageName))) {
            restoreDefaultRefreshRates(mActiveUserId);
            mOverrideApplied = false;
        }

        cacheDefaultRefreshRates(userId);

        final float peakRefreshRate = refreshRate;
        final RefreshRateDefaults defaults = mDefaultRefreshRates.get(userId);
        final float minRefreshRate = defaults != null
                ? Math.min(defaults.minRefreshRate, peakRefreshRate)
                : peakRefreshRate;
        Settings.System.putFloatForUser(getContentResolver(), Settings.System.PEAK_REFRESH_RATE,
                peakRefreshRate, userId);
        Settings.System.putFloatForUser(getContentResolver(), Settings.System.MIN_REFRESH_RATE,
                minRefreshRate, userId);

        mActivePackageName = packageName;
        mActiveUserId = userId;
        mAppliedRefreshRate = refreshRate;
        mOverrideApplied = true;
    }

    private void clearOverride() {
        if (mOverrideApplied && mActiveUserId != UserHandle.USER_NULL) {
            restoreDefaultRefreshRates(mActiveUserId);
        }
        mActivePackageName = null;
        mActiveUserId = UserHandle.USER_NULL;
        mAppliedRefreshRate = 0;
        mOverrideApplied = false;
    }

    private void cacheDefaultRefreshRates(int userId) {
        if (userId == UserHandle.USER_NULL || mDefaultRefreshRates.get(userId) != null) {
            return;
        }

        final float peakRefreshRate = Settings.System.getFloatForUser(getContentResolver(),
                Settings.System.PEAK_REFRESH_RATE, mManager.getDefaultPeakRefreshRate(), userId);
        final float minRefreshRate = Settings.System.getFloatForUser(getContentResolver(),
                Settings.System.MIN_REFRESH_RATE, mManager.getDefaultMinRefreshRate(), userId);
        mDefaultRefreshRates.put(userId, new RefreshRateDefaults(peakRefreshRate, minRefreshRate));
    }

    private void restoreDefaultRefreshRates(int userId) {
        if (userId == UserHandle.USER_NULL) {
            return;
        }

        final RefreshRateDefaults defaults = mDefaultRefreshRates.get(userId);
        if (defaults == null) {
            return;
        }

        Settings.System.putFloatForUser(getContentResolver(), Settings.System.PEAK_REFRESH_RATE,
                defaults.peakRefreshRate, userId);
        Settings.System.putFloatForUser(getContentResolver(), Settings.System.MIN_REFRESH_RATE,
                defaults.minRefreshRate, userId);
        mDefaultRefreshRates.remove(userId);
    }

    private void registerScreenStateReceiver() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mScreenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void unregisterScreenStateReceiver() {
        try {
            unregisterReceiver(mScreenStateReceiver);
        } catch (IllegalArgumentException e) {
            // Ignore duplicate unregisters during teardown.
        }
    }

    private static final class RefreshRateDefaults {
        final float peakRefreshRate;
        final float minRefreshRate;

        RefreshRateDefaults(float peakRefreshRate, float minRefreshRate) {
            this.peakRefreshRate = peakRefreshRate;
            this.minRefreshRate = minRefreshRate;
        }
    }
}
