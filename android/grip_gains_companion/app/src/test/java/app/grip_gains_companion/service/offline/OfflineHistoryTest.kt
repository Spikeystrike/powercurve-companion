package app.grip_gains_companion.service.offline

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class OfflineHistoryTest {
    private fun local(owner: String="7")=JSONObject("""{"owner":"$owner","date_time":"2026-09-20T12:00:00Z","gripper":"crusher","side":"left","weightKg":20,"reps":[40,90,60],"pending":true}""")
    @Test fun zoneUsesLongestMeasuredRepAndDaysUseCalendarDates() {
        val set=OfflineHistorySet.parse(local(),true,true)!!
        assertEquals(90.0,set.hold,0.0);assertEquals(2,set.zone)
        assertEquals(7L,set.daysAgo(LocalDate.of(2026,9,27),ZoneId.of("Europe/Berlin")))
        assertEquals(0L,set.daysAgo(LocalDate.of(2026,9,19),ZoneId.of("Europe/Berlin")))
    }
    @Test fun deduplicatesImportedLocalSetAndKeepsAccountAndSideIsolation() {
        val remote=JSONObject("""{"date_time":"2026-09-20T12:00:00Z","gripper":"crusher","side":"left","weight":44.09245,"rep_durations":[40,90,60]}""")
        val merged=OfflineHistorySet.merge(JSONArray().put(remote),listOf(local(),local("8")),"7","crusher","left")
        assertEquals(1,merged.size);assertFalse(merged[0].pending)
        assertTrue(OfflineHistorySet.merge(null,listOf(local("8")),"7","crusher","left").isEmpty())
        assertTrue(OfflineHistorySet.merge(null,listOf(local()),"7","crusher","right").isEmpty())
    }
    @Test fun fadeDependsOnRankNotElapsedDays() {
        assertEquals(1f,OfflineHistorySet.opacity(0,60),0.0001f)
        assertEquals(0.2f,OfflineHistorySet.opacity(59,60),0.0001f)
        assertTrue(OfflineHistorySet.opacity(2,60)>OfflineHistorySet.opacity(3,60))
        assertEquals(1f,OfflineHistorySet.opacity(0,1),0.0f)
    }
    @Test fun chronologicalMergeIncludesPendingSetsAndUsesOnlyMatchingSide() {
        val older=local().put("date_time","2026-01-01T12:00:00Z")
        val newer=local().put("date_time","2026-09-25T12:00:00Z")
        val history=OfflineHistorySet.merge(null,listOf(older,newer),"7","crusher","left")
        assertEquals("2026-09-25T12:00:00Z",history.first().date)
        assertTrue(history.first().pending)
    }
    @Test fun preselectsZoneWhoseLatestSetIsOldestIncludingLocalSets() {
        val rows=listOf(40.0,60.0,100.0,150.0,220.0).mapIndexed {i,hold ->
            OfflineHistorySet("2026-09-"+(20+i)+"T12:00:00Z",40.0,hold,"crusher","left",listOf(hold),false)
        }
        assertEquals(0,OfflineHistorySet.oldestZone(rows))
        val pending=rows[0].copy(date="2026-09-27T12:00:00Z",pending=true)
        assertEquals(1,OfflineHistorySet.oldestZone(rows+pending))
        assertEquals(4,OfflineHistorySet.oldestZone(emptyList()))
        assertEquals(3,OfflineHistorySet.oldestZone(listOf(rows[4])))
    }
}
