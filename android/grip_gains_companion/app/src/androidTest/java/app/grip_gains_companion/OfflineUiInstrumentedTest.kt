package app.grip_gains_companion

import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import org.json.JSONObject
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
    private val panelHeight=mutableStateOf(700.dp)
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
        ui.setContent {GripGainsTheme(darkTheme=true) {Column {OfflineStatus(training);OfflineTimer(training,false,Modifier.height(panelHeight.value).fillMaxWidth().testTag("offline-timer"))}}}
    }
    @After fun cleanup() {directory.listFiles()?.forEach {it.delete()};directory.delete()}
    @Test fun twoSetsCanBeCompletedAndRemainQueuedAcrossReopening() {
        ui.onNodeWithText("Weight (kg)").performScrollTo().performTextReplacement("20")
        ui.onNodeWithText("Reps (1–100)").performScrollTo().performTextReplacement("1")
        ui.onNodeWithText("Countdown (0–60 s)").performScrollTo().performTextReplacement("0")
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
    @Test fun cachedCurveSelectsMatchingZoneAndKeepsBackgroundWhenResized() {
        ui.runOnUiThread {
            training.accountSeen("curve-test","Test")
            training.cacheCurves("curve-test",JSONObject("""{"savedAt":1790500000000,"sides":[{"gripper":"crusher","side":"left","params":{"a":400,"b":0.025,"x0":0,"c":0,"d":0},"points":[{"hold":40},{"hold":60},{"hold":100},{"hold":150},{"hold":230}],"zone_characteristic_times":{"power":40,"power_strength":60,"strength":100,"strength_endurance":150,"endurance":230}}]}"""))
        }
        ui.onNodeWithText("Hold time (s) vs weight (kg)").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Endurance",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("offline-curve-plot").performScrollTo().performTouchInput {click(center)}
        ui.onNodeWithTag("offline-curve-readout").assertTextContains("Curve point:",substring=true)
        ui.onNodeWithTag("offline-curve-readout").assertTextContains("kg",substring=true)
        ui.onNodeWithText("Weight (kg)").performScrollTo().assertTextContains("25.98")
        ui.onNodeWithText("Target hold (s, optional)").performScrollTo().assertTextContains("96")
        ui.onNodeWithText("Strength",useUnmergedTree=true).performScrollTo().performClick()
        ui.onNodeWithText("Weight (kg)").performScrollTo().assertTextContains("25.00")
        ui.onNodeWithText("Reps (1–100)").assertTextContains("5")
        ui.onNodeWithText("Strength · 101 s estimated hold",substring=true).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Weight (kg)").performScrollTo().performTextReplacement("10")
        ui.onNodeWithText("Target hold (s, optional)").performScrollTo().assertTextContains("231")
        ui.onNodeWithText("Reps (1–100)").performScrollTo().assertTextContains("4")
        ui.onNodeWithText("Match hold time (1–400 s)").performScrollTo().performTextReplacement("1")
        ui.onNodeWithText("Match",useUnmergedTree=true).performScrollTo().performClick()
        ui.onNodeWithText("Target hold (s, optional)").performScrollTo().assertTextContains("1")
        ui.onNodeWithText("Reps (1–100)").performScrollTo().assertTextContains("6")
        ui.onNodeWithText("Match hold time (1–400 s)").performScrollTo().performTextReplacement("350")
        ui.onNodeWithText("Match",useUnmergedTree=true).performScrollTo().performClick()
        ui.onNodeWithText("Target hold (s, optional)").performScrollTo().assertTextContains("350")
        ui.onNodeWithText("300 s").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("350 s").assertDoesNotExist()
        ui.onNodeWithText("Local set timer").performScrollTo()
        ui.waitForIdle()
        ui.onNodeWithTag("offline-timer").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,File(context.cacheDir,"offline-curve-test.png").outputStream())
        val before=ui.onNodeWithTag("offline-timer").captureToImage().toPixelMap()
        ui.runOnUiThread {panelHeight.value=420.dp}
        val after=ui.onNodeWithTag("offline-timer").captureToImage().toPixelMap()
        // Sample the app background below the status-bar overlap of the test activity.
        assertEquals(Color(0xFF1A2231),before[before.width-1,100])
        assertEquals(before[before.width-1,100],after[after.width-1,100])
    }
    @Test fun targetCountdownAndDiscardControlsAreAvailableDuringARep() {
        ui.runOnUiThread {training.start("crusher","left",20.0,3,0,0,5)}
        ui.onNodeWithText("Target countdown: 5 s").assertIsDisplayed()
        ui.onNodeWithText("End rep").performClick()
        ui.onNodeWithText("Target countdown:",substring=true).assertDoesNotExist()
        ui.onNodeWithText("Discard set without saving").performClick()
        ui.onNodeWithText("Start set").performScrollTo().assertExists()
        assertEquals(0,training.pending)
        assertFalse(OfflineStore(context).snapshot().has("active"))
    }
    @Test fun historyShowsSixtySetsAndLastZoneDetailsWithoutFittedCurve() {
        ui.runOnUiThread {
            val rows=org.json.JSONArray()
            repeat(65) {i->rows.put(JSONObject().put("date_time",java.time.Instant.parse("2026-09-27T12:00:00Z").minusSeconds(i*86400L).toString()).put("gripper","crusher").put("side","left").put("weight",40+i*0.2).put("rep_durations",org.json.JSONArray().put(if(i==0) 220 else 90)))}
            training.accountSeen("history-test","Test")
            training.cacheCurves("history-test",JSONObject().put("sides",org.json.JSONArray()).put("sessions",rows).put("savedAt",1790500000000L))
        }
        ui.onNodeWithTag("offline-history-count").performScrollTo().assertTextContains("60 recent sets",substring=true)
        ui.onNodeWithText("Endurance",useUnmergedTree=true).performScrollTo().performClick()
        ui.onNodeWithTag("offline-last-zone-set").performScrollTo().assertTextContains("Last Endurance set",substring=true)
        ui.onNodeWithTag("offline-last-zone-set").assertTextContains("220.0 s",substring=true)
        ui.onNodeWithTag("offline-last-zone-set").assertTextContains("days ago",substring=true)
        ui.onNodeWithTag("offline-timer").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,File(context.cacheDir,"offline-history-test.png").outputStream())
    }
}
