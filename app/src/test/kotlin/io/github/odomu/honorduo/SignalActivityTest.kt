package io.github.odomu.honorduo

import io.github.odomu.honorduo.model.SignalActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class SignalActivityTest {
    @Test
    fun dataActivityFollowsOnlyTheVisibleDataSim() {
        assertEquals(0, SignalActivity.row(true, 0, 0, true))
        assertEquals(1, SignalActivity.row(true, 1, 0, true))
        assertEquals(0, SignalActivity.row(true, 1, 1, false))
        assertEquals(-1, SignalActivity.row(true, 1, 0, false))
        assertEquals(-1, SignalActivity.row(false, 0, 0, true))
        assertEquals(-1, SignalActivity.row(true, -1, 0, true))
    }
}
