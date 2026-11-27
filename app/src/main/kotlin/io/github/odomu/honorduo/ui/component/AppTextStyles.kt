package io.github.odomu.honorduo.ui.component

import androidx.compose.ui.text.TextStyle
import top.yukonga.miuix.kmp.theme.TextStyles
import top.yukonga.miuix.kmp.theme.defaultTextStyles

/** Use the settings module tiles as the typography reference throughout the app. */
internal fun compactAppTextStyles(): TextStyles {
    val defaults = defaultTextStyles()
    val label = defaults.body2
    fun TextStyle.labelSize() = copy(fontSize = label.fontSize, lineHeight = label.lineHeight)
    return defaults.copy(
        main = defaults.main.labelSize(),
        paragraph = defaults.paragraph.labelSize(),
        body1 = defaults.body1.labelSize(),
        // Miuix preferences use body2 for summaries and headline1 for titles.
        body2 = defaults.footnote2,
        button = defaults.button.labelSize(),
        footnote1 = defaults.footnote2,
        headline1 = defaults.headline1.labelSize(),
        headline2 = defaults.headline2.labelSize(),
        title1 = defaults.title1.labelSize(),
        title2 = defaults.title2.labelSize(),
        title3 = defaults.title3.labelSize(),
        title4 = defaults.title4.labelSize(),
    )
}
