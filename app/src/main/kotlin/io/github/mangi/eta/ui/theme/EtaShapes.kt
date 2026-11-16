package io.github.mangi.eta.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object EtaShapes {
    val radiusXs = RoundedCornerShape(6.dp)
    val radiusSm = RoundedCornerShape(10.dp)
    val radiusMd = RoundedCornerShape(14.dp)
    val radiusLg = RoundedCornerShape(20.dp)
    val radiusXl = RoundedCornerShape(28.dp)
    val radiusFull = RoundedCornerShape(999.dp)
    /** 玻璃窗口、悬浮侧栏。 */
    val radiusWindow = RoundedCornerShape(36.dp)
    val userBubble = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 8.dp)
}
