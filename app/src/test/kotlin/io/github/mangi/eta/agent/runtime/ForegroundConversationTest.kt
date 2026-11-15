package io.github.mangi.eta.agent.runtime

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 正在看的对话里完成的任务不弹完成横幅；别的对话、应用在后台时照常弹。 */
class ForegroundConversationTest {
    @After fun reset() { ForegroundConversation.id = null }

    @Test fun onlyTheConversationOnScreenIsTreatedAsShowing() {
        ForegroundConversation.id = "c1"
        assertTrue(ForegroundConversation.isShowing("c1"))
        assertFalse(ForegroundConversation.isShowing("c2"))
        assertFalse(ForegroundConversation.isShowing(null))
    }

    @Test fun nothingIsShowingWhenTheAppIsInTheBackground() {
        ForegroundConversation.id = null
        assertFalse(ForegroundConversation.isShowing("c1"))
    }
}
