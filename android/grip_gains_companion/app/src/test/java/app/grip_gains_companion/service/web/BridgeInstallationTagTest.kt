package app.grip_gains_companion.service.web

import android.app.Application
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BridgeInstallationTagTest {
    @Test fun frameworkKeyReproducesTheStartupCrash() {
        val view = View(RuntimeEnvironment.getApplication<Application>())
        assertThrows(IllegalArgumentException::class.java) {
            view.setTag(android.R.id.custom, true)
        }
    }

    @Test fun appKeyCanMarkTheBridgeWithoutCrashing() {
        val view = View(RuntimeEnvironment.getApplication<Application>())
        assertFalse(BridgeInstallationTag.isInstalled(view))
        BridgeInstallationTag.markInstalled(view)
        assertTrue(BridgeInstallationTag.isInstalled(view))
        BridgeInstallationTag.markInstalled(view)
        assertTrue(BridgeInstallationTag.isInstalled(view))
    }

    @Test fun anotherViewStartsUnmarked() {
        val context = RuntimeEnvironment.getApplication<Application>()
        val first = View(context)
        val second = View(context)
        BridgeInstallationTag.markInstalled(first)
        assertFalse(BridgeInstallationTag.isInstalled(second))
    }
}
