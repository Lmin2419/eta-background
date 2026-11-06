package io.github.mangi.eta.ui.components

import android.os.SystemClock
import android.view.MotionEvent

/** Goes through Compose pointer/nested-scroll dispatch, never writes a scroll state's offset. */
internal fun Stage3TestPanel.dragFinger(fromX: Float = 400f, fromY: Float = 1400f,
    toX: Float = fromX, toY: Float = 400f, settleFrames: Int = 30) {
    val down = SystemClock.uptimeMillis()
    touch(MotionEvent.ACTION_DOWN, fromX, fromY, down)
    advance()
    for (step in 1..20) {
        touch(MotionEvent.ACTION_MOVE, fromX + (toX - fromX) * step / 20,
            fromY + (toY - fromY) * step / 20, down)
        advance()
    }
    touch(MotionEvent.ACTION_UP, toX, toY, down)
    advance(settleFrames)
}

internal fun Stage3TestPanel.tapFinger(x: Float, y: Float) {
    val down = SystemClock.uptimeMillis()
    touch(MotionEvent.ACTION_DOWN, x, y, down); advance()
    touch(MotionEvent.ACTION_UP, x, y, down); advance(30)
}
