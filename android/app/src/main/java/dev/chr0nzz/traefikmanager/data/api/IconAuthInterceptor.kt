package dev.chr0nzz.traefikmanager.data.api

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class IconAuthInterceptor @Inject constructor(
    private val apiProvider: ApiProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val ready = apiProvider.state.value as? ApiState.Ready ?: return chain.proceed(request)
        val apiKey = ready.apiKey
        if (apiKey.isNullOrEmpty()) return chain.proceed(request)

        val serverHost = ready.baseUrl.toHttpUrlOrNull()?.host ?: return chain.proceed(request)
        if (!request.url.host.equals(serverHost, ignoreCase = true)) return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header("X-Api-Key", apiKey)
                .header("X-Requested-With", "fetch")
                .build(),
        )
    }
}
