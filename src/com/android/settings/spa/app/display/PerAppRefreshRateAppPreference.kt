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
import android.os.UserHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.settings.R
import com.android.settings.display.PerAppRefreshRateManager
import com.android.settingslib.spa.widget.preference.ListPreference
import com.android.settingslib.spa.widget.preference.ListPreferenceModel
import com.android.settingslib.spa.widget.preference.ListPreferenceOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

@Composable
fun PerAppRefreshRateAppPreference(app: ApplicationInfo) {
    val context = LocalContext.current
    val presenter = remember { PerAppRefreshRateAppPresenter(context, app) }
    if (!presenter.isAvailableFlow.collectAsStateWithLifecycle(initialValue = false).value) return

    val supportedRates = remember { presenter.supportedRates }
    val options = remember(supportedRates) {
        buildList {
            add(
                ListPreferenceOption(
                    id = 0,
                    text = context.getString(R.string.per_app_refresh_rate_app_default),
                )
            )
            supportedRates.forEach { rate ->
                add(
                    ListPreferenceOption(
                        id = rate,
                        text = context.getString(R.string.per_app_refresh_rate_hz, rate),
                    )
                )
            }
        }
    }
    val selectedId = remember { mutableIntStateOf(0) }
    LaunchedEffect(app.packageName, app.uid) {
        selectedId.intValue = presenter.getSelectedRate()
    }

    ListPreference(
        object : ListPreferenceModel {
            override val title = stringResource(R.string.per_app_refresh_rate_preference_title)
            override val options = options
            override val selectedId = selectedId
            override val onIdSelected: (Int) -> Unit = { id: Int ->
                selectedId.intValue = id
                presenter.setSelectedRate(id)
            }
        }
    )
}

class PerAppRefreshRateAppPresenter(
    private val context: Context,
    private val app: ApplicationInfo,
) {
    private val manager = PerAppRefreshRateManager(context)
    val supportedRates: List<Int> = manager.getSupportedRefreshRates()

    val isAvailableFlow = flow {
        emit(manager.isFeatureEnabled() && manager.canDisplayRefreshRateUi(app))
    }.flowOn(Dispatchers.IO)

    fun getSelectedRate(): Int {
        val userId = UserHandle.getUserId(app.uid)
        return manager.getRefreshRateForPackage(app.packageName, userId)
    }

    fun setSelectedRate(rate: Int) {
        val userId = UserHandle.getUserId(app.uid)
        manager.setRefreshRateForPackage(app.packageName, userId, rate)
    }
}
