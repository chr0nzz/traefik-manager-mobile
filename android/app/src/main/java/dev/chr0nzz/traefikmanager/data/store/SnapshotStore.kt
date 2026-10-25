package dev.chr0nzz.traefikmanager.data.store

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

@Singleton
class SnapshotStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) {

    private val dir: File by lazy { File(context.filesDir, DIR).apply { mkdirs() } }

    suspend fun <T> read(name: String, server: String, serializer: KSerializer<T>): T? =
        withContext(Dispatchers.IO) {
            val file = fileFor(name, server)
            if (!file.exists()) return@withContext null
            if (System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS) {
                file.delete()
                return@withContext null
            }
            runCatching { json.decodeFromString(serializer, file.readText()) }.getOrElse {
                file.delete()
                null
            }
        }

    suspend fun <T> write(name: String, server: String, value: T, serializer: KSerializer<T>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = json.encodeToString(serializer, value)
                if (payload.length > MAX_BYTES) return@runCatching
                val file = fileFor(name, server)
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(payload)
                if (!tmp.renameTo(file)) {
                    file.writeText(payload)
                    tmp.delete()
                }
            }
        }
    }

    suspend fun keyFor(baseUrl: String, agentId: String?): String =
        baseUrl + "|" + agentId.orEmpty()

    suspend fun forget(name: String, server: String) {
        withContext(Dispatchers.IO) { runCatching { fileFor(name, server).delete() } }
    }

    private fun fileFor(name: String, server: String) = File(dir, "$name-${slug(server)}.json")

    companion object {
        private const val DIR = "snapshots"
        private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        private const val MAX_BYTES = 2 * 1024 * 1024

        fun slug(server: String): String {
            val cleaned = server.ifBlank { "host" }
            return cleaned.hashCode().toUInt().toString(16)
        }
    }
}
