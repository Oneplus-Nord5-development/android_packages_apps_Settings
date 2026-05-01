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

package com.android.settings.security;

import android.app.Application;
import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.ArraySet;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.app.AppLockUtils;
import com.android.internal.widget.LockPatternUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.AppCheckBoxPreference;
import com.android.settingslib.applications.AppUtils;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.ApplicationsState.AppEntry;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.FooterPreference;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

import lineageos.providers.LineageSettings;

/**
 * Settings surface for LineageOS App Lock.
 */
@SearchIndexable
public class AppLockSettings extends SettingsPreferenceFragment
        implements ApplicationsState.Callbacks, Preference.OnPreferenceChangeListener {

    private static final String KEY_BIOMETRICS = "cm_app_lock_biometrics";
    private static final String KEY_PASSWORD = "cm_app_lock_password";
    private static final String KEY_SECURE_WINDOW = "cm_app_lock_secure_window";
    private static final String KEY_APPS = "cm_app_lock_apps";
    private static final String KEY_FOOTER = "cm_app_lock_footer";
    private static final String KEY_REQUIREMENT_PLACEHOLDER = "cm_app_lock_requirement";

    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private LockPatternUtils mLockPatternUtils;

    private SwitchPreferenceCompat mBiometricsPreference;
    private SwitchPreferenceCompat mCustomPasswordPreference;
    private SwitchPreferenceCompat mSecureWindowPreference;
    private PreferenceCategory mAppsCategory;
    private FooterPreference mFooterPreference;

    private final ArraySet<String> mLockedPackages = new ArraySet<>();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.security_app_lock_settings);

        final Context context = requireContext();
        mApplicationsState = ApplicationsState.getInstance(
                (Application) context.getApplicationContext());
        mSession = mApplicationsState.newSession(this, getSettingsLifecycle());
        mLockPatternUtils = new LockPatternUtils(context);

        final PreferenceScreen screen = getPreferenceScreen();
        mBiometricsPreference = screen.findPreference(KEY_BIOMETRICS);
        mSecureWindowPreference = screen.findPreference(KEY_SECURE_WINDOW);
        mAppsCategory = screen.findPreference(KEY_APPS);
        mFooterPreference = screen.findPreference(KEY_FOOTER);

        mBiometricsPreference.setOnPreferenceChangeListener(this);
        mSecureWindowPreference.setOnPreferenceChangeListener(this);

        mCustomPasswordPreference = screen.findPreference(KEY_PASSWORD);
        if (mCustomPasswordPreference != null) {
            mCustomPasswordPreference.setOnPreferenceChangeListener(this);
        }
        mAppsCategory.setOrderingAsAdded(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUi();
        rebuildApps();
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.PAGE_UNKNOWN;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (KEY_BIOMETRICS.equals(preference.getKey())) {
            LineageSettings.Secure.putIntForUser(getContentResolver(),
                    AppLockUtils.LINEAGE_SETTINGS_APP_LOCK_BIOMETRICS_ALLOWED,
                    (Boolean) newValue ? 1 : 0, UserHandle.myUserId());
            return true;
        } else if (KEY_SECURE_WINDOW.equals(preference.getKey())) {
            LineageSettings.Secure.putIntForUser(getContentResolver(),
                    AppLockUtils.LINEAGE_SETTINGS_APP_LOCK_SECURE_WINDOW,
                    (Boolean) newValue ? 1 : 0, UserHandle.myUserId());
            return true;
        } else if (KEY_PASSWORD.equals(preference.getKey())) {
            boolean enabled = (Boolean) newValue;
            if (enabled) {
                showCredentialTypeDialog();
                return false; // Don't toggle yet, wait for setup to complete
            } else {
                clearCustomCredential();
                return true;
            }
        }

        if (preference instanceof AppCheckBoxPreference) {
            final String packageName = preference.getKey();
            final boolean enabled = newValue == Boolean.TRUE;
            if (enabled) {
                mLockedPackages.add(packageName);
            } else {
                mLockedPackages.remove(packageName);
            }
            persistLockedPackages();
            return true;
        }

        return false;
    }

    @Override
    public void onRunningStateChanged(boolean running) {
    }

    @Override
    public void onPackageListChanged() {
        rebuildApps();
    }

    @Override
    public void onRebuildComplete(ArrayList<AppEntry> apps) {
        if (!isAdded()) {
            return;
        }
        bindApps(apps);
    }

    @Override
    public void onPackageIconChanged() {
        rebuildApps();
    }

    @Override
    public void onPackageSizeChanged(String packageName) {
    }

    @Override
    public void onAllSizesComputed() {
    }

    @Override
    public void onLauncherInfoChanged() {
        rebuildApps();
    }

    @Override
    public void onLoadEntriesCompleted() {
        rebuildApps();
    }

    private void refreshUi() {
        mLockedPackages.clear();
        mLockedPackages.addAll(AppLockUtils.decodePackages(LineageSettings.Secure.getStringForUser(
                getContentResolver(), AppLockUtils.LINEAGE_SETTINGS_APP_LOCK_PACKAGES,
                UserHandle.myUserId())));
        if (sanitizeLockedPackages()) {
            persistLockedPackages();
        }

        final boolean secure = mLockPatternUtils.isSecure(UserHandle.myUserId());
        mBiometricsPreference.setChecked(
                LineageSettings.Secure.getIntForUser(getContentResolver(),
                        AppLockUtils.LINEAGE_SETTINGS_APP_LOCK_BIOMETRICS_ALLOWED, 0,
                        UserHandle.myUserId()) == 1);
        mBiometricsPreference.setEnabled(secure);
        mBiometricsPreference.setSummary(secure
                ? R.string.cm_app_lock_biometrics_summary
                : R.string.cm_app_lock_requires_screen_lock_summary);
        
        mSecureWindowPreference.setChecked(
                LineageSettings.Secure.getIntForUser(getContentResolver(),
                        AppLockUtils.LINEAGE_SETTINGS_APP_LOCK_SECURE_WINDOW, 1,
                        UserHandle.myUserId()) == 1);
        mSecureWindowPreference.setEnabled(secure);
        
        if (mCustomPasswordPreference != null) {
            String customPassword = LineageSettings.Secure.getStringForUser(getContentResolver(),
                    AppLockUtils.LINEAGE_SETTINGS_APP_LOCK_CUSTOM_PASSWORD, UserHandle.myUserId());
            mCustomPasswordPreference.setChecked(!TextUtils.isEmpty(customPassword));
            mCustomPasswordPreference.setEnabled(secure);
        }

        mFooterPreference.setTitle(secure
                ? R.string.cm_app_lock_footer
                : R.string.cm_app_lock_requires_screen_lock_summary);
    }

    private boolean sanitizeLockedPackages() {
        boolean changed = mLockedPackages.remove(AppLockUtils.APP_LOCK_ACTIVITY_PACKAGE);
        final String homePackage = resolveHomePackage();
        if (!TextUtils.isEmpty(homePackage)) {
            changed |= mLockedPackages.remove(homePackage);
        }
        return changed;
    }

    private void persistLockedPackages() {
        LineageSettings.Secure.putStringForUser(getContentResolver(),
                AppLockUtils.LINEAGE_SETTINGS_APP_LOCK_PACKAGES,
                AppLockUtils.encodePackages(mLockedPackages), UserHandle.myUserId());
    }

    private void rebuildApps() {
        if (!isAdded() || !mLockPatternUtils.isSecure(UserHandle.myUserId())) {
            bindApps(null);
            return;
        }

        final ArrayList<AppEntry> apps = mSession.rebuild(
                ApplicationsState.FILTER_DOWNLOADED_AND_LAUNCHER,
                ApplicationsState.ALPHA_COMPARATOR);
        if (apps != null) {
            bindApps(apps);
        }
    }

    private void bindApps(ArrayList<AppEntry> apps) {
        final boolean secure = mLockPatternUtils.isSecure(UserHandle.myUserId());
        final Set<String> visibleKeys = new TreeSet<>();

        if (!secure) {
            addPlaceholderPreference(R.string.cm_app_lock_requires_screen_lock_summary);
            visibleKeys.add(KEY_REQUIREMENT_PLACEHOLDER);
            removeObsoletePrefs(visibleKeys);
            return;
        }

        removePlaceholderPreference();
        if (apps == null || apps.isEmpty()) {
            addPlaceholderPreference(R.string.cm_app_lock_no_apps);
            visibleKeys.add(KEY_REQUIREMENT_PLACEHOLDER);
            removeObsoletePrefs(visibleKeys);
            return;
        }

        int order = 0;
        for (AppEntry entry : apps) {
            if (!shouldShowEntry(entry)) {
                continue;
            }
            entry.ensureLabel(getPrefContext());

            final String prefKey = entry.info.packageName;
            visibleKeys.add(prefKey);

            AppCheckBoxPreference preference = mAppsCategory.findPreference(prefKey);
            if (preference == null) {
                preference = new AppCheckBoxPreference(mAppsCategory.getContext());
                preference.setKey(prefKey);
                preference.setOnPreferenceChangeListener(this);
                preference.setPersistent(false);
                mAppsCategory.addPreference(preference);
            }

            preference.setOrder(order++);
            preference.setIcon(AppUtils.getIcon(getPrefContext(), entry));
            preference.setTitle(entry.label);
            preference.setSummary(entry.info.packageName);
            preference.setChecked(mLockedPackages.contains(prefKey));
        }

        if (visibleKeys.isEmpty()) {
            addPlaceholderPreference(R.string.cm_app_lock_no_apps);
            visibleKeys.add(KEY_REQUIREMENT_PLACEHOLDER);
        }

        removeObsoletePrefs(visibleKeys);
    }

    private boolean shouldShowEntry(@NonNull AppEntry entry) {
        if (entry.info == null || !entry.info.enabled) {
            return false;
        }
        if (UserHandle.getUserId(entry.info.uid) != UserHandle.myUserId()) {
            return false;
        }
        if (entry.isHomeApp) {
            return false;
        }
        if (TextUtils.equals(entry.info.packageName, AppLockUtils.APP_LOCK_ACTIVITY_PACKAGE)) {
            return false;
        }
        return hasLauncherEntry(entry.info);
    }

    private boolean hasLauncherEntry(@NonNull ApplicationInfo info) {
        return getPackageManager().getLaunchIntentForPackage(info.packageName) != null;
    }

    private String resolveHomePackage() {
        final PackageManager packageManager = getPackageManager();
        if (packageManager == null) {
            return null;
        }

        final Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME);
        final ResolveInfo resolveInfo = packageManager.resolveActivity(homeIntent,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return null;
        }
        return resolveInfo.activityInfo.packageName;
    }

    private void addPlaceholderPreference(int titleResId) {
        Preference preference = mAppsCategory.findPreference(KEY_REQUIREMENT_PLACEHOLDER);
        if (preference == null) {
            preference = new Preference(mAppsCategory.getContext());
            preference.setKey(KEY_REQUIREMENT_PLACEHOLDER);
            preference.setSelectable(false);
            preference.setPersistent(false);
            mAppsCategory.addPreference(preference);
        }
        preference.setTitle(titleResId);
    }

    private void removePlaceholderPreference() {
        final Preference preference = mAppsCategory.findPreference(KEY_REQUIREMENT_PLACEHOLDER);
        if (preference != null) {
            mAppsCategory.removePreference(preference);
        }
    }

    private void removeObsoletePrefs(@NonNull Set<String> visibleKeys) {
        for (int i = mAppsCategory.getPreferenceCount() - 1; i >= 0; i--) {
            final Preference preference = mAppsCategory.getPreference(i);
            if (!visibleKeys.contains(preference.getKey())) {
                mAppsCategory.removePreference(preference);
            }
        }
    }

    private void showCredentialTypeDialog() {
        new com.android.settings.core.SubSettingLauncher(requireContext())
                .setDestination(com.android.settings.security.applock.AppLockChooseTypeFragment.class.getName())
                .setSourceMetricsCategory(getMetricsCategory())
                .setResultListener(this, 123)
                .launch();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 123 && resultCode == android.app.Activity.RESULT_OK) {
            if (mCustomPasswordPreference != null) {
                mCustomPasswordPreference.setChecked(true);
            }
        }
    }

    private void clearCustomCredential() {
        LineageSettings.Secure.putStringForUser(getContentResolver(),
                AppLockUtils.LINEAGE_SETTINGS_APP_LOCK_CUSTOM_PASSWORD,
                "", UserHandle.myUserId());
        LineageSettings.Secure.putIntForUser(getContentResolver(),
                AppLockUtils.LINEAGE_SETTINGS_APP_LOCK_CUSTOM_TYPE,
                0, UserHandle.myUserId());
    }



    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.security_app_lock_settings);
}
