/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * 来源：io.github.itoseo:coui 0.0.1-alpha11（包名 com.itos.coui），胖小胖把液态玻璃用到的部分搬进仓库，
 * 只改了包名（见 etax/third_party/coui/NOTICE.md）。
 */
package io.github.mangi.eta.thirdparty.coui.backdrop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalCouiBackdrop = staticCompositionLocalOf<CouiBackdrop?> { null }

/** 提供页面正文采样源；宿主仍须将 couiBackdrop 挂到唯一正文节点，并排除其消费者。 */
@Composable
fun ProvideCouiBackdrop(backdrop: CouiBackdrop, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCouiBackdrop provides backdrop, content = content)
}
