package app.grip_gains_companion.service.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import app.grip_gains_companion.service.web.WebViewBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlin.properties.Delegates

class OfflineTraining(private val context: Context, private val bridge: WebViewBridge, scope: CoroutineScope) {
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()
    private var store: OfflineStore? = null
    private fun <T> observed(initial: T) = Delegates.observable(initial) { _, old, new -> if(old != new) _revision.value++ }
    var error by observed(""); private set
    var online by observed(true); private set
    var pageFailed by observed(false)
    var open by observed(false); private set
    var phase by observed("setup"); private set
    var seconds by observed(0); private set
    var active by observed<JSONObject?>(null); private set
    var account by observed<String?>(null); private set
    var accountName by observed(""); private set
    var syncMessage by observed(""); private set
    private var webRecord: JSONObject? = null
    private var webWasOffline = false
    private var webSaved = false
    private var phaseStart = 0L
    private var lastNetworkCheck = 0L
    private var lastTimerVisible = false
    private var cachedCurveData: JSONObject? = null
    private val curveModels = mutableMapOf<String, OfflineCurve>()
    private val sync = OfflineSync(context, this)
    val state: JSONObject get() = store?.snapshot() ?: JSONObject()
    val pending: Int get() = store?.queueSize ?: 0
    val synced: Int get() = store?.syncedCount ?: 0
    val available: Boolean get() = store != null && error.isEmpty()
    val inProgress: Boolean get() = active != null
    val showTimer: Boolean get() = open || inProgress || ((!online || pageFailed) && !bridge.webSetRunning)
    val needsAccount: Boolean get() = account != null && queue().any { it.optString("owner").isEmpty() }
    fun queue(): List<JSONObject> { val q=state.optJSONArray("queue") ?: JSONArray(); return (0 until q.length()).map(q::getJSONObject) }
    init {
        try {
            store = OfflineStore(context)
            refreshCurveMemory()
            active = state.optJSONObject("active") ?: state.optJSONObject("webActive")
            if (active != null) { phase = "paused"; open = true }
        } catch (_: Exception) { error = "Offline data could not be read. Your saved file has been kept." }
        scope.launch {
            while (true) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastNetworkCheck >= 1000 || lastNetworkCheck == 0L) {
                    lastNetworkCheck = now
                    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    connectionChanged(manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
                    sync.tick(online)
                }
                if (pageFailed && !inProgress && store?.has("webActive") == true) {
                    active=state.getJSONObject("webActive");phase="paused";open=true
                }
                tick(now)
                if(lastTimerVisible != showTimer) { lastTimerVisible=showTimer; _revision.value++ }
                delay(200)
            }
        }
    }
    internal fun connectionChanged(connected: Boolean) {
        val wasOnline=online
        online=connected
        if(!online && !bridge.webSetRunning) open=true
        if(online && !wasOnline && !inProgress && !bridge.webSetRunning && store?.has("webActive") != true) bridge.reloadPage()
    }
    fun retryStorage() { if(store!=null) save {} }
    private fun save(change: (JSONObject) -> Unit): Boolean { return try {
        val destination = store ?: return false
        destination.update(change); error=""; _revision.value++; true
    } catch (_: Exception) { error = "Could not save offline data. Free storage and try again; keep this set open."; false } }
    fun start(gripper: String, side: String, weightKg: Double, reps: Int, rest: Int, countdown: Int, targetDuration: Int? = null) {
        if (inProgress || !available || gripper !in listOf("micro","crusher","prime") || side !in listOf("left","right") || !weightKg.isFinite() || weightKg <= 0 || reps !in 1..100 || rest !in 0..600 || countdown !in 0..60) return
        if(store?.has("webActive") == true) { active=state.getJSONObject("webActive");phase="paused";open=true;publish();return }
        val record = JSONObject().put("id",UUID.randomUUID().toString()).put("owner",state.optString("lastAccount"))
            .put("date_time",Instant.ofEpochMilli(System.currentTimeMillis()).toString()).put("gripper",gripper).put("side",side).put("weightKg",weightKg)
            .put("targetDuration",targetDuration).put("plannedReps",reps).put("rest",rest).put("countdown",countdown).put("reps",JSONArray())
        if (!save { it.put("active",record).put("defaults",record) }) return
        active=record; open=true; phase=if(countdown>0) "countdown" else "rep"; phaseStart=SystemClock.elapsedRealtime()
        bridge.offlineEndRep = ::endRep
        publish()
    }
    internal fun tick(now: Long) {
        val record=active ?: return
        val elapsed=((now-phaseStart)/1000).toInt().coerceAtLeast(0)
        seconds=when(phase) { "countdown" -> (record.getInt("countdown")-elapsed).coerceAtLeast(0); "rest" -> (record.getInt("rest")-elapsed).coerceAtLeast(0); "rep" -> elapsed; else -> 0 }
        if ((phase=="countdown" || phase=="rest") && seconds==0) { phase="rep"; phaseStart=now }
        publish()
    }
    private fun publish() {
        val record=active ?: return
        bridge.offlineEndRep=::endRep
        bridge.onOfflineSnapshot(JSONObject().put("phase",if(phase=="paused") "setup" else phase).put("active",phase=="rep")
            .put("targetDuration",record.optInt("targetDuration")).put("repKey",record.getString("id")+":"+record.getJSONArray("reps").length()).put("seconds",seconds)
            .put("weight","${record.getDouble("weightKg")} kg").put("gripper",record.getString("gripper"))
            .put("side",record.getString("side")).put("url","offline://timer").toString())
    }
    fun endRep() {
        val record=active ?: return
        if (phase!="rep") return
        val next=JSONObject(record.toString())
        // Match the website timer: round elapsed time to positive integer seconds.
        next.getJSONArray("reps").put(kotlin.math.round((SystemClock.elapsedRealtime()-phaseStart)/1000.0).toInt().coerceAtLeast(1))
        if (!save { it.put("active",next) }) return
        active=next
        if (next.getJSONArray("reps").length()>=next.getInt("plannedReps")) { phase="complete"; publish(); finish() }
        else { phase=if(next.getInt("rest")>0) "rest" else "rep"; phaseStart=SystemClock.elapsedRealtime(); publish() }
    }
    fun resumeRecovered() { if(phase=="paused" && active!=null) {phase="countdown";phaseStart=SystemClock.elapsedRealtime();publish()} }
    fun finish() {
        val record=active ?: return
        if (record.getJSONArray("reps").length()==0) { syncMessage="Complete a rep before saving this set."; return }
        try { store?.enqueue(record) ?: return } catch (_: Exception) {error="Could not save the set. Free storage and try Save set again.";return}
        active=null;phase="setup";bridge.offlineEndRep=null;bridge.invalidate("Offline set saved");syncMessage="";error="";_revision.value++
    }
    fun cancelEmpty() { if(active?.getJSONArray("reps")?.length()!=0)return; if(save {it.remove("active");it.remove("webActive")}){active=null;phase="setup";bridge.offlineEndRep=null;bridge.invalidate()} }
    fun useWebsite() { if(inProgress)return;open=false;pageFailed=false;bridge.offlineEndRep=null;bridge.reloadPage() }
    fun openTimer() {open=true}
    fun accountSeen(id: String?, name: String) {
        account=id;accountName=name
        if(id!=null && store?.lastAccount!=id && save {it.put("lastAccount",id)}) refreshCurveMemory()
    }
    val curveCache: JSONObject? get() = cachedCurveData?.let { JSONObject(it.toString()) }
    val curveSavedAt: Long? get() = cachedCurveData?.optLong("savedAt")
    private fun refreshCurveMemory() {
        val snapshot=state
        cachedCurveData=snapshot.optJSONObject("curvesByOwner")?.optJSONObject(snapshot.optString("lastAccount"))
        curveModels.clear()
        val sides=cachedCurveData?.optJSONArray("sides") ?: return
        for(i in 0 until sides.length()) {
            val side=sides.getJSONObject(i)
            runCatching { OfflineCurve(side) }.getOrNull()?.let { curveModels[side.optString("gripper")+":"+side.optString("side")]=it }
        }
    }
    fun curve(gripper: String, side: String): OfflineCurve? {
        return curveModels[gripper+":"+side]
    }
    fun cacheCurves(owner: String, data: JSONObject) {
        if(owner!=account || !data.has("sides")) return
        if(cachedCurveData?.optJSONArray("sides")?.toString()==data.optJSONArray("sides")?.toString()) return
        if(save { state -> val caches=state.optJSONObject("curvesByOwner") ?: JSONObject(); caches.put(owner,data);state.put("curvesByOwner",caches) }) refreshCurveMemory()
    }
    fun assignUnowned() { val id=account ?: return;save {state -> val q=state.optJSONArray("queue")?:JSONArray();for(i in 0 until q.length())if(q.getJSONObject(i).optString("owner").isEmpty())q.getJSONObject(i).put("owner",id)} }
    fun acknowledge(id: String) {try{store?.acknowledge(id);syncMessage="";error="";_revision.value++}catch(_:Exception){error="Import succeeded, but local confirmation could not be saved. The next attempt will check for this set first."}}
    fun status(message: String) {syncMessage=message}
    fun dismissSuccess() {save {it.put("synced",0)}}
    fun onWebSnapshot(snapshot: JSONObject) {
        if(inProgress) return
        val p=snapshot.optString("phase")
        if(p=="setup") {webRecord=null;webWasOffline=false;webSaved=false;return}
        if(p !in listOf("countdown","rep","rest","complete")) return
        val weight=bridge.targetWeight.value ?: return
        val gripper=snapshot.optString("gripper").lowercase()
        val side=snapshot.optString("side").lowercase()
        if(gripper !in listOf("micro","crusher","prime") || side !in listOf("left","right")) return
        if(webRecord==null) webRecord=JSONObject().put("id",UUID.randomUUID().toString()).put("owner",state.optString("lastAccount"))
            .put("date_time",Instant.ofEpochMilli(System.currentTimeMillis()).toString()).put("gripper",gripper).put("side",side)
            .put("weightKg",weight).put("plannedReps",snapshot.optInt("plannedReps",6).coerceIn(1,100)).put("rest",10).put("countdown",3).put("reps",JSONArray())
        if(!online) webWasOffline=true
        val record=webRecord ?: return
        val reps=snapshot.optJSONArray("completedReps") ?: JSONArray()
        if((0 until reps.length()).any {reps.optInt(it)<=0}) return
        val changed=record.getJSONArray("reps").toString()!=reps.toString()
        record.put("reps",reps)
        if(webWasOffline && !webSaved && (changed || store?.has("webActive") != true)) save {it.put("webActive",record)}
        if(p=="complete" && webWasOffline && !webSaved && reps.length()>0) {
            try {store?.enqueue(record) ?: return;webSaved=true;open=true;syncMessage=""} catch(_:Exception){error="Could not save the completed set. Keep this screen open and free storage."}
        }
    }
    fun close() {sync.close()}
}
