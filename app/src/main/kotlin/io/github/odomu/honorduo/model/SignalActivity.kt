package io.github.odomu.honorduo.model

/** Maps the current data SIM to the visible row in the stacked signal. */
internal object SignalActivity {
    fun row(dataEnabled: Boolean, dataSlot: Int, firstSlot: Int, dual: Boolean): Int = when {
        !dataEnabled || dataSlot !in 0..1 -> -1
        dataSlot == firstSlot -> 0
        dual && dataSlot == 1 -> 1
        else -> -1
    }
}
