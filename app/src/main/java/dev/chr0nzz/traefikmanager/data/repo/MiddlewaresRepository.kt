package dev.chr0nzz.traefikmanager.data.repo

import dev.chr0nzz.traefikmanager.data.model.fail
import dev.chr0nzz.traefikmanager.R
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
import retrofit2.HttpException

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
        if (!result.ok) fail(result.message ?: result.error, R.string.middlewares_error_save)
        routesRepository.notifyChangedExternally()
    }

    suspend fun delete(name: String, configFile: String, force: Boolean = false) {
        val ready = apiProvider.ready()
        val body = FormBody.Builder()
            .add("configFile", configFile)
            .add("agent_id", ready.agentId.orEmpty())
            .apply { if (force) add("force", "true") }
            .build()
        val result = try {
            ready.api.deleteMiddleware(name, body)
        } catch (exception: HttpException) {
            throw InUse.from(exception, R.string.middlewares_error_delete)
        }
        if (!result.ok) fail(result.message ?: result.error, R.string.middlewares_error_delete)
        routesRepository.notifyChangedExternally()
    }

    suspend fun templates(): List<MiddlewareTemplate> =
        runCatching { apiProvider.api().middlewareTemplates().templates }.getOrDefault(emptyList())

    suspend fun createTemplate(name: String, yaml: String) {
        val result = apiProvider.api().createMiddlewareTemplate(TemplateBody(name, yaml))
        if (!result.ok) fail(result.message ?: result.error, R.string.error_could_not_create_the_template)
    }

    suspend fun updateTemplate(id: String, name: String, yaml: String) {
        val result = apiProvider.api().updateMiddlewareTemplate(id, TemplateBody(name, yaml))
        if (!result.ok) fail(result.message ?: result.error, R.string.error_could_not_update_the_template)
    }

    suspend fun deleteTemplate(id: String) {
        val result = apiProvider.api().deleteMiddlewareTemplate(id)
        if (!result.ok) fail(result.message ?: result.error, R.string.middlewares_templates_error_delete)
    }

    suspend fun htpasswdEntry(username: String, password: String): String {
        val result = apiProvider.api().htpasswd(HtpasswdRequest(username, password))
        if (!result.ok) fail(result.error, R.string.middlewares_error_generate)
        return result.entry
    }

    suspend fun digestEntry(username: String, password: String, realm: String): String {
        val result = apiProvider.api().digestAuth(DigestRequest(username, password, realm))
        if (!result.ok) fail(result.error, R.string.middlewares_error_generate)
        return result.entry
    }
}
