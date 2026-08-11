package dev.chr0nzz.traefikmanager.data.api

import dev.chr0nzz.traefikmanager.data.model.AgentHealth
import dev.chr0nzz.traefikmanager.data.model.AgentsResponse
import dev.chr0nzz.traefikmanager.data.model.ApiKeyStatus
import dev.chr0nzz.traefikmanager.data.model.Entrypoint
import dev.chr0nzz.traefikmanager.data.model.ManagerVersion
import dev.chr0nzz.traefikmanager.data.model.OkResponse
import dev.chr0nzz.traefikmanager.data.model.Overview
import dev.chr0nzz.traefikmanager.data.model.ProtoEnvelope
import dev.chr0nzz.traefikmanager.data.model.RoutesResponse
import dev.chr0nzz.traefikmanager.data.model.ToggleRequest
import dev.chr0nzz.traefikmanager.data.model.TraefikVersion
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface TmApi {

    @GET("api/auth/apikey/status")
    suspend fun apiKeyStatus(): ApiKeyStatus

    @GET("api/manager/version")
    suspend fun managerVersion(): ManagerVersion

    @GET("api/traefik/version")
    suspend fun traefikVersion(): TraefikVersion

    @GET("api/traefik/overview")
    suspend fun overview(): Overview

    @GET("api/traefik/entrypoints")
    suspend fun entrypoints(): List<Entrypoint>

    @GET("api/traefik/routers")
    suspend fun routers(): ProtoEnvelope

    @GET("api/traefik/services")
    suspend fun services(): ProtoEnvelope

    @GET("api/traefik/middlewares")
    suspend fun middlewares(): ProtoEnvelope

    @GET("api/routes")
    suspend fun routes(): RoutesResponse

    @GET("api/agents/{agentId}/routes")
    suspend fun agentRoutes(@Path("agentId") agentId: String): RoutesResponse

    @POST("api/routes/{routeId}/toggle")
    suspend fun toggleRoute(
        @Path("routeId", encoded = false) routeId: String,
        @Body body: ToggleRequest,
    ): OkResponse

    @GET("api/agents")
    suspend fun agents(): AgentsResponse

    @GET("api/agents/{agentId}/health")
    suspend fun agentHealth(@Path("agentId") agentId: String): AgentHealth
}
