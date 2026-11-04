package io.github.mangi.eta.agent.browser

import android.graphics.Bitmap
import android.graphics.Color

internal object BrowserCaptureReadiness {
    /** Exact scan avoids mistaking sparse text, a small control, or a corner image for a blank page. */
    fun isUniformBlank(bitmap: Bitmap): Boolean {
        val first = bitmap.getPixel(0, 0)
        val channels = listOf(Color.red(first), Color.green(first), Color.blue(first))
        val blankColor = Color.alpha(first) == 0 || channels.all { it >= 248 } || channels.all { it <= 7 }
        if (!blankColor) return false
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            if (row.any { it != first }) return false
        }
        return true
    }
}
