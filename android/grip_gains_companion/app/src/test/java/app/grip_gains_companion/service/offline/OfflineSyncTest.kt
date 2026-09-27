package app.grip_gains_companion.service.offline

import android.app.Application
import android.content.Context
import android.webkit.ValueCallback
import android.webkit.WebView
import app.grip_gains_companion.service.web.WebViewBridge
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class OfflineSyncTest {
    private class Page(context: Context): WebView(context) {
        var response: String = "null"
        var reloads=0
        override fun reload() { reloads++ }
        override fun evaluateJavascript(script: String, callback: ValueCallback<String>?) { callback?.onReceiveValue(response) }
    }
    @Test fun failedPageRecoversWithoutQueuedSetsAndRefreshesHistory() {
        val context=RuntimeEnvironment.getApplication()
        File(context.filesDir,"offline-training.json").delete()
        val bridge=WebViewBridge()
        val training=OfflineTraining(context,bridge,CoroutineScope(Job().apply {cancel()}+Dispatchers.Main))
        val page=Page(context)
        val sync=OfflineSync(context,training){page}
        assertEquals(0,training.pending)
        sync.tick(true)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(31));sync.tick(false)
        assertEquals(0,page.reloads)
        sync.tick(true)
        assertEquals(1,page.reloads)
        sync.tick(true);assertEquals(1,page.reloads)
        page.response=JSONObject.quote("{}")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(31));sync.tick(true)
        assertEquals(2,page.reloads)
        page.response=JSONObject.quote("""{"state":"idle","account":null}""")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(31));sync.tick(true)
        assertEquals(3,page.reloads)
        assertTrue(training.syncMessage.isEmpty())
        page.response=JSONObject.quote("""{"state":"idle","account":"7","curves":{"owner":"7","data":{"sides":[],"sessions":[{"date_time":"2026-09-27T12:00:00Z","gripper":"prime","side":"left","weight":40,"rep_durations":[70]}],"savedAt":123}}}""")
        sync.tick(true)
        assertEquals(70.0,training.history("prime","left").single().hold,0.0)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(31));sync.tick(true)
        assertEquals(3,page.reloads)
        sync.close()
    }
}
