package app.grip_gains_companion.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.grip_gains_companion.config.AppConstants
import app.grip_gains_companion.service.web.BridgeInstallationTag
import app.grip_gains_companion.service.web.JavaScriptBridge
import app.grip_gains_companion.service.web.WebViewBridge

@Composable
fun TimerWebView(bridge: WebViewBridge, cachedWebView: WebView, modifier: Modifier = Modifier) {
    AndroidView(modifier = modifier, factory = {
        cachedWebView.apply {
            (parent as? ViewGroup)?.removeView(this)
            bridge.setWebView(this)
            val supportsBridge = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
            if (supportsBridge && !BridgeInstallationTag.isInstalled(this)) {
                WebViewCompat.addWebMessageListener(this, "PowercurveNative", setOf(AppConstants.POWERCURVE_ORIGIN)) { _, message, origin, mainFrame, _ ->
                    if (mainFrame && origin.toString().trimEnd('/') == AppConstants.POWERCURVE_ORIGIN) {
                        message.data?.let { bridge.onSnapshot(it) }
                    }
                }
                BridgeInstallationTag.markInstalled(this)
            }
            fun trusted(url: String?): Boolean {
                val uri = url?.let(Uri::parse) ?: return false
                return uri.scheme == "https" && uri.host == "powercurve.tantaluspath.com" && (uri.port == -1 || uri.port == 443)
            }
            fun install(view: WebView?, url: String?) {
                if (trusted(url) && supportsBridge) view?.evaluateJavascript("window.PowercurveStartupToken=" + org.json.JSONObject.quote(bridge.startupToken) + ";" + JavaScriptBridge.install(context), null)
                else if (!supportsBridge) bridge.invalidate("Update Android System WebView to enable auto-end")
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val target = request?.url ?: return true
                    if (trusted(target.toString())) return false
                    if (request.isForMainFrame && target.scheme in setOf("https", "http", "mailto")) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target)) }
                    }
                    return true
                }
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    bridge.offline.pageFailed = false
                    if (bridge.offlineEndRep == null) bridge.invalidate()
                    url?.let(bridge::updateUrl)
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    bridge.updateHistoryState(canGoBack(), canGoForward())
                    install(view, url)
                }
                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    bridge.updateHistoryState(canGoBack(), canGoForward())
                    url?.let(bridge::updateUrl)
                    install(view, url)
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        bridge.offline.pageFailed = true
                        if (bridge.offlineEndRep == null) bridge.invalidate("Offline timer available")
                    }
                }
                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                    if (request?.isForMainFrame == true && (response?.statusCode ?: 0) >= 400) bridge.offline.pageFailed = true
                }
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                    handler?.cancel()
                    bridge.offline.pageFailed = true
                }
                // Invalid certificates are never bypassed.
            }
            if (url == null) loadUrl(AppConstants.POWERCURVE_ORIGIN + "/timer")
        }
    })
}
