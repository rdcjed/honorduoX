package io.github.odomu.honorduo.ui.screen

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import io.github.libxposed.service.XposedService
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.model.ModuleSettings
import io.github.odomu.honorduo.model.Prefs
import io.github.odomu.honorduo.model.StatusAppearance
import io.github.odomu.honorduo.ui.component.ActivatedContainer
import io.github.odomu.honorduo.ui.component.ActivatedContainerDark
import io.github.odomu.honorduo.ui.component.ActivatedContent
import io.github.odomu.honorduo.ui.component.GlassDropdownPreference
import io.github.odomu.honorduo.ui.component.SettingTile
import io.github.odomu.honorduo.ui.component.isDarkTheme
import io.github.odomu.honorduo.ui.control.ContentControl
import io.github.odomu.honorduo.ui.control.ContentControlRow
import io.github.odomu.honorduo.ui.control.SettingsPart
import io.github.odomu.honorduo.ui.control.SliderRow
import io.github.odomu.honorduo.ui.control.contentControlsFor
import io.github.odomu.honorduo.ui.control.geometrySliders
import io.github.odomu.honorduo.ui.control.slidersForEditor
import io.github.odomu.honorduo.ui.manager.AppUpdater
import io.github.odomu.honorduo.ui.manager.SettingsStore
import io.github.odomu.honorduo.ui.manager.UpdateState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

/**
 * The settings grid: one tile per sheet, two to a row.
 *
 * The order is the reading order of the grid, so a pair is always (left, right).
 */
private class SettingEntry(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    @DrawableRes val icon: Int,
    val part: SettingsPart,
)

private val STATUS_BAR_ENTRIES = listOf(
    SettingEntry(
        R.string.section_appearance, R.string.card_appearance_summary,
        R.drawable.ic_card_appearance, SettingsPart.Appearance,
    ),
    SettingEntry(
        R.string.drawing_part_shade, R.string.card_shade_summary,
        R.drawable.ic_lucide_sliders_horizontal, SettingsPart.Shade,
    ),
    SettingEntry(
        R.string.card_battery_title, R.string.card_battery_summary,
        R.drawable.ic_card_battery, SettingsPart.Battery,
    ),
    SettingEntry(
        R.string.card_wifi_title, R.string.card_wifi_summary,
        R.drawable.ic_card_wifi, SettingsPart.Wifi,
    ),
    SettingEntry(
        R.string.card_mobile_title, R.string.card_mobile_summary,
        R.drawable.ic_card_signal, SettingsPart.Mobile,
    ),
    SettingEntry(
        R.string.card_type_title, R.string.card_type_summary,
        R.drawable.ic_card_type, SettingsPart.Type,
    ),
    SettingEntry(
        R.string.card_readouts_title, R.string.card_readouts_summary,
        R.drawable.ic_card_readouts, SettingsPart.Readouts,
    ),
    SettingEntry(
        R.string.card_clock_title, R.string.card_clock_summary,
        R.drawable.ic_card_clock, SettingsPart.Clock,
    ),
)

private data class SettingsLink(
    @StringRes val title: Int,
    @StringRes val summary: Int,
    @DrawableRes val icon: Int,
    val open: () -> Unit,
)

internal fun LazyListScope.settingsTab(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenPart: (SettingsPart) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
) {
    val trio = SettingsLink(
        R.string.settings_trio_title, R.string.settings_trio_summary, R.drawable.ic_lucide_circle_dot,
    ) { onOpenPart(SettingsPart.Trio) }
    val backup = listOf(
        SettingsLink(R.string.backup_export_title, R.string.settings_export_summary,
            R.drawable.ic_lucide_share, onExportBackup),
        SettingsLink(R.string.backup_import_title, R.string.settings_import_summary,
            R.drawable.ic_lucide_rotate_ccw, onImportBackup),
    )

    item(key = "settings-search") {
        SettingsSearchField(query, onQueryChange)
    }
    if (query.isNotBlank()) {
        item(key = "settings-search-results") {
            val context = LocalContext.current
            val needle = query.trim()
            // Recompute only when the trimmed query changes, not on every recomposition
            // (e.g. theme change or parent scroll). searchableSettings() allocates ~200
            // SearchSetting objects; filtering iterates all of them with string ops.
            val results = remember(needle) {
                searchableSettings(context).filter { entry ->
                    settingSearchMatches(
                        needle,
                        context.getString(entry.titleRes),
                        entry.summaryRes?.let(context::getString).orEmpty(),
                        context.getString(entry.part.labelRes),
                    )
                }
            }
            val links = (listOf(trio) + backup).filter {
                settingSearchMatches(needle, context.getString(it.title), context.getString(it.summary))
            }
            if (results.isEmpty() && links.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.settings_search_empty),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    results.forEachIndexed { index, entry ->
                        if (index > 0) {
                            Box(
                                Modifier.fillMaxWidth().padding(start = 16.dp)
                                    .height(0.5.dp).background(MiuixTheme.colorScheme.outline.copy(alpha = 0.12f)),
                            )
                        }
                        SearchResultRow(
                            title = stringResource(entry.titleRes),
                            summary = entry.summaryRes?.let { stringResource(it) }
                                ?: stringResource(entry.part.labelRes),
                            query = needle,
                            onClick = { onOpenPart(entry.part) },
                        )
                    }
                    links.forEach { entry ->
                        SearchResultRow(
                            title = stringResource(entry.title),
                            summary = stringResource(entry.summary),
                            query = needle,
                            onClick = entry.open,
                        )
                    }
                }
            }
        }
        return
    }
    val statusBar = STATUS_BAR_ENTRIES.map { entry ->
        SettingsLink(entry.title, entry.summary, entry.icon) { onOpenPart(entry.part) }
    }
    settingsLinkSection(R.string.settings_group_trio, listOf(trio), columns = 1)
    settingsLinkSection(R.string.settings_group_status_bar, statusBar, columns = 2)
    settingsLinkSection(R.string.settings_group_backup, backup, columns = 2)
}

private fun LazyListScope.settingsLinkSection(
    @StringRes title: Int,
    links: List<SettingsLink>,
    columns: Int,
) {
    settingsGroupTitle(title)
    links.chunked(columns).forEach { pair ->
        item(key = "tile-${pair[0].title}") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { entry ->
                    SettingTile(
                        title = stringResource(entry.title),
                        summary = stringResource(entry.summary),
                        icon = ImageVector.vectorResource(entry.icon),
                        onClick = entry.open,
                        modifier = Modifier.weight(1f),
                        showChevron = columns == 1,
                    )
                }
                // An odd count would leave the last tile stretched across the row; a spacer
                // keeps it half-width and in its column.
                if (pair.size < columns) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun LazyListScope.settingsGroupTitle(@StringRes title: Int) {
    item(key = "settings-group-$title") {
        SmallTitle(
            text = stringResource(title),
            insideMargin = PaddingValues(start = 8.dp, top = 7.dp, end = 8.dp, bottom = 2.dp),
        )
    }
}

internal fun LazyListScope.appearanceSettingsTab(
    settings: ModuleSettings,
    update: ((SettingsStore) -> Unit) -> Unit,
) {
    val appearance = StatusAppearance.of(settings)
    val controls = contentControlsFor(SettingsPart.Appearance)
    val displayControls = setOf(
        ContentControl.AntiBurnIn, ContentControl.EffectiveScope,
        ContentControl.CapsuleNoScale, ContentControl.Enabled,
    )
    settingsGroupTitle(R.string.settings_appearance_size)
    item(key = "appearance-size") {
        Card(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Column {
                slidersForEditor(SettingsPart.Appearance).filter { it.enabled(appearance) }.forEach { slider ->
                    SliderRow(slider, settings, update)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 48.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        for (bound in listOf(slider.min, slider.maxFor(appearance))) {
                            Text(
                                text = "$bound${slider.suffix.orEmpty()}",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }
    }
    settingsGroupTitle(R.string.settings_appearance_icons)
    item(key = "appearance-icons") {
        Card(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            controls.filterNot { it in displayControls }.forEach { ContentControlRow(it, settings, update) }
        }
    }
    settingsGroupTitle(R.string.settings_appearance_display)
    item(key = "appearance-display") {
        Card(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            displayControls.filter { it in controls }.forEach { ContentControlRow(it, settings, update) }
        }
    }
}

internal data class SearchSetting(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int?,
    val part: SettingsPart,
)

/** Only names whose strings do not follow the enum name; the rest resolve by convention. */
private val CONTROL_TITLE_ALIASES = mapOf(
    ContentControl.Enabled to "master_module_title",
    ContentControl.MergeIcons to "master_title",
    ContentControl.Style to "trio_style_title",
    ContentControl.MobileActivity to "show_mobile_activity_title",
    ContentControl.Sim1 to "show_sim_1_title",
    ContentControl.Sim2 to "show_sim_2_title",
    ContentControl.Value to "show_value_title",
    ContentControl.Bolt to "show_bolt_title",
    ContentControl.BatteryIconVertical to "battery_icon_vertical_title",
    ContentControl.Clock to "drawing_occupant_clock",
    ContentControl.NetworkSpeedPosition to "network_speed_position_title",
    ContentControl.ShadeWeatherStyle to "weather_style_title",
    ContentControl.ShadeWeatherClick to "shade_weather_click_title",
)

private fun camelToSnake(name: String): String =
    name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()

/** Every control and size row, derived from the definitions themselves so new rows are searchable. */
internal fun searchableSettings(context: Context): List<SearchSetting> {
    val resources = context.resources
    fun stringId(name: String): Int =
        resources.getIdentifier(name, "string", context.packageName)

    val controls = ContentControl.entries.map { control ->
        val name = camelToSnake(control.name)
        val titleName = CONTROL_TITLE_ALIASES[control] ?: "${name}_title"
        val title = stringId(titleName).takeIf { it != 0 } ?: control.owner.labelRes
        val summary = stringId(titleName.removeSuffix("_title") + "_summary").takeIf { it != 0 }
        SearchSetting("control-${control.name}", title, summary, control.owner)
    }

    val appearance = StatusAppearance.of(ModuleSettings.defaults())
    val sliders = geometrySliders().map { slider ->
        val title = slider.titleRes(appearance)
        SearchSetting(
            key = "slider-${slider.editorOwner.name}-$title",
            titleRes = title,
            summaryRes = slider.summaryRes(appearance).takeIf { it != 0 },
            part = slider.editorOwner,
        )
    }
    return controls + sliders
}

internal fun settingSearchMatches(query: String, vararg values: String): Boolean =
    query.isNotBlank() && values.any { it.contains(query.trim(), ignoreCase = true) }

@Composable
private fun SettingsSearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusManager.clearFocus() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .background(MiuixTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(28.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_search),
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(stringResource(R.string.settings_search_hint), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(32.dp)) {
            Icon(ImageVector.vectorResource(R.drawable.ic_lucide_x), stringResource(R.string.settings_search_clear), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

/** One search hit: matched text is tinted, the trailing chevron mirrors a preference row. */
@Composable
private fun SearchResultRow(title: String, summary: String?, query: String, onClick: () -> Unit) {
    val accent = MiuixTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(
                text = highlightMatch(title, query, accent),
                style = TextStyle(
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (!summary.isNullOrBlank()) {
                BasicText(
                    text = highlightMatch(summary, query, accent),
                    style = TextStyle(
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                    ),
                )
            }
        }
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_lucide_chevron_right),
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Case-insensitive match highlighting; every hit is tinted, not just the first. */
internal fun highlightMatch(text: String, query: String, accent: Color): AnnotatedString = buildAnnotatedString {
    if (query.isEmpty()) {
        append(text)
        return@buildAnnotatedString
    }
    var cursor = 0
    while (true) {
        val at = text.indexOf(query, cursor, ignoreCase = true)
        if (at < 0) {
            append(text.substring(cursor))
            break
        }
        append(text.substring(cursor, at))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
            append(text.substring(at, at + query.length))
        }
        cursor = at + query.length
    }
}

/** About tab: activation, updates, logs, font and navigation styles. */
internal fun LazyListScope.aboutTab(
    settings: ModuleSettings,
    repository: SettingsStore,
    service: XposedService?,
    updater: AppUpdater,
    installingFont: Boolean,
    onChooseFont: () -> Unit,
    onClearFont: (String) -> Unit,
    onOpenLogs: () -> Unit,
    onOpenAboutPage: () -> Unit,
    update: ((SettingsStore) -> Unit) -> Unit,
) {
    val isActivated = service != null
    val rowMargin = PaddingValues(horizontal = 16.dp, vertical = 7.dp)
    val scopes = runCatching { service?.scope }.getOrNull() ?: emptyList()
    val hasSystemUiScope = scopes.contains("com.android.systemui")
    if (!isActivated) {
        item {
            ActivationBanner(
                title = stringResource(R.string.home_banner_inactive_title),
                summary = stringResource(R.string.home_banner_inactive_summary),
                isWarning = true,
            )
        }
    } else if (!hasSystemUiScope) {
        item {
            ActivationBanner(
                title = stringResource(R.string.home_banner_noscope_title),
                summary = stringResource(R.string.home_banner_noscope_summary),
                isWarning = true,
            )
        }
    }

    item { ActivatedRow(settings = settings, service = service) }
    item { InfoCard(service = service) }

    item {
        Card {
            LauncherIconEntry(rowMargin)
            ArrowPreference(
                title = stringResource(R.string.log_viewer_title),
                summary = stringResource(R.string.log_viewer_summary),
                insideMargin = rowMargin,
                onClick = onOpenLogs,
            )
            val aboutSummary = when (val s = updater.state) {
                is UpdateState.Checking -> stringResource(R.string.update_checking)
                is UpdateState.UpToDate -> stringResource(R.string.update_uptodate, s.current)
                is UpdateState.Available -> stringResource(R.string.update_available, s.info.version)
                is UpdateState.Downloading -> "正在下载 ${(s.progress * 100).toInt()}%"
                is UpdateState.Ready -> "新版本已下载完成，点击安装"
                is UpdateState.NoRelease -> stringResource(R.string.update_no_release)
                else -> stringResource(R.string.view_repository_summary)
            }
            ArrowPreference(
                title = stringResource(R.string.about_title),
                summary = aboutSummary,
                insideMargin = rowMargin,
                endActions = {
                    if (updater.state is UpdateState.Checking || updater.state is UpdateState.Downloading) {
                        CircularProgressIndicator(size = 18.dp, modifier = Modifier.padding(end = 4.dp))
                    }
                },
                onClick = onOpenAboutPage,
            )
        }
    }

    item {
        Card {
            GlassDropdownPreference(
                title = stringResource(R.string.language_title),
                items = listOf(
                    stringResource(R.string.language_system),
                    stringResource(R.string.language_zh),
                    stringResource(R.string.language_en),
                ),
                selectedIndex = repository.getAppLanguage(),
                insideMargin = rowMargin,
            ) { index ->
                repository.setAppLanguage(index)
                (repository.context as? Activity)?.recreate()
            }
            GlassDropdownPreference(
                title = stringResource(R.string.nav_style_title),
                items = listOf(
                    stringResource(R.string.nav_style_docked),
                    stringResource(R.string.nav_style_floating),
                    stringResource(R.string.nav_style_liquid),
                ),
                selectedIndex = Prefs.normalizeNavStyle(settings.navStyle),
                insideMargin = rowMargin,
            ) { index -> update { it.setNavStyle(index) } }
            GlassDropdownPreference(
                title = stringResource(R.string.nav_labels_title),
                items = listOf(
                    stringResource(R.string.nav_labels_both),
                    stringResource(R.string.nav_labels_icon),
                    stringResource(R.string.nav_labels_text),
                ),
                selectedIndex = Prefs.normalizeNavLabels(settings.navLabels),
                insideMargin = rowMargin,
            ) { index -> update { it.setNavLabels(index) } }
        }
    }

    item {
        Card {
            val chosen = settings.fontPath
            ArrowPreference(
                title = stringResource(R.string.font_title),
                summary = when {
                    installingFont -> stringResource(R.string.font_installing)
                    chosen.isEmpty() -> stringResource(R.string.font_summary_system)
                    else -> File(chosen).name
                },
                insideMargin = rowMargin,
                enabled = !installingFont,
                onClick = onChooseFont,
            )
            val nothingToClear = chosen.isEmpty()
            ArrowPreference(
                title = stringResource(R.string.font_clear_title),
                summary = if (nothingToClear) {
                    stringResource(R.string.font_clear_summary_idle)
                } else {
                    stringResource(R.string.font_clear_summary)
                },
                insideMargin = rowMargin,
                enabled = !nothingToClear && !installingFont,
                onClick = { onClearFont(chosen) },
            )
        }
    }
}

@Composable
fun ActivationBanner(
    title: String,
    summary: String,
    isWarning: Boolean = true,
) {
    val bg = if (isWarning) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
    val titleColor = if (isWarning) Color(0xFFC62828) else Color(0xFF2E7D32)
    val contentColor = if (isWarning) Color(0xFFB71C1C) else Color(0xFF1B5E20)
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = if (isDarkTheme()) Color(0xFF2C1E1E) else bg,
            contentColor = contentColor,
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.headline2,
                color = if (isDarkTheme()) Color(0xFFEF9A9A) else titleColor,
            )
            Text(
                text = summary,
                style = MiuixTheme.textStyles.footnote1,
                color = if (isDarkTheme()) Color(0xFFE57373) else contentColor,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ActivatedRow(settings: ModuleSettings, service: XposedService?) {
    val context = LocalContext.current
    val module = remember(context) { readModuleInfo(context) }
    val dark = isDarkTheme()
    val content = if (dark) MiuixTheme.colorScheme.onSurface else ActivatedContent
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = if (dark) ActivatedContainerDark else ActivatedContainer,
            contentColor = content,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1.35f)) {
                Text(
                    text = stringResource(if (service != null) R.string.about_activated else R.string.about_inactive),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (service != null) {
                        stringResource(R.string.about_module_version, module.moduleVersion)
                    } else {
                        stringResource(R.string.about_inactive_hint)
                    },
                    style = MiuixTheme.textStyles.footnote2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            AboutStat(stringResource(R.string.about_hook_count), hookCount(settings), Modifier.weight(1f))
            AboutStat(stringResource(R.string.about_enabled_count), enabledCount(settings), Modifier.weight(1f))
        }
    }
}

@Composable
private fun AboutStat(label: String, value: Int, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MiuixTheme.textStyles.body1, fontWeight = FontWeight.SemiBold)
        Text(label, style = MiuixTheme.textStyles.footnote2)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MiuixTheme.textStyles.footnote1, modifier = Modifier.weight(0.9f))
        Text(
            value, style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(2.1f),
        )
    }
}

@Composable
private fun LauncherIconEntry(rowMargin: PaddingValues) {
    val context = LocalContext.current
    var hidden by remember(context) { mutableStateOf(isLauncherIconHidden(context)) }
    var showConfirm by remember { mutableStateOf(false) }

    SwitchPreference(
        checked = hidden,
        onCheckedChange = { value ->
            if (value) {
                showConfirm = true
            } else {
                setLauncherIconHidden(context, false)
                hidden = isLauncherIconHidden(context)
            }
        },
        title = stringResource(R.string.about_hide_icon),
        summary = stringResource(R.string.about_hide_icon_summary),
        insideMargin = rowMargin,
    )

    if (showConfirm) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.hide_icon_confirm_title),
            summary = stringResource(R.string.hide_icon_confirm_summary),
            onDismissRequest = { showConfirm = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = { showConfirm = false },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        showConfirm = false
                        setLauncherIconHidden(context, true)
                        hidden = isLauncherIconHidden(context)
                    },
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.hide_icon_confirm_action))
                }
            }
        }
    }
}

private fun launcherAlias(context: Context): ComponentName =
    ComponentName(context.packageName, Prefs.LAUNCHER_ALIAS)

private fun isLauncherIconHidden(context: Context): Boolean = try {
    context.packageManager.getComponentEnabledSetting(launcherAlias(context)) ==
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
} catch (_: Throwable) {
    false
}

private fun setLauncherIconHidden(context: Context, hidden: Boolean) {
    try {
        context.packageManager.setComponentEnabledSetting(
            launcherAlias(context),
            if (hidden) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    } catch (_: Throwable) {
    }
}

@Composable
private fun InfoCard(service: XposedService?) {
    val context = LocalContext.current
    val unknown = stringResource(R.string.info_unknown)
    val module = remember(context) { readModuleInfo(context) }
    val framework = remember(service, unknown) { readFrameworkInfo(service, unknown) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            InfoRow(stringResource(R.string.info_module_version), module.moduleVersion)
            InfoRow(stringResource(R.string.info_xposed_version), framework)
            InfoRow(stringResource(R.string.info_device_model), module.deviceModel)
            InfoRow(stringResource(R.string.info_android_version), module.androidVersion)
            InfoRow(
                label = stringResource(R.string.info_system_fingerprint),
                value = module.systemFingerprint,
            )
        }
    }
}

data class ModuleInfo(
    val moduleVersion: String,
    val systemFingerprint: String,
    val androidVersion: String,
    val deviceModel: String,
)

fun readModuleInfo(context: Context): ModuleInfo {
    val packageInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()
    val versionName = packageInfo?.versionName ?: "?"
    val versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
    val systemVersion = listOf(Build.VERSION.INCREMENTAL, Build.DISPLAY)
        .filter { !it.isNullOrBlank() }
        .distinct()
        .joinToString(" · ")
    return ModuleInfo(
        moduleVersion = "$versionName ($versionCode)",
        systemFingerprint = Build.FINGERPRINT.ifBlank { "?" },
        androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        deviceModel = readDeviceName(context),
    )
}

fun readDeviceName(context: Context): String {
    val name = runCatching {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() } ?: Build.MODEL.orEmpty()
}

/**
 * The hooks the switched-on features install: one per hooked surface in use.
 *
 * Counted from the settings in this process. The install count lives in SystemUI, and
 * every write from there is lost the next time this process saves the same preference
 * file, so the number the screen showed was always zero.
 */
fun hookCount(settings: ModuleSettings): Int {
    val a = StatusAppearance.of(settings)
    if (!a.enabled) return 0
    return 1 + listOf( // the bar and row hooks every enabled module installs
        a.mobile,
        a.replaceStatusIcon || a.hideAlarmIcon || a.hideBluetoothIcon || a.hidePrivacyChip,
        a.shadeHidePower || a.shadeHideBell || a.shadeLunar || a.shadeWeather || a.shadeHideChargeNotif || a.shadeHideHotspotNotif || a.shadeHideAdbNotif,
        a.btBatteryMode != 0,
        a.capsuleNoScale,
        a.hideSim2 || a.hideNetType,
    ).count { it }
}

fun enabledCount(settings: ModuleSettings): Int {
    val a = StatusAppearance.of(settings)
    if (!a.enabled) return 0
    return listOf(
        a.wifi || a.replaceWifiIcon,
        a.mobile,
        a.dualSim,
        a.value,
        a.drawsBolt(),
        a.typeAnywhere(),
        a.perfPower || a.perfRunning,
        a.perfTemp,
        a.perfCurrent,
        a.perfFps,
        a.clockTimePattern.isNotEmpty() || a.clockSeconds || a.clockDate || a.clockWeekday || a.clockLunar || a.clockWeather,
        a.shadeHidePower || a.shadeHideBell || a.shadeLunar || a.shadeWeather,
        a.replaceStatusIcon || a.hideAlarmIcon || a.hideBluetoothIcon,
    ).count { it }
}

fun readFrameworkInfo(service: XposedService?, unknown: String): String {
    if (service == null) return unknown
    return runCatching {
        val name = service.frameworkName
        val version = service.frameworkVersion
        val code = service.frameworkVersionCode
        "$name $version ($code) API ${service.apiVersion}"
    }.getOrDefault(unknown)
}

