package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.ChannelKinds
import dev.chr0nzz.traefikmanager.data.model.NotificationChannel
import dev.chr0nzz.traefikmanager.data.model.missingFields
import dev.chr0nzz.traefikmanager.data.model.summary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationChannelTest {

    @Test
    fun `every kind the server accepts is offered`() {
        assertEquals(
            listOf("discord", "slack", "ntfy", "generic", "gotify", "pushover", "pushbullet", "telegram"),
            ChannelKinds.all.map { it.key },
        )
    }

    @Test
    fun `required slots match the server's map`() {
        val required = ChannelKinds.all.associate { kind -> kind.key to kind.required.map { it.key } }
        assertEquals(listOf("url"), required["discord"])
        assertEquals(listOf("url"), required["ntfy"])
        assertEquals(listOf("url", "token"), required["gotify"])
        assertEquals(listOf("token", "token2"), required["pushover"])
        assertEquals(listOf("token"), required["pushbullet"])
        assertEquals(listOf("token", "token2"), required["telegram"])
    }

    @Test
    fun `a redacted secret counts as filled in`() {
        val channel = NotificationChannel(kind = "gotify", url = "https://gotify.example.com", token = "***")
        assertTrue(channel.missingFields().isEmpty())
    }

    @Test
    fun `missing slots are named by their label`() {
        assertEquals(listOf("App Token", "User Key"), NotificationChannel(kind = "pushover").missingFields())
    }

    @Test
    fun `an empty category list reads as all of them`() {
        assertEquals("All categories", NotificationChannel(kind = "discord").summary())
    }

    @Test
    fun `every category selected also reads as all of them`() {
        val channel = NotificationChannel(kind = "discord", categories = ChannelKinds.categories.map { it.first })
        assertEquals("All categories", channel.summary())
    }

    @Test
    fun `the summary spells out the filters that are not defaults`() {
        val channel = NotificationChannel(
            kind = "gotify",
            categories = listOf("certs", "crowdsec"),
            minSeverity = "warning",
            digest = "daily",
            quietHours = "23:00-07:00",
            breakThrough = true,
        )
        assertEquals(
            "Certificates, CrowdSec · Warning and above · Daily digest · Quiet 23:00-07:00, errors break through",
            channel.summary(),
        )
    }

    @Test
    fun `defaults are left out of the summary`() {
        val channel = NotificationChannel(kind = "discord", categories = listOf("config"))
        assertEquals("Config", channel.summary())
    }
}
