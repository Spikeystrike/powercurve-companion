package app.grip_gains_companion.service.web

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WebViewBridgeTest {
    private fun update(bridge: WebViewBridge, weight: String?) {
        bridge.onSnapshot(JSONObject().put("phase", "setup").put("weight", weight ?: JSONObject.NULL).toString())
    }
    @Test fun targetTracksEachWebsiteWeightAndConvertsUnits() {
        val bridge = WebViewBridge()
        update(bridge, "32,5 kg")
        assertEquals(32.5, bridge.targetWeight.value!!, 0.000001)
        update(bridge, "45 lb")
        assertEquals(20.41165665, bridge.targetWeight.value!!, 0.000001)
        update(bridge, "12 kg")
        assertEquals(12.0, bridge.targetWeight.value!!, 0.000001)
    }
    @Test fun missingAndInvalidWeightsClearThePreviousTarget() {
        val bridge = WebViewBridge()
        for (invalid in listOf(null, "", "bad", "-20 kg", "0 kg", "20 unknown")) {
            update(bridge, "20 kg")
            update(bridge, invalid)
            assertNull(bridge.targetWeight.value)
        }
    }
    @Test fun navigationClearsStaleWeight() {
        val bridge = WebViewBridge()
        update(bridge, "20 kg")
        bridge.invalidate()
        assertNull(bridge.targetWeight.value)
    }
}
