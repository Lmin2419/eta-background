package io.github.mangi.eta.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** 任务胶囊和任务菜单里的标题只占一行。 */
class TaskLabelTest {
    @Test fun longTitleIsCutToOneShortLine() {
        assertEquals("帮我做一下本机临时root的方案…", "帮我做一下本机临时root的方案研究。我想获取临时root，而且".taskLabel(16))
    }

    @Test fun onlyTheFirstNonEmptyLineIsUsed() {
        assertEquals("整理周报", "\n  整理周报  \n第二行".taskLabel(16))
    }

    @Test fun shortTitleIsKept() {
        assertEquals("查天气", "查天气".taskLabel(16))
    }
}
