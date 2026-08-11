package dev.chr0nzz.traefikmanager.data.api

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Singleton
class ApiFactory @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {

    fun create(baseUrl: String, apiKey: String?, agentId: String?): TmApi {
        val scoped = client.newBuilder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("X-Requested-With", "fetch")
                if (!apiKey.isNullOrEmpty()) {
                    builder.header("X-Api-Key", apiKey)
                }
                chain.proceed(builder.build())
            }
            .addInterceptor(AgentProxyInterceptor(agentId))
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(scoped)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TmApi::class.java)
    }
}
