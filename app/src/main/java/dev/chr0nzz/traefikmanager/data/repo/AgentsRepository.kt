package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.Agent
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

@Singleton
class AgentsRepository @Inject constructor(
    private val apiProvider: ApiProvider,
) {

    suspend fun agents(): List<Agent> = try {
        apiProvider.api().agents().agents
    } catch (e: HttpException) {
        if (e.code() == 404) emptyList() else throw e
    }
}
