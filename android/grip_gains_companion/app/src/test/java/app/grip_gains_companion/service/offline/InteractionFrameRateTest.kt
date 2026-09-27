package app.grip_gains_companion.service.offline

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.MotionEvent
import app.grip_gains_companion.ui.InteractionFrameRate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class InteractionFrameRateTest {
    @Test fun boostLastsThroughGestureAndResetsAfterReleaseOrPause() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val window=activity.window
        val controller=InteractionFrameRate(window){120f}
        controller.onTouch(MotionEvent.ACTION_DOWN)
        assertEquals(120f,window.attributes.preferredRefreshRate,0f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(120f,window.attributes.preferredRefreshRate,0f)
        controller.onTouch(MotionEvent.ACTION_UP)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        controller.onTouch(MotionEvent.ACTION_DOWN)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(120f,window.attributes.preferredRefreshRate,0f)
        controller.onTouch(MotionEvent.ACTION_CANCEL)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        assertEquals(0f,window.attributes.preferredRefreshRate,0f)
        controller.onTouch(MotionEvent.ACTION_DOWN);controller.reset()
        assertEquals(0f,window.attributes.preferredRefreshRate,0f)
    }
    @Test fun sixtyHertzDisplayKeepsSystemPreference() {
        val window=Robolectric.buildActivity(Activity::class.java).setup().get().window
        val controller=InteractionFrameRate(window){60f}
        controller.onTouch(MotionEvent.ACTION_DOWN)
        assertEquals(0f,window.attributes.preferredRefreshRate,0f)
    }
}
