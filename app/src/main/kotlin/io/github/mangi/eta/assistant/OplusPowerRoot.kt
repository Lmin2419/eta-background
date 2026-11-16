package io.github.mangi.eta.assistant

import android.os.Build
import android.os.IBinder
import java.io.File
import org.json.JSONObject

/** Fixed OEM operations in a short-lived helper, never loaded into system_server. */
internal object OplusPowerRoot {
    fun read(user: Int): JSONObject {
        requireUser(user)
        val (api, service) = policyService()
        val gate = api.getMethod("getData", String::class.java, Int::class.javaPrimitiveType)
            .invoke(service, OplusPowerContract.POLICY, 0) as String?
        val route = if (OplusPowerContract.isCandidate(Build.MODEL, Build.VERSION.SDK_INT)) {
            frameworkHasRoute(OplusPowerContract.FRAMEWORKS.map(::File))
        } else false
        return JSONObject().put("user", user).put("broadcast", property())
            .put("gate", gate ?: JSONObject.NULL)
            .put("fingerprint", Build.FINGERPRINT)
            .put("supported", OplusPowerContract.supported(Build.MODEL, Build.VERSION.SDK_INT, user, route))
    }

    fun changeProperty(user: Int, expected: String, target: String) {
        requireUser(user)
        if (expected.length > 32 || target !in setOf("", "0", "1", "false", "true", "no", "yes", "off", "on")) {
            throw AssistantSetupFailure("UNSUPPORTED_SETTING_VALUE")
        }
        if (property() != expected) throw AssistantSetupFailure("CONFIGURATION_CHANGED")
        val set = Class.forName("android.os.SystemProperties").getMethod("set", String::class.java, String::class.java)
        try {
            set.invoke(null, OplusPowerContract.PROPERTY, target)
        } catch (_: Exception) {
            throw AssistantSetupFailure("COMMAND_RESULT_UNKNOWN")
        }
        if (property() != target) throw AssistantSetupFailure("CONFIGURATION_NOT_APPLIED")
    }

    /** Vendor permission check accepts system UID; start this helper as UID 1000 before Binder opens. */
    fun changePolicy(user: Int, expected: String?, target: String?) {
        requireUser(user)
        if (expected !in setOf(null, "", "0", "1") || target !in setOf(null, "", "0", "1")) {
            throw AssistantSetupFailure("UNSUPPORTED_SETTING_VALUE")
        }
        val (api, service) = policyService()
        val get = api.getMethod("getData", String::class.java, Int::class.javaPrimitiveType)
        if (get.invoke(service, OplusPowerContract.POLICY, 0) != expected) {
            throw AssistantSetupFailure("CONFIGURATION_CHANGED")
        }
        val method = if (target == null) {
            api.getMethod("removeData", String::class.java, Int::class.javaPrimitiveType)
        } else {
            api.getMethod("setData", String::class.java, String::class.java, Int::class.javaPrimitiveType)
        }
        val applied = try {
            if (target == null) method.invoke(service, OplusPowerContract.POLICY, 0)
            else method.invoke(service, OplusPowerContract.POLICY, target, 0)
        } catch (_: Exception) {
            // The service can fail while notifying listeners after updating its cache. An exception
            // therefore cannot prove the write was rejected; keep the journal, do not replay it.
            throw AssistantSetupFailure("COMMAND_RESULT_UNKNOWN")
        }
        if (applied != true) throw AssistantSetupFailure("COLOROS_POLICY_UNAVAILABLE")
        if (get.invoke(service, OplusPowerContract.POLICY, 0) != target) {
            throw AssistantSetupFailure("CONFIGURATION_NOT_APPLIED")
        }
    }

    private fun property(): String = Class.forName("android.os.SystemProperties")
        .getMethod("get", String::class.java, String::class.java)
        .invoke(null, OplusPowerContract.PROPERTY, "") as String

    private fun policyService(): Pair<Class<*>, Any> {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "oplusdevicepolicy") as? IBinder
            ?: throw AssistantSetupFailure("COLOROS_POLICY_UNAVAILABLE")
        val name = "android.os.oplusdevicepolicy.IOplusDevicePolicyManagerService"
        val api = Class.forName(name)
        val service = Class.forName("${name}\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            ?: throw AssistantSetupFailure("COLOROS_POLICY_UNAVAILABLE")
        return api to service
    }

    private fun requireUser(user: Int) {
        // This vendor service stores its policy for user 0 regardless of the calling Android user.
        if (user != 0) throw AssistantSetupFailure("USER_CHANGED")
    }

    /**
     * 框架里还有没有长按广播：在这些 jar 的 classes*.dex 里找广播名（dex 字符串池里 ASCII 原样存放）。
     * 任一 jar 找到就是 true；读得到 dex 却都找不到是 false；一个 dex 都读不到（预编译剥离）是 null。
     */
    internal fun frameworkHasRoute(jars: List<File>): Boolean? {
        val marker = OplusPowerContract.ACTION.toByteArray(Charsets.US_ASCII)
        var readable = false
        for (jar in jars) {
            if (!jar.isFile || jar.length() !in 1..512L * 1024 * 1024) continue
            val found = runCatching {
                java.util.zip.ZipFile(jar).use { zip ->
                    val dexes = zip.entries().asSequence().filter { DexEntry.matches(it.name) }.toList()
                    if (dexes.isNotEmpty()) readable = true
                    dexes.any { entry -> zip.getInputStream(entry).use { contains(it, marker) } }
                }
            }.getOrDefault(false)
            if (found) return true
        }
        return if (readable) false else null
    }

    private val DexEntry = Regex("classes\\d*\\.dex")

    /** 流式查找：块与块之间留 marker.size - 1 字节重叠，跨块的匹配也找得到。 */
    internal fun contains(input: java.io.InputStream, marker: ByteArray): Boolean {
        val overlap = marker.size - 1
        val buffer = ByteArray(1 shl 20)
        var kept = 0
        while (true) {
            val read = input.read(buffer, kept, buffer.size - kept)
            if (read < 0) return false
            val end = kept + read
            var i = 0
            while (i + marker.size <= end) {
                var j = 0
                while (j < marker.size && buffer[i + j] == marker[j]) j++
                if (j == marker.size) return true
                i++
            }
            kept = minOf(overlap, end)
            System.arraycopy(buffer, end - kept, buffer, 0, kept)
        }
    }
}
