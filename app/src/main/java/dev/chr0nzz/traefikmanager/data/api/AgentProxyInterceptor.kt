package dev.chr0nzz.traefikmanager.data.api

import okhttp3.Interceptor
import okhttp3.Response

class AgentProxyInterceptor(private val agentId: String?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (agentId.isNullOrEmpty()) return chain.proceed(request)

        val path = request.url.encodedPath
        val index = path.indexOf(API_MARKER)
        if (index == -1) return chain.proceed(request)

        // A call that already names an agent is a question for the hub about that agent, not a
        // call to the agent: Git backup on a host-backed agent lives in the hub's repo, on the
        // agent's own branch (core/git.py:194-196). Proxying it would reach the wrong repo.
        if (request.url.queryParameter(AGENT_QUERY) != null) return chain.proceed(request)

        val prefix = path.substring(0, index)
        val tail = path.substring(index + API_MARKER.length)
        if (!shouldProxy(tail)) return chain.proceed(request)

        val proxied = request.url.newBuilder()
            .encodedPath("$prefix/api/agents/proxy/$agentId/$tail")
            .build()
        return chain.proceed(request.newBuilder().url(proxied).build())
    }

    private fun shouldProxy(tail: String): Boolean {
        if (tail.substringBefore('/') in PROXIED) return true
        // The route list comes from /api/agents/{id}/routes, but the raw YAML editor has no such
        // sibling: unproxied it would read and overwrite the host's config file while an agent is
        // selected. The agent serves this exact path (agent/main.go:272-276).
        return tail.startsWith("routes/") && tail.endsWith("/raw")
    }

    private companion object {
        const val API_MARKER = "/api/"
        const val AGENT_QUERY = "agent_id"
        val PROXIED = setOf("traefik", "backups", "backup", "restore", "crowdsec", "static", "configs")
    }
}
