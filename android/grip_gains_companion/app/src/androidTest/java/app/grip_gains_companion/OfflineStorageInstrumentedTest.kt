package app.grip_gains_companion

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.grip_gains_companion.service.offline.OfflineStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OfflineStorageInstrumentedTest {
    @Test fun queueAndAcknowledgementSurviveReopeningOnAndroid() {
        val original=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(original.cacheDir,"offline-storage-test-${UUID.randomUUID()}").apply {mkdirs()}
        val isolated=object:ContextWrapper(original) {override fun getFilesDir():File=directory}
        try {
            val store=OfflineStore(isolated)
            store.enqueue(JSONObject().put("id","first").put("reps",JSONArray().put(5)))
            store.enqueue(JSONObject().put("id","second").put("reps",JSONArray().put(8)))
            val reopened=OfflineStore(isolated)
            assertEquals(2,reopened.snapshot().getJSONArray("queue").length())
            reopened.acknowledge("first")
            val confirmed=OfflineStore(isolated).snapshot()
            assertEquals(1,confirmed.getInt("synced"))
            assertEquals("second",confirmed.getJSONArray("queue").getJSONObject(0).getString("id"))
        } finally {
            directory.listFiles()?.forEach {it.delete()}
            directory.delete()
        }
    }
}
