package app.grip_gains_companion.service.offline

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], application=Application::class)
class OfflineCurveTest {
    private fun data()=JSONObject("""{"gripper":"crusher","params":{"a":400,"b":0.025,"x0":0,"c":0,"d":0},"points":[{"hold":40},{"hold":60},{"hold":100},{"hold":150},{"hold":230}],"zone_characteristic_times":{"power":40,"power_strength":60,"strength":100,"strength_endurance":150,"endurance":230}}""")
    @Test fun inverseMatchesWebsiteModel() {
        val curve=OfflineCurve(data())
        assertEquals(100.0,curve.hold(55.451774444795625),0.000001)
        for(seconds in listOf(30.0,40.0,60.0,100.0,150.0,230.0,300.0)) assertEquals(seconds,curve.hold(curve.pounds(seconds)),0.000001)
    }
    @Test fun allZonesMatchRoundedWeightsAndRecommendedReps() {
        val curve=OfflineCurve(data())
        for(lbs in listOf(false,true)) for(index in 0..4) {
            val match=curve.match(index,lbs)!!
            assertEquals(OfflineCurve.zones[index],match.zone)
            assertEquals(listOf(6,5,5,4,4)[index],match.zone.reps)
            val step=if(lbs)1.0 else 0.5
            assertEquals(0.0,match.weight%step,0.000001)
            assertEquals(match,curve.estimate(match.weight,lbs))
        }
        assertEquals(25.0,curve.match(2,false)!!.weight,0.001)
        assertEquals(101,curve.match(2,false)!!.seconds)
    }
    @Test fun missingNeighborEvidenceDisablesOnlyAffectedZones() {
        val data=data();data.getJSONArray("points").remove(4)
        val curve=OfflineCurve(data)
        assertNotNull(curve.match(2,false))
        assertNull(curve.match(3,false));assertNull(curve.match(4,false))
    }
    @Test fun invalidAndOutOfDomainWeightsHaveNoEstimate() {
        val curve=OfflineCurve(data())
        assertNull(curve.estimate(Double.NaN,false));assertNull(curve.estimate(0.0,false));assertNull(curve.estimate(500.0,false))
        assertEquals(0,OfflineCurve.zone(47.0));assertEquals(1,OfflineCurve.zone(48.0));assertEquals(2,OfflineCurve.zone(82.0));assertEquals(3,OfflineCurve.zone(129.0));assertEquals(4,OfflineCurve.zone(180.0))
    }
    @Test fun primeUsesSmallerWeightSteps() {
        val curve=OfflineCurve(data().put("gripper","prime"))
        assertEquals(25.15,curve.match(2,false)!!.weight,0.000001)
        assertEquals(55.5,curve.match(2,true)!!.weight,0.000001)
    }
    @Test fun plotUsesIncreasingWeightAndDecreasingHoldTime() {
        val curve=OfflineCurve(data())
        val segments=curve.plotSegments()
        assertEquals(5,segments.size)
        segments.forEach { points ->
            assertEquals(41,points.size)
            points.zipWithNext().forEach { (left,right) ->
                assertTrue(right.weightPounds>left.weightPounds)
                assertTrue(right.seconds<left.seconds)
            }
            points.forEach { assertEquals(curve.hold(it.weightPounds),it.seconds,0.000001) }
        }
        assertEquals(30.0,segments.first().last().seconds,0.000001)
        assertEquals(300.0,segments.last().first().seconds,0.000001)
    }
}
