package app.grip_gains_companion

import android.content.ContextWrapper
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.grip_gains_companion.data.PreferencesRepository
import app.grip_gains_companion.service.ProgressorHandler
import app.grip_gains_companion.service.ble.BluetoothManager
import app.grip_gains_companion.service.offline.OfflineTraining
import app.grip_gains_companion.service.web.WebViewBridge
import app.grip_gains_companion.ui.screens.MainScreen
import app.grip_gains_companion.ui.theme.GripGainsTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

class ForceSheetInstrumentedTest {
    @get:Rule val ui=createComposeRule()
    @Test fun manualCollapseSurvivesRepChangesButNewSampleConnectionOpensSheet() {
        val original=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(original.cacheDir,"sheet-test-"+UUID.randomUUID()).apply {mkdirs()}
        val context=object:ContextWrapper(original){override fun getFilesDir()=directory}
        lateinit var manager:BluetoothManager
        lateinit var bridge:WebViewBridge
        lateinit var web:WebView
        ui.runOnUiThread {
            manager=BluetoothManager(context)
            bridge=WebViewBridge()
            bridge.offline=OfflineTraining(context,bridge,CoroutineScope(Job().apply {cancel()}+Dispatchers.Main))
            bridge.offline.openTimer()
            web=WebView(context).apply {loadData("<html></html>","text/html","UTF-8")}
        }
        ui.setContent {GripGainsTheme {
            MainScreen(PreferencesRepository(context),manager,ProgressorHandler(),bridge,web,
                true,10,false,true,null,0.1,false,false,
                onSettingsTap={},onHistoryTap={},onSetManualWeightTap={})
        }}
        fun height()=ui.onNodeWithTag("force-sheet").getUnclippedBoundsInRoot().let {it.bottom.value-it.top.value}
        ui.waitForIdle();assertTrue(height()>200f)
        ui.onNodeWithTag("force-sheet-handle").performTouchInput {swipeDown(startY=centerY,endY=centerY+250f,durationMillis=300)}
        ui.waitForIdle();assertTrue(height()<160f)
        ui.runOnUiThread {bridge.offline.start("micro","left",40.0,2,60,0)}
        ui.waitForIdle();assertTrue(height()<160f)
        ui.runOnUiThread {bridge.offline.endRep();bridge.setToolbarVisible(false);bridge.setToolbarVisible(true)}
        ui.waitForIdle();assertTrue(height()<160f)
        @Suppress("UNCHECKED_CAST")
        val sampleConnection=BluetoothManager::class.java.getDeclaredField("_sampleConnection").apply {isAccessible=true}.get(manager) as MutableStateFlow<Long>
        ui.runOnUiThread {sampleConnection.value=1L}
        ui.waitForIdle();assertTrue(height()>200f)
        ui.onNodeWithTag("force-sheet-handle").performClick()
        ui.waitForIdle();assertTrue(height()<160f)
        @Suppress("UNCHECKED_CAST")
        val samples=BluetoothManager::class.java.getDeclaredField("_receivingSamples").apply {isAccessible=true}.get(manager) as MutableStateFlow<Boolean>
        ui.runOnUiThread {samples.value=true}
        ui.waitForIdle();assertTrue(height()<160f)
        ui.runOnUiThread {manager.disconnect();bridge.offline.close();web.destroy()}
        directory.deleteRecursively()
    }
}
