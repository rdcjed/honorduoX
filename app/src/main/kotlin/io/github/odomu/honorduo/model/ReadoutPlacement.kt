package io.github.odomu.honorduo.model

/** A readout has one destination. Negative positions are outside the layout bands. */
internal object ReadoutPlacement {
    const val OFF = -3
    const val LEFT = -2
    const val RIGHT = -1

    fun isReadout(part: Int): Boolean = part in Prefs.PART_POWER..Prefs.PART_BT

    fun onLeft(mask: Int, part: Int): Boolean = isReadout(part) && mask and (1 shl part) != 0

    fun setLeft(mask: Int, part: Int, left: Boolean): Int {
        if (!isReadout(part)) return mask
        val bit = 1 shl part
        return if (left) mask or bit else mask and bit.inv()
    }

    fun enabled(s: ModuleSettings, part: Int): Boolean = when (part) {
        Prefs.PART_POWER -> s.perfPower
        Prefs.PART_RUNNING -> s.perfRunning
        Prefs.PART_TEMP -> s.perfTemp
        Prefs.PART_FPS -> s.perfFps
        Prefs.PART_CURRENT -> s.perfCurrent
        Prefs.PART_VOLTAGE -> s.perfVoltage
        Prefs.PART_SIGNAL -> s.perfSignal
        Prefs.PART_BT -> s.btBatteryMode != Prefs.BT_BATTERY_OFF
        else -> false
    }

    fun setEnabled(s: ModuleSettings, part: Int, enabled: Boolean) {
        when (part) {
            Prefs.PART_POWER -> s.perfPower = enabled
            Prefs.PART_RUNNING -> s.perfRunning = enabled
            Prefs.PART_TEMP -> s.perfTemp = enabled
            Prefs.PART_FPS -> s.perfFps = enabled
            Prefs.PART_CURRENT -> s.perfCurrent = enabled
            Prefs.PART_VOLTAGE -> s.perfVoltage = enabled
            Prefs.PART_SIGNAL -> s.perfSignal = enabled
            Prefs.PART_BT -> s.btBatteryMode = if (enabled) {
                s.btBatteryMode.takeIf { it != Prefs.BT_BATTERY_OFF } ?: Prefs.BT_BATTERY_ARC
            } else Prefs.BT_BATTERY_OFF
        }
    }

    fun orderId(part: Int): Int = when (part) {
        Prefs.PART_POWER, Prefs.PART_RUNNING -> Prefs.PERF_ITEM_POWER
        Prefs.PART_TEMP -> Prefs.PERF_ITEM_TEMP
        Prefs.PART_FPS -> Prefs.PERF_ITEM_FPS
        Prefs.PART_CURRENT -> Prefs.PERF_ITEM_CURRENT
        Prefs.PART_VOLTAGE -> Prefs.PERF_ITEM_VOLTAGE
        Prefs.PART_SIGNAL -> Prefs.PERF_ITEM_SIGNAL
        Prefs.PART_BT -> Prefs.PERF_ITEM_BT
        else -> error("Not a readout")
    }
}
