/*
 * Copyright (C) 2026 The Android Open Source Project
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

import androidx.appcompat.app.AlertDialog;
import android.app.settings.SettingsEnums;
import android.hardware.display.DisplayManager;
import android.hardware.display.RefreshRateManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.applications.AppInfoBase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RefreshRateAppDetails extends AppInfoBase
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_MIN = "app_min_refresh_rate";
    private static final String KEY_MAX = "app_max_refresh_rate";

    private ListPreference mMinPreference;
    private ListPreference mMaxPreference;
    private RefreshRateManager mRefreshRateManager;
    private final List<Float> mSupportedRates = new ArrayList<>();

    @Override
    public void onCreate(@NonNull Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.refresh_rate_app_detail);
        mRefreshRateManager = getContext().getSystemService(RefreshRateManager.class);

        mMinPreference = findPreference(KEY_MIN);
        mMaxPreference = findPreference(KEY_MAX);

        buildSupportedRates();
        configureListPreference(mMinPreference);
        configureListPreference(mMaxPreference);

        mMinPreference.setOnPreferenceChangeListener(this);
        mMaxPreference.setOnPreferenceChangeListener(this);

        refreshUi();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.refresh_rate_app_detail;
    }

    @Override
    protected boolean refreshUi() {
        if (mPackageInfo == null || mPackageInfo.applicationInfo == null) {
            return false;
        }
        float min = 0f;
        float max = 0f;
        if (mRefreshRateManager != null) {
            float[] range = mRefreshRateManager.getPerAppRefreshRateRange(mPackageName);
            if (range != null && range.length >= 2) {
                min = range[0];
                max = range[1];
            }
        }
        setPreferenceValue(mMinPreference, min);
        setPreferenceValue(mMaxPreference, max);
        return true;
    }

    @Override
    protected AlertDialog createDialog(int id, int errorCode) {
        return null;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        float min = parsePreferenceValue(mMinPreference.getValue());
        float max = parsePreferenceValue(mMaxPreference.getValue());
        float newRate = parsePreferenceValue((String) newValue);

        if (TextUtils.equals(preference.getKey(), KEY_MIN)) {
            min = newRate;
            if (min > 0f && max > 0f && min > max) {
                max = min;
                setPreferenceValue(mMaxPreference, max);
            }
        } else if (TextUtils.equals(preference.getKey(), KEY_MAX)) {
            max = newRate;
            if (min > 0f && max > 0f && max < min) {
                min = max;
                setPreferenceValue(mMinPreference, min);
            }
        }

        applyRange(min, max);
        return true;
    }

    private void applyRange(float min, float max) {
        if (mRefreshRateManager == null) {
            return;
        }
        if (min <= 0f && max <= 0f) {
            mRefreshRateManager.clearPerAppRefreshRateRange(mPackageName);
        } else {
            mRefreshRateManager.setPerAppRefreshRateRange(mPackageName, min, max);
        }
    }

    private void buildSupportedRates() {
        DisplayManager dm = getContext().getSystemService(DisplayManager.class);
        Display display = dm != null ? dm.getDisplay(Display.DEFAULT_DISPLAY) : null;
        if (display == null) {
            return;
        }
        for (Display.Mode mode : display.getSupportedModes()) {
            float rate = mode.getRefreshRate();
            if (!mSupportedRates.contains(rate)) {
                mSupportedRates.add(rate);
            }
        }
        Collections.sort(mSupportedRates);
    }

    private void configureListPreference(ListPreference preference) {
        if (preference == null) {
            return;
        }
        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        entries.add(getContext().getString(R.string.refresh_rate_app_default));
        values.add("0");
        for (Float rate : mSupportedRates) {
            entries.add(String.format("%dHz", Math.round(rate)));
            values.add(Float.toString(rate));
        }
        preference.setEntries(entries.toArray(new CharSequence[0]));
        preference.setEntryValues(values.toArray(new CharSequence[0]));
    }

    private void setPreferenceValue(ListPreference preference, float value) {
        if (preference == null) {
            return;
        }
        String stringValue = value <= 0f ? "0" : Float.toString(value);
        if (!containsValue(preference, stringValue)) {
            stringValue = "0";
        }
        preference.setValue(stringValue);
    }

    private boolean containsValue(ListPreference preference, String value) {
        CharSequence[] values = preference.getEntryValues();
        if (values == null) {
            return false;
        }
        for (CharSequence entryValue : values) {
            if (TextUtils.equals(entryValue, value)) {
                return true;
            }
        }
        return false;
    }

    private float parsePreferenceValue(String value) {
        if (TextUtils.isEmpty(value)) {
            return 0f;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }
}
