package io.github.mangi.eta.ui.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import io.github.mangi.eta.R

/** Settings remain reachable after granting permission; OEMs may omit the package-specific screen. */
internal object OverlayPermissionSettings {
    enum class Destination { APP_OVERLAY, OVERLAY_LIST, APP_DETAILS, UNAVAILABLE }

    fun open(context: Context) {
        val message = when (launch(context)) {
            Destination.APP_OVERLAY -> null
            Destination.OVERLAY_LIST -> R.string.overlay_settings_choose_app
            Destination.APP_DETAILS -> R.string.overlay_settings_app_details
            Destination.UNAVAILABLE -> R.string.overlay_settings_unavailable
        }
        message?.let { io.github.mangi.eta.ui.components.EtaToastCenter.show(context, it, Toast.LENGTH_LONG) }
    }

    internal fun launch(context: Context): Destination {
        val packageUri = Uri.fromParts("package", context.packageName, null)
        val routes = listOf(
            Destination.APP_OVERLAY to Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri),
            Destination.OVERLAY_LIST to Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Destination.APP_DETAILS to Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        )
        for ((destination, intent) in routes) {
            try {
                // Attempt the standard intent directly; package visibility can hide resolveActivity results.
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return destination
            } catch (_: ActivityNotFoundException) {
                // Try the next standard system entry point.
            } catch (_: SecurityException) {
                // The ROM may protect a particular settings entry point.
            }
        }
        return Destination.UNAVAILABLE
    }
}
