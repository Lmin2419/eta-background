package io.github.mangi.eta.agent.model

import io.github.mangi.eta.agent.terminal.TerminalEnvironment
import io.github.mangi.eta.agent.terminal.TerminalExecutionContext
import io.github.mangi.eta.agent.subagent.SubagentProfile
import io.github.mangi.eta.agent.memory.AgentMemoryContext
import io.github.mangi.eta.agent.skill.SkillContext
import io.github.mangi.eta.agent.skill.SkillIndexEntry
import io.github.mangi.eta.agent.device.RootAccessState
import io.github.mangi.eta.agent.device.RootAccessStatus
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPromptBuilderTest {
    @Test
    fun customProviderPromptsUseOneObservationContractForBothLocations() {
        for (custom in listOf("", "用户自定义语气")) {
            val contents = AgentPromptBuilder.buildSystemMessages(
                modelConfig(custom, terminalTools = false, browserTools = false),
                SkillContext.EMPTY, AgentMemoryContext.DISABLED, rootAvailable = true,
            ).toString()
            assertTrue(contents.contains("内置前台操作和后台虚拟屏操作"))
            assertTrue(contents.contains("后台最多同时操作 2 个不同应用"))
            assertTrue(contents.contains("后台虚拟屏任务可在主屏关闭后继续"))
            assertTrue(contents.contains("前后台共用 observation_id 合同"))
            assertTrue(contents.contains("execution_location="))
            assertFalse(contents.contains("旧 screen_*"))
            assertTrue(contents.contains("后台按键也用 press_key"))
            assertTrue(contents.contains("NO_PROGRESS"))
            assertTrue(contents.contains("未翻到历史、文字被裁切或复制失败不能推断消息被删除"))
            if (custom.isNotBlank()) assertTrue(contents.contains(custom))
        }
    }

    @Test
    fun ordinaryTasksBoundDataExplorationWhileExplicitAnalysisKeepsRootAccess() {
        for (rootAvailable in listOf(false, true)) {
            for (terminalTools in listOf(false, true)) {
                val contents = AgentPromptBuilder.buildSystemMessages(
                    modelConfig("自定义助手", terminalTools, browserTools = false),
                    SkillContext.EMPTY, AgentMemoryContext.DISABLED, rootAvailable,
                ).systemContents().joinToString("\n")

                assertTrue(contents.contains("普通查询、计数和界面操作优先用现成工具或目标 App 界面"))
                assertTrue(contents.contains("不为取数临时安装 Linux 环境或编写数据库二进制解析器"))
                assertFalse(contents.contains("可以主动使用它们定位并只读检查相关应用私有文件与数据库"))
                if (rootAvailable) {
                    assertTrue(contents.contains("已知路径、已知格式执行有界只读查询"))
                    assertTrue(contents.contains("遇到加密、格式不支持或路径不可访问，就结束该取数分支"))
                    assertTrue(contents.contains("只有用户明确要求文件/数据库分析、排障或开发时，才探索"))
                    assertTrue(contents.contains("不修改源数据"))
                } else {
                    assertFalse(contents.contains("可用已公开的 Root Shell/文件工具"))
                    assertTrue(contents.contains("只能使用本轮公开的工具与已确认授权的数据来源"))
                }
            }
        }
    }

    @Test
    fun countingGuidanceKeepsOneTimeRangeAndRequiresContinuousCoverage() {
        val contents = AgentPromptBuilder.buildSystemMessages(
            modelConfig("", terminalTools = false, browserTools = false),
            SkillContext.EMPTY, AgentMemoryContext.DISABLED, rootAvailable = false,
        ).systemContents().joinToString("\n")

        assertTrue(contents.contains("按首次取时与设备时区确定起止时间"))
        assertTrue(contents.contains("后续翻页保持这个区间"))
        assertTrue(contents.contains("重叠条目确认连续覆盖，再累计新条目"))
        assertTrue(contents.contains("缺少重叠就回查缺口"))
        assertTrue(contents.contains("不能只按时间戳去重"))
        assertTrue(contents.contains("相同内容也可能是多条"))
        assertTrue(contents.contains("无法区分的部分标为未确认"))
        assertTrue(contents.contains("start_time/end_time"))
        assertTrue(contents.contains("next_cursor"))
        assertTrue(contents.contains("total_matches 只代表现存匹配通知记录"))
        assertTrue(contents.contains("未经完整性核对不能当作聊天消息总数"))
    }

    @Test
    fun attachedScreenshotsDoNotRequireAnotherReadImageRoundTrip() {
        val contents = AgentPromptBuilder.buildSystemMessages(
            modelConfig("", terminalTools = true, browserTools = false),
            SkillContext.EMPTY, AgentMemoryContext.DISABLED, rootAvailable = true,
        ).systemContents().joinToString("\n")

        assertTrue(contents.contains("screenshot.attached=true 且实际收到图片附件"))
        assertTrue(contents.contains("不再调用 read_image 重读同帧"))
        assertTrue(contents.contains("仅返回路径或 attached=false 不表示已看图"))
        assertTrue(contents.contains("避免每轮先查空树再补图"))
        assertTrue(contents.contains("页面改变后重新判断观察方式"))
        assertFalse(contents.contains("screen_shot"))
        assertTrue(contents.contains("coordinate_contract"))
        assertTrue(contents.contains("只读取 UI 树，不附截图"))
        assertTrue(contents.contains("禁止把新截图与旧节点混用"))
        assertTrue(contents.contains("必须先重新观察，禁止直接重放动作"))
    }

    @Test
    fun rootPromptOnlyDependsOnGrantSoTransientProbeStatesKeepThePrefixStable() {
        fun prompt(state: RootAccessState) = AgentPromptBuilder.buildSystemMessages(
            config = modelConfig("", terminalTools = true, browserTools = false),
            skillContext = SkillContext.EMPTY, memoryContext = AgentMemoryContext.DISABLED,
            rootAvailable = state.isGranted,
        ).toString()
        val ungranted = listOf(
            RootAccessState(isChecking = true),
            RootAccessState(RootAccessStatus.UNKNOWN),
            RootAccessState(RootAccessStatus.TIMED_OUT),
            RootAccessState(RootAccessStatus.DENIED),
            RootAccessState(RootAccessStatus.UNAVAILABLE),
        ).map(::prompt)
        assertEquals(1, ungranted.distinct().size)
        val granted = prompt(RootAccessState(RootAccessStatus.GRANTED, suPresent = true))
        assertEquals(granted, prompt(RootAccessState(RootAccessStatus.GRANTED, suPresent = true, isChecking = true)))
        assertTrue(granted.contains("Root 授权已确认"))
        assertTrue(ungranted.first().contains("这不代表设备不支持 Root"))
        for (contents in ungranted + granted) {
            assertTrue(contents.contains("identity=user 是显式选择"))
            assertTrue(contents.contains("terminal(action=check_root)"))
        }
    }

    @Test
    fun terminalPromptDescribesTheActualRunWorkspaceAndSeparateLifecycles() {
        val messages = AgentPromptBuilder.buildSystemMessages(
            config = modelConfig("", terminalTools = true, browserTools = false),
            skillContext = SkillContext.EMPTY,
            memoryContext = AgentMemoryContext.DISABLED,
            rootAvailable = false,
            executionContext = TerminalExecutionContext(TerminalEnvironment.DEBIAN, null, "/eta/custom workspace"),
        )
        val terminal = messages.systemContents().joinToString("\n")
        assertTrue(terminal.contains("/eta/custom workspace"))
        assertTrue(terminal.contains("debian"))
        assertTrue(terminal.contains("identity=user"))
        assertTrue(terminal.contains("不要用 & 起后台进程"))
        assertTrue(terminal.contains("common_tools"))
        assertTrue(terminal.contains("profiles"))
        assertTrue(terminal.contains("terminal_job"))
        assertTrue(terminal.contains("terminal_daemon"))
        assertTrue(terminal.contains("environment=\"linux\""))
        assertTrue(terminal.contains("同一个物理文件夹"))
        assertTrue(terminal.contains("禁止在两个环境之间重复复制"))
        assertTrue(terminal.contains("实际可见性和可用性以终端或文件工具的执行结果为准"))
        assertFalse(terminal.contains("底层均为 bind mount 的同一个物理文件夹"))
        assertTrue(messages.systemContents().any { it.contains("后台最多同时操作 2 个不同应用") })
        assertTrue(messages.systemContents().any { it.contains("后台虚拟屏任务可在主屏关闭后继续") })
        assertTrue(messages.systemContents().any { it.contains("只有用户明确要求后台执行") })
        assertFalse(terminal.contains("async=true"))
        assertFalse(terminal.contains("read_async_result"))
    }

    @Test
    fun androidLinuxGuidanceIsConsistentAcrossPromptToolSchemasAndCoderProfile() {
        val messages = AgentPromptBuilder.buildSystemMessages(
            config = modelConfig("", terminalTools = true, browserTools = false),
            skillContext = SkillContext.EMPTY,
            memoryContext = AgentMemoryContext.DISABLED,
            rootAvailable = true,
            executionContext = TerminalExecutionContext(TerminalEnvironment.DEBIAN, null, "/eta/workspace"),
        )
        val terminalPrompt = messages.systemContents().single { it.contains("胖小胖内置文件与终端工具") }
        assertTrue(terminalPrompt.contains("environment=android 操作 Android 宿主系统与设备"))
        assertTrue(terminalPrompt.contains("environment=linux 使用设置中选定的完整 Linux 发行版"))
        assertTrue(terminalPrompt.contains("凡涉及代码开发、git 拉取/提交/diff"))
        assertTrue(terminalPrompt.contains("APK 逆向分析"))
        assertTrue(terminalPrompt.contains("两边的 revision 按各自环境独立计算"))

        fun environmentDescriptions(tools: JSONArray): List<String> =
            (0 until tools.length()).map { index ->
                tools.getJSONObject(index)
                    .getJSONObject("function")
                    .getJSONObject("parameters")
                    .getJSONObject("properties")
                    .getJSONObject("environment")
                    .getString("description")
            }
        val descriptions = environmentDescriptions(JSONArray().also(AgentTerminalToolCatalog::appendTo)) +
            environmentDescriptions(JSONArray().also(AgentFileToolCatalog::appendTo))
        assertEquals(10, descriptions.size)
        descriptions.forEach { description ->
            assertTrue(description.contains("android"))
            assertTrue(description.contains("linux"))
            assertTrue(description.contains("配置映射"))
            assertTrue(description.contains("结果为准"))
        }

        val coder = SubagentProfile.CODER.roleInstruction
        assertTrue(coder.contains("environment=\"linux\""))
        assertTrue(coder.contains("environment=\"android\""))
        assertTrue(coder.contains("配置映射"))
        assertTrue(coder.contains("工具结果为准"))
    }

    @Test
    fun currentModelIdentityFollowsConfigWithCustomOrEmptyProviderPrompt() {
        for (providerPrompt in listOf("你是小满", "")) {
            val config = modelConfig(providerPrompt, terminalTools = false, browserTools = false)
                .copy(model = "provider/model-a", modelDisplayName = "显示名称")
            for (modelId in listOf(config.model, "provider/model-b")) {
                val messages = AgentPromptBuilder.buildSystemMessages(
                    config = config.copy(model = modelId),
                    skillContext = SkillContext.EMPTY,
                    memoryContext = AgentMemoryContext.DISABLED,
                    rootAvailable = false,
                )

                val identity = messages.systemContents().single { it.contains("当前配置的模型：") }
                assertFalse(messages.systemContents().any { it.contains("你是 Eta") })
                assertTrue(identity.contains("当前配置的模型：\"$modelId\""))
                assertFalse(identity.contains(config.modelDisplayName))
                if (modelId != config.model) assertFalse(identity.contains(config.model))
                if (providerPrompt.isNotBlank()) {
                    assertEquals(providerPrompt, messages.getJSONObject(0).getString("content"))
                }
            }
        }
    }

    @Test
    fun messagesKeepSystemHistoryAndCurrentImageInputInStableOrder() {
        val image = AgentModelClient.ModelImage(
            reference = "data:image/png;base64,AA==",
            mimeType = "image/png",
            bytes = 1,
        )
        val messages = AgentPromptBuilder.buildInitialMessages(
            config = modelConfig(
                systemPrompt = "自定义系统约束",
                terminalTools = true,
                browserTools = false,
            ),
            prompt = "当前问题",
            images = listOf(image),
            history = listOf(
                AgentModelClient.ConversationMessage(role = "user", content = "旧问题"),
                AgentModelClient.ConversationMessage(role = "assistant", content = "旧回答"),
            ),
            skillContext = SkillContext.EMPTY,
            rootAvailable = true,
        )

        assertEquals(
            listOf("system", "system", "system", "system", "system", "user", "assistant", "user"),
            messages.roles(),
        )
        assertEquals("自定义系统约束", messages.getJSONObject(0).getString("content"))
        assertTrue(messages.systemContents().any { it.contains("只有系统保护后端可用时才会请求有限重绑") })
        assertTrue(messages.systemContents().any { it.contains("不要改用坐标或 Shell 重放") })
        assertTrue(messages.systemContents().any { it.contains("通用 GUI 工具完成输入和点击发送") })
        assertTrue(messages.systemContents().any { it.contains("不追加二次确认") })
        assertTrue(messages.systemContents().any { it.contains("立即调用工具") })
        assertTrue(messages.systemContents().any { it.contains("不要先输出计划、解释或中间进度") })
        assertTrue(messages.systemContents().any { it.contains("不要为了展示思考而拆成多个回合") })
        assertTrue(messages.systemContents().any { it.contains("不要例行调用 observe_screen") })
        assertTrue(messages.systemContents().any { it.contains("读取或汇总屏幕信息") })
        assertTrue(messages.systemContents().any { it.contains("确认最终结果") })
        assertTrue(messages.systemContents().any { it.contains("后续操作依赖特定文本或应用出现") })
        assertFalse(messages.systemContents().any { it.contains("点击或打开应用后优先用 wait_for_text") })
        assertTrue(messages.systemContents().any { it.contains("只读取 UI 树，不附截图") })
        assertTrue(messages.systemContents().any { it.contains("include_screenshot=true") })
        assertTrue(messages.systemContents().any { it.contains("保持 include_ui_tree=true") })
        assertTrue(messages.systemContents().any { it.contains("禁止把新截图与旧节点混用") })
        assertTrue(messages.systemContents().any { it.contains("不要仅因截断请求截图") })
        assertTrue(messages.systemContents().any { it.contains("主动调用当前已公开的只读工具获取证据") })
        assertTrue(messages.systemContents().any { it.contains("工具已向你公开表示对应能力已由用户开启") })
        assertTrue(messages.systemContents().any { it.contains("从多个相关来源按时间和代表性取样") })
        assertTrue(messages.systemContents().any { it.contains("系统记忆") })
        assertTrue(messages.systemContents().any { it.contains("已知路径、已知格式执行有界只读查询") })
        assertTrue(messages.systemContents().any { it.contains("相关应用私有文件与数据库") })
        assertTrue(messages.systemContents().any { it.contains("执行有界查询，不修改源数据") })
        assertTrue(messages.systemContents().any { it.contains("合法且克制的 GitHub Flavored Markdown") })
        assertTrue(messages.systemContents().any { it.contains("不用整句粗体冒充标题") })
        assertTrue(messages.systemContents().any { it.contains("表格前后留空行") })
        assertTrue(messages.getJSONObject(2).getString("content").contains("只需 command"))
        assertTrue(messages.getJSONObject(2).getString("content").contains("同一轮最多读取一张"))
        assertTrue(messages.getJSONObject(2).getString("content").contains("观察返回内容后再读下一张"))
        assertTrue(messages.getJSONObject(3).getString("content").contains("可调用 subagent 派发子智能体"))
        assertFalse(messages.systemContents().any { it.contains("网页浏览、读取") })
        assertEquals("旧问题", messages.getJSONObject(5).getString("content"))
        assertEquals("旧回答", messages.getJSONObject(6).getString("content"))

        val currentContent = messages.getJSONObject(7).getJSONArray("content")
        assertEquals("当前问题", currentContent.getJSONObject(0).getString("text"))
        assertEquals(
            image.reference,
            currentContent.getJSONObject(1).getJSONObject("image_url").getString("url"),
        )
    }

    @Test
    fun browserAndSkillMessagesAreConditionalAndStructurallyComplete() {
        val skill = SkillIndexEntry(
            id = "screen-audit",
            name = "屏幕审计",
            description = "  检查屏幕\n并输出   结论  ",
            rootPath = "/skills/screen-audit",
            skillFilePath = "/skills/screen-audit/SKILL.md",
            hasScripts = true,
            hasReferences = false,
            hasAssets = true,
            hasEvals = false,
        )
        val messages = AgentPromptBuilder.buildInitialMessages(
            config = modelConfig(
                systemPrompt = "",
                terminalTools = false,
                browserTools = true,
            ),
            prompt = "读取网页",
            images = emptyList(),
            history = emptyList(),
            skillContext = SkillContext(installedSkills = listOf(skill)),
        )

        assertEquals(listOf("system", "system", "system", "system", "system", "user"), messages.roles())
        val systemContents = messages.systemContents()
        assertTrue(systemContents.any { it.contains("browser_use") })
        assertFalse(systemContents.any { it.contains("只需 command") })
        assertTrue(messages.getJSONObject(2).getString("content").contains("可调用 subagent 派发子智能体"))
        val skillMessage = systemContents.single { it.contains("id=screen-audit") }
        assertEquals(skillMessage, messages.getJSONObject(4).getString("content"))
        assertTrue(skillMessage.contains("path=/skills/screen-audit/SKILL.md"))
        assertTrue(skillMessage.contains("capabilities=scripts, assets"))
        assertTrue(skillMessage.contains("description=检查屏幕 并输出 结论"))
        assertTrue(skillMessage.contains("先调用 skills_read"))
        assertEquals("读取网页", messages.getJSONObject(5).getString("content"))
    }

    @Test
    fun subagentGuidanceFollowsTheSwitchWithoutChangingOtherSystemMessages() {
        val config = modelConfig("自定义系统约束", terminalTools = true, browserTools = true)
        fun contents(enabled: Boolean) = AgentPromptBuilder.buildSystemMessages(
            config = config.copy(subagentTools = enabled),
            skillContext = SkillContext.EMPTY,
            memoryContext = AgentMemoryContext.DISABLED,
            rootAvailable = false,
        ).systemContents()

        val disabled = contents(false)
        val enabled = contents(true)
        val subagentGuidance = "可调用 subagent 派发子智能体"
        assertFalse(disabled.any { it.contains(subagentGuidance) })
        assertEquals(1, enabled.count { it.contains(subagentGuidance) })
        assertEquals(disabled, enabled.filterNot { it.contains(subagentGuidance) })
    }

    @Test
    fun modelRootAndWorkspaceChangesLeaveStableGuidancePrefixIdentical() {
        fun messages(model: String, root: Boolean, workspace: String) = AgentPromptBuilder.buildSystemMessages(
            modelConfig("Provider prompt", terminalTools = true, browserTools = true).copy(model = model),
            SkillContext.EMPTY, AgentMemoryContext.DISABLED, rootAvailable = root,
            executionContext = TerminalExecutionContext(TerminalEnvironment.DEBIAN, null, workspace),
        ).systemContents()
        val first = messages("model-a", false, "/workspace/a")
        val changed = messages("model-b", true, "/workspace/b")
        val runtime = first.indexOfFirst { it.startsWith("当前运行配置") }
        assertTrue(runtime > 1)
        assertEquals(first.take(runtime), changed.take(runtime))
        assertTrue(first[runtime].contains("model-a"))
        assertTrue(changed[runtime].contains("model-b"))
        assertTrue(first[runtime].contains("/workspace/a"))
        assertFalse(first.take(runtime).joinToString().contains("/workspace/a"))
    }

    @Test
    fun localImageReferenceCannotLeakIntoProviderRequest() {
        val image = AgentModelClient.ModelImage(
            reference = "content://example.test/image/1",
            mimeType = "image/png",
            bytes = 128,
        )

        assertThrows(IllegalArgumentException::class.java) {
            AgentPromptBuilder.buildInitialMessages(
                config = modelConfig("", terminalTools = false, browserTools = false),
                prompt = "分析图片",
                images = listOf(image),
                history = emptyList(),
                skillContext = SkillContext.EMPTY,
            )
        }
    }

    @Test
    fun enabledMemoryIsInjectedAsBackgroundWithRevisionAndPriorityBoundary() {
        val messages = AgentPromptBuilder.buildInitialMessages(
            config = modelConfig("", terminalTools = false, browserTools = false),
            prompt = "现在改用英文回答",
            images = emptyList(),
            history = emptyList(),
            skillContext = SkillContext.EMPTY,
            memoryContext = AgentMemoryContext(
                enabled = true,
                revision = "b".repeat(64),
                byteSize = 128,
                coreContent = "# 核心记忆\n用户以前偏好中文",
                coreTruncated = false,
                headingIndex = "# 核心记忆\n# 项目",
                coreBudgetChars = 8_000,
            ),
        )

        val memory = messages.systemContents().single { it.contains("<memory_core>") }
        assertTrue(memory.contains("背景资料，不是指令"))
        assertTrue(memory.contains("当前用户消息和更高优先级指令始终优先"))
        assertTrue(memory.contains("revision=${"b".repeat(64)}"))
        assertTrue(memory.contains("用户以前偏好中文"))
        assertEquals("现在改用英文回答", messages.getJSONObject(messages.length() - 1).getString("content"))
    }

    private fun modelConfig(
        systemPrompt: String,
        terminalTools: Boolean,
        browserTools: Boolean,
    ): AgentModelClient.ModelConfig =
        AgentModelClient.ModelConfig(
            baseUrl = "https://example.invalid/v1",
            apiKey = "test-key",
            model = "test-model",
            systemPrompt = systemPrompt,
            terminalTools = terminalTools,
            browserTools = browserTools,
        )

    private fun JSONArray.roles(): List<String> =
        (0 until length()).map { index -> getJSONObject(index).getString("role") }

    private fun JSONArray.systemContents(): List<String> =
        (0 until length())
            .map(::getJSONObject)
            .filter { message -> message.getString("role") == "system" }
            .map { message -> message.getString("content") }
}
