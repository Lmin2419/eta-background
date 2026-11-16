package io.github.mangi.eta.ui.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.mangi.eta.agent.terminal.DetachedTaskSupervisor
import io.github.mangi.eta.agent.terminal.LinuxEnvironmentPaths
import io.github.mangi.eta.agent.terminal.LinuxPackageProfiles
import io.github.mangi.eta.agent.terminal.SharedFolderMounts
import io.github.mangi.eta.agent.terminal.linuxPackageProfileReady
import io.github.mangi.eta.agent.terminal.terminalEnvironment
import io.github.mangi.eta.core.AndroidAgentLogger
import io.github.mangi.eta.core.safeLogType
import io.github.mangi.eta.data.repository.LinuxEnvironmentSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Activity 级状态所有者；配置变更只重建 UI，不替换正在运行的 Agent 会话。 */
internal class AgentAppViewModel(
    application: Application,
    private val loadConversations: suspend () -> AgentConversationStore.Snapshot,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, { AgentConversationStore.loadAsync(application) })

    var state by mutableStateOf<AgentAppState?>(null)
        private set
    var conversationStartupFailed by mutableStateOf(false)
        private set
    private var conversationStartupJob: Job? = null

    val skills = AgentSkillsStore(application, viewModelScope)
    val memory = AgentMemoryStore(application, viewModelScope)
    val commonTasks = CommonTasksStore(application, viewModelScope)
    val permissionHealth = PermissionHealthStore(application, viewModelScope)
    private val terminalHost = TerminalSessionHost.get(application)
    val terminalStore = terminalHost.terminal
    val consoleStore = terminalHost.console
    init {
        loadInitialConversations()
    }

    fun loadInitialConversations() {
        if (state != null || conversationStartupJob?.isActive == true) return
        conversationStartupFailed = false
        conversationStartupJob = viewModelScope.launch {
            try {
                val snapshot = loadConversations()
                state = AgentAppState(getApplication(), viewModelScope, snapshot)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                AndroidAgentLogger.error("Agent initial conversation load failed: type=${failure.safeLogType()}")
                conversationStartupFailed = true
            }
        }
    }
}
