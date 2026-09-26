package app.grip_gains_companion

import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.grip_gains_companion.service.offline.OfflineStore
import app.grip_gains_companion.service.offline.OfflineTraining
import app.grip_gains_companion.service.web.WebViewBridge
import app.grip_gains_companion.ui.components.OfflineStatus
import app.grip_gains_companion.ui.components.OfflineTimer
import app.grip_gains_companion.ui.theme.GripGainsTheme
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OfflineUiInstrumentedTest {
    @get:Rule val ui=createComposeRule()
    private lateinit var directory:File
    private lateinit var training:OfflineTraining
    private lateinit var context:ContextWrapper
    @Before fun setup() {
        val original=InstrumentationRegistry.getInstrumentation().targetContext
        directory=File(original.cacheDir,"offline-ui-test-${UUID.randomUUID()}").apply {mkdirs()}
        context=object:ContextWrapper(original){override fun getFilesDir():File=directory}
        ui.runOnUiThread {
            val bridge=WebViewBridge()
            training=OfflineTraining(context,bridge,CoroutineScope(Job().apply {cancel()}+Dispatchers.Main))
            bridge.offline=training
        }
        ui.setContent {GripGainsTheme {Column {OfflineStatus(training);OfflineTimer(training,false,Modifier.weight(1f))}}}
    }
    @After fun cleanup() {directory.listFiles()?.forEach {it.delete()};directory.delete()}
    @Test fun twoSetsCanBeCompletedAndRemainQueuedAcrossReopening() {
        ui.onNodeWithText("Weight (kg)").performTextReplacement("20")
        ui.onNodeWithText("Reps (1–100)").performTextReplacement("1")
        ui.onNodeWithText("Countdown (0–60 s)").performTextReplacement("0")
        repeat(2) {
            ui.onNodeWithText("Start set").performScrollTo().performClick()
            ui.onNodeWithText("End rep").performScrollTo().performClick()
            ui.onNodeWithText("${it+1} set(s) waiting to sync").assertIsDisplayed()
        }
        assertEquals(2,OfflineStore(context).snapshot().getJSONArray("queue").length())
        ui.runOnUiThread {training.queue().forEach {training.acknowledge(it.getString("id"))}}
        ui.onNodeWithText("2 set(s) synced",substring=true).assertIsDisplayed()
        ui.onNodeWithText("Dismiss").performClick()
        ui.onNodeWithText("set(s) synced",substring=true).assertDoesNotExist()
    }
}
