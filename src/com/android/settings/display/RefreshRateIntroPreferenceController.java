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

import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.display.RefreshRateSettingsUtils;
import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.widget.TopIntroPreference;

public class RefreshRateIntroPreferenceController extends BasePreferenceController {

    public RefreshRateIntroPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        Preference preference = screen.findPreference(getPreferenceKey());
        if (!(preference instanceof TopIntroPreference)) {
            return;
        }
        int maxRefreshRate = Math.round(
                RefreshRateSettingsUtils.findHighestRefreshRateForDefaultDisplay(mContext));
        preference.setSummary(
                mContext.getString(R.string.refresh_rate_intro_summary, maxRefreshRate));
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }
}
