package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.ChannelKinds
import dev.chr0nzz.traefikmanager.data.model.NotificationChannel
import dev.chr0nzz.traefikmanager.data.model.PluginEntry
import dev.chr0nzz.traefikmanager.data.model.PluginVersions
import dev.chr0nzz.traefikmanager.data.model.TmNotification
import dev.chr0nzz.traefikmanager.data.model.missingFields
import dev.chr0nzz.traefikmanager.data.model.summary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationChannelTest {

    @Test
    fun `every kind the server accepts is offered`() {
        assertEquals(
            listOf(
                "discord", "slack", "ntfy", "generic", "gotify",
                "pushover", "pushbullet", "unifiedpush", "telegram",
            ),
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
        assertEquals(listOf("url"), required["unifiedpush"])
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

class NotificationStampTest {

    @Test
    fun `an epoch is rendered in the reader's own zone`() {
        val at = 1786026664L
        val expected = java.time.Instant.ofEpochSecond(at)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm:ss"))
        assertEquals(expected, TmNotification(ts = "2026-08-12 10:31:04", at = at).stamp)
    }

    @Test
    fun `without an epoch the server's own string is shown`() {
        assertEquals("12 Aug, 10:31:04", TmNotification(ts = "2026-08-12 10:31:04").stamp)
    }

    @Test
    fun `an unparseable stamp from an old server is passed through`() {
        assertEquals("whenever", TmNotification(ts = "whenever").stamp)
    }

    @Test
    fun `relative time only exists when the server sent an epoch`() {
        assertNull(TmNotification(ts = "2026-08-12 10:31:04").since())
    }

    @Test
    fun `relative time counts back from now`() {
        val now = java.time.Instant.ofEpochSecond(1786026664L)
        assertEquals("just now", TmNotification(at = 1786026634L).since(now))
        assertEquals("5 min ago", TmNotification(at = 1786026364L).since(now))
        assertEquals("2 h ago", TmNotification(at = 1786019464L).since(now))
        assertEquals("3 d ago", TmNotification(at = 1785767464L).since(now))
    }

    @Test
    fun `anything older than a week falls back to the date`() {
        val now = java.time.Instant.ofEpochSecond(1786026664L)
        assertNull(TmNotification(at = 1785000000L).since(now))
    }
}

class PluginVersionTest {

    @Test
    fun `a higher patch is an update`() {
        assertEquals(true, PluginVersions.isNewer("v1.4.6", "v1.4.5"))
    }

    @Test
    fun `the same version is not an update`() {
        assertEquals(false, PluginVersions.isNewer("v1.4.5", "v1.4.5"))
    }

    @Test
    fun `an older catalogue entry is not an update`() {
        assertEquals(false, PluginVersions.isNewer("v1.4.4", "v1.4.5"))
    }

    @Test
    fun `a missing v prefix compares the same`() {
        assertEquals(true, PluginVersions.isNewer("1.5.0", "v1.4.9"))
    }

    @Test
    fun `different lengths compare by the parts they have`() {
        assertEquals(true, PluginVersions.isNewer("v1.5", "v1.4.9"))
        assertEquals(false, PluginVersions.isNewer("v1.4", "v1.4.0"))
    }

    @Test
    fun `a pre-release is left alone rather than guessed at`() {
        assertEquals(false, PluginVersions.isNewer("v1.5.0-beta", "v1.4.5"))
        assertEquals(false, PluginVersions.isNewer("v1.5.0", "latest"))
    }

    @Test
    fun `matching is by lowercased module path`() {
        val plugins = listOf(
            PluginEntry(name = "crowdsec", moduleName = "github.com/Org/Plugin", version = "v1.0.0"),
        )
        val updates = PluginVersions.updates(plugins, mapOf("github.com/org/plugin" to "v1.1.0"))
        assertEquals(mapOf("crowdsec" to "v1.1.0"), updates)
    }
}
