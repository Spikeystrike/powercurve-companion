package app.grip_gains_companion.service.offline

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import app.grip_gains_companion.config.AppConstants
import org.json.JSONObject
import org.json.JSONTokener

/** Separate first-party WebView keeps synchronization from navigating away from the user's timer. */
@SuppressLint("SetJavaScriptEnabled")
class OfflineSync(private val context: Context, private val training: OfflineTraining) {
    private var view: WebView? = null
    private var polling = false
    private var inFlight: String? = null
    private var started = 0L
    private var retryAt = 0L
    private var failures = 0
    private var lastReload = 0L
    private val script by lazy { context.assets.open("offline-import.js").bufferedReader().use { it.readText() } }
    fun tick(online: Boolean) {
        if (!online) return
        val now = SystemClock.elapsedRealtime()
        if (view == null) {
            view = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean =
                        request?.url?.let { it.scheme != "https" || it.host != "powercurve.tantaluspath.com" || it.port !in listOf(-1,443) } ?: true
                    override fun onPageFinished(v: WebView?, url: String?) {
                        if (url?.startsWith(AppConstants.POWERCURVE_ORIGIN + "/") == true) v?.evaluateJavascript(script, null)
                    }
                }
                loadUrl(AppConstants.POWERCURVE_ORIGIN + "/sessions")
            }
            lastReload=now
        }
        if (inFlight!=null && now-started>90000) {inFlight=null;retryAt=now+30000;view?.reload();training.status("Sync interrupted. Saved sets will be checked before retrying.")}
        if (polling) return
        polling=true
        view?.evaluateJavascript("JSON.stringify(window.PowercurveOfflineImport?.poll() || {})") { raw ->
            polling=false
            val state=runCatching {JSONObject(JSONTokener(raw).nextValue() as String)}.getOrNull() ?: return@evaluateJavascript
            val account=state.optString("account").takeUnless {it.isEmpty() || it=="null"}
            training.accountSeen(account,state.optString("name"))
            state.optJSONObject("curves")?.let { cache -> cache.optJSONObject("data")?.let { training.cacheCurves(cache.optString("owner"),it) } }
            val id=inFlight
            if (id!=null && state.optString("id")==id) when(state.optString("state")) {
                "success" -> {training.acknowledge(id);inFlight=null;failures=0;retryAt=now+1000}
                "error" -> {training.status(state.optString("message"));inFlight=null;failures++;retryAt=now+(5000L*failures).coerceAtMost(60000)}
            }
            if (training.pending==0 || inFlight!=null || now<retryAt) return@evaluateJavascript
            if(account==null) {
                training.status("Sign in to Powercurve to sync saved sets.")
                if(now-lastReload>30000){view?.reload();lastReload=now}
                return@evaluateJavascript
            }
            val next=training.queue().firstOrNull {it.optString("owner")==account}
            if(next==null){training.status(if(training.needsAccount) "Choose the account for your saved sets." else "Sign in to the account used for these offline sets.");return@evaluateJavascript}
            if(!state.has("state")) { if(now-lastReload>30000){view?.reload();lastReload=now};return@evaluateJavascript }
            inFlight=next.getString("id");started=now
            training.status("Syncing ${training.pending} saved set(s)…")
            view?.evaluateJavascript("window.PowercurveOfflineImport.submit(${next});",null)
        }
    }
    fun close() {view?.destroy();view=null}
}
