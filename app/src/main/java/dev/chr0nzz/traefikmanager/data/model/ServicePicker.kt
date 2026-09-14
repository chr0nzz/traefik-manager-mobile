package dev.chr0nzz.traefikmanager.data.model

object ServicePicker {
    const val NOOP = "noop@internal"

    fun providerServices(envelope: ServiceEnvelope): ServicesByProtocol = ServicesByProtocol(
        http = names(envelope.http, extra = listOf(NOOP)),
        tcp = names(envelope.tcp),
        udp = names(envelope.udp),
    )

    fun options(own: List<String>, provider: List<String>): List<String> = (own + provider).distinct()

    private fun names(services: List<TraefikService>, extra: List<String> = emptyList()): List<String> =
        (services.mapNotNull { service ->
            val provider = service.provider.ifEmpty { service.name.substringAfter('@', "") }
            service.name.takeIf { it.isNotEmpty() && provider.isNotEmpty() && provider != "file" }
        } + extra)
            .distinct()
            .sortedWith(compareBy({ it.substringAfterLast('@') }, { it }))
}
