package app.grip_gains_companion.service.offline

import android.content.Context
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** All mutations are committed atomically before publishing the new state. */
class OfflineStore(context: Context) {
    private val file = File(context.filesDir, "offline-training.json")
    private var data = if (file.exists()) JSONObject(file.readText()) else JSONObject()
    val queueSize: Int get() = data.optJSONArray("queue")?.length() ?: 0
    val syncedCount: Int get() = data.optInt("synced")
    val lastAccount: String get() = data.optString("lastAccount")
    fun has(key: String): Boolean = data.has(key)
    fun snapshot(): JSONObject = JSONObject(data.toString())
    fun update(change: (JSONObject) -> Unit) {
        val next = snapshot()
        change(next)
        val temporary = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(next.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        // Android API 29+ supports an atomic same-directory rename. A failed rename leaves the old queue intact.
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        data = JSONObject(next.toString())
    }

    fun enqueue(record: JSONObject) = update { state ->
        val queue = state.optJSONArray("queue") ?: JSONArray()
        if ((0 until queue.length()).none { queue.getJSONObject(it).getString("id") == record.getString("id") }) queue.put(record)
        state.put("queue", queue)
        for (key in listOf("active", "webActive")) {
            if (state.optJSONObject(key)?.optString("id") == record.getString("id")) state.remove(key)
        }
    }
    fun acknowledge(id: String) = update { state ->
        val queue = state.optJSONArray("queue") ?: JSONArray()
        val remaining = JSONArray()
        var found = false
        for (i in 0 until queue.length()) {
            val record = queue.getJSONObject(i)
            if (record.getString("id") == id) found = true else remaining.put(record)
        }
        if (found) state.put("queue", remaining).put("synced", state.optInt("synced") + 1).put("syncedAt", System.currentTimeMillis())
    }
}
