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

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;
import android.view.Display.Mode;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.airbnb.lottie.LottieAnimationView;
import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;
import com.android.settingslib.widget.LayoutPreference;

import java.util.TreeSet;

public class RefreshRateLottiePreferenceController extends BasePreferenceController
        implements LifecycleObserver, OnStart, OnStop {

    private LayoutPreference mPreference;
    private LottieAnimationView mSlowView;
    private LottieAnimationView mFastView;
    private TextView mSlowLabel;
    private TextView mFastLabel;

    public RefreshRateLottiePreferenceController(@NonNull Context context, @NonNull String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }

    @Override
    public void displayPreference(androidx.preference.PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
        if (mPreference == null) {
            return;
        }
        bindViews();
        updateContent();
    }

    @Override
    public void onStart() {
        if (mSlowView != null) {
            mSlowView.playAnimation();
        }
        if (mFastView != null) {
            mFastView.playAnimation();
        }
    }

    @Override
    public void onStop() {
        if (mSlowView != null) {
            mSlowView.pauseAnimation();
        }
        if (mFastView != null) {
            mFastView.pauseAnimation();
        }
    }

    private void bindViews() {
        if (mPreference == null) {
            return;
        }
        mSlowView = mPreference.findViewById(R.id.refresh_rate_lottie_slow);
        mFastView = mPreference.findViewById(R.id.refresh_rate_lottie_fast);
        mSlowLabel = mPreference.findViewById(R.id.refresh_rate_label_slow);
        mFastLabel = mPreference.findViewById(R.id.refresh_rate_label_fast);
    }

    private void updateContent() {
        if (mSlowView == null || mFastView == null || mSlowLabel == null || mFastLabel == null) {
            return;
        }

        final int maxRate = Math.max(60, getMaxRefreshRate());
        mSlowLabel.setText(mContext.getString(R.string.per_app_refresh_rate_hz, 60));
        mFastLabel.setText(mContext.getString(R.string.per_app_refresh_rate_hz, maxRate));

        mSlowView.setSpeed(1.0f);
        mFastView.setSpeed(Math.max(1.0f, maxRate / 60f));
    }

    private int getMaxRefreshRate() {
        final DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        final Display display = dm != null ? dm.getDisplay(Display.DEFAULT_DISPLAY) : null;
        if (display == null) {
            return 60;
        }

        final Mode defaultMode = display.getDefaultMode();
        final int width = defaultMode.getPhysicalWidth();
        final int height = defaultMode.getPhysicalHeight();
        final TreeSet<Integer> rates = new TreeSet<>();
        for (Mode mode : display.getSupportedModes()) {
            if (mode.getPhysicalWidth() == width && mode.getPhysicalHeight() == height) {
                rates.add(Math.round(mode.getRefreshRate()));
            }
        }
        if (rates.isEmpty()) {
            return Math.round(display.getRefreshRate());
        }
        return rates.last();
    }
}
