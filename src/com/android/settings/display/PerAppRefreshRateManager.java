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
import android.content.pm.ApplicationInfo;
import android.hardware.display.DisplayManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.view.Display;
import android.view.Display.Mode;

import androidx.annotation.NonNull;
import android.content.pm.LauncherApps;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

public class PerAppRefreshRateManager {
    @VisibleForTesting
    static final String SETTING_KEY = "per_app_refresh_rate_ranges";

    private final Context mContext;

    public PerAppRefreshRateManager(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    public boolean isFeatureEnabled() {
        return getSupportedRefreshRates().size() > 1;
    }

    public boolean canDisplayRefreshRateUi(@NonNull ApplicationInfo app) {
        return !mContext.getSystemService(LauncherApps.class)
                .getActivityList(app.packageName, UserHandle.getUserHandleForUid(app.uid))
                .isEmpty();
    }

    @NonNull
    public List<Integer> getSupportedRefreshRates() {
        final DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        final Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        if (display == null) {
            return Collections.singletonList(60);
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
            rates.add(Math.round(display.getRefreshRate()));
        }
        return new ArrayList<>(rates);
    }

    public int getRefreshRateForPackage(@NonNull String packageName, int userId) {
        final Integer value = getAllOverrides(userId).get(packageName);
        return value != null ? value : 0;
    }

    public boolean isAppOverridden(@NonNull String packageName, int userId) {
        return getRefreshRateForPackage(packageName, userId) > 0;
    }

    public void setRefreshRateForPackage(@NonNull String packageName, int userId, int rate) {
        final ArrayMap<String, Integer> overrides = getAllOverrides(userId);
        if (rate <= 0) {
            overrides.remove(packageName);
        } else {
            overrides.put(packageName, rate);
        }
        persistOverrides(overrides, userId);
    }

    public void clearRefreshRateForPackage(@NonNull String packageName, int userId) {
        final ArrayMap<String, Integer> overrides = getAllOverrides(userId);
        overrides.remove(packageName);
        persistOverrides(overrides, userId);
    }

    @NonNull
    public ArrayMap<String, Integer> getAllOverrides(int userId) {
        final String setting = Settings.Secure.getStringForUser(
                mContext.getContentResolver(),
                SETTING_KEY,
                userId);
        return parseOverrides(setting);
    }

    private void persistOverrides(@NonNull ArrayMap<String, Integer> overrides, int userId) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < overrides.size(); i++) {
            final String pkg = overrides.keyAt(i);
            final int rate = overrides.valueAt(i);
            if (TextUtils.isEmpty(pkg) || rate <= 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(pkg).append(':').append(rate).append(':').append(rate);
        }
        Settings.Secure.putStringForUser(
                mContext.getContentResolver(),
                SETTING_KEY,
                builder.toString(),
                userId);
    }

    @NonNull
    private static ArrayMap<String, Integer> parseOverrides(String setting) {
        final ArrayMap<String, Integer> result = new ArrayMap<>();
        if (TextUtils.isEmpty(setting)) {
            return result;
        }
        final String[] entries = setting.split(";");
        for (String entry : entries) {
            if (TextUtils.isEmpty(entry)) {
                continue;
            }
            final String[] parts = entry.split(":");
            if (parts.length != 3) {
                continue;
            }
            final String pkg = parts[0].trim();
            if (TextUtils.isEmpty(pkg)) {
                continue;
            }
            try {
                final int min = Math.round(Float.parseFloat(parts[1]));
                final int max = Math.round(Float.parseFloat(parts[2]));
                if (min <= 0 || max <= 0) {
                    continue;
                }
                result.put(pkg, Math.min(min, max));
            } catch (NumberFormatException e) {
                // Ignore malformed entries.
            }
        }
        return result;
    }
}
