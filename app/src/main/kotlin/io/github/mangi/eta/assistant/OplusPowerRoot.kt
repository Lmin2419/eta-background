package io.github.mangi.eta.assistant

import android.os.Build
import android.os.IBinder
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

/** Fixed OEM operations in a short-lived helper, never loaded into system_server. */
internal object OplusPowerRoot {
    fun read(user: Int): JSONObject {
        requireUser(user)
        val (api, service) = policyService()
        val gate = api.getMethod("getData", String::class.java, Int::class.javaPrimitiveType)
            .invoke(service, OplusPowerContract.POLICY, 0) as String?
        val hash = if (OplusPowerContract.isCandidate(Build.MODEL, Build.VERSION.SDK_INT)) frameworkHash() else ""
        return JSONObject().put("user", user).put("broadcast", property())
            .put("gate", gate ?: JSONObject.NULL)
            .put("fingerprint", Build.FINGERPRINT)
            .put("supported", OplusPowerContract.supported(Build.MODEL, Build.VERSION.SDK_INT, user, hash))
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

    private fun frameworkHash(): String {
        val file = File(OplusPowerContract.FRAMEWORK)
        if (!file.isFile || file.length() !in 1..64L * 1024 * 1024) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                digest.update(bytes, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
