package dev.chr0nzz.traefikmanager.data.api

import dev.chr0nzz.traefikmanager.data.model.AgentHealth
import dev.chr0nzz.traefikmanager.data.model.AgentMutationResponse
import dev.chr0nzz.traefikmanager.data.model.AgentConfigsResponse
import dev.chr0nzz.traefikmanager.data.model.AgentsResponse
import dev.chr0nzz.traefikmanager.data.model.ChannelListResponse
import dev.chr0nzz.traefikmanager.data.model.ChannelPayload
import dev.chr0nzz.traefikmanager.data.model.ChannelSaveResponse
import dev.chr0nzz.traefikmanager.data.model.ChannelTestResult
import dev.chr0nzz.traefikmanager.data.model.MarkReadRequest
import dev.chr0nzz.traefikmanager.data.model.NotificationState
import dev.chr0nzz.traefikmanager.data.model.CreateAgentRequest
import dev.chr0nzz.traefikmanager.data.model.ApiKeyStatus
import dev.chr0nzz.traefikmanager.data.model.AuthActionResponse
import dev.chr0nzz.traefikmanager.data.model.ChangePasswordRequest
import dev.chr0nzz.traefikmanager.data.model.GenerateKeyRequest
import dev.chr0nzz.traefikmanager.data.model.GenerateKeyResponse
import dev.chr0nzz.traefikmanager.data.model.OtpStatus
import dev.chr0nzz.traefikmanager.data.model.RevokeKeyRequest
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
import dev.chr0nzz.traefikmanager.data.model.ClientIpDiagnostic
import dev.chr0nzz.traefikmanager.data.model.CsAlert
import dev.chr0nzz.traefikmanager.data.model.CsDecision
import dev.chr0nzz.traefikmanager.data.model.GeoLookupRequest
import dev.chr0nzz.traefikmanager.data.model.GeoLookupResponse
import dev.chr0nzz.traefikmanager.data.model.GeoStatus
import dev.chr0nzz.traefikmanager.data.model.LogsResponse
import dev.chr0nzz.traefikmanager.data.model.PluginsResponse
import dev.chr0nzz.traefikmanager.data.model.DashboardConfig
import dev.chr0nzz.traefikmanager.data.model.UiPrefsRequest
import dev.chr0nzz.traefikmanager.data.model.UiPrefsResponse
import dev.chr0nzz.traefikmanager.data.model.SaveSettingsResponse
import dev.chr0nzz.traefikmanager.data.model.ServerSettings
import dev.chr0nzz.traefikmanager.data.model.DeleteNotificationRequest
import dev.chr0nzz.traefikmanager.data.model.AgentBackupsResponse
import dev.chr0nzz.traefikmanager.data.model.CreateBackupResponse
import dev.chr0nzz.traefikmanager.data.model.GitCommit
import dev.chr0nzz.traefikmanager.data.model.GitDiff
import dev.chr0nzz.traefikmanager.data.model.GitPushRequest
import dev.chr0nzz.traefikmanager.data.model.GitStatus
import dev.chr0nzz.traefikmanager.data.model.HubBackup
import dev.chr0nzz.traefikmanager.data.model.RestoreResponse
import dev.chr0nzz.traefikmanager.data.model.TmNotification
import dev.chr0nzz.traefikmanager.data.model.WebhookTestRequest
import dev.chr0nzz.traefikmanager.data.model.WebhookTestResult
import dev.chr0nzz.traefikmanager.data.model.TestConnectionRequest
import dev.chr0nzz.traefikmanager.data.model.TestConnectionResult
import dev.chr0nzz.traefikmanager.data.model.StaticConfigResponse
import dev.chr0nzz.traefikmanager.data.model.ToggleRequest
import dev.chr0nzz.traefikmanager.data.model.TraefikVersion
import dev.chr0nzz.traefikmanager.data.model.ConfigsResponse
import dev.chr0nzz.traefikmanager.data.model.TlsOptionProfile
import kotlinx.serialization.json.JsonObject
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

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): AuthActionResponse

    @GET("api/auth/otp/status")
    suspend fun otpStatus(): OtpStatus

    @POST("api/auth/otp/disable")
    suspend fun disableOtp(): AuthActionResponse

    @POST("api/auth/apikey/generate")
    suspend fun generateApiKey(@Body body: GenerateKeyRequest): GenerateKeyResponse

    @POST("api/auth/apikey/revoke")
    suspend fun revokeApiKey(@Body body: RevokeKeyRequest): AuthActionResponse

    @GET("api/diagnostics/client-ip")
    suspend fun clientIpDiagnostic(): ClientIpDiagnostic

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

    /** Merges rather than replaces, so one key at a time is safe here (app.py:3432-3433). */
    @POST("api/settings/ui")
    suspend fun saveUiPrefs(@Body body: UiPrefsRequest): UiPrefsResponse

    @GET("api/dashboard/config")
    suspend fun dashboardConfig(@Query("server") server: String? = null): DashboardConfig

    /**
     * Read-modify-write over the whole document: the hub replaces custom_groups and
     * route_overrides with exactly what this body carries (app.py:3926-3942).
     */
    @POST("api/dashboard/config")
    suspend fun saveDashboardConfig(
        @Body body: DashboardConfig,
        @Query("server") server: String? = null,
    ): OkResponse

    /** The hub answers a bare array here; an agent answers an object. Pick by active server. */
    @GET("api/backups")
    suspend fun hubBackups(): List<HubBackup>

    @GET("api/backups")
    suspend fun agentBackups(): AgentBackupsResponse

    @POST("api/backup/create")
    suspend fun createBackup(): CreateBackupResponse

    /** Host only: the hub's plain create never touches the static file, an agent's always does. */
    @POST("api/static/backup/create")
    suspend fun createStaticBackup(): CreateBackupResponse

    @POST("api/backup/delete/{filename}")
    suspend fun deleteBackup(@Path("filename") filename: String): OkResponse

    @POST("api/restore/{filename}")
    suspend fun restoreBackup(@Path("filename") filename: String): RestoreResponse

    @POST("api/static/restart")
    suspend fun restartTraefik(): OkResponse

    @GET("api/backup/git/status")
    suspend fun gitStatus(@Query("agent_id") agentId: String? = null): GitStatus

    @GET("api/backup/git/commits")
    suspend fun gitCommits(@Query("agent_id") agentId: String? = null): List<GitCommit>

    @GET("api/backup/git/commit/{sha}/diff")
    suspend fun gitDiff(@Path("sha") sha: String, @Query("agent_id") agentId: String? = null): GitDiff

    @POST("api/backup/git/push")
    suspend fun gitPush(
        @Body body: GitPushRequest,
        @Query("agent_id") agentId: String? = null,
    ): OkResponse

    @POST("api/backup/git/restore/{sha}")
    suspend fun gitRestore(
        @Path("sha") sha: String,
        @Query("agent_id") agentId: String? = null,
    ): OkResponse

    @GET("api/notifications")
    suspend fun notifications(): List<TmNotification>

    @POST("api/notifications/delete")
    suspend fun deleteNotification(@Body body: DeleteNotificationRequest): OkResponse

    @POST("api/notifications/clear")
    suspend fun clearNotifications(): OkResponse

    @GET("api/notifications/state")
    suspend fun notificationState(): NotificationState

    @POST("api/notifications/read")
    suspend fun markNotificationsRead(@Body body: MarkReadRequest): OkResponse

    @POST("api/settings/webhook-test")
    suspend fun testWebhook(@Body body: WebhookTestRequest): WebhookTestResult

    @GET("api/notifications/channels")
    suspend fun notificationChannels(): ChannelListResponse

    @POST("api/notifications/channels")
    suspend fun createNotificationChannel(@Body body: ChannelPayload): ChannelSaveResponse

    @PUT("api/notifications/channels/{id}")
    suspend fun updateNotificationChannel(
        @Path("id") id: String,
        @Body body: ChannelPayload,
    ): ChannelSaveResponse

    @DELETE("api/notifications/channels/{id}")
    suspend fun deleteNotificationChannel(@Path("id") id: String): OkResponse

    @POST("api/notifications/channels/{id}/test")
    suspend fun testNotificationChannel(@Path("id") id: String): ChannelTestResult

    @GET("api/settings")
    suspend fun settings(): ServerSettings

    /** The same document as [settings], untyped, so a save can round-trip keys this app does not model. */
    @GET("api/settings")
    suspend fun settingsRaw(): JsonObject

    @POST("api/settings")
    suspend fun saveSettings(@Body body: JsonObject): SaveSettingsResponse

    @POST("api/settings/test-connection")
    suspend fun testTraefikConnection(@Body body: TestConnectionRequest): TestConnectionResult

    @GET("api/configs")
    suspend fun configs(): ConfigsResponse

    @GET("api/tls-options")
    suspend fun tlsOptions(@Query("server") server: String? = null): List<TlsOptionProfile>

    @GET("api/agents")
    suspend fun agents(): AgentsResponse

    /** The same call, read whole: every configurable field, with secrets redacted to "***". */
    @GET("api/agents")
    suspend fun agentConfigs(): AgentConfigsResponse

    @POST("api/agents")
    suspend fun createAgent(@Body body: CreateAgentRequest): AgentMutationResponse

    @PUT("api/agents/{agentId}")
    suspend fun updateAgent(@Path("agentId") agentId: String, @Body body: JsonObject): OkResponse

    @DELETE("api/agents/{agentId}")
    suspend fun deleteAgent(@Path("agentId") agentId: String): OkResponse

    @POST("api/agents/{agentId}/rotate-key")
    suspend fun rotateAgentKey(@Path("agentId") agentId: String): AgentMutationResponse

    @GET("api/agents/{agentId}/health")
    suspend fun agentHealth(@Path("agentId") agentId: String): AgentHealth
}
