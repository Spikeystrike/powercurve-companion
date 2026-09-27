package app.grip_gains_companion.ui

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.Window

/** Request a high display rate during gestures and their trailing animations only. */
class InteractionFrameRate(private val window: Window, private val maximumRate: () -> Float = {
    val display=window.decorView.display
    val current=display?.mode
    display?.supportedModes?.filter { it.physicalWidth==current?.physicalWidth && it.physicalHeight==current.physicalHeight }
        ?.maxOfOrNull { it.refreshRate } ?: 0f
}) {
    private val handler = Handler(Looper.getMainLooper())
    private var previousRate: Float? = null
    private val restore = Runnable { reset() }

    fun onTouch(action: Int) {
        when(action) {
            MotionEvent.ACTION_DOWN -> {
                handler.removeCallbacks(restore)
                val best = maximumRate()
                if(best <= 60f) return
                if(previousRate == null) previousRate = window.attributes.preferredRefreshRate
                window.attributes = window.attributes.apply { preferredRefreshRate = best }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(restore)
                handler.postDelayed(restore, 2000)
            }
        }
    }

    fun reset() {
        handler.removeCallbacks(restore)
        previousRate?.let { rate ->
            window.attributes = window.attributes.apply { preferredRefreshRate = rate }
        }
        previousRate = null
    }
}
