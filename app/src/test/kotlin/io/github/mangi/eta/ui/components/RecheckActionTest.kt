package io.github.mangi.eta.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「复核报错再试」只在回答像是放弃了的时候出现。 */
class RecheckActionTest {
    @Test fun answersThatGiveUpOfferTheRecheck() {
        assertTrue("试了几种写法都报错，这个功能在当前环境里无法实现。".looksLikeGaveUp())
        assertTrue("安装依赖失败，我没能找到可用的镜像。".looksLikeGaveUp())
        assertTrue("I tried twice but the build failed and I'm unable to continue.".looksLikeGaveUp())
    }

    @Test fun ordinaryOrSolvedAnswersDoNot() {
        assertFalse("本周三个项目的进展如下：手机端改版、电脑同步、排错改进。".looksLikeGaveUp())
        assertFalse("刚才的报错是引号没转义，已修复，测试全部通过。".looksLikeGaveUp())
        assertFalse("The first run failed on a typo; fixed it and all tests pass now.".looksLikeGaveUp())
    }

    @Test fun onlyTheEndingCounts() {
        val early = "开头提到一次失败的尝试。" + "后面是正常的长篇说明。".repeat(80)
        assertFalse(early.looksLikeGaveUp())
    }
}
