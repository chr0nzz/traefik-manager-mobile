package dev.chr0nzz.traefikmanager

import dev.chr0nzz.traefikmanager.ui.nav.TmDestination
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NavGraphTest {

    @Test
    fun `every destination the drawer can offer is registered in the nav graph`() {
        val source = File("src/main/java/dev/chr0nzz/traefikmanager/ui/nav/TmApp.kt").readText()
        val missing = TmDestination.entries.filter { destination ->
            val byConstant = "TmDestination.${destination.name}.route"
            !source.contains(byConstant) && !source.contains("\"${destination.route}\"")
        }
        assertTrue("no composable registered for $missing", missing.isEmpty())
    }
}
