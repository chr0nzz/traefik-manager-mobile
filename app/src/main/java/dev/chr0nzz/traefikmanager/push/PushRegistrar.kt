package dev.chr0nzz.traefikmanager.push

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.unifiedpush.android.connector.UnifiedPush

data class PushDistributor(val packageName: String, val label: String)

@Singleton
class PushRegistrar @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun distributors(): List<PushDistributor> = UnifiedPush.getDistributors(context)
        .filterNot { it == context.packageName }
        .map { PushDistributor(packageName = it, label = label(it)) }

    fun current(): String? = UnifiedPush.getAckDistributor(context)

    fun register(distributor: String) {
        UnifiedPush.saveDistributor(context, distributor)
        UnifiedPush.register(context, messageForDistributor = "Traefik Manager events")
    }

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
