package app.grip_gains_companion.service.offline

import android.app.Application
import app.grip_gains_companion.service.web.WebViewBridge
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
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
class OfflineTrainingTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @Before fun clearStore() { File(context.filesDir,"offline-training.json").delete(); File(context.filesDir,"offline-training.json.bak").delete() }
    private fun record(id:String) = JSONObject().put("id",id).put("owner","7").put("reps",JSONArray().put(12))
    private fun training(): Pair<OfflineTraining,WebViewBridge> {
        // Keep the UI polling loop stopped; tests advance the real timer's monotonic clock explicitly.
        val scope=CoroutineScope(Job().apply {cancel()} + Dispatchers.Main)
        val bridge=WebViewBridge()
        val training=OfflineTraining(context,bridge,scope)
        bridge.offline=training
        return training to bridge
    }
    @Test fun queueSurvivesRestartAndAcknowledgesOnlyTheMatchingSetOnce() {
        val store=OfflineStore(context)
        store.enqueue(record("a"));store.enqueue(record("b"));store.enqueue(record("a"))
        val restored=OfflineStore(context)
        assertEquals(2,restored.snapshot().getJSONArray("queue").length())
        restored.acknowledge("unknown")
        assertEquals(0,restored.snapshot().optInt("synced"))
        restored.acknowledge("a");restored.acknowledge("a")
        val final=OfflineStore(context).snapshot()
        assertEquals(1,final.getInt("synced"))
        assertEquals("b",final.getJSONArray("queue").getJSONObject(0).getString("id"))
    }
    @Test fun setIsQueuedAutomaticallyWithMeasuredDurationsAndOriginalAccount() {
        val (t,b)=training()
        t.accountSeen("7","Test")
        t.start("crusher","left",2.0,2,0,0)
        assertTrue(b.isFreshActive)
        assertEquals(2.0,b.targetWeight.value!!,0.0)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(12));t.endRep()
        t.accountSeen("8","Another")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(4));t.endRep()
        assertFalse(t.inProgress);assertEquals(1,t.pending)
        val saved=t.queue().single()
        assertEquals("7",saved.getString("owner"))
        assertEquals("[12,4]",saved.getJSONArray("reps").toString())
        assertNotNull(saved.getString("date_time"))
    }
    @Test fun restartRecoversCompletedRepsWithoutInventingTimeForInterruptedRep() {
        val (t,_)=training()
        t.start("micro","right",10.0,3,0,0)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6));t.endRep()
        ShadowSystemClock.advanceBy(Duration.ofHours(1))
        val (restored,_)=training()
        assertEquals("paused",restored.phase)
        assertEquals("[6]",restored.active!!.getJSONArray("reps").toString())
        restored.finish()
        assertEquals(1,restored.pending)
        assertEquals("[6]",restored.queue().single().getJSONArray("reps").toString())
    }
    @Test fun websiteCannotOverwriteAnActiveOfflineRep() {
        val (t,b)=training();t.start("prime","left",4.0,2,10,0)
        b.onSnapshot("""{"phase":"setup","weight":"100 kg"}""")
        assertEquals(4.0,b.targetWeight.value!!,0.0)
        assertTrue(b.buttonEnabled.value)
        ShadowSystemClock.advanceBy(Duration.ofMillis(500));b.clickFailButton()
        assertEquals("rest",t.phase)
        b.clickFailButton()
        assertEquals(1,t.active!!.getJSONArray("reps").length())
    }
    @Test fun invalidSetupAndEmptySetCannotEnterQueue() {
        val (t,_)=training()
        for(weight in listOf(0.0,-2.0,Double.NaN,Double.POSITIVE_INFINITY))t.start("crusher","left",weight,2,0,0)
        assertFalse(t.inProgress)
        t.start("crusher","left",20.0,2,10,5);t.finish()
        assertEquals(0,t.pending)
        t.cancelEmpty();assertFalse(t.inProgress)
    }
    @Test fun unknownAccountRequiresExplicitAssignment() {
        val (t,_)=training();t.start("crusher","left",20.0,1,0,0)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3));t.endRep()
        assertEquals("",t.queue().single().getString("owner"))
        t.accountSeen("42","Test");assertTrue(t.needsAccount)
        t.assignUnowned();assertEquals("42",t.queue().single().getString("owner"))
    }
    @Test fun corruptionIsReportedAndNotOverwritten() {
        val file=File(context.filesDir,"offline-training.json");file.writeText("broken")
        val (t,_)=training()
        assertFalse(t.available);assertTrue(t.error.isNotEmpty())
        t.start("crusher","left",20.0,1,0,0)
        assertEquals("broken",file.readText())
    }
    @Test fun mutationFailureLeavesPreviousDurableStateUntouched() {
        val store=OfflineStore(context);store.enqueue(record("a"))
        runCatching {store.update {it.put("queue",JSONArray());error("simulated failure")}}
        assertEquals(1,OfflineStore(context).snapshot().getJSONArray("queue").length())
    }

    @Test fun receptionLossKeepsExistingWebSetUntilItsCompletedRepsAreQueuedOnce() {
        val (t,b)=training();t.accountSeen("7","Test")
        fun snapshot(phase:String,reps:String) = """{"phase":"$phase","active":true,"weight":"20 kg","gripper":"Crusher","side":"Left","plannedReps":2,"completedReps":$reps}"""
        b.onSnapshot(snapshot("rep","[]"))
        t.connectionChanged(false)
        assertFalse(t.showTimer)
        b.onSnapshot(snapshot("rest","[5]"))
        assertFalse(t.showTimer)
        b.onSnapshot(snapshot("complete","[5,3]"))
        b.onSnapshot(snapshot("complete","[5,3]"))
        assertTrue(t.showTimer);assertEquals(1,t.pending)
        assertEquals("[5,3]",t.queue().single().getJSONArray("reps").toString())
    }
    @Test fun ordinaryOnlineSetNeverEntersOfflineQueue() {
        val (t,b)=training()
        b.onSnapshot("""{"phase":"complete","weight":"20 kg","gripper":"Crusher","side":"Left","completedReps":[5,3]}""")
        assertEquals(0,t.pending);assertEquals(0,t.synced);assertFalse(t.showTimer)
    }
}
