package app.grip_gains_companion

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import app.grip_gains_companion.service.ble.FrezKeyStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class FrezKeyStoreInstrumentedTest {
    @Test fun keyIsEncryptedPersistsAndCanBeRemoved() {
        val original=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(original.cacheDir,"frez-key-test-"+UUID.randomUUID()).apply {mkdirs()}
        val context=object:ContextWrapper(original) {override fun getNoBackupFilesDir()=directory}
        val store=FrezKeyStore(context)
        try {
            assertEquals("",store.read())
            store.save("test-only-developer-key")
            assertEquals("test-only-developer-key",FrezKeyStore(context).read())
            assertFalse(File(directory,"frez-key").readText().contains("test-only-developer-key"))
            store.save("");assertEquals("",store.read())
        } finally {store.save("");directory.delete()}
    }
}
