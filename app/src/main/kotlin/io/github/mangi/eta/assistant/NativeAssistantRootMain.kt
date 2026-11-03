package io.github.mangi.eta.assistant

import android.os.IBinder
import android.os.Process
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import org.json.JSONArray
import org.json.JSONObject

/** Short-lived Root command, not an injected module or a resident service. Requests use stdin. */
internal object NativeAssistantRootMain {
    const val MARKER = "ETA_ASSISTANT_RESULT:"
    private const val ROLE = "android.app.role.ASSISTANT"
    @Volatile private var activeCommand: java.lang.Process? = null

    @JvmStatic
    fun main(arguments: Array<String>) {
        // Also bound the helper if the App is killed while a Binder call is stuck.
        val deadline = java.util.Timer("eta-assistant-deadline", true)
        deadline.schedule(object : java.util.TimerTask() {
            override fun run() {
                runCatching { activeCommand?.destroyForcibly() }
                exitProcess(124)
            }
        }, 25_000L)
        val reply = try {
            val bytes = System.`in`.readNBytes(16 * 1024 + 1)
            require(bytes.size <= 16 * 1024)
            val request = JSONObject(bytes.toString(Charsets.UTF_8))
            require(request.getInt("version") == 1)
            val operation = request.getString("operation")
            val requiredUid = if (operation == "oplus_policy") 1000 else 0
            if (Process.myUid() != requiredUid) throw AssistantSetupFailure("ROOT_REQUIRED")
            val user = request.getInt("user").also { require(it in 0..21474) }
            checkUser(user)
            val result = when (operation) {
                "read" -> readSnapshot(user).toJson()
                "oplus_read" -> OplusPowerRoot.read(user)
                "oplus_property" -> {
                    OplusPowerRoot.changeProperty(user, request.getString("expected"), request.getString("target"))
                    checkUser(user)
                    JSONObject()
                }
                "oplus_policy" -> {
                    OplusPowerRoot.changePolicy(user, request.nullableString("expected"), request.nullableString("target"))
                    checkUser(user)
                    JSONObject()
                }
                "role" -> {
                    val expected = request.getJSONArray("expected").roles()
                    val target = request.getJSONArray("target").roles()
                    if (roleHolders(user) != expected) throw AssistantSetupFailure("CONFIGURATION_CHANGED")
                    if (target != expected) {
                        changeRole(user, expected, target)
                    }
                    checkUser(user)
                    if (roleHolders(user) != target) throw AssistantSetupFailure("ROLE_NOT_APPLIED")
                    JSONObject()
                }
                "setting" -> {
                    val setting = AssistantSetting.valueOf(request.getString("setting"))
                    val expected = request.nullableString("expected")
                    val target = request.nullableString("target")
                    validateValue(setting, expected)
                    validateValue(setting, target)
                    if (readSetting(user, setting) != expected) throw AssistantSetupFailure("CONFIGURATION_CHANGED")
                    checkUser(user)
                    val args = mutableListOf("/system/bin/settings", "--user", "$user",
                        if (target == null) "delete" else "put", setting.namespace, setting.key)
                    if (target != null) args.add(target)
                    runCommand(args.toTypedArray(), 4)
                    checkUser(user)
                    if (readSetting(user, setting) != target) throw AssistantSetupFailure("CONFIGURATION_NOT_APPLIED")
                    JSONObject()
                }
                else -> throw AssistantSetupFailure("UNSUPPORTED_COMMAND")
            }
            result.put("ok", true)
        } catch (failure: AssistantSetupFailure) {
            JSONObject().put("ok", false).put("code", failure.code)
        } catch (_: Throwable) {
            JSONObject().put("ok", false).put("code", "NATIVE_ROUTE_UNAVAILABLE")
        }
        println(MARKER + reply.toString())
        deadline.cancel()
        exitProcess(0)
    }

    private fun readSnapshot(user: Int): AssistantSnapshot {
        val holders = roleHolders(user)
        val settings = AssistantSetting.entries.associateWith { readSetting(user, it) }
        checkUser(user)
        return AssistantSnapshot(user, holders, settings)
    }

    private fun roleService(): Pair<Class<*>, Any> {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java).invoke(null, "role") as? IBinder
            ?: throw AssistantSetupFailure("NATIVE_ROUTE_UNAVAILABLE")
        val api = Class.forName("android.app.role.IRoleManager")
        val service = Class.forName("android.app.role.IRoleManager\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: throw AssistantSetupFailure("ROLE_QUERY_UNAVAILABLE")
        return api to service
    }

    private fun roleHolders(user: Int): List<String> = try {
        val (api, service) = roleService()
        val result = api.getMethod("getRoleHoldersAsUser", String::class.java, Int::class.javaPrimitiveType)
            .invoke(service, ROLE, user) as? List<*>
            ?: throw AssistantSetupFailure("NATIVE_ROUTE_UNAVAILABLE")
        result.map { (it as String).also(::validatePackage) }.sorted()
    } catch (_: Exception) {
        throw AssistantSetupFailure("ROLE_QUERY_UNAVAILABLE")
    }

    private fun changeRole(user: Int, expected: List<String>, target: List<String>) {
        val (api, service) = roleService()
        val callbackClass = Class.forName("android.os.RemoteCallback")
        val listenerClass = Class.forName("android.os.RemoteCallback\$OnResultListener")
        val finished = java.util.concurrent.CountDownLatch(1)
        val succeeded = java.util.concurrent.atomic.AtomicBoolean(false)
        val listener = java.lang.reflect.Proxy.newProxyInstance(listenerClass.classLoader, arrayOf(listenerClass)) { proxy, method, args ->
            when (method.name) {
                "onResult" -> { succeeded.set(args?.getOrNull(0) != null); finished.countDown(); null }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.getOrNull(0)
                "toString" -> "EtaRoleResult"
                else -> null
            }
        }
        // RemoteCallback without a Handler receives the result on its own Binder thread.
        val callback = callbackClass.getConstructor(listenerClass).newInstance(listener)
        val method = api.getMethod(if (target.isEmpty()) "removeRoleHolderAsUser" else "addRoleHolderAsUser",
            String::class.java, String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, callbackClass)
        val holder = if (target.isEmpty()) expected.single() else target.single()
        checkUser(user)
        try {
            method.invoke(service, ROLE, holder, 0, user, callback)
        } catch (_: Exception) { throw AssistantSetupFailure("COMMAND_RESULT_UNKNOWN") }
        // RoleController has its own 15 s deadline; the CLI only waits 5 s, so do not use it.
        if (!finished.await(18, TimeUnit.SECONDS)) throw AssistantSetupFailure("COMMAND_TIMEOUT")
        // A null result can also be RoleControllerManager timing out, not just a rejection.
        if (!succeeded.get()) throw AssistantSetupFailure("COMMAND_RESULT_UNKNOWN")
    }

    private fun checkUser(expected: Int) {
        val user = try {
            Class.forName("android.app.ActivityManager").getMethod("getCurrentUser").invoke(null) as Int
        } catch (_: Exception) { throw AssistantSetupFailure("ACTIVE_USER_UNAVAILABLE") }
        if (user != expected) throw AssistantSetupFailure("USER_CHANGED")
    }

    private fun readSetting(user: Int, setting: AssistantSetting): String? {
        val output = runCommand(arrayOf("/system/bin/settings", "--user", "$user", "get", setting.namespace, setting.key), 4)
            .trimEnd('\r', '\n')
        return output.takeUnless { it == "null" }.also { validateValue(setting, it) }
    }

    private fun validateValue(setting: AssistantSetting, value: String?) {
        if (value == null || value == "") return
        val valid = if (setting == AssistantSetting.LONG_PRESS) value.matches(Regex("[0-9]{1,3}"))
            else value.length <= 512 && value.matches(Regex("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+"))
        if (!valid) throw AssistantSetupFailure("UNSUPPORTED_SETTING_VALUE")
    }

    private fun validatePackage(value: String) {
        require(value.length <= 255 && value.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")))
    }

    private fun JSONArray.roles(): List<String> {
        require(length() <= 1)
        return (0 until length()).map { getString(it).also(::validatePackage) }
    }

    private fun runCommand(args: Array<String>, seconds: Long): String {
        val process = ProcessBuilder(*args).redirectErrorStream(true).start()
        activeCommand = process
        process.outputStream.close()
        val reader = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "eta-assistant-command").apply { isDaemon = true } }
        try {
            val output = reader.submit<String> {
                process.inputStream.use { input ->
                    val buffer = ByteArray(1024)
                    val bytes = java.io.ByteArrayOutputStream()
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (bytes.size() + count <= 8192) bytes.write(buffer, 0, count)
                        else throw AssistantSetupFailure("COMMAND_RESULT_UNKNOWN")
                    }
                    bytes.toString("UTF-8")
                }
            }
            if (!process.waitFor(seconds, TimeUnit.SECONDS)) throw AssistantSetupFailure("COMMAND_TIMEOUT")
            val text = try { output.get(1, TimeUnit.SECONDS) }
                catch (_: Exception) { throw AssistantSetupFailure("COMMAND_RESULT_UNKNOWN") }
            if (process.exitValue() != 0) throw AssistantSetupFailure("COMMAND_FAILED")
            return text
        } finally {
            if (process.isAlive) process.destroyForcibly()
            activeCommand = null
            reader.shutdownNow()
        }
    }
}

internal fun JSONObject.nullableString(key: String): String? {
    require(has(key))
    return if (isNull(key)) null else getString(key)
}

internal fun AssistantSnapshot.toJson(): JSONObject = JSONObject()
    .put("user", userId).put("holders", JSONArray(holders)).put("settings", JSONObject().also { json ->
        AssistantSetting.entries.forEach { json.put(it.name, settings[it] ?: JSONObject.NULL) }
    })

internal fun assistantSnapshotFromJson(json: JSONObject): AssistantSnapshot {
    val holders = json.getJSONArray("holders")
    val settings = json.getJSONObject("settings")
    return AssistantSnapshot(json.getInt("user"), (0 until holders.length()).map(holders::getString),
        AssistantSetting.entries.associateWith { settings.nullableString(it.name) })
}
