/*
 * Copyright (C) 2026 yixing233 and HyperDuo contributors
 * Copyright (C) 2026 odomu and HonorDuo contributors
 *
 * This file is part of HonorDuo, a fork of HyperDuo, released under the GNU
 * General Public License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.odomu.honorduo.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.ui.manager.SettingsStore
import io.github.odomu.honorduo.ui.component.compactAppTextStyles
import io.github.odomu.honorduo.ui.screen.SettingsScreen
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import java.util.Locale

/**
 * The module's settings screen.
 *
 * Reachable both from the launcher (through the activity alias) and from the
 * LSPosed manager, which launches the activity advertising
 * `de.robv.android.xposed.category.MODULE_SETTINGS`.
 */
class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        val mode = prefs.getInt(Prefs.KEY_APP_LANGUAGE, Prefs.DEF_APP_LANGUAGE)
        val locales = when (mode) {
            Prefs.APP_LANG_ZH -> LocaleList(Locale.SIMPLIFIED_CHINESE)
            Prefs.APP_LANG_EN -> LocaleList(Locale.ENGLISH)
            else -> Resources.getSystem().configuration.locales
        }
        // Copy the configuration so an app override never changes the base
        // resources used to resolve "follow system" on the next recreation.
        val config = Configuration(newBase.resources.configuration)
        config.setLocales(locales)
        Locale.setDefault(locales[0])
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // the bar icons following the theme rather than the flags baked into AppTheme.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val repository = remember { SettingsStore(applicationContext) }
            MiuixTheme(
                controller = remember { ThemeController() },
                textStyles = remember { compactAppTextStyles() },
            ) {
                SettingsScreen(repository)
            }
        }
    }
}
