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

package com.android.settings.spa.app.refresh

import android.app.settings.SettingsEnums
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.internal.display.RefreshRateSettingsUtils
import com.android.settings.R
import com.android.settings.applications.appinfo.AppInfoDashboardFragment
import com.android.settings.display.RefreshRateAppDetails
import com.android.settingslib.spa.framework.common.SettingsPageProvider
import com.android.settingslib.spa.framework.common.createSettingsPage
import com.android.settingslib.spa.framework.compose.rememberContext
import com.android.settingslib.spa.framework.util.asyncMap
import com.android.settingslib.spa.framework.util.filterItem
import com.android.settingslib.spaprivileged.model.app.AppListModel
import com.android.settingslib.spaprivileged.model.app.AppRecord
import com.android.settingslib.spaprivileged.model.app.userId
import com.android.settingslib.spaprivileged.template.app.AppList
import com.android.settingslib.spaprivileged.template.app.AppListInput
import com.android.settingslib.spaprivileged.template.app.AppListItem
import com.android.settingslib.spaprivileged.template.app.AppListItemModel
import com.android.settingslib.spaprivileged.template.app.AppListPage
import com.android.settingslib.spa.widget.ui.SpinnerOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.roundToInt

object RefreshRateAppsPageProvider : SettingsPageProvider {
    const val NAME = "RefreshRateAppsPage"
    override val name = NAME
    private val owner = createSettingsPage()

    override fun isEnabled(arguments: Bundle?): Boolean = true

    @Composable
    override fun Page(arguments: Bundle?) = RefreshRateAppList()
}

@Composable
fun RefreshRateAppList(
    appList: @Composable AppListInput<RefreshRateAppListItemModel>.() -> Unit = { AppList() },
) {
    AppListPage(
        title = stringResource(R.string.per_app_refresh_rate_title),
        listModel = rememberContext(::RefreshRateAppListModel),
        appList = appList,
        noMoreOptions = true,
    )
}

data class RefreshRateAppListItemModel(
    override val app: ApplicationInfo,
    val min: Float,
    val max: Float,
) : AppRecord

class RefreshRateAppListModel(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppListModel<RefreshRateAppListItemModel> {

    override fun getSpinnerOptions(
        recordList: List<RefreshRateAppListItemModel>
    ): List<SpinnerOption> = emptyList()

    @Composable
    override fun AppListItemModel<RefreshRateAppListItemModel>.AppItem() {
        AppListItem(
            onClick = {
                navigateToAppRefreshRateSettings(
                    context,
                    record.app,
                    SettingsEnums.DISPLAY,
                )
            }
        )
    }

    override fun transform(
        userIdFlow: Flow<Int>,
        appListFlow: Flow<List<ApplicationInfo>>
    ): Flow<List<RefreshRateAppListItemModel>> =
        userIdFlow.combine(appListFlow) { uid, appList ->
            val config = RefreshRateSettingsUtils.parsePerAppRefreshRateConfig(
                Settings.System.getStringForUser(
                    context.contentResolver,
                    Settings.System.PER_APP_REFRESH_RATE_CONFIG,
                    uid
                )
            )
            appList.asyncMap { app ->
                val range = config[app.packageName]
                RefreshRateAppListItemModel(
                    app = app,
                    min = range?.min ?: 0f,
                    max = range?.max ?: 0f,
                )
            }
        }

    override fun filter(
        userIdFlow: Flow<Int>,
        option: Int,
        recordListFlow: Flow<List<RefreshRateAppListItemModel>>
    ): Flow<List<RefreshRateAppListItemModel>> = recordListFlow.filterItem { true }

    @Composable
    override fun getSummary(option: Int, record: RefreshRateAppListItemModel): () -> String {
        val summary by remember(record.min, record.max) {
            flow {
                emit(formatSummary(record.min, record.max))
            }.flowOn(ioDispatcher)
        }.collectAsStateWithLifecycle(initialValue = stringResource(R.string.summary_placeholder))
        return { summary }
    }

    private fun formatSummary(min: Float, max: Float): String {
        if (min <= 0f && max <= 0f) {
            return context.getString(R.string.refresh_rate_app_default)
        }
        val minLabel = if (min > 0f) "${min.roundToInt()}Hz" else context.getString(
            R.string.refresh_rate_app_default
        )
        val maxLabel = if (max > 0f) "${max.roundToInt()}Hz" else context.getString(
            R.string.refresh_rate_app_default
        )
        return context.getString(R.string.refresh_rate_app_range_summary, minLabel, maxLabel)
    }
}

fun navigateToAppRefreshRateSettings(
    context: Context,
    app: ApplicationInfo,
    metricsCategory: Int,
) {
    AppInfoDashboardFragment.startAppInfoFragment(
        RefreshRateAppDetails::class.java,
        app,
        context,
        metricsCategory,
    )
}
