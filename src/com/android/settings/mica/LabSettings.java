package com.android.settings.mica;

import android.os.Bundle;

import com.android.internal.logging.nano.MetricsProto;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class LabSettings extends SettingsPreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.lab_settings);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.MICA;
    }
}
