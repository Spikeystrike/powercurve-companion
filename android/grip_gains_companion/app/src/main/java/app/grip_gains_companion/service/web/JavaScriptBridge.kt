package app.grip_gains_companion.service.web

import android.content.Context
import org.json.JSONObject

object JavaScriptBridge {
    fun install(context: Context): String = context.assets.open("powercurve-bridge.js").bufferedReader().use { it.readText() }
    fun endRep(key: String): String = "window.PowercurveCompanion?.endRep(${JSONObject.quote(key)}) === true"
}
