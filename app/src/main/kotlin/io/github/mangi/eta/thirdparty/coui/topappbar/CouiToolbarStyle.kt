/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * 来源：io.github.itoseo:coui 0.0.1-alpha11（包名 com.itos.coui），胖小胖把液态玻璃用到的部分搬进仓库，
 * 只改了包名（见 etax/third_party/coui/NOTICE.md）。
 */
package io.github.mangi.eta.thirdparty.coui.topappbar

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import io.github.mangi.eta.thirdparty.coui.effect.CouiFloatingMaterial
import io.github.mangi.eta.thirdparty.coui.effect.CouiFloatingShadow

data class CouiToolbarStyle(
    val dark: Boolean,
    val circleShape: Shape,
    val capsuleShape: Shape,
    val material: (capsule: Boolean) -> CouiFloatingMaterial,
    val shadow: (capsule: Boolean) -> CouiFloatingShadow,
    val contentColor: Color,
    val textStyle: TextStyle,
    val cursorColor: Color,
    val contourGlowEnabled: Boolean,
)

internal val LocalCouiToolbarStyle = staticCompositionLocalOf<CouiToolbarStyle> {
    error("顶栏样式必须由组件入口提供")
}
