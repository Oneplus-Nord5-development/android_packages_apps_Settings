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

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.spa.SpaActivity;
import com.android.settings.spa.app.display.PerAppRefreshRateAppsPageProvider;

public class PerAppRefreshRateAppsPreferenceController extends BasePreferenceController
        implements Preference.OnPreferenceClickListener {

    private final PerAppRefreshRateManager mManager;

    public PerAppRefreshRateAppsPreferenceController(@NonNull Context context,
            @NonNull String preferenceKey) {
        super(context, preferenceKey);
        mManager = new PerAppRefreshRateManager(context);
    }

    @Override
    public int getAvailabilityStatus() {
        return mManager.isFeatureEnabled() ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public CharSequence getSummary() {
        return mContext.getString(R.string.per_app_refresh_rate_summary);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        final Preference preference = screen.findPreference(getPreferenceKey());
        if (preference != null) {
            preference.setOnPreferenceClickListener(this);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        SpaActivity.startSpaActivity(mContext, PerAppRefreshRateAppsPageProvider.INSTANCE.getName());
        return true;
    }
}
