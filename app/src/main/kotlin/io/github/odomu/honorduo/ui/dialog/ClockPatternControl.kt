package io.github.odomu.honorduo.ui.dialog

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.odomu.honorduo.R
import io.github.odomu.honorduo.model.ClockPattern
import io.github.odomu.honorduo.ui.component.SettingsItemMargin
import java.text.SimpleDateFormat
import java.util.Date
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ClockPatternControl(
    title: Int,
    value: String,
    defaultPattern: String?,
    hint: Int,
    showDefaultSeconds: Boolean = false,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    val today = Date()
    val is24Hour = DateFormat.is24HourFormat(context)
    val systemTimeFormat = DateFormat.getTimeFormat(context)
    val defaultInput = defaultPattern ?: when {
        showDefaultSeconds -> ClockPattern.timeWithSeconds(is24Hour)
        systemTimeFormat is SimpleDateFormat -> systemTimeFormat.toPattern()
        is24Hour -> "HH:mm"
        else -> "h:mm a"
    }
    fun preview(pattern: String): String? = when {
        pattern.isNotBlank() -> ClockPattern.format(pattern.trim(), today)
        defaultPattern != null -> ClockPattern.format(defaultPattern, today)
        showDefaultSeconds -> ClockPattern.format(ClockPattern.timeWithSeconds(is24Hour), today)
        else -> systemTimeFormat.format(today)
    }

    var open by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var useDefault by remember { mutableStateOf(false) }
    val sample = preview(value) ?: preview("").orEmpty()
    ArrowPreference(
        title = stringResource(title),
        summary = stringResource(R.string.clock_pattern_preview, sample),
        insideMargin = SettingsItemMargin,
        onClick = {
            useDefault = value.isBlank()
            draft = value.ifBlank { defaultInput }
            open = true
        },
    )
    if (!open) return

    val currentPreview = preview(if (useDefault) "" else draft)
    val colors = MiuixTheme.colorScheme
    OverlayDialog(
        show = true,
        title = stringResource(title),
        summary = stringResource(hint),
        onDismissRequest = { open = false },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BasicTextField(
                value = draft,
                onValueChange = {
                    draft = it.take(ClockPattern.MAX_LENGTH)
                    useDefault = false
                },
                singleLine = true,
                textStyle = MiuixTheme.textStyles.body1.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                modifier = Modifier.fillMaxWidth()
                    .background(colors.surfaceContainerHigh, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            Text(
                text = if (currentPreview != null) stringResource(R.string.clock_pattern_preview, currentPreview)
                else stringResource(R.string.clock_pattern_invalid),
                style = MiuixTheme.textStyles.body2,
                color = if (currentPreview != null) colors.onSurface else colors.error,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = stringResource(R.string.clock_pattern_default),
                    onClick = {
                        useDefault = true
                        draft = defaultInput
                    },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        onSave(if (useDefault) "" else draft.trim())
                        open = false
                    },
                    enabled = currentPreview != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.clock_pattern_save))
                }
            }
        }
    }
}
