package io.github.odomu.honorduo

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import io.github.odomu.honorduo.hook.tracker.FpsTicker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class FpsTickerTest {
    @Test
    fun stoppingCancelsPendingFrameBeforeSamplingCanRestart() {
        fun field(name: String) = FpsTicker::class.java.getDeclaredField(name).apply { isAccessible = true }
        val callback = field("frameCallback").get(null) as Choreographer.FrameCallback
        val choreographer = Choreographer.getInstance()
        fun pending(): Int {
            val queues = Choreographer::class.java.getDeclaredField("mCallbackQueues").apply { isAccessible = true }
                .get(choreographer) as Array<*>
            val queue = queues[1]!!
            var record = queue.javaClass.getDeclaredField("mHead").apply { isAccessible = true }.get(queue)
            var count = 0
            while (record != null) {
                val action = record.javaClass.getDeclaredField("action").apply { isAccessible = true }.get(record)
                if (action === callback) count++
                record = record.javaClass.getDeclaredField("next").apply { isAccessible = true }.get(record)
            }
            return count
        }
        val main = field("main")
        val previousMain = main.get(null)
        try {
            main.set(null, Handler(Looper.getMainLooper()))
            choreographer.postFrameCallback(callback)
            assertEquals(1, pending())
            FpsTicker::class.java.getDeclaredMethod("stopSampling").apply { isAccessible = true }.invoke(FpsTicker)
            assertEquals("A stale callback could join a new sampling chain after rapid reactivation", 0, pending())
        } finally {
            choreographer.removeFrameCallback(callback)
            main.set(null, previousMain)
        }
    }
}
