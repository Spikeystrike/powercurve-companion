package app.grip_gains_companion.service.web

import android.view.View
import app.grip_gains_companion.R

/** View.setTag(key, value) rejects framework IDs such as android.R.id.custom. */
internal object BridgeInstallationTag {
    fun isInstalled(view: View): Boolean = view.getTag(R.id.powercurve_web_bridge_installed) == true
    fun markInstalled(view: View) { view.setTag(R.id.powercurve_web_bridge_installed, true) }
}
