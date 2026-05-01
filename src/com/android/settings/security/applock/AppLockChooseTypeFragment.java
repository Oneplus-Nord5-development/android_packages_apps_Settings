package com.android.settings.security.applock;

import android.app.Activity;
import android.app.settings.SettingsEnums;
import android.content.Intent;
import android.os.Bundle;

import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class AppLockChooseTypeFragment extends SettingsPreferenceFragment {

    private static final String KEY_PATTERN = "unlock_set_pattern";
    private static final String KEY_PIN = "unlock_set_pin";
    private static final String KEY_PASSWORD = "unlock_set_password";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.app_lock_choose_type_settings);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setTitle(R.string.lock_settings_picker_new_lock_title);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final String key = preference.getKey();
        int type = -1;

        if (KEY_PATTERN.equals(key)) {
            type = 3;
        } else if (KEY_PIN.equals(key)) {
            type = 1;
        } else if (KEY_PASSWORD.equals(key)) {
            type = 2;
        }

        if (type != -1) {
            Intent intent = new Intent(requireContext(), AppLockCredentialSetupActivity.class);
            intent.putExtra(AppLockCredentialSetupActivity.EXTRA_TYPE, type);
            startActivityForResult(intent, 123);
            return true;
        }

        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 123 && resultCode == Activity.RESULT_OK) {
            getActivity().setResult(Activity.RESULT_OK);
            getActivity().finish();
        }
    }
}
