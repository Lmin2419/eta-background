package io.github.mangi.eta.agent.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

internal object CalendarPermissions {
    val requested = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun granted(context: Context, write: Boolean): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            (!write ||
                context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) ==
                    PackageManager.PERMISSION_GRANTED)
}
