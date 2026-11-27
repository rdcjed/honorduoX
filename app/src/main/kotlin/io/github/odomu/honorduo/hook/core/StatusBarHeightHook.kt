package io.github.odomu.honorduo.hook.core

import android.content.Context
import android.view.DisplayCutout
import android.view.View
import io.github.libxposed.api.XposedModule
import io.github.odomu.honorduo.model.StatusAppearance
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Uses the same height for the status bar window, its insets, and the phone bar view. */
internal object StatusBarHeightHook {
    private class Bindings(
        val height: Method,
        val refreshView: Method,
        val applyWindow: Method,
        val windowContext: Field,
        val windowHeight: Field,
        val windowState: Field,
        val rotateCutout: Method?,
    )

    private val windows = WeakHashMap<Any, Unit>()
    private val views = WeakHashMap<View, Unit>()
    private val resolvingHeight = ThreadLocal<Boolean>()
    @Volatile private var bindings: Bindings? = null
    @Volatile private var offsetDp = 0

    fun install(module: XposedModule, loader: ClassLoader): Int {
        val intType = Int::class.javaPrimitiveType!!
        val utils = Refl.cls("com.android.internal.policy.SystemBarUtils", loader) ?: return 0
        val height = Refl.method(utils, "getStatusBarHeight", Context::class.java) ?: return 0
        val rotatedHeight = Refl.method(utils, "getStatusBarHeightForRotation", Context::class.java, intType)
            ?: return 0
        val window = Refl.cls("com.android.systemui.statusbar.window.StatusBarWindowControllerImpl", loader)
            ?: Refl.cls("com.android.systemui.statusbar.window.StatusBarWindowController", loader)
            ?: return 0
        val attachWindow = Refl.method(window, "attach$1") ?: Refl.method(window, "attach") ?: return 0
        val windowContext = Refl.field(window, "mContext") ?: return 0
        val windowHeight = Refl.field(window, "mBarHeight") ?: return 0
        val windowState = Refl.field(window, "mCurrentState") ?: return 0
        val applyWindow = Refl.method(window, "apply", windowState.type) ?: return 0
        val phoneBar = Refl.cls("com.android.systemui.statusbar.phone.HwPhoneStatusBarView", loader)
            ?: return 0
        val attachView = Refl.method(phoneBar, "onAttachedToWindow") ?: return 0
        val refreshView = Refl.method(phoneBar, "updateStatusBarHeight$1")
            ?: Refl.method(phoneBar, "updateStatusBarHeight") ?: return 0
        val rotateCutout = Refl.method(DisplayCutout::class.java, "getRotated", intType, intType, intType, intType)

        var hooked = SystemUiHook.hook(module, attachWindow, "honorduo-height-window") { chain ->
            val result = chain.proceed()
            chain.thisObject?.let { windows[it] = Unit }
            result
        }
        hooked += SystemUiHook.hook(module, attachView, "honorduo-height-view") { chain ->
            val result = chain.proceed()
            (chain.thisObject as? View)?.let { views[it] = Unit }
            result
        }
        for (method in listOf(height, rotatedHeight)) {
            hooked += SystemUiHook.hook(module, method, "honorduo-height-${method.name}") { chain ->
                val offset = offsetDp
                val target = bindings
                if (offset == 0 || target == null || resolvingHeight.get() == true) {
                    return@hook chain.proceed()
                }
                // A vendor implementation may delegate one public overload to the other.
                resolvingHeight.set(true)
                try {
                    val native = chain.proceed() as Int
                    val context = chain.getArg(0) as Context
                    val rotation = if (method == rotatedHeight) chain.getArg(1) as Int else null
                    runCatching { adjustedHeight(native, context, rotation, offset, target.rotateCutout) }
                        .getOrDefault(native)
                } finally {
                    resolvingHeight.remove()
                }
            }
        }
        if (hooked == 4) {
            bindings = Bindings(height, refreshView, applyWindow, windowContext, windowHeight, windowState, rotateCutout)
        }
        return hooked
    }

    fun onConfigChanged(appearance: StatusAppearance) {
        val target = bindings ?: return
        val next = appearance.statusBarHeightOffset
        if (next == offsetDp) return
        offsetDp = next
        for (window in windows.keys.toList()) {
            runCatching {
                val context = target.windowContext.get(window) as Context
                val state = target.windowState.get(window)
                target.windowHeight.setInt(window, target.height.invoke(null, context) as Int)
                target.applyWindow.invoke(window, state)
            }.onFailure { SystemUiHook.log(SystemUiHook.LOG_WARN, "status bar window height refresh failed: $it") }
        }
        for (view in views.keys.toList()) {
            if (view.isAttachedToWindow && view.layoutParams != null) {
                runCatching { target.refreshView.invoke(view) }
                    .onFailure { SystemUiHook.log(SystemUiHook.LOG_WARN, "status bar view height refresh failed: $it") }
            }
        }
    }

    fun currentHeight(context: Context): Int? {
        if (offsetDp == 0) return null
        return runCatching { bindings?.height?.invoke(null, context) as? Int }.getOrNull()
    }

    private fun adjustedHeight(
        native: Int, context: Context, rotation: Int?, offset: Int, rotateCutout: Method?,
    ): Int {
        if (native <= 0) return native
        val metrics = context.resources.displayMetrics
        val delta = (offset * metrics.density).roundToInt()
        if (delta >= 0) return native + delta
        val display = context.display ?: return native
        val currentRotation = display.rotation
        var cutout = display.cutout
        if (cutout != null && rotation != null && currentRotation != rotation) {
            cutout = rotateCutout?.invoke(cutout, metrics.widthPixels, metrics.heightPixels, currentRotation, rotation)
                as? DisplayCutout ?: return native
        }
        val floor = maxOf(cutout?.safeInsetTop ?: 0,
            (24 * metrics.density).roundToInt() + (cutout?.waterfallInsets?.top ?: 0))
        return constrainHeight(native, delta, floor)
    }

    internal fun constrainHeight(native: Int, delta: Int, floor: Int): Int =
        maxOf(native + delta, minOf(native, floor))
}
