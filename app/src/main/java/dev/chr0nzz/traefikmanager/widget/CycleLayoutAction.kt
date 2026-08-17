package dev.chr0nzz.traefikmanager.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * Widgets cannot be swiped: Glance has no pager, and the only Android widget primitive that takes
 * a swipe is the old StackView, which would mean abandoning Glance entirely. So a tap turns the
 * page to the next card you picked, and the dots in the header say which one you are on.
 */
class CycleLayoutAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        var needsFetch = false
        updateAppWidgetState(context, glanceId) { prefs ->
            val config = WidgetConfig.read(prefs)
            val pages = config.pages.size.coerceAtLeast(1)
            val next = ((prefs[WidgetConfig.PAGE] ?: 0) + 1) % pages
            prefs[WidgetConfig.PAGE] = next
            // Servers are fetched when you turn to them, so stacking does not multiply the poll.
            val slot = config.pages.getOrNull(next)
            needsFetch = slot != null &&
                WidgetPayloads.decode(prefs[WidgetConfig.PAYLOAD]).byServer[slot.encode()] == null
        }
        StatusWidget().update(context, glanceId)
        if (needsFetch) WidgetUpdateWorker.refreshNow(context)
    }
}
