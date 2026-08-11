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

        val prefix = path.substring(0, index)
        val tail = path.substring(index + API_MARKER.length)
        if (tail.substringBefore('/') !in PROXIED) return chain.proceed(request)

        val proxied = request.url.newBuilder()
            .encodedPath("$prefix/api/agents/proxy/$agentId/$tail")
            .build()
        return chain.proceed(request.newBuilder().url(proxied).build())
    }

    private companion object {
        const val API_MARKER = "/api/"
        val PROXIED = setOf("traefik", "backups", "backup", "restore", "crowdsec", "static", "configs")
    }
}
