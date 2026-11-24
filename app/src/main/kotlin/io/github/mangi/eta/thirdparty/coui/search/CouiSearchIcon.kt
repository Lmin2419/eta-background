/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * 来源：io.github.itoseo:coui 0.0.1-alpha11（包名 com.itos.coui），胖小胖把液态玻璃用到的部分搬进仓库，
 * 只改了包名（见 etax/third_party/coui/NOTICE.md）。
 */
package io.github.mangi.eta.thirdparty.coui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.thirdparty.coui.theme.CouiTheme

// 搜索组件不依赖可选图标包；图形与 CouiIcons.Outlined.Search 保持一致。
internal object CouiSearchIcon {
    private val variants: Pair<ImageVector, ImageVector> by lazy {
        createVector(Color.Black) to createVector(Color.White)
    }

    val imageVector: ImageVector
        @Composable get() = if (CouiTheme.isDark) variants.second else variants.first

    private fun createVector(tint: Color): ImageVector = ImageVector.Builder(
        "COUI.Search", 20.dp, 20.dp, 20f, 20f, tintColor = tint, autoMirror = false,
    ).apply {
        addPath(PathParser().parsePathString("M2.822,8.191C2.822,5.226 5.226,2.822 8.191,2.822C11.156,2.822 13.56,5.226 13.56,8.191C13.56,11.156 11.156,13.56 8.191,13.56C5.226,13.56 2.822,11.156 2.822,8.191ZM8.191,1.422C4.452,1.422 1.422,4.452 1.422,8.191C1.422,11.929 4.452,14.96 8.191,14.96C9.735,14.96 11.159,14.442 12.298,13.571L17.075,18.348C17.426,18.699 17.996,18.699 18.347,18.348C18.699,17.997 18.699,17.427 18.347,17.075L13.571,12.299C14.442,11.16 14.96,9.736 14.96,8.191C14.96,4.452 11.929,1.422 8.191,1.422Z").toNodes(),
            fill = SolidColor(Color(0xFF000000)), fillAlpha = 1f, stroke = null, strokeAlpha = 1f,
            strokeLineWidth = 0f, strokeLineCap = StrokeCap.Butt, strokeLineJoin = StrokeJoin.Miter,
            pathFillType = PathFillType.EvenOdd)
    }.build()
}
