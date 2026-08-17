package dev.chr0nzz.traefikmanager.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import dev.chr0nzz.traefikmanager.MainActivity

/** Refreshes just the widget that was tapped, rather than every one on the home screen. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        WidgetUpdateWorker.refreshNow(context, glanceId)
    }
}

/** Opens the app on whatever the widget is showing. */
class OpenAppAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        var first: WidgetCardType? = null
        var serverId: String? = null
        updateAppWidgetState(context, glanceId) { prefs ->
            first = WidgetCardType.parse(prefs[WidgetConfig.CARDS]).firstOrNull()
            serverId = prefs[WidgetConfig.SERVER_ID]?.takeIf { it.isNotEmpty() }
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DESTINATION, first.destinationRoute)
            serverId?.let { putExtra(EXTRA_SERVER_ID, it) }
        }
        context.startActivity(intent)
    }

    companion object {
        const val EXTRA_DESTINATION = "tm.widget.destination"
        const val EXTRA_SERVER_ID = "tm.widget.server"
    }
}

/** Where a tap lands, so the widget opens the page whose card it was showing. */
val WidgetCardType?.destinationRoute: String
    get() = when (this) {
        null, WidgetCardType.Overview -> "home"
        WidgetCardType.Certs -> "certificates"
        WidgetCardType.Entrypoints -> "home"
        WidgetCardType.Http, WidgetCardType.Stream -> "routes"
        WidgetCardType.Services -> "services"
        WidgetCardType.Middlewares -> "middlewares"
        WidgetCardType.Sources, WidgetCardType.Scenarios,
        WidgetCardType.Paths, WidgetCardType.Bans,
        -> "crowdsec"
    }
