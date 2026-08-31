package dev.chr0nzz.traefikmanager.push

import dev.chr0nzz.traefikmanager.data.model.PushFailure
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

class TmPushService : PushService() {

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        PushSyncWorker.sync(this, endpoint.url)
    }

    override fun onMessage(message: PushMessage, instance: String) {
        PushNotifier.show(this, message.content)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        PushSyncWorker.failed(this, PushFailure.describe(reason.name))
    }

    override fun onUnregistered(instance: String) {
        PushSyncWorker.remove(this)
    }
}
