package dev.chr0nzz.traefikmanager.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

class CycleLayoutAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        var needsFetch = false
        updateAppWidgetState(context, glanceId) { prefs ->
            val config = WidgetConfig.read(prefs)
            val pages = config.pages.size.coerceAtLeast(1)
            val next = ((prefs[WidgetConfig.PAGE] ?: 0) + 1) % pages
            prefs[WidgetConfig.PAGE] = next
            val slot = config.pages.getOrNull(next)
            needsFetch = slot != null &&
                WidgetPayloads.decode(prefs[WidgetConfig.PAYLOAD]).byServer[slot.encode()] == null
        }
        StatusWidget().update(context, glanceId)
        if (needsFetch) WidgetUpdateWorker.refreshNow(context)
    }
}
