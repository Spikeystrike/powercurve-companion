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
    private val useLbs=mutableStateOf(false)
    private val panelHeight=mutableStateOf(700.dp)
    private lateinit var directory:File
    private lateinit var bridge:WebViewBridge
    private lateinit var training:OfflineTraining
    private lateinit var context:ContextWrapper
    @Before fun setup() {
        val original=InstrumentationRegistry.getInstrumentation().targetContext
        directory=File(original.cacheDir,"offline-ui-test-${UUID.randomUUID()}").apply {mkdirs()}
        context=object:ContextWrapper(original){override fun getFilesDir():File=directory}
        ui.runOnUiThread {
            bridge=WebViewBridge()
            training=OfflineTraining(context,bridge,CoroutineScope(Job().apply {cancel()}+Dispatchers.Main))
            bridge.offline=training
            training.openTimer()
        }
        ui.setContent {GripGainsTheme(darkTheme=true) {Column {OfflineStatus(training,useLbs.value);OfflineTimer(training,useLbs.value,Modifier.height(panelHeight.value).fillMaxWidth().testTag("offline-timer"))}}}
    }
    @After fun cleanup() {directory.listFiles()?.forEach {it.delete()};directory.delete()}
    @Test fun startsWithMicroLeft() {
        ui.onNodeWithText("Micro").assertIsSelected()
        ui.onNodeWithText("Left").assertIsSelected()
    }
    @Test fun twoSetsCanBeCompletedAndRemainQueuedAcrossReopening() {
        ui.onNodeWithText("Weight (kg)").performScrollTo().performTextReplacement("20")
        ui.onNodeWithText("Reps (1–100)").performScrollTo().performTextReplacement("1")
        ui.onNodeWithText("Countdown (0–60 s)").performScrollTo().performTextReplacement("0")
        repeat(2) {
            ui.onNodeWithText("Start set").performScrollTo().performClick()
            ui.onNodeWithText("End rep").performScrollTo().performClick()
            ui.onNodeWithText("Save set").performScrollTo().performClick()
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
            training.cacheCurves("curve-test",JSONObject("""{"savedAt":1790500000000,"sides":[{"gripper":"micro","side":"left","params":{"a":400,"b":0.025,"x0":0,"c":0,"d":0},"points":[{"hold":40},{"hold":60},{"hold":100},{"hold":150},{"hold":230}],"zone_characteristic_times":{"power":40,"power_strength":60,"strength":100,"strength_endurance":150,"endurance":230}}]}"""))
        }
        ui.onNodeWithText("Endurance").assertIsSelected()
        assertNotNull(bridge.targetWeight.value)
        ui.onNodeWithText("Hold time (s) vs weight (kg)").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Endurance",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("offline-curve-plot").performScrollTo().performTouchInput {click(center)}
        ui.onNodeWithTag("offline-curve-readout").assertTextContains("Curve point:",substring=true)
        ui.onNodeWithTag("offline-curve-readout").assertTextContains("kg",substring=true)
        ui.onNodeWithText("Weight (kg)").performScrollTo().assertTextContains("59.42")
        assertEquals(59.42,bridge.targetWeight.value!!,0.00001)
        ui.onNodeWithText("Target hold (s, optional)").performScrollTo().assertTextContains("15")
        ui.onNodeWithText("Strength",useUnmergedTree=true).performScrollTo().performClick()
        ui.onNodeWithText("Weight (kg)").performScrollTo().assertTextContains("25.00")
        assertEquals(25.0,bridge.targetWeight.value!!,0.00001)
        ui.onNodeWithText("Reps (1–100)").assertTextContains("5")
        ui.onNodeWithText("Strength · 101 s estimated hold",substring=true).performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("offline-curve-readout").performScrollTo().assertTextContains("25.00 kg",substring=true)
        ui.onNodeWithTag("selected-weight-axis").assertTextContains("25.00 kg",substring=true)
        ui.onNodeWithTag("selected-time-axis").assertTextContains("100.8 s",substring=true)
        ui.onNodeWithTag("offline-curve-plot").performScrollTo()
        ui.onNodeWithText("0 s").assertExists()
        val plot=ui.onNodeWithTag("offline-curve-plot").captureToImage().toPixelMap()
        val min=40*kotlin.math.ln(400.0/300)*0.45359237*0.95
        val high=40*kotlin.math.ln(400.0)
        val max=(high+(high-40*kotlin.math.ln(400.0/300))*0.05)*0.45359237
        val px=((25-min)/(max-min)*plot.width).toInt()
        val py=((300-400*kotlin.math.exp(-0.025*25/0.45359237))/300*plot.height).toInt()
        fun guideAt(x:Int,y:Int): Boolean = (-1..1).any {dx->(-1..1).any {dy->
            val c=plot[(x+dx).coerceIn(0,plot.width-1),(y+dy).coerceIn(0,plot.height-1)]
            c.red>c.blue+0.2f && c.green>c.blue+0.1f
        }}
        assertTrue(guideAt(px,(py+plot.height)/2));assertTrue(guideAt(px/2,py))
        assertFalse(guideAt(px,py/2));assertFalse(guideAt((px+plot.width)/2,py))
        ui.onNodeWithText("Weight (kg)").performScrollTo().performTextReplacement("10")
        ui.onNodeWithTag("offline-curve-readout").performScrollTo().assertTextContains("10.00 kg",substring=true)
        assertEquals(10.0,bridge.targetWeight.value!!,0.00001)
        ui.onNodeWithTag("selected-weight-axis").assertTextContains("10.00 kg",substring=true)
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
        ui.runOnUiThread {training.start("crusher","left",20.0/0.45359237,3,0,0,5)}
        ui.onNodeWithText("Target countdown: 5 s").assertIsDisplayed()
        ui.onNodeWithText("End rep").performClick()
        ui.onNodeWithText("Target countdown:",substring=true).assertDoesNotExist()
        hold("Discard set without saving")
        ui.onNodeWithText("Start set").performScrollTo().assertExists()
        assertEquals(0,training.pending)
        assertFalse(OfflineStore(context).snapshot().has("active"))
    }
    @Test fun historyShowsSixtySetsAndLastZoneDetailsWithoutFittedCurve() {
        ui.runOnUiThread {
            val rows=org.json.JSONArray()
            repeat(65) {i->rows.put(JSONObject().put("date_time",java.time.Instant.parse("2026-09-27T12:00:00Z").minusSeconds(i*86400L).toString()).put("gripper","micro").put("side","left").put("weight",40+i*0.2).put("rep_durations",org.json.JSONArray().put(if(i==0) 220 else 90)))}
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

    @Test fun switchingGripperOrHandClearsPreviousTargetsWithoutARecommendation() {
        fun enter() {
            ui.onNodeWithText("Weight (kg)").performScrollTo().performTextReplacement("20")
            ui.onNodeWithText("Reps (1–100)").performScrollTo().performTextReplacement("3")
            ui.onNodeWithText("Target hold (s, optional)").performScrollTo().performTextReplacement("90")
        }
        fun cleared() {
            for(label in listOf("Weight (kg)","Reps (1–100)","Target hold (s, optional)"))
                ui.onNodeWithText(label).performScrollTo().assertTextEquals(label,"")
            ui.onNodeWithText("Start set").performScrollTo().assertIsNotEnabled()
        }
        enter()
        ui.onNodeWithText("Micro").performScrollTo().performClick()
        ui.onNodeWithText("Weight (kg)").performScrollTo().assertTextContains("20")
        for(gripper in listOf("Prime","Micro","Crusher")) {
            ui.onNodeWithText(gripper).performScrollTo().performClick();cleared();enter()
        }
        for(hand in listOf("Right","Left")) {
            ui.onNodeWithText(hand).performScrollTo().performClick();cleared();enter()
        }
    }

    @Test fun pendingSetCanBeEditedAndDeletedFromTheDialog() {
        ui.runOnUiThread {training.start("prime","left",20.0/0.45359237,1,0,0);training.endRep();training.finish()}
        ui.onNodeWithText("Review pending sets").performClick()
        ui.onNodeWithText("Edit or delete").performClick()
        ui.onNodeWithText("Set weight (kg)").performTextReplacement("22")
        ui.onNodeWithText("Rep durations (seconds, comma-separated)").performTextReplacement("30, 40")
        ui.onNodeWithText("Save changes").performClick()
        assertEquals(22.0/0.45359237,training.queue().single().getDouble("weightLbs"),0.0)
        ui.onNodeWithText("Edit or delete").performClick()
        ui.onNodeWithText("Delete set").performClick()
        ui.onNodeWithText("Delete",substring=false).performClick()
        assertEquals(0,training.pending)
        ui.onNodeWithText("Done").performClick()
        assertFalse(training.managingQueue)
    }
    @Test fun curveTapWithoutZoneEvidenceDoesNotChangeTimerTargets() {
        ui.runOnUiThread {
            training.accountSeen("tap-test","Test")
            training.cacheCurves("tap-test",JSONObject("""{"sides":[{"gripper":"micro","side":"left","params":{"a":400,"b":0.025,"x0":0,"c":0,"d":0},"points":[]}]}"""))
        }
        ui.onNodeWithText("Weight (kg)").performScrollTo().performTextReplacement("20")
        ui.onNodeWithTag("offline-curve-plot").performScrollTo().performTouchInput {click(center)}
        ui.onNodeWithText("Not enough training data for a recommendation in this zone.").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Weight (kg)").performScrollTo().assertTextContains("20")
    }

    private fun assertAbove(result: String, action: String) {
        ui.onNodeWithText(action).performScrollTo()
        val resultBounds=ui.onNodeWithText(result).getUnclippedBoundsInRoot()
        val actionBounds=ui.onNodeWithText(action).getUnclippedBoundsInRoot()
        assertTrue(resultBounds.bottom<=actionBounds.top)
    }
    private fun hold(label:String, millis:Long=2300) {
        ui.onNodeWithText(label).performScrollTo()
        ui.mainClock.autoAdvance=false
        ui.onNodeWithText(label).performTouchInput {down(center)}
        ui.mainClock.advanceTimeBy(millis)
        ui.onRoot().performTouchInput {up()}
        ui.mainClock.autoAdvance=true
        ui.waitForIdle()
    }
    @Test fun prematureSaveAndDiscardRequireHoldingAndCompletedSetUsesNormalSave() {
        ui.runOnUiThread {training.start("prime","left",20.0/0.45359237,2,60,0);training.endRep()}
        ui.onNodeWithText("Locked").assertIsNotEnabled()
        assertAbove("Rep 1: 1 s","Hold to save set now")
        ui.onNodeWithText("Hold to save set now").performScrollTo().performTouchInput {click()}
        assertEquals(0,training.pending);assertTrue(training.inProgress)
        hold("Hold to save set now",600)
        assertEquals(0,training.pending)
        hold("Hold to save set now")
        assertEquals(1,training.pending)
        ui.runOnUiThread {training.start("prime","left",20.0/0.45359237,1,0,0)}
        hold("Discard set without saving",600);assertTrue(training.inProgress)
        hold("Discard set without saving");assertFalse(training.inProgress);assertEquals(1,training.pending)
        ui.runOnUiThread {training.start("prime","left",20.0/0.45359237,1,0,0);training.endRep()}
        assertEquals(1,training.pending)
        assertAbove("Rep 1: 1 s","Save set")
        ui.onNodeWithText("Save set").performScrollTo().performClick()
        assertEquals(2,training.pending)
    }
    @Test fun pendingEditorUsesPoundsAndConvertsEditedWeight() {
        ui.runOnUiThread {useLbs.value=true;training.start("prime","left",20.0/0.45359237,1,0,0);training.endRep();training.finish()}
        ui.onNodeWithText("Review pending sets").performClick()
        ui.onNodeWithText("44.092 lb",substring=true).assertExists()
        ui.onNodeWithText("Edit or delete").performClick()
        ui.onNodeWithText("Set weight (lb)").assertTextContains("44.092")
        ui.onNodeWithText("Set weight (lb)").performTextReplacement("50.12345")
        ui.onNodeWithText("Set weight (lb)").assertTextContains("50.123")
        ui.onNodeWithText("Save changes").performClick()
        assertEquals(50.123,training.queue().single().getDouble("weightLbs"),0.000001)
        ui.onNodeWithText("Done").performClick()
    }

    @Test fun realForceIsOptInRequiresMeterAndDisplaysMeasuredResult() {
        ui.onNodeWithText("Start real force set").assertDoesNotExist()
        ui.runOnUiThread {training.configureRealForce(true,"median",false,0.5,250)}
        ui.onNodeWithText("Weight (kg)").performScrollTo().performTextReplacement("20")
        ui.onNodeWithText("Reps (1–100)").performScrollTo().performTextReplacement("1")
        ui.onNodeWithText("Start real force set").performScrollTo().assertIsNotEnabled()
        ui.runOnUiThread {training.configureRealForce(true,"median",true,0.5,250)}
        ui.onNodeWithText("Start real force set").performScrollTo().assertIsEnabled().performClick()
        ui.onNodeWithText("Pull when ready").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Locked").assertDoesNotExist()
        ui.onNodeWithText("Save set").assertDoesNotExist()
        ui.onNodeWithText("History has not been refreshed yet.").assertDoesNotExist()
        assertEquals(0,training.active!!.getInt("countdown"))
        ui.runOnUiThread {
            val base=android.os.SystemClock.elapsedRealtime()
            training.forceSample(17.0,base)
            assertEquals("ready",training.phase)
            repeat(20) {training.forceSample(22.0,base+100L+it*100)}
            repeat(5) {training.forceSample(0.0,base+2100L+it*100)}
        }
        ui.onNodeWithText("Rep 1: 22 kg · 2 s · set weight").performScrollTo().assertIsDisplayed()
        assertEquals(22.0,bridge.targetWeight.value!!,0.000001)
        assertAbove("Rep 1: 22 kg · 2 s · set weight","Save set")
        ui.onNodeWithText("Save set").performScrollTo().performClick()
        assertEquals(22.0/0.45359237,training.queue().single().getDouble("weightLbs"),0.000001)
        ui.onNodeWithText("Rep 1: 22 kg · 2 s · set weight").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("History has not been refreshed yet.").assertExists()
    }
}
