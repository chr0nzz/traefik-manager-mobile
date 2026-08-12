package dev.chr0nzz.traefikmanager.data.api

import dev.chr0nzz.traefikmanager.data.model.AgentHealth
import dev.chr0nzz.traefikmanager.data.model.AgentsResponse
import dev.chr0nzz.traefikmanager.data.model.ApiKeyStatus
import dev.chr0nzz.traefikmanager.data.model.Entrypoint
import dev.chr0nzz.traefikmanager.data.model.ManagerVersion
import dev.chr0nzz.traefikmanager.data.model.DigestRequest
import dev.chr0nzz.traefikmanager.data.model.HtpasswdRequest
import dev.chr0nzz.traefikmanager.data.model.HtpasswdResponse
import dev.chr0nzz.traefikmanager.data.model.MiddlewareTemplatesResponse
import dev.chr0nzz.traefikmanager.data.model.OkResponse
import dev.chr0nzz.traefikmanager.data.model.TemplateBody
import dev.chr0nzz.traefikmanager.data.model.PingResult
import dev.chr0nzz.traefikmanager.data.model.RawRoute
import dev.chr0nzz.traefikmanager.data.model.RawRouteSave
import dev.chr0nzz.traefikmanager.data.model.Overview
import dev.chr0nzz.traefikmanager.data.model.ProtoEnvelope
import dev.chr0nzz.traefikmanager.data.model.RoutesResponse
import dev.chr0nzz.traefikmanager.data.model.ServiceEnvelope
import dev.chr0nzz.traefikmanager.data.model.CertResolversResponse
import dev.chr0nzz.traefikmanager.data.model.AddDecisionRequest
import dev.chr0nzz.traefikmanager.data.model.CertsResponse
import dev.chr0nzz.traefikmanager.data.model.CsAlert
import dev.chr0nzz.traefikmanager.data.model.CsDecision
import dev.chr0nzz.traefikmanager.data.model.GeoLookupRequest
import dev.chr0nzz.traefikmanager.data.model.GeoLookupResponse
import dev.chr0nzz.traefikmanager.data.model.GeoStatus
import dev.chr0nzz.traefikmanager.data.model.LogsResponse
import dev.chr0nzz.traefikmanager.data.model.PluginsResponse
import dev.chr0nzz.traefikmanager.data.model.DashboardConfig
import dev.chr0nzz.traefikmanager.data.model.UiPrefsResponse
import dev.chr0nzz.traefikmanager.data.model.ServerSettings
import dev.chr0nzz.traefikmanager.data.model.StaticConfigResponse
import dev.chr0nzz.traefikmanager.data.model.ToggleRequest
import dev.chr0nzz.traefikmanager.data.model.TraefikVersion
import dev.chr0nzz.traefikmanager.data.model.ConfigsResponse
import dev.chr0nzz.traefikmanager.data.model.TlsOptionProfile
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

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

    @GET("api/crowdsec/decisions")
    suspend fun crowdSecDecisions(@Query("full") full: String? = null): Response<List<CsDecision>>

    @GET("api/crowdsec/alerts")
    suspend fun crowdSecAlerts(): Response<List<CsAlert>>

    @POST("api/crowdsec/decisions")
    suspend fun crowdSecAddDecision(@Body body: AddDecisionRequest): Response<OkResponse>

    @DELETE("api/crowdsec/decisions/{id}")
    suspend fun crowdSecDeleteDecision(@Path("id") id: Long): Response<OkResponse>

    @GET("api/traefik/logs")
    suspend fun logs(@Query("lines") lines: Int): LogsResponse

    @GET("api/geoip/status")
    suspend fun geoStatus(): GeoStatus

    @POST("api/geoip/lookup")
    suspend fun geoLookup(@Body body: GeoLookupRequest): GeoLookupResponse

    @GET("api/traefik/certs")
    suspend fun certs(): CertsResponse

    @GET("api/traefik/plugins")
    suspend fun plugins(): PluginsResponse

    @GET("api/traefik/services")
    suspend fun services(): ServiceEnvelope

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

    @POST("save")
    suspend fun saveRoute(@Body body: RequestBody): OkResponse

    @POST("delete/{routeId}")
    suspend fun deleteRoute(
        @Path("routeId", encoded = false) routeId: String,
        @Body body: RequestBody,
    ): OkResponse

    @POST("save-middleware")
    suspend fun saveMiddleware(@Body body: RequestBody): OkResponse

    @POST("delete-middleware/{name}")
    suspend fun deleteMiddleware(
        @Path("name", encoded = false) name: String,
        @Body body: RequestBody,
    ): OkResponse

    @GET("api/mw/templates")
    suspend fun middlewareTemplates(): MiddlewareTemplatesResponse

    @POST("api/mw/templates")
    suspend fun createMiddlewareTemplate(@Body body: TemplateBody): OkResponse

    @PUT("api/mw/templates/{id}")
    suspend fun updateMiddlewareTemplate(
        @Path("id") id: String,
        @Body body: TemplateBody,
    ): OkResponse

    @DELETE("api/mw/templates/{id}")
    suspend fun deleteMiddlewareTemplate(@Path("id") id: String): OkResponse

    @POST("api/tools/htpasswd")
    suspend fun htpasswd(@Body body: HtpasswdRequest): HtpasswdResponse

    @POST("api/tools/digestauth")
    suspend fun digestAuth(@Body body: DigestRequest): HtpasswdResponse

    @GET("api/routes/{routeId}/raw")
    suspend fun routeRaw(@Path("routeId", encoded = false) routeId: String): RawRoute

    @POST("api/routes/{routeId}/raw")
    suspend fun saveRouteRaw(
        @Path("routeId", encoded = false) routeId: String,
        @Body body: RawRouteSave,
    ): OkResponse

    @GET("api/ping")
    suspend fun ping(
        @Query("url") url: String,
        @Query("fallback") fallback: String? = null,
    ): PingResult

    @GET("api/agents/{agentId}/cert-resolvers")
    suspend fun agentCertResolvers(@Path("agentId") agentId: String): CertResolversResponse

    @GET("api/static/config")
    suspend fun staticConfig(): StaticConfigResponse

    @GET("api/settings/ui")
    suspend fun uiPrefs(): UiPrefsResponse

    @GET("api/dashboard/config")
    suspend fun dashboardConfig(@Query("server") server: String? = null): DashboardConfig

    @GET("api/settings")
    suspend fun settings(): ServerSettings

    @GET("api/configs")
    suspend fun configs(): ConfigsResponse

    @GET("api/tls-options")
    suspend fun tlsOptions(@Query("server") server: String? = null): List<TlsOptionProfile>

    @GET("api/agents")
    suspend fun agents(): AgentsResponse

    @GET("api/agents/{agentId}/health")
    suspend fun agentHealth(@Path("agentId") agentId: String): AgentHealth
}
