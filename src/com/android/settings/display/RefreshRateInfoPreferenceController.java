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

import static com.android.internal.display.RefreshRateSettingsUtils.findHighestRefreshRateForDefaultDisplay;

import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

public class RefreshRateInfoPreferenceController extends BasePreferenceController {

    public RefreshRateInfoPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);

        Preference infoPreference = screen.findPreference(getPreferenceKey());
        if (infoPreference == null) {
            return;
        }

        int peakRefreshRate = Math.round(findHighestRefreshRateForDefaultDisplay(mContext));
        infoPreference.setTitle(mContext.getString(
                R.string.refresh_rate_info_text, peakRefreshRate));
        infoPreference.setSelectable(false);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }
}
