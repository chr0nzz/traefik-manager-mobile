package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.LogLine
import dev.chr0nzz.traefikmanager.data.model.LogParser
import javax.inject.Inject
import javax.inject.Singleton

data class LogsSnapshot(
    val lines: List<LogLine> = emptyList(),
    val error: String? = null,
)

@Singleton
class LogsRepository @Inject constructor(
    private val apiProvider: ApiProvider,
) {

    suspend fun load(lines: Int): LogsSnapshot {
        val response = apiProvider.api().logs(lines)
        val raw = response.lines.orEmpty()
        return LogsSnapshot(
            lines = raw.mapIndexed { index, line -> LogLine(index, line, LogParser.parse(line)) },
            error = response.error?.takeIf { it.isNotBlank() },
        )
    }
}
