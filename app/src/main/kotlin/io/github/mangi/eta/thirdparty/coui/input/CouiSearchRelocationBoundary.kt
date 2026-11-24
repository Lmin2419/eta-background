/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * 来源：io.github.itoseo:coui 0.0.1-alpha11（包名 com.itos.coui），胖小胖把液态玻璃用到的部分搬进仓库，
 * 只改了包名（见 etax/third_party/coui/NOTICE.md）。
 */
package io.github.mangi.eta.thirdparty.coui.input

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.relocation.bringIntoView
import androidx.compose.ui.unit.toSize

internal val CouiSearchRelocationBoundary: Modifier = SearchRelocationBoundary

private data object SearchRelocationBoundary : ModifierNodeElement<SearchRelocationNode>() {
    override fun create() = SearchRelocationNode()
    override fun update(node: SearchRelocationNode) = Unit
}

private class SearchRelocationNode : Modifier.Node(), BringIntoViewModifierNode {
    override suspend fun bringIntoView(childCoordinates: LayoutCoordinates, boundsProvider: () -> Rect?) {
        if (!childCoordinates.isAttached || boundsProvider() == null) return
        // 输入保留内部滚动; 祖先只需显示固定顶栏, 不能追逐展开时越界的输入坐标而翻页。
        bringIntoView {
            if (isAttached) Rect(Offset.Zero, requireLayoutCoordinates().size.toSize()) else null
        }
    }
}
