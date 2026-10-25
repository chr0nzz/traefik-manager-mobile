package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.ServiceEnvelope
import dev.chr0nzz.traefikmanager.data.model.ServicePicker
import dev.chr0nzz.traefikmanager.data.model.TraefikService
import org.junit.Assert.assertEquals
import org.junit.Test

class ServicePickerTest {

    private val envelope = ServiceEnvelope(
        http = listOf(
            TraefikService(name = "web@file", provider = "file"),
            TraefikService(name = "whoami@docker", provider = "docker"),
            TraefikService(name = "api@internal", provider = "internal"),
            TraefikService(name = "app@docker"),
            TraefikService(name = "bare"),
        ),
        tcp = listOf(TraefikService(name = "db@docker", provider = "docker")),
    )

    @Test
    fun `live services from other providers are offered, grouped by provider`() {
        val picked = ServicePicker.providerServices(envelope)
        assertEquals(listOf("app@docker", "whoami@docker", "api@internal", "noop@internal"), picked.http)
        assertEquals(listOf("db@docker"), picked.tcp)
        assertEquals(emptyList<String>(), picked.udp)
    }

    @Test
    fun `noop is offered for http even when Traefik is unreachable`() {
        assertEquals(listOf("noop@internal"), ServicePicker.providerServices(ServiceEnvelope()).http)
    }

    @Test
    fun `the file services come first and duplicates collapse`() {
        assertEquals(
            listOf("web", "api", "whoami@docker", "noop@internal"),
            ServicePicker.options(listOf("web", "api"), listOf("whoami@docker", "noop@internal", "web")),
        )
    }
}
