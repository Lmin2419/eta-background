package io.github.mangi.eta.agent.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

internal object LocalNetworkPermission {
    enum class AccessState {
        NOT_REQUIRED,
        GRANTED,
        DENIED,
    }

    fun accessState(context: Context): AccessState {
        if (Build.VERSION.SDK_INT < 37) return AccessState.NOT_REQUIRED
        return if (context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) {
            AccessState.GRANTED
        } else {
            AccessState.DENIED
        }
    }
}
