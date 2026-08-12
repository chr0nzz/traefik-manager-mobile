package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.Countries
import dev.chr0nzz.traefikmanager.data.model.GeoLookupRequest
import dev.chr0nzz.traefikmanager.data.model.GeoStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val LOOKUP_BATCH = 1000
private const val CACHE_LIMIT = 5000

@Singleton
class GeoRepository @Inject constructor(
    private val apiProvider: ApiProvider,
) {

    private val cache = object : LinkedHashMap<String, String?>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>): Boolean =
            size > CACHE_LIMIT
    }
    private val mutex = Mutex()

    @Volatile
    private var status: GeoStatus? = null

    suspend fun status(force: Boolean = false): GeoStatus {
        val known = status
        if (known != null && !force) return known
        val fetched = runCatching { apiProvider.api().geoStatus() }
            .getOrDefault(GeoStatus(enabled = false, available = false))
        status = fetched
        return fetched
    }

    fun cachedCode(ip: String): String? = cache[ip]

    suspend fun lookup(ips: List<String>): Map<String, String> {
        if (!status().usable) return emptyMap()

        val wanted = ips.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && Countries.worthLookingUp(it) }
            .distinct()
            .toList()

        val missing = mutex.withLock { wanted.filterNot { cache.containsKey(it) } }
        missing.chunked(LOOKUP_BATCH).forEach { batch ->
            val response = runCatching {
                apiProvider.api().geoLookup(GeoLookupRequest(ips = batch, aggregate = true))
            }.getOrNull() ?: return@forEach

            if (!response.available) {
                status = status?.copy(available = false)
                return@forEach
            }
            val resolved = if (response.codes.isNotEmpty()) {
                response.codes
            } else {
                response.results.mapValues { it.value.countryCode }
            }
            mutex.withLock {
                batch.forEach { ip -> cache[ip] = resolved[ip]?.takeIf { it.isNotEmpty() } }
            }
        }

        return mutex.withLock {
            wanted.mapNotNull { ip -> cache[ip]?.let { ip to it } }.toMap()
        }
    }

    fun clear() {
        status = null
        cache.clear()
    }
}
