package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.data.model.CertEntry
import dev.chr0nzz.traefikmanager.data.model.CertManageState
import dev.chr0nzz.traefikmanager.data.model.CertRows
import dev.chr0nzz.traefikmanager.data.model.CertUsage
import dev.chr0nzz.traefikmanager.data.model.CertVerdict
import dev.chr0nzz.traefikmanager.ui.certs.CertFilter
import dev.chr0nzz.traefikmanager.ui.certs.CertificatesUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertFilterTest {

    private val now = 1_800_000_000_000L

    private val state = CertificatesUiState(
        certs = CertRows.from(
            listOf(
                CertEntry(resolver = "le", main = "old.example.com", notAfter = "2030-01-01T00:00:00Z"),
                CertEntry(resolver = "gone", main = "lost.example.com", notAfter = "2030-01-01T00:00:00Z"),
                CertEntry(resolver = "le", main = "soon.example.com", notAfter = "2027-01-18T00:00:00Z"),
                CertEntry(resolver = "file", main = "static.example.com"),
            ),
            now,
        ),
        usage = CertUsage(
            unusedKnown = true,
            certs = listOf(
                CertVerdict(main = "old.example.com", resolver = "le", unused = true),
                CertVerdict(main = "lost.example.com", resolver = "gone", orphaned = true),
                CertVerdict(main = "static.example.com", resolver = "file", unused = true),
            ),
        ),
        manage = CertManageState(available = true),
    )

    @Test
    fun `filters follow the server verdicts and expiry`() {
        assertEquals(4, state.visible.size)
        assertEquals(
            listOf("old.example.com", "static.example.com"),
            state.copy(filter = CertFilter.Unused).visible.map { it.main }.sorted(),
        )
        assertEquals(listOf("lost.example.com"), state.copy(filter = CertFilter.NoResolver).visible.map { it.main })
        assertEquals(listOf("soon.example.com"), state.copy(filter = CertFilter.Expiring).visible.map { it.main })
    }

    @Test
    fun `only resolver certificates are removable, and only when removal is available`() {
        assertEquals(listOf("old.example.com"), state.unusedRemovable.map { it.main })
        assertFalse(state.removable(state.certs.first { it.main == "static.example.com" }))
        assertTrue(state.copy(manage = CertManageState()).unusedRemovable.isEmpty())
    }
}
