/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * 来源：io.github.itoseo:coui 0.0.1-alpha11（包名 com.itos.coui），胖小胖把液态玻璃用到的部分搬进仓库，
 * 只改了包名（见 etax/third_party/coui/NOTICE.md）。
 */
package io.github.mangi.eta.thirdparty.coui.input

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role

private class MovingActionCoordinates {
    var value: LayoutCoordinates? = null
}

/** 动画中的按钮按窗口坐标判定拖动，避免按钮自身位移取消已命中的点击。 */
@Composable
internal fun Modifier.couiMovingActionClick(
    source: MutableInteractionSource,
    enabled: Boolean,
    animating: Boolean,
    onClick: () -> Unit,
): Modifier {
    val coordinates = remember { MovingActionCoordinates() }
    val moving = rememberUpdatedState(animating)
    val click = rememberUpdatedState(onClick)
    return onGloballyPositioned { coordinates.value = it }
        .pointerInput(enabled, source) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(pass = PointerEventPass.Initial)
                if (!moving.value) return@awaitEachGesture
                val layout = coordinates.value?.takeIf { it.isAttached } ?: return@awaitEachGesture
                val origin = layout.localToWindow(down.position)
                val press = PressInteraction.Press(down.position)
                source.tryEmit(press)
                down.consume()
                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.isCouiTouchCancelled() || event.changes.size != 1) break
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val current = coordinates.value?.takeIf { it.isAttached } ?: break
                        if (change.isConsumed ||
                            (current.localToWindow(change.position) - origin).getDistance() > viewConfiguration.touchSlop) break
                        if (!change.pressed) {
                            change.consume()
                            source.tryEmit(PressInteraction.Release(press))
                            released = true
                            click.value()
                            break
                        }
                    }
                } finally {
                    if (!released) source.tryEmit(PressInteraction.Cancel(press))
                }
            }
        }
        .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
}
