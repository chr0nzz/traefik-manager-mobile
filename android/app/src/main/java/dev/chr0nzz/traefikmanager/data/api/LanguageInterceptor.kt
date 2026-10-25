package dev.chr0nzz.traefikmanager.data.api

import okhttp3.Interceptor
import okhttp3.Response

class LanguageInterceptor(private val tag: () -> String?) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val language = tag()
        if (language.isNullOrEmpty() || request.url.queryParameter(LANGUAGE_QUERY) != null) {
            return chain.proceed(request)
        }
        val url = request.url.newBuilder().addQueryParameter(LANGUAGE_QUERY, language).build()
        return chain.proceed(request.newBuilder().url(url).build())
    }

    private companion object {
        const val LANGUAGE_QUERY = "lang"
    }
}
