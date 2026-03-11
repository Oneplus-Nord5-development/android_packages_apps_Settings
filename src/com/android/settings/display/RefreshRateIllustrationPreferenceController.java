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

import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settingslib.widget.IllustrationPreference;

public class RefreshRateIllustrationPreferenceController extends BasePreferenceController {

    public RefreshRateIllustrationPreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        IllustrationPreference preference = screen.findPreference(getPreferenceKey());
        if (preference != null) {
            preference.setSelectable(false);
            preference.setImageDrawable(mContext.getDrawable(R.drawable.refresh_rate_illustration));
        }
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE;
    }
}
