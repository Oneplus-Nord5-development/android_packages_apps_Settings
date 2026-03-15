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

package com.android.settings.spa.app.display

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.dimensionResource
import com.android.settings.R
import com.android.settings.display.PerAppRefreshRateManager
import com.android.settingslib.spa.framework.common.SettingsEntryBuilder
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory
import com.android.settingslib.spa.framework.common.createSettingsPage
import com.android.settingslib.spa.framework.compose.navigator
import com.android.settingslib.spa.framework.compose.rememberContext
import com.android.settingslib.spa.framework.util.asyncMap
import com.android.settingslib.spa.framework.util.filterItem
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.ui.SpinnerOption
import com.android.settingslib.spaprivileged.model.app.AppListModel
import com.android.settingslib.spaprivileged.model.app.AppRecord
import com.android.settingslib.spaprivileged.model.app.userId
import com.android.settingslib.spaprivileged.template.app.AppList
import com.android.settingslib.spaprivileged.template.app.AppListInput
import com.android.settingslib.spaprivileged.template.app.AppListItem
import com.android.settingslib.spaprivileged.template.app.AppListItemModel
import com.android.settingslib.spaprivileged.template.app.AppListPage
import com.google.common.annotations.VisibleForTesting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

object PerAppRefreshRateAppsPageProvider : SettingsPageProvider {
    override val name = "PerAppRefreshRateAppsPage"
    private val owner = createSettingsPage()

    override fun isEnabled(arguments: Bundle?): Boolean =
        PerAppRefreshRateManager(SpaEnvironmentFactory.instance.appContext).isFeatureEnabled()

    @Composable
    override fun Page(arguments: Bundle?) =
        PerAppRefreshRateAppList()

    @Composable
    @VisibleForTesting
    fun EntryItem() {
        val summary = stringResource(R.string.per_app_refresh_rate_summary)
        Preference(object : PreferenceModel {
            override val title = stringResource(R.string.per_app_refresh_rate_title)
            override val summary = { summary }
            override val onClick = navigator(name)
        })
    }

    @VisibleForTesting
    fun buildInjectEntry() = SettingsEntryBuilder
        .createInject(owner)
        .setSearchDataFn { null }
        .setUiLayoutFn { EntryItem() }
}

@Composable
fun PerAppRefreshRateAppList(
    appList: @Composable AppListInput<PerAppRefreshRateAppListItemModel>.() -> Unit = { AppList() },
) {
    AppListPage(
        title = stringResource(R.string.per_app_refresh_rate_title),
        listModel = rememberContext(::PerAppRefreshRateAppListModel),
        appList = appList,
        noMoreOptions = true,
    )
}

data class PerAppRefreshRateAppListItemModel(
    override val app: ApplicationInfo,
    val overrideRate: Int,
    val canDisplay: Boolean,
) : AppRecord

class PerAppRefreshRateAppListModel(
    private val context: Context,
) : AppListModel<PerAppRefreshRateAppListItemModel> {

    private val manager = PerAppRefreshRateManager(context)
    private val supportedRates = manager.getSupportedRefreshRates()
    private val overridesFlow = MutableStateFlow<Map<String, Int>>(emptyMap())

    override fun getSpinnerOptions(
        recordList: List<PerAppRefreshRateAppListItemModel>
    ): List<SpinnerOption> {
        val hasOverride = recordList.any { it.overrideRate > 0 }
        val hasDefault = recordList.any { it.overrideRate <= 0 }
        val options = mutableListOf(SpinnerItem.All)
        if (hasOverride) options += SpinnerItem.Overridden
        if (hasDefault) options += SpinnerItem.NotOverridden
        return options.map {
            SpinnerOption(
                id = it.ordinal,
                text = context.getString(it.stringResId),
            )
        }
    }

    @Composable
    override fun AppListItemModel<PerAppRefreshRateAppListItemModel>.AppItem() {
        val app = record.app
        var showDialog by remember(app.packageName, app.userId) { mutableStateOf(false) }
        val appKey = overrideKey(app)
        val selectedRate = record.overrideRate

        key(app.packageName, app.userId, record.overrideRate) {
            AppListItem(onClick = { showDialog = true })
        }

        if (showDialog) {
            RefreshRateDialog(
                title = context.getString(R.string.per_app_refresh_rate_preference_title),
                selectedRate = selectedRate,
                onDismiss = { showDialog = false },
                onRateSelected = { rate ->
                    manager.setRefreshRateForPackage(app.packageName, app.userId, rate)
                    overridesFlow.value = overridesFlow.value.toMutableMap().apply {
                        put(appKey, rate)
                    }
                    showDialog = false
                },
            )
        }
    }

    override fun transform(
        userIdFlow: Flow<Int>,
        appListFlow: Flow<List<ApplicationInfo>>
    ) = combine(userIdFlow, appListFlow, overridesFlow) { uid, appList, overrides ->
        appList.asyncMap { app ->
            val key = overrideKey(app)
            PerAppRefreshRateAppListItemModel(
                app = app,
                overrideRate = overrides[key]
                    ?: manager.getRefreshRateForPackage(app.packageName, uid),
                canDisplay = manager.canDisplayRefreshRateUi(app),
            )
        }
    }

    override fun filter(
        userIdFlow: Flow<Int>,
        option: Int,
        recordListFlow: Flow<List<PerAppRefreshRateAppListItemModel>>
    ): Flow<List<PerAppRefreshRateAppListItemModel>> = recordListFlow.filterItem(
        when (SpinnerItem.entries.getOrNull(option)) {
            SpinnerItem.Overridden -> ({ it.canDisplay && it.overrideRate > 0 })
            SpinnerItem.NotOverridden -> ({ it.canDisplay && it.overrideRate <= 0 })
            else -> ({ it.canDisplay })
        }
    )

    @Composable
    override fun getSummary(
        option: Int,
        record: PerAppRefreshRateAppListItemModel
    ): () -> String {
        val summary = if (record.overrideRate > 0) {
            context.getString(R.string.per_app_refresh_rate_hz, record.overrideRate)
        } else {
            context.getString(R.string.per_app_refresh_rate_app_default)
        }
        return { summary }
    }

    @Composable
    private fun RefreshRateDialog(
        title: String,
        selectedRate: Int,
        onDismiss: () -> Unit,
        onRateSelected: (Int) -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column {
                    RefreshRateOption(
                        label = context.getString(R.string.per_app_refresh_rate_app_default),
                        selected = selectedRate == 0,
                        onClick = { onRateSelected(0) },
                    )
                    supportedRates.forEach { rate ->
                        RefreshRateOption(
                            label = context.getString(R.string.per_app_refresh_rate_hz, rate),
                            selected = selectedRate == rate,
                            onClick = { onRateSelected(rate) },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    @Composable
    private fun RefreshRateOption(
        label: String,
        selected: Boolean,
        onClick: () -> Unit,
    ) {
        val optionPadding = dimensionResource(R.dimen.refresh_rate_dialog_option_padding_vertical)
        val radioSize = dimensionResource(R.dimen.refresh_rate_dialog_radio_size)
        val textStartPadding = dimensionResource(R.dimen.refresh_rate_dialog_text_padding_start)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = optionPadding),
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                modifier = Modifier.size(radioSize),
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = textStartPadding),
            )
        }
    }

    private fun overrideKey(app: ApplicationInfo): String =
        "${app.userId}:${app.packageName}"

    private enum class SpinnerItem(val stringResId: Int) {
        All(R.string.filter_all_apps),
        Overridden(R.string.filter_overridden),
        NotOverridden(R.string.filter_not_overridden),
    }
}
