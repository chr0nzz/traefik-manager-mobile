package dev.chr0nzz.traefikmanager.push

import dev.chr0nzz.traefikmanager.data.model.PushFailure
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * Where a UnifiedPush distributor delivers to.
 *
 * The distributor hands over the raw body the server posted, which for a Generic JSON channel is
 * the notification itself, so nothing here has to know about ntfy or whatever else is carrying it.
 */
class TmPushService : PushService() {

    /** Called again whenever the endpoint rotates, so the server is simply pointed at the new one. */
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        PushSyncWorker.sync(this, endpoint.url)
    }

    override fun onMessage(message: PushMessage, instance: String) {
        PushNotifier.show(this, message.content)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        PushSyncWorker.failed(this, PushFailure.describe(reason.name))
    }

    /** The distributor dropped us, so the channel pointing here is dead and should go. */
    override fun onUnregistered(instance: String) {
        PushSyncWorker.remove(this)
    }
}
