package app.grip_gains_companion.service.offline

import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One point per set: longest completed rep, matching Powercurve's max_hold. */
data class OfflineHistorySet(val date: String, val pounds: Double, val hold: Double, val gripper: String, val side: String, val reps: List<Double>, val pending: Boolean) {
    val zone: Int get() = OfflineCurve.zone(hold)
    val timestamp: Long get() = runCatching {Instant.parse(date).toEpochMilli()}.getOrDefault(0)
    fun daysAgo(today: LocalDate = LocalDate.now(), zoneId: ZoneId = ZoneId.systemDefault()): Long =
        ChronoUnit.DAYS.between(Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate(),today).coerceAtLeast(0)
    fun sameSet(other: OfflineHistorySet): Boolean = timestamp==other.timestamp && gripper==other.gripper && side==other.side && kotlin.math.abs(pounds-other.pounds)<0.011 && reps==other.reps
    companion object {
        fun parse(row: JSONObject, local: Boolean, pending: Boolean): OfflineHistorySet? {
            val array=row.optJSONArray(if(local) "reps" else "rep_durations") ?: JSONArray()
            val reps=(0 until array.length()).map {array.optDouble(it)}.filter {it.isFinite() && it>0}
            val hold=reps.maxOrNull() ?: row.optDouble("max_hold")
            val pounds=if(local) row.optDouble("weightLbs") else row.optDouble("weight")
            val date=row.optString("date_time")
            if(!hold.isFinite() || hold<=0 || !pounds.isFinite() || pounds<=0 || runCatching {Instant.parse(date)}.isFailure) return null
            return OfflineHistorySet(date,pounds,hold,row.optString("gripper"),row.optString("side"),reps,pending)
        }
        fun merge(remote: JSONArray?, local: List<JSONObject>, owner: String, gripper: String, side: String): List<OfflineHistorySet> {
            val result=mutableListOf<OfflineHistorySet>()
            for(row in local) if(row.optString("owner")==owner || row.optString("owner").isEmpty()) {
                parse(row,true,row.optBoolean("pending"))?.let { if(it.gripper==gripper && it.side==side && result.none {old->old.sameSet(it)}) result.add(it) }
            }
            if(remote!=null) for(i in 0 until remote.length()) parse(remote.getJSONObject(i),false,false)?.let { item ->
                if(item.gripper==gripper && item.side==side) {
                    val existing=result.indexOfFirst {it.sameSet(item)}
                    if(existing>=0) result[existing]=item else result.add(item)
                }
            }
            return result.sortedByDescending {it.timestamp}
        }
        fun oldestZone(history: List<OfflineHistorySet>): Int {
            val latest=history.groupBy {it.zone}.mapValues {entry->entry.value.maxOf {it.timestamp}}
            // Never trained first; ties follow the displayed Endurance-to-Power order.
            return OfflineCurve.zones.indices.reversed().minBy {latest[it] ?: Long.MIN_VALUE}
        }
        fun opacity(rank: Int, count: Int): Float = if(count<=1) 1f else 1f-0.8f*rank.coerceIn(0,count-1)/(count-1)
    }
}
