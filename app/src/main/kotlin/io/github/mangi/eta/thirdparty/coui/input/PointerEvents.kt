/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * 来源：io.github.itoseo:coui 0.0.1-alpha11（包名 com.itos.coui），胖小胖把液态玻璃用到的部分搬进仓库，
 * 只改了包名（见 etax/third_party/coui/NOTICE.md）。
 */
package io.github.mangi.eta.thirdparty.coui.input

import androidx.compose.ui.input.pointer.PointerEvent

// Compose 合成的 CANCEL 没有平台事件, 且将上一帧原地转为已消费的抬手。
internal fun PointerEvent.isCouiTouchCancelled(): Boolean =
    motionEvent == null && changes.any { it.previousPressed } && changes.all {
        !it.pressed && (!it.previousPressed ||
            (it.isConsumed && it.uptimeMillis == it.previousUptimeMillis &&
                it.position == it.previousPosition))
    }
