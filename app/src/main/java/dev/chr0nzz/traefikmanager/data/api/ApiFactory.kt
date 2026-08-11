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

    fun retrofit(baseUrl: String, apiKey: String?): Retrofit {
        val authedClient = client.newBuilder()
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("X-Requested-With", "fetch")
                if (!apiKey.isNullOrEmpty()) {
                    builder.header("X-Api-Key", apiKey)
                }
                chain.proceed(builder.build())
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .client(authedClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    fun managerApi(baseUrl: String, apiKey: String?): ManagerApi =
        retrofit(baseUrl, apiKey).create(ManagerApi::class.java)
}
