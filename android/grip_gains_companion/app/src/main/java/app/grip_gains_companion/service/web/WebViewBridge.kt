package app.grip_gains_companion.service.web

import android.os.SystemClock
import android.webkit.WebView
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Called only from an origin-restricted, main-frame WebMessageListener on the UI thread. */
class WebViewBridge {
    private var webView: WebView? = null
    private var heartbeat = 0L
    private var phase = "unavailable"
    var repKey = ""
        private set
    val isFreshActive: Boolean get() = _buttonEnabled.value && SystemClock.elapsedRealtime() - heartbeat < 1500
    private val _sessionGripper = MutableStateFlow<String?>(null)
    val sessionGripper = _sessionGripper.asStateFlow()
    private val _sessionSide = MutableStateFlow<String?>(null)
    val sessionSide = _sessionSide.asStateFlow()
    private val _manualSessionEndTrigger = MutableStateFlow(false)
    val manualSessionEndTrigger = _manualSessionEndTrigger.asStateFlow()
    private val _canGoBack = MutableStateFlow(false)
    val canGoBack = _canGoBack.asStateFlow()
    private val _canGoForward = MutableStateFlow(false)
    val canGoForward = _canGoForward.asStateFlow()
    private val _isToolbarVisible = MutableStateFlow(true)
    val isToolbarVisible = _isToolbarVisible.asStateFlow()
    private val _currentUrl = MutableStateFlow("")
    val currentUrl = _currentUrl.asStateFlow()
    private val _remainingTime = MutableStateFlow<Int?>(null)
    val remainingTime = _remainingTime.asStateFlow()
    private val _buttonEnabled = MutableStateFlow(false)
    val buttonEnabled = _buttonEnabled.asStateFlow()
    private val _targetWeight = MutableStateFlow<Double?>(null)
    val targetWeight = _targetWeight.asStateFlow()
    private val _targetDuration = MutableStateFlow<Int?>(null)
    val targetDuration = _targetDuration.asStateFlow()
    private val _saveButtonAppeared = MutableStateFlow(false)
    val saveButtonAppeared = _saveButtonAppeared.asStateFlow()
    private val _status = MutableStateFlow("Loading Powercurve…")
    val status = _status.asStateFlow()

    fun setWebView(view: WebView) { webView = view }
    fun updateHistoryState(back: Boolean, forward: Boolean) { _canGoBack.value = back; _canGoForward.value = forward }
    fun setToolbarVisible(visible: Boolean) { _isToolbarVisible.value = visible }
    fun goBack() { webView?.goBack() }
    fun goForward() { webView?.goForward() }
    fun reloadPage() { webView?.reload() }
    fun updateUrl(url: String) { _currentUrl.value = url }
    fun resetSaveFlag() { _saveButtonAppeared.value = false }
    fun resetManualSessionEndTrigger() { _manualSessionEndTrigger.value = false }
    fun invalidate(message: String = "Connecting to the timer…") {
        heartbeat = 0; _targetWeight.value = null; _buttonEnabled.value = false; _remainingTime.value = null; _status.value = message
    }
    fun clearWebsiteData() {
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
            webView?.clearCache(true)
            webView?.reload()
        }
    }
    fun onSnapshot(raw: String) {
        if (raw.length > 8192) return
        val data = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val next = data.optString("phase")
        if (next !in setOf("setup", "countdown", "rep", "rest", "complete", "unavailable")) return
        heartbeat = SystemClock.elapsedRealtime()
        repKey = data.optString("repKey").take(200)
        _buttonEnabled.value = next == "rep" && data.optBoolean("active")
        _remainingTime.value = when (next) {
            "countdown", "rest" -> data.optInt("seconds", 0)
            "rep" -> -data.optInt("seconds", 0)
            else -> null
        }
        _targetWeight.value = parseWeight(data.optString("weight"))
        _targetDuration.value = data.optInt("targetDuration", -1).takeIf { it > 0 }
        _sessionGripper.value = data.optString("gripper").takeUnless { it == "null" || it.isBlank() }
        _sessionSide.value = data.optString("side").takeUnless { it == "null" || it.isBlank() }
        _currentUrl.value = data.optString("url")
        if (next == "complete" && phase != "complete") _saveButtonAppeared.value = true
        phase = next
        _status.value = when {
            next == "unavailable" -> "Powercurve: open the timer / sign in"
            next == "rep" && !_buttonEnabled.value -> "Timer not recognized — end the rep manually"
            next == "rep" -> "Force curve · Rep in progress"
            next == "rest" -> "Rest · Auto-end waiting"
            next == "complete" -> "Set complete · Save in Powercurve"
            else -> "Powercurve connected · $next"
        }
    }
    fun clickFailButton() {
        val key = repKey
        if (!isFreshActive) return
        webView?.evaluateJavascript(JavaScriptBridge.endRep(key)) { result ->
            if (result != "true") _status.value = "Auto-end not confirmed — tap End rep"
        }
    }
    // Powercurve intentionally requires a hold gesture to end an entire set.
    fun clickEndSessionButton() { _status.value = "To end the set, press and hold the button in Powercurve" }

    private fun parseWeight(text: String): Double? {
        val match = Regex("([0-9]+(?:[.,][0-9]+)?)\\s*(kg|lbs?|pounds?)", RegexOption.IGNORE_CASE).matchEntire(text.trim()) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return (if (match.groupValues[2].lowercase() == "kg") value else value * 0.45359237).takeIf { it.isFinite() && it > 0 }
    }
}
