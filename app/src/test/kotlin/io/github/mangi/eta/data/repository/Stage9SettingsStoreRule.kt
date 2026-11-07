package io.github.mangi.eta.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.mangi.eta.data.datastore.SettingsDataStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.rules.ExternalResource
import org.robolectric.RuntimeEnvironment

/** A real, independently scoped DataStore; native/legacy Robolectric sandboxes must not share a file lock. */
class Stage9SettingsStoreRule : ExternalResource() {
    private val field = SettingsDataStore::class.java.getDeclaredField("dataStore").apply { isAccessible = true }
    private var previous: Any? = null
    private lateinit var job: Job
    private lateinit var file: File
    override fun before() {
        io.github.mangi.eta.data.db.EtaDatabase.closeForTests()
        RuntimeEnvironment.getApplication().deleteDatabase("eta.db")
        previous = field.get(SettingsDataStore)
        job = SupervisorJob()
        file = File(RuntimeEnvironment.getApplication().cacheDir, "stage9-${UUID.randomUUID()}.preferences_pb")
        field.set(SettingsDataStore, PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file }))
    }
    override fun after() {
        io.github.mangi.eta.data.db.EtaDatabase.closeForTests()
        RuntimeEnvironment.getApplication().deleteDatabase("eta.db")
        job.cancel(); runBlocking { job.join() }
        field.set(SettingsDataStore, previous)
        file.delete()
    }
}
