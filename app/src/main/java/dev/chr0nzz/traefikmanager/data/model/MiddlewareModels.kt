package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MiddlewareTemplate(
    val id: String = "",
    val name: String = "",
    val yaml: String = "",
)

@Serializable
data class MiddlewareTemplatesResponse(
    val templates: List<MiddlewareTemplate> = emptyList(),
)

@Serializable
data class TemplateBody(
    val name: String,
    val yaml: String,
)

@Serializable
data class HtpasswdRequest(
    val username: String,
    val password: String,
)

@Serializable
data class HtpasswdResponse(
    val ok: Boolean = false,
    val entry: String = "",
    val error: String? = null,
)

@Serializable
data class DigestRequest(
    val username: String,
    val password: String,
    val realm: String = "",
)

enum class MiddlewareProtocol(val wire: String) {
    Http("http"),
    Tcp("tcp"),
    ;

    companion object {
        fun from(value: String): MiddlewareProtocol =
            entries.firstOrNull { it.wire == value.lowercase() } ?: Http
    }
}

data class MiddlewareForm(
    val name: String = "",
    val protocol: MiddlewareProtocol = MiddlewareProtocol.Http,
    val yaml: String = "",
    val configFile: String = "",
    val isEdit: Boolean = false,
    val originalName: String = "",
    val originalProtocol: MiddlewareProtocol = MiddlewareProtocol.Http,
) {
    val validationError: String?
        get() = when {
            name.isBlank() -> "A middleware name is required."
            yaml.isBlank() -> "Middleware content cannot be empty."
            else -> null
        }

    val isValid: Boolean get() = validationError == null
}

object MiddlewareFormEncoder {

    fun fields(form: MiddlewareForm, agentId: String?): List<Pair<String, String>> = listOf(
        "middlewareName" to form.name.trim(),
        "middlewareContent" to form.yaml.trim(),
        "mwProtocol" to form.protocol.wire,
        "isMwEdit" to form.isEdit.toString(),
        "originalMwId" to form.originalName,
        "originalMwProtocol" to form.originalProtocol.wire,
        "configFile" to form.configFile,
        "agent_id" to agentId.orEmpty(),
    )
}

object MiddlewareUsage {

    fun countsFor(routes: List<Route>, middlewares: List<MiddlewareDef>): Map<String, Int> {
        val referenced = mutableMapOf<String, Int>()
        routes.forEach { route ->
            val names = (route.middlewareNames + route.entrypointMiddlewares)
                .map { it.substringBefore('@') }
                .toSet()
            names.forEach { name -> referenced[name] = (referenced[name] ?: 0) + 1 }
        }
        val chainMembers = middlewares.flatMap { middleware ->
            chainRefs(middleware.yaml).map { it.substringBefore('@') to middleware.name }
        }
        chainMembers.forEach { (member, owner) ->
            val ownerUses = referenced[owner] ?: 0
            if (ownerUses > 0) referenced[member] = (referenced[member] ?: 0) + ownerUses
        }
        return middlewares.associate { it.name to (referenced[it.name] ?: 0) }
    }

    private val CHAIN_ITEM = Regex("""^\s*-\s*(.+?)\s*$""", RegexOption.MULTILINE)

    private fun chainRefs(yaml: String): List<String> {
        if (!yaml.contains("chain:")) return emptyList()
        val afterChain = yaml.substringAfter("chain:", "")
        return CHAIN_ITEM.findAll(afterChain)
            .map { it.groupValues[1].trim('"', '\'', ' ') }
            .filter { it.isNotEmpty() }
            .toList()
    }
}
