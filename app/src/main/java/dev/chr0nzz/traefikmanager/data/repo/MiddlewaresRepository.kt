package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.api.ApiProvider
import dev.chr0nzz.traefikmanager.data.model.DigestRequest
import dev.chr0nzz.traefikmanager.data.model.HtpasswdRequest
import dev.chr0nzz.traefikmanager.data.model.MiddlewareForm
import dev.chr0nzz.traefikmanager.data.model.MiddlewareFormEncoder
import dev.chr0nzz.traefikmanager.data.model.MiddlewareTemplate
import dev.chr0nzz.traefikmanager.data.model.TemplateBody
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.FormBody

@Singleton
class MiddlewaresRepository @Inject constructor(
    private val apiProvider: ApiProvider,
    private val routesRepository: RoutesRepository,
) {

    suspend fun save(form: MiddlewareForm) {
        val ready = apiProvider.ready()
        val builder = FormBody.Builder()
        MiddlewareFormEncoder.fields(form, ready.agentId).forEach { (key, value) ->
            builder.add(key, value)
        }
        val result = ready.api.saveMiddleware(builder.build())
        if (!result.ok) error(result.message ?: result.error ?: "Could not save the middleware")
        routesRepository.notifyChangedExternally()
    }

    suspend fun delete(name: String, configFile: String) {
        val ready = apiProvider.ready()
        val body = FormBody.Builder()
            .add("configFile", configFile)
            .add("agent_id", ready.agentId.orEmpty())
            .build()
        val result = ready.api.deleteMiddleware(name, body)
        if (!result.ok) error(result.message ?: result.error ?: "Could not delete the middleware")
        routesRepository.notifyChangedExternally()
    }

    suspend fun templates(): List<MiddlewareTemplate> =
        runCatching { apiProvider.api().middlewareTemplates().templates }.getOrDefault(emptyList())

    suspend fun createTemplate(name: String, yaml: String) {
        val result = apiProvider.api().createMiddlewareTemplate(TemplateBody(name, yaml))
        if (!result.ok) error(result.message ?: result.error ?: "Could not create the template")
    }

    suspend fun updateTemplate(id: String, name: String, yaml: String) {
        val result = apiProvider.api().updateMiddlewareTemplate(id, TemplateBody(name, yaml))
        if (!result.ok) error(result.message ?: result.error ?: "Could not update the template")
    }

    suspend fun deleteTemplate(id: String) {
        val result = apiProvider.api().deleteMiddlewareTemplate(id)
        if (!result.ok) error(result.message ?: result.error ?: "Could not delete the template")
    }

    suspend fun htpasswdEntry(username: String, password: String): String {
        val result = apiProvider.api().htpasswd(HtpasswdRequest(username, password))
        if (!result.ok) error(result.error ?: "Could not generate the entry")
        return result.entry
    }

    suspend fun digestEntry(username: String, password: String, realm: String): String {
        val result = apiProvider.api().digestAuth(DigestRequest(username, password, realm))
        if (!result.ok) error(result.error ?: "Could not generate the entry")
        return result.entry
    }
}
