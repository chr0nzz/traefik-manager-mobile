package dev.chr0nzz.traefikmanager.data.model

/**
 * The agent install snippet. There is no server endpoint for this - the web builds the same
 * string client-side from the agent's own fields, so the app reproduces that builder.
 */
object AgentCompose {

    private const val KEY_PLACEHOLDER = "<your-api-key>"
    private const val DEFAULT_TRAEFIK_API = "http://traefik:8080"
    private const val DEFAULT_CONFIG_PATH = "/app/config"
    private const val DEFAULT_PORT = "8090"
    private const val DEFAULT_RATE_LIMIT = "300"

    fun envLines(agent: AgentConfig, apiKey: String?): List<String> = buildList {
        add("TMA_API_KEY=${apiKey ?: KEY_PLACEHOLDER}")
        add("TRAEFIK_API_URL=${agent.traefikApiUrl.ifEmpty { DEFAULT_TRAEFIK_API }}")
        add("CONFIG_PATH=${agent.configPath.ifEmpty { DEFAULT_CONFIG_PATH }}")

        if (agent.tmaPort.isNotEmpty() && agent.tmaPort != DEFAULT_PORT) add("TMA_PORT=${agent.tmaPort}")
        if (agent.tmaRateLimit.isNotEmpty() && agent.tmaRateLimit != DEFAULT_RATE_LIMIT) {
            add("TMA_RATE_LIMIT=${agent.tmaRateLimit}")
        }
        if (agent.traefikInsecureSkipVerify) add("TRAEFIK_INSECURE_SKIP_VERIFY=true")
        if (agent.staticConfigPath.isNotEmpty()) add("STATIC_CONFIG_PATH=${agent.staticConfigPath}")
        if (agent.restartMethod.isNotEmpty()) {
            add("RESTART_METHOD=${agent.restartMethod}")
            if (agent.traefikContainer.isNotEmpty()) add("TRAEFIK_CONTAINER=${agent.traefikContainer}")
            if (agent.restartMethod == "proxy" && agent.dockerHost.isNotEmpty()) {
                add("DOCKER_HOST=${agent.dockerHost}")
            }
            if (agent.restartMethod == "poison-pill" && agent.signalFilePath.isNotEmpty()) {
                add("SIGNAL_FILE_PATH=${agent.signalFilePath}")
            }
        }
        if (agent.acmeJsonPath.isNotEmpty()) add("ACME_JSON_PATH=${agent.acmeJsonPath}")
        if (agent.accessLogPath.isNotEmpty()) add("ACCESS_LOG_PATH=${agent.accessLogPath}")
        if (agent.pluginsDir.isNotEmpty()) add("PLUGINS_DIR=${agent.pluginsDir}")
        if (agent.backupDir.isNotEmpty()) add("BACKUP_DIR=${agent.backupDir}")
        if (agent.backupKeepCount.isNotEmpty() && agent.backupKeepCount != "0") {
            add("BACKUP_KEEP_COUNT=${agent.backupKeepCount}")
        }
        if (agent.crowdsecLapiUrl.isNotEmpty()) add("CROWDSEC_LAPI_URL=${agent.crowdsecLapiUrl}")
        if (agent.crowdsecMachineId.isNotEmpty()) add("CROWDSEC_MACHINE_ID=${agent.crowdsecMachineId}")
        if (agent.crowdsecClientCert.isNotEmpty()) add("CROWDSEC_CLIENT_CERT=${agent.crowdsecClientCert}")
        if (agent.crowdsecClientKey.isNotEmpty()) add("CROWDSEC_CLIENT_KEY=${agent.crowdsecClientKey}")
        if (agent.crowdsecCaCert.isNotEmpty()) add("CROWDSEC_CA_CERT=${agent.crowdsecCaCert}")
        if (agent.gitBackupEnabled) {
            add("GIT_BACKUP_ENABLED=true")
            if (agent.gitBackupRepo.isNotEmpty()) add("GIT_BACKUP_REPO=${agent.gitBackupRepo}")
            add("GIT_BACKUP_BRANCH=${agent.gitBackupBranch.ifEmpty { "main" }}")
            if (agent.gitBackupUsername.isNotEmpty()) add("GIT_BACKUP_USERNAME=${agent.gitBackupUsername}")
            add("GIT_BACKUP_AUTO_PUSH=${agent.gitBackupAutoPush}")
        }
    }

    fun volumeLines(agent: AgentConfig): List<String> = buildList {
        val configPath = agent.configPath.ifEmpty { DEFAULT_CONFIG_PATH }
        add("$configPath:$configPath")
        if (agent.staticConfigPath.isNotEmpty()) add("${agent.staticConfigPath}:${agent.staticConfigPath}")
        if (agent.backupDir.isNotEmpty()) add("${agent.backupDir}:/app/backups") else add("tma_backups:/app/backups")
        listOf(agent.acmeJsonPath, agent.accessLogPath, agent.pluginsDir)
            .filter { it.isNotEmpty() }
            .forEach { add("$it:$it:ro") }
        listOf(agent.crowdsecClientCert, agent.crowdsecClientKey, agent.crowdsecCaCert)
            .filter { it.isNotEmpty() }
            .forEach { add("$it:$it:ro") }
        if (agent.restartMethod == "socket") add("/var/run/docker.sock:/var/run/docker.sock:ro")
        if (agent.restartMethod == "poison-pill") add("traefik-signals:/signals")
    }

    fun compose(agent: AgentConfig, apiKey: String?): String = buildString {
        appendLine("services:")
        appendLine("  tma:")
        appendLine("    image: ghcr.io/chr0nzz/traefik-manager-agent:latest")
        appendLine("    container_name: tma")
        appendLine("    restart: unless-stopped")
        appendLine("    ports:")
        appendLine("      - \"${agent.tmaPort.ifEmpty { DEFAULT_PORT }}:${agent.tmaPort.ifEmpty { DEFAULT_PORT }}\"")
        appendLine("    environment:")
        envLines(agent, apiKey).forEach { appendLine("      - $it") }
        appendLine("    volumes:")
        volumeLines(agent).forEach { appendLine("      - $it") }

        val named = buildList {
            if (agent.backupDir.isEmpty()) add("tma_backups:")
            if (agent.restartMethod == "poison-pill") add("traefik-signals:")
        }
        if (named.isNotEmpty()) {
            appendLine("volumes:")
            named.forEach { appendLine("  $it") }
        }
    }.trimEnd()
}
