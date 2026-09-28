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
    @Test fun legacyKilogramsMigrateOnceAcrossAllLocalRecords() {
        val legacy=JSONObject().put("lastAccount","7").put("synced",3)
        val row=JSONObject().put("id","old").put("owner","7").put("weightKg",20.123456789).put("reps",JSONArray().put(40)).put("uploadStarted",true)
        for(key in listOf("active","webActive","defaults")) legacy.put(key,JSONObject(row.toString()))
        for(key in listOf("queue","localHistory")) legacy.put(key,JSONArray().put(JSONObject(row.toString())))
        legacy.put("curvesByOwner",JSONObject().put("7",JSONObject().put("sessions",JSONArray().put(JSONObject().put("weight",44.0)))))
        File(context.filesDir,"offline-training.json").writeText(legacy.toString())
        val migrated=OfflineStore(context).snapshot()
        assertEquals(2,migrated.getInt("schemaVersion"))
        val rows=listOf("active","webActive","defaults").map {migrated.getJSONObject(it)}+listOf("queue","localHistory").map {migrated.getJSONArray(it).getJSONObject(0)}
        rows.forEach {assertFalse(it.has("weightKg"));assertEquals(20.123456789/0.45359237,it.getDouble("weightLbs"),0.0);assertTrue(it.getBoolean("uploadStarted"));assertEquals("old",it.getString("id"))}
        assertEquals(44.0,migrated.getJSONObject("curvesByOwner").getJSONObject("7").getJSONArray("sessions").getJSONObject(0).getDouble("weight"),0.0)
        assertEquals(migrated.toString(),OfflineStore(context).snapshot().toString())
    }
    @Test fun poundsAreStoredWithoutRoundingAndBridgeStillReceivesCorrectKilograms() {
        val (t,b)=training()
        t.start("micro","left",50.123456789,1,0,0)
        assertEquals(50.123456789,t.state.getJSONObject("active").getDouble("weightLbs"),0.0)
        assertEquals(50.123456789*0.45359237,b.targetWeight.value!!,0.000001)
        t.endRep();t.finish()
        assertEquals(50.123456789,t.queue().single().getDouble("weightLbs"),0.0)
        assertFalse(t.queue().single().has("weightKg"))
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
    @Test fun savedSetKeepsMeasuredDurationsAndOriginalAccount() {
        val (t,b)=training()
        t.accountSeen("7","Test")
        t.start("crusher","left",2.0/0.45359237,2,0,0)
        assertTrue(b.isFreshActive)
        assertEquals(2.0,b.targetWeight.value!!,0.0)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(12));t.endRep();if(t.phase=="complete")t.finish()
        t.accountSeen("8","Another")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(4));t.endRep();if(t.phase=="complete")t.finish()
        assertFalse(t.inProgress);assertEquals(1,t.pending)
        val saved=t.queue().single()
        assertEquals("7",saved.getString("owner"))
        assertEquals("[12,4]",saved.getJSONArray("reps").toString())
        assertNotNull(saved.getString("date_time"))
    }
    @Test fun restartRecoversCompletedRepsWithoutInventingTimeForInterruptedRep() {
        val (t,_)=training()
        t.start("micro","right",10.0/0.45359237,3,0,0)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(6));t.endRep();if(t.phase=="complete")t.finish()
        ShadowSystemClock.advanceBy(Duration.ofHours(1))
        val (restored,_)=training()
        assertEquals("paused",restored.phase)
        assertEquals("[6]",restored.active!!.getJSONArray("reps").toString())
        restored.finish()
        assertEquals(1,restored.pending)
        assertEquals("[6]",restored.queue().single().getJSONArray("reps").toString())
    }
    @Test fun websiteCannotOverwriteAnActiveOfflineRep() {
        val (t,b)=training();t.start("prime","left",4.0/0.45359237,2,10,0)
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
        t.start("crusher","left",20.0/0.45359237,2,10,5);t.finish()
        assertEquals(0,t.pending)
        t.cancelEmpty();assertFalse(t.inProgress)
    }
    @Test fun unknownAccountRequiresExplicitAssignment() {
        val (t,_)=training();t.start("crusher","left",20.0/0.45359237,1,0,0)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3));t.endRep();if(t.phase=="complete")t.finish()
        assertEquals("",t.queue().single().getString("owner"))
        t.accountSeen("42","Test");assertTrue(t.needsAccount)
        t.assignUnowned();assertEquals("42",t.queue().single().getString("owner"))
    }
    @Test fun corruptionIsReportedAndNotOverwritten() {
        val file=File(context.filesDir,"offline-training.json");file.writeText("broken")
        val (t,_)=training()
        assertFalse(t.available);assertTrue(t.error.isNotEmpty())
        t.start("crusher","left",20.0/0.45359237,1,0,0)
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
    @Test fun curveCacheSurvivesRestartAndIsIsolatedByAccount() {
        val (t,b)=training()
        val data=JSONObject("""{"savedAt":123,"sides":[{"gripper":"crusher","side":"left","params":{"a":400,"b":0.025,"x0":0,"c":0,"d":0}}]}""")
        t.accountSeen("7","First");t.cacheCurves("7",data)
        assertNotNull(t.curve("crusher","left"))
        assertNotNull(training().first.curve("crusher","left"))
        t.accountSeen("8","Second")
        assertNull(t.curve("crusher","left"))
        t.cacheCurves("7",data)
        assertNull(t.curve("crusher","left"))
        t.accountSeen("7","First")
        assertNotNull(t.curve("crusher","left"))
        t.start("crusher","left",25.0/0.45359237,5,10,0,101)
        assertEquals(101,t.active!!.getInt("targetDuration"))
    }
    @Test fun idleTicksDoNotInvalidateUiAndActiveTicksKeepHeartbeatWithoutRedundantUiUpdates() {
        val (t,b)=training()
        val idle=t.revision.value
        repeat(50) { t.tick(android.os.SystemClock.elapsedRealtime()) }
        assertEquals(idle,t.revision.value)
        t.start("crusher","left",20.0/0.45359237,5,10,0)
        val initial=t.revision.value
        repeat(50) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(100))
            t.tick(android.os.SystemClock.elapsedRealtime())
            assertTrue(b.isFreshActive)
        }
        assertEquals(5,t.seconds)
        assertEquals(5,t.revision.value-initial)
    }
    @Test fun unchangedCurveDoesNotWriteOrRecreateModel() {
        val (t,_)=training()
        t.accountSeen("7","Test")
        val cache=JSONObject("""{"savedAt":123,"sides":[{"gripper":"crusher","side":"left","params":{"a":400,"b":0.025,"x0":0,"c":0,"d":0}}]}""")
        t.cacheCurves("7",cache)
        val curve=t.curve("crusher","left")
        val revision=t.revision.value
        t.cacheCurves("7",JSONObject(cache.toString()).put("savedAt",456))
        assertEquals(revision,t.revision.value)
        assertSame(curve,t.curve("crusher","left"))
        assertEquals(123L,t.curveSavedAt)
    }
    @Test fun discardCompletedAndActiveRepsNeverQueuesAndSurvivesRestart() {
        val (t,b)=training()
        t.start("crusher","left",20.0/0.45359237,1,0,0);t.endRep();if(t.phase=="complete")t.finish()
        val savedId=t.queue().single().getString("id")
        t.start("crusher","left",20.0/0.45359237,3,0,0)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(4));t.endRep();if(t.phase=="complete")t.finish()
        t.discardSet();t.endRep();if(t.phase=="complete")t.finish()
        assertFalse(t.inProgress);assertFalse(b.isFreshActive)
        assertNull(b.offlineEndRep)
        assertEquals(savedId,t.queue().single().getString("id"))
        val restored=training().first
        assertFalse(restored.inProgress);assertEquals(1,restored.pending)
    }
    @Test fun targetCountdownCrossesZeroAndResetsForNextRep() {
        val (t,_)=training()
        t.start("crusher","left",20.0/0.45359237,3,2,0,5)
        assertEquals(5,t.targetRemaining)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(5));t.tick(android.os.SystemClock.elapsedRealtime())
        assertEquals(0,t.targetRemaining)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3));t.tick(android.os.SystemClock.elapsedRealtime())
        assertEquals(-3,t.targetRemaining)
        assertTrue(t.inProgress)
        t.endRep();if(t.phase=="complete")t.finish()
        assertEquals(8,t.active!!.getJSONArray("reps").getInt(0))
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2));t.tick(android.os.SystemClock.elapsedRealtime())
        assertEquals("rep",t.phase);assertEquals(5,t.targetRemaining)
        t.discardSet();assertNull(t.targetRemaining)
    }
    @Test fun discardIsAvailableDuringCountdownRestAndRecovery() {
        val (t,_)=training()
        t.start("crusher","left",20.0/0.45359237,3,10,20);t.discardSet()
        assertFalse(t.inProgress)
        t.start("crusher","left",20.0/0.45359237,3,10,0);t.endRep();if(t.phase=="complete")t.finish()
        assertEquals("rest",t.phase)
        val restored=training().first
        assertEquals("paused",restored.phase)
        restored.discardSet()
        assertFalse(training().first.inProgress);assertEquals(0,restored.pending)
    }
    @Test fun importedSetRemainsInHistoryAcrossRestartBeforeServerRefresh() {
        val (t,_)=training();t.accountSeen("7","Test")
        t.start("crusher","left",20.0/0.45359237,1,0,0)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(90));t.endRep();if(t.phase=="complete")t.finish()
        val before=t.history("crusher","left").single();assertTrue(before.pending)
        t.acknowledge(t.queue().single().getString("id"))
        assertEquals(0,t.pending)
        val after=training().first.history("crusher","left").single()
        assertFalse(after.pending);assertEquals(before.hold,after.hold,0.0)
    }
    @Test fun historyRefreshIsAppliedEvenWhenCurveHasNotChanged() {
        val (t,_)=training();t.accountSeen("7","Test")
        val cache=JSONObject().put("sides",JSONArray()).put("sessions",JSONArray())
        t.cacheCurves("7",cache)
        val row=JSONObject("""{"date_time":"2026-09-27T12:00:00Z","gripper":"crusher","side":"left","weight":40,"rep_durations":[70]}""")
        t.cacheCurves("7",JSONObject(cache.toString()).put("sessions",JSONArray().put(row)))
        assertEquals(70.0,t.history("crusher","left").single().hold,0.0)
    }

    @Test fun freshServerSnapshotRemovesDeletedImportedSetsButPreservesPendingSets() {
        for(gripper in listOf("prime","crusher","micro")) {
            val (t,_)=training();t.accountSeen("7","Test")
            t.start(gripper,"left",20.0,1,0,0)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(90));t.endRep();if(t.phase=="complete")t.finish()
            t.acknowledge(t.queue().single().getString("id"))
            val ack=t.state.getJSONArray("localHistory").getJSONObject(0).getLong("acknowledgedAt")
            val cache=JSONObject().put("sides",JSONArray()).put("sessions",JSONArray()).put("sessionsFetchedAt",ack-1)
            t.cacheCurves("7",cache)
            assertEquals(1,t.history(gripper,"left").size)
            t.start(gripper,"left",22.0,1,0,0)
            ShadowSystemClock.advanceBy(Duration.ofSeconds(40));t.endRep();if(t.phase=="complete")t.finish()
            t.cacheCurves("7",JSONObject(cache.toString()).put("sessionsFetchedAt",ack+1))
            assertEquals(1,t.history(gripper,"left").size)
            assertTrue(t.history(gripper,"left").single().pending)
            assertEquals(0,t.state.getJSONArray("localHistory").length())
            t.acknowledge(t.queue().single().getString("id"))
            t.cacheCurves("7",JSONObject(cache.toString()).put("sessionsFetchedAt",Long.MAX_VALUE))
            assertTrue(training().first.history(gripper,"left").isEmpty())
        }
    }

    @Test fun pendingEditsAndDeletionPersistAndUploadingRowsAreProtected() {
        val (t,_)=training();t.accountSeen("7","Test")
        t.start("prime","left",20.0/0.45359237,1,0,0);ShadowSystemClock.advanceBy(Duration.ofSeconds(20));t.endRep();if(t.phase=="complete")t.finish()
        val row=t.queue().single();val id=row.getString("id");val date=row.getString("date_time")
        t.manageQueue(true);assertFalse(t.markUploading(id))
        assertFalse(t.updatePending(id,"prime","right",-1.0,listOf(30)))
        assertFalse(t.updatePending(id,"prime","right",22.0,listOf(0)))
        assertTrue(t.updatePending(id,"crusher","right",22.0,listOf(30,40)))
        val restored=training().first
        assertEquals(date,restored.queue().single().getString("date_time"))
        assertEquals(40.0,restored.history("crusher","right").single().hold,0.0)
        assertTrue(restored.history("prime","left").isEmpty())
        assertTrue(restored.markUploading(id))
        assertFalse(restored.updatePending(id,"prime","left",20.0,listOf(10)))
        assertFalse(restored.deletePending(id))
        assertFalse(training().first.canEditPending(id))
        restored.start("micro","left",10.0/0.45359237,1,0,0);restored.endRep();if(restored.phase=="complete")restored.finish()
        val other=restored.queue().last().getString("id")
        assertTrue(restored.deletePending(other))
        assertEquals(1,training().first.pending)
        assertEquals(0,restored.synced)
    }
    @Test fun successfulUnchangedHistoryFetchUpdatesVisibleCheckTime() {
        val (t,_)=training();t.accountSeen("7","Test")
        val cache=JSONObject().put("sides",JSONArray()).put("sessions",JSONArray()).put("sessionsFetchedAt",123L)
        t.cacheCurves("7",cache);assertEquals(123L,t.historyCheckedAt)
        t.cacheCurves("7",JSONObject(cache.toString()).put("sessionsFetchedAt",456L))
        assertEquals(456L,t.historyCheckedAt)
        t.cacheCurves("8",JSONObject(cache.toString()).put("sessionsFetchedAt",999L))
        assertEquals(456L,t.historyCheckedAt)
    }

    @Test fun completedSetWaitsForExplicitSaveAndSurvivesRestart() {
        val (t,_)=training();t.start("prime","left",20.0/0.45359237,1,0,0);t.endRep()
        assertEquals("complete",t.phase);assertEquals(0,t.pending);assertTrue(t.inProgress)
        val restored=training().first
        assertEquals("complete",restored.phase);assertEquals(0,restored.pending)
        restored.finish();assertEquals(1,restored.pending);assertFalse(restored.inProgress)
    }

    private fun realRep(t: OfflineTraining, kg: Double) {
        repeat(20) {t.forceSample(kg,android.os.SystemClock.elapsedRealtime());ShadowSystemClock.advanceBy(Duration.ofMillis(100))}
        repeat(5) {t.forceSample(0.0,android.os.SystemClock.elapsedRealtime());ShadowSystemClock.advanceBy(Duration.ofMillis(100))}
    }
    @Test fun realForceRequiresOptInAndConnectedMeter() {
        val (t,_)=training()
        t.startRealForce("micro","left",50.0,2,3,null);assertFalse(t.inProgress)
        t.configureRealForce(true,"median",false,0.5,250)
        t.startRealForce("micro","left",50.0,2,3,null);assertFalse(t.inProgress)
    }
    @Test fun realForceWaitsForPullAndKeepsFirstRepWeightForRestAndImport() {
        val (t,b)=training();var cues=0;t.onPullCue={cues++}
        t.configureRealForce(true,"median",true,0.5,250)
        t.startRealForce("micro","left",20.0/0.45359237,2,2,60)
        assertEquals("ready",t.phase);assertFalse(b.isFreshActive)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(30));t.tick(android.os.SystemClock.elapsedRealtime())
        assertEquals("ready",t.phase);assertEquals(0,t.seconds)
        realRep(t,22.0)
        assertEquals("rest",t.phase);assertEquals(1,t.active!!.getJSONArray("reps").length())
        assertEquals(22.0,b.targetWeight.value!!,0.00001)
        val first=t.active!!.getDouble("weightLbs")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2));t.tick(android.os.SystemClock.elapsedRealtime())
        assertEquals("ready",t.phase);assertEquals(1,cues)
        t.forceSample(19.0,android.os.SystemClock.elapsedRealtime());assertEquals("ready",t.phase)
        ShadowSystemClock.advanceBy(Duration.ofMillis(100));realRep(t,26.0)
        assertEquals("complete",t.phase);assertEquals(first,t.active!!.getDouble("weightLbs"),0.0)
        assertEquals(26.0,t.realResults.last().pounds*0.45359237,0.00001)
        t.finish()
        val saved=t.queue().single()
        assertEquals(first,saved.getDouble("weightLbs"),0.0)
        assertEquals("[2,2]",saved.getJSONArray("reps").toString())
        assertFalse(saved.has("realResults"));assertFalse(saved.has("readings"))
        assertFalse(File(context.filesDir,"offline-training.json").readText().contains(t.realResults.last().pounds.toString()))
    }
    @Test fun realForceSignalLossKeepsRepRunningAndAppRecoveryKeepsFirstWeight() {
        val (t,_)=training();t.configureRealForce(true,"median",true,0.5,250)
        t.startRealForce("micro","left",40.0,2,0,null)
        realRep(t,20.0);val weight=t.active!!.getDouble("weightLbs")
        t.forceSample(20.0,android.os.SystemClock.elapsedRealtime());assertEquals("rep",t.phase)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2));t.tick(android.os.SystemClock.elapsedRealtime())
        assertEquals("rep",t.phase);assertEquals(2,t.seconds);assertEquals(1,t.active!!.getJSONArray("reps").length())
        t.configureRealForce(true,"median",false,0.5,250)
        t.tick(android.os.SystemClock.elapsedRealtime()+2000)
        assertEquals("rep",t.phase);assertEquals(4,t.seconds)
        t.configureRealForce(true,"median",true,0.5,250)
        t.forceSample(20.0,android.os.SystemClock.elapsedRealtime()+2100)
        assertEquals("rep",t.phase)
        val (restored,_)=training();restored.configureRealForce(true,"average",true,0.5,250)
        assertEquals("paused",restored.phase);assertEquals(weight,restored.active!!.getDouble("weightLbs"),0.0)
        assertEquals("median",restored.active!!.getString("realMethod"))
        restored.resumeRecovered();assertEquals("ready",restored.phase)
        assertTrue(restored.realResults.isEmpty())
    }
    @Test fun onlineRealForceUsesWebsiteSettingsAndIgnoresDuplicateStartRequests() {
        val (t,b)=training();t.configureRealForce(true,"average",true,0.4,300)
        val request=JSONObject().put("action","startRealForce").put("phase","setup").put("weight","50 lb")
            .put("gripper","micro").put("side","right").put("plannedReps",4).put("restSeconds",17).put("targetDuration",90)
        b.onSnapshot(request.toString())
        val id=t.active!!.getString("id")
        assertEquals("ready",t.phase);assertEquals(50.0,t.active!!.getDouble("weightLbs"),0.000001)
        assertEquals(17,t.active!!.getInt("rest"));assertEquals(4,t.active!!.getInt("plannedReps"));assertEquals(0,t.active!!.getInt("countdown"))
        b.onSnapshot(request.toString());assertEquals(id,t.active!!.getString("id"))
    }
}
