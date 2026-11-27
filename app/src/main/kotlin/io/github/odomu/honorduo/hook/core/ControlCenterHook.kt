/*
 * Copyright (C) 2026 odomu and HonorDuo contributors
 *
 * This file is part of HonorDuo, a fork of HyperDuo, released under the GNU
 * General Public License v3.0. See the LICENSE file at the repository root, or
 * <https://www.gnu.org/licenses/gpl-3.0.html>.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 */

package io.github.odomu.honorduo.hook.core

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.hook.tracker.HiddenViews
import io.github.odomu.honorduo.hook.tracker.ControlCenterPanelScale
import io.github.odomu.honorduo.hook.tracker.SliderPress
import io.github.odomu.honorduo.model.StatusAppearance

/**
 * Hooks for the MagicOS control center (right pull panel).
 *
 * Responsibilities:
 * - Hides the header's power button.
 * - Turns a long press on the volume slider into the system media-output dialog.
 */
internal object ControlCenterHook {

    private const val CLS_CONTROL_HEADER = "com.android.systemui.controllercenter.HnControlCenterHeader"
    private const val CLS_SLIDER_VIEW = "com.android.systemui.settings.brightness.HnCentralSliderView"
    private const val CLS_SWITCHER_UI = "com.android.systemui.media.dialog.MediaOutputSwitcherDialogUI"

    private const val FIELD_POWER_BUTTON = "mPowerButton"

    fun install(module: XposedModule, cl: ClassLoader): Int {
        var n = 0
        n += hookPowerButton(module, cl)
        n += hookVolumeSlider(module, cl)
        n += hookSwitcher(module, cl)
        n += hookIconSize(module, cl)
        n += hookPanelState(module, cl)
        n += hookPanelViewport(module, cl)
        return n
    }

    private fun hookIconSize(module: XposedModule, cl: ClassLoader): Int {
        val container = Refl.cls("com.android.systemui.controllercenter.HnControlCenterContainer", cl) ?: return 0
        val layout = Refl.methodNamed(container, "onLayout", 5) ?: return 0
        return SystemUiHook.hook(module, layout, "honorduo-cc-panel-size") { chain ->
            val result = chain.proceed()
            val view = chain.thisObject as? ViewGroup
            if (view != null) {
                val editing = Refl.getBool(Refl.field(view.javaClass, "isEditMode"), view, false)
                var parent = view.parent
                while (parent is View) {
                    if (parent.javaClass.name == "com.android.systemui.statusbar.phone.HnColumeContainer") {
                        val expanded = Refl.getBool(Refl.field(parent.javaClass, "isControlCenterExpand"), parent, false)
                        ControlCenterPanelScale.register(parent, view, editing, expanded)
                        break
                    }
                    parent = parent.parent
                }
            }
            result
        }
    }

    private fun hookPanelState(module: XposedModule, cl: ClassLoader): Int {
        val listener = Refl.cls("com.android.systemui.statusbar.phone.HnColumeContainer" + '$' + "mControlCenterExpandListener" + '$' + "1", cl) ?: return 0
        return SystemUiHook.hook(module, Refl.methodNamed(listener, "onControlCenterPanelChange", 1), "honorduo-cc-panel-state") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            val panel = self?.let { Refl.get(Refl.field(it.javaClass, "this" + '$' + "0"), it) } as? View
            if (panel != null) ControlCenterPanelScale.setExpanded(panel, chain.getArg(0) as Boolean)
            result
        }
    }

    private fun hookPanelViewport(module: XposedModule, cl: ClassLoader): Int {
        val column = Refl.cls("com.android.systemui.statusbar.phone.HnColumeContainer", cl) ?: return 0
        val measure = Refl.methodNamed(column, "onMeasure", 2) ?: return 0
        return SystemUiHook.hook(module, measure, "honorduo-cc-viewport") { chain ->
            val panel = chain.thisObject as? View
            if (panel == null || !column.isInstance(panel)) chain.proceed() else {
                val height = chain.getArg(1) as Int
                val adjusted = ControlCenterPanelScale.heightMeasureSpec(panel, height)
                if (adjusted == height) chain.proceed() else chain.proceed(arrayOf<Any>(chain.getArg(0), adjusted))
            }
        }
    }

    private fun hookPowerButton(module: XposedModule, cl: ClassLoader): Int {
        val header = Refl.cls(CLS_CONTROL_HEADER, cl) ?: return 0
        val inflate = Refl.method(header, "onFinishInflate") ?: return 0
        return SystemUiHook.hook(module, inflate, "honorduo-cc-power") { chain ->
            val result = chain.proceed()
            val self = chain.thisObject
            if (self is View) {
                val button = Refl.get(Refl.field(self.javaClass, FIELD_POWER_BUTTON), self)
                if (button is View) HiddenViews.register(button) { a -> a.shadeHidePower }
            }
            result
        }
    }

    /**
     * Long press on the volume slider opens the media output dialog.
     *
     * The slider only sees the gesture, so the press has to be timed here; motion
     * beyond the touch slop cancels it, which keeps a plain volume drag untouched.
     */
    private fun hookVolumeSlider(module: XposedModule, cl: ClassLoader): Int {
        val sliderClass = Refl.cls(CLS_SLIDER_VIEW, cl) ?: return 0
        val touch = Refl.methodNamed(sliderClass, "dispatchTouchEvent", 1) ?: return 0
        return SystemUiHook.hook(module, touch, "honorduo-cc-slider-touch") { chain ->
            val self = chain.thisObject
            val event = chain.args.getOrNull(0) as? MotionEvent
            if (self is View && event != null && SliderPress.isSlider(self)) {
                SliderPress.onTouch(self, event)
            }
            chain.proceed()
        }
    }

    /**
     * Keeps the media-output switcher, which is a `CoreStartable` the host starts once.
     *
     * Its `showMediaOutputSwitcher` is the vendor path: it pre-inflates
     * `HnMediaDialogEx` and asks the manager for the rounded MagicOS list. Reached
     * through `CommandQueue` it is refused - that entry only accepts uid 0/1000, and
     * SystemUI runs as its own uid here - so the instance is called directly.
     */
    private fun hookSwitcher(module: XposedModule, cl: ClassLoader): Int {
        val switcherClass = Refl.cls(CLS_SWITCHER_UI, cl) ?: return 0
        val start = Refl.methodNamed(switcherClass, "start", 0) ?: return 0
        return SystemUiHook.hook(module, start, "honorduo-cc-switcher") { chain ->
            chain.proceed()
            chain.thisObject?.let { SliderPress.remember(it) }
            null
        }
    }

    fun onConfigChanged(a: StatusAppearance) {
        HiddenViews.applyAll(a)
        ControlCenterPanelScale.applyAll(a.shadeIconScale)
    }
}
