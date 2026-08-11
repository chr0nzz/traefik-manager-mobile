package dev.chr0nzz.traefikmanager.data.api

import dev.chr0nzz.traefikmanager.data.model.ApiKeyStatus
import dev.chr0nzz.traefikmanager.data.model.ManagerVersion
import retrofit2.http.GET

interface ManagerApi {

    @GET("api/manager/version")
    suspend fun managerVersion(): ManagerVersion

    @GET("api/auth/apikey/status")
    suspend fun apiKeyStatus(): ApiKeyStatus
}
