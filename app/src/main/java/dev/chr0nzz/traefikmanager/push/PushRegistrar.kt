package dev.chr0nzz.traefikmanager.push

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.unifiedpush.android.connector.UnifiedPush

/** A distributor the phone already has, and the name a person would recognise it by. */
data class PushDistributor(val packageName: String, val label: String)

/**
 * The UnifiedPush side of push: which distributor apps are installed, and registering with one.
 *
 * A distributor is a separate app the user already runs, usually ntfy, which holds the one socket
 * the phone keeps open and hands each app its own endpoint. Nothing here reaches Google.
 */
@Singleton
class PushRegistrar @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun distributors(): List<PushDistributor> = UnifiedPush.getDistributors(context)
        .filterNot { it == context.packageName }
        .map { PushDistributor(packageName = it, label = label(it)) }

    /** The distributor currently registered with, or null if there is none. */
    fun current(): String? = UnifiedPush.getAckDistributor(context)

    fun register(distributor: String) {
        UnifiedPush.saveDistributor(context, distributor)
        UnifiedPush.register(context, messageForDistributor = "Traefik Manager events")
    }

    /**
     * Ask again for the endpoint we already have. The distributor can be uninstalled or reset
     * behind the app's back, and re-registering is how the library says to find that out.
     */
    fun refresh() {
        if (current() == null) return
        UnifiedPush.register(context, messageForDistributor = "Traefik Manager events")
    }

    fun unregister() {
        UnifiedPush.unregister(context)
        UnifiedPush.removeDistributor(context)
    }

    private fun label(packageName: String): String = runCatching {
        val manager = context.packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}
