package dev.chr0nzz.traefikmanager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Agent(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    @SerialName("cert_resolver") val certResolver: String = "",
    val domains: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String = "",
    /** Empty when the agent has never had tabs configured, which means "show everything". */
    @SerialName("visible_tabs") val visibleTabs: Map<String, Boolean> = emptyMap(),
    /** The only reliable per-agent CrowdSec signal; the host's crowdsec_enabled says nothing about it. */
    @SerialName("crowdsec_lapi_url") val crowdsecLapiUrl: String = "",
) {
    fun tabVisible(tab: String): Boolean = visibleTabs[tab] ?: true

    val crowdsecConfigured: Boolean get() = crowdsecLapiUrl.isNotEmpty()
}

@Serializable
data class AgentsResponse(
    val agents: List<Agent> = emptyList(),
)

@Serializable
data class AgentHealth(
    val ok: Boolean = false,
    val version: String? = null,
    @SerialName("latency_ms") val latencyMs: Int? = null,
    val error: String? = null,
)

data class ServerTarget(
    val id: String?,
    val name: String,
) {
    val isHost: Boolean get() = id == null

    companion object {
        val Host = ServerTarget(null, "Host")
    }
}

/** Every field the agent edit sheet and the install snippet need. All defaulted: POST and GET return different key sets. */
@Serializable
data class AgentConfig(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    @SerialName("api_key") val apiKey: String = "",
    @SerialName("api_key_raw") val apiKeyRaw: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("traefik_api_url") val traefikApiUrl: String = "",
    @SerialName("traefik_insecure_skip_verify") val traefikInsecureSkipVerify: Boolean = false,
    @SerialName("cert_resolver") val certResolver: String = "",
    @SerialName("config_path") val configPath: String = "",
    @SerialName("backup_dir") val backupDir: String = "",
    @SerialName("backup_keep_count") @Serializable(with = LenientStringSerializer::class)
    val backupKeepCount: String = "",
    @SerialName("static_config_path") val staticConfigPath: String = "",
    @SerialName("acme_json_path") val acmeJsonPath: String = "",
    @SerialName("access_log_path") val accessLogPath: String = "",
    @SerialName("plugins_dir") val pluginsDir: String = "",
    @SerialName("restart_method") val restartMethod: String = "",
    @SerialName("traefik_container") val traefikContainer: String = "",
    @SerialName("docker_host") val dockerHost: String = "",
    @SerialName("signal_file_path") val signalFilePath: String = "",
    @SerialName("crowdsec_lapi_url") val crowdsecLapiUrl: String = "",
    @SerialName("crowdsec_machine_id") val crowdsecMachineId: String = "",
    @SerialName("crowdsec_client_cert") val crowdsecClientCert: String = "",
    @SerialName("crowdsec_client_key") val crowdsecClientKey: String = "",
    @SerialName("crowdsec_ca_cert") val crowdsecCaCert: String = "",
    @SerialName("git_backup_enabled") val gitBackupEnabled: Boolean = false,
    @SerialName("git_backup_repo") val gitBackupRepo: String = "",
    @SerialName("git_backup_branch") val gitBackupBranch: String = "main",
    @SerialName("git_backup_username") val gitBackupUsername: String = "",
    @SerialName("git_backup_auto_push") val gitBackupAutoPush: Boolean = true,
    @SerialName("tma_port") @Serializable(with = LenientStringSerializer::class) val tmaPort: String = "",
    @SerialName("tma_rate_limit") @Serializable(with = LenientStringSerializer::class) val tmaRateLimit: String = "",
    val domains: List<String> = emptyList(),
    @SerialName("visible_tabs") val visibleTabs: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class AgentMutationResponse(
    val ok: Boolean = false,
    val agent: AgentConfig? = null,
    val error: String? = null,
) {
    /** The raw key is nested under `agent`, not top-level - the web reads the wrong place and always fails. */
    val rawKey: String? get() = agent?.apiKeyRaw?.takeIf { it.isNotEmpty() }
}

@Serializable
data class CreateAgentRequest(
    val name: String,
    val url: String,
)
