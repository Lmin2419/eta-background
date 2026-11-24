/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * 来源：io.github.itoseo:coui 0.0.1-alpha11（包名 com.itos.coui），胖小胖把液态玻璃用到的部分搬进仓库，
 * 这一行取自原 topappbar/TopAppBarSurface.kt（胖小胖不再用旧库顶栏，这里始终为 null），只改了包名（见 etax/third_party/coui/NOTICE.md）。
 */
package io.github.mangi.eta.thirdparty.coui.topappbar

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.mangi.eta.thirdparty.coui.backdrop.CouiBackdrop

internal val LocalCouiToolbarBackdrop = staticCompositionLocalOf<CouiBackdrop?> { null }
