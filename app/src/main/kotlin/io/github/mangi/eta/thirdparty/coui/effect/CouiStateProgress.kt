/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * 来源：io.github.itoseo:coui 0.0.1-alpha11（包名 com.itos.coui），胖小胖把液态玻璃用到的部分搬进仓库，
 * 只改了包名（见 etax/third_party/coui/NOTICE.md）。
 */
package io.github.mangi.eta.thirdparty.coui.effect

import androidx.compose.runtime.State
import io.github.mangi.eta.thirdparty.coui.motion.CouiSpring

/** StateEffectAnimator 的原始进度与短按最低进度；状态选择和颜色仍由消费者持有。 */
internal class CouiStateProgress(initial: Float = 0f) {
    private var active = initial > 0f
    private var pendingRelease = false
    private var onHidden: (() -> Unit)? = null
    private val spring: CouiSpring = CouiSpring(initial * ProgressMax) {
        if (pendingRelease && spring.value > MinimumPress && spring.value < ProgressMax) {
            pendingRelease = false
            animate(0f)
        }
    }

    val progress: State<Float> = object : State<Float> {
        override val value: Float get() = spring.value / ProgressMax
    }
    val value: Float get() = progress.value

    fun setActive(value: Boolean, minimumPress: Boolean = false, onHidden: (() -> Unit)? = null) {
        this.onHidden = onHidden
        if (active == value) {
            if (!value && !spring.isRunning) onHidden?.invoke()
            return
        }
        active = value
        if (!value && minimumPress && spring.isRunning && spring.value <= MinimumPress) {
            pendingRelease = true
        } else {
            pendingRelease = false
            animate(if (value) ProgressMax else 0f)
        }
    }

    fun dispose() {
        pendingRelease = false
        onHidden = null
        spring.cancel()
    }

    /** 禁用或显式重置清除会话与速度；实例仍可再次进入。 */
    fun reset() {
        active = false
        pendingRelease = false
        onHidden = null
        spring.snapTo(0f)
    }

    private fun animate(target: Float) {
        spring.animateTo(target, response = 0.3f, onEnd = {
            // 掉帧可能直接到终点；此时须等旧帧结束再退场，不能留下未消费的 pending。
            if (pendingRelease) {
                pendingRelease = false
                animate(0f)
            } else if (!active && spring.value == 0f) {
                onHidden?.invoke()
            }
        })
    }

    private companion object {
        const val ProgressMax = 10000f
        const val MinimumPress = 7000f
    }
}
