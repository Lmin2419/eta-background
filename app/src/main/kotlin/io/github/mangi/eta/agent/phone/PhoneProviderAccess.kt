package io.github.mangi.eta.agent.phone

import android.content.AttributionSource
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.CursorWrapper
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean

/** 业务层共用同一 Provider 合同；普通授权与 Root 只在连接方式上不同。 */
internal interface PhoneProviderAccess {
    val callerPackage: String

    fun query(
        uri: Uri,
        columns: Array<String>,
        selection: String?,
        args: Array<String>?,
        sort: String?,
    ): Cursor?

    fun insert(uri: Uri, values: ContentValues): Uri?

    fun delete(uri: Uri, selection: String?, args: Array<String>?): Int

    fun call(uri: Uri, method: String, arg: String?, extras: Bundle): Bundle?

    fun applyBatch(
        authority: String,
        operations: ArrayList<ContentProviderOperation>,
    ): Array<ContentProviderResult>
}

internal class AppPhoneProviderAccess(
    private val resolver: ContentResolver,
    override val callerPackage: String,
) : PhoneProviderAccess {
    override fun query(
        uri: Uri,
        columns: Array<String>,
        selection: String?,
        args: Array<String>?,
        sort: String?,
    ) = resolver.query(uri, columns, selection, args, sort)

    override fun insert(uri: Uri, values: ContentValues) = resolver.insert(uri, values)

    override fun delete(uri: Uri, selection: String?, args: Array<String>?) =
        resolver.delete(uri, selection, args)

    override fun call(uri: Uri, method: String, arg: String?, extras: Bundle) =
        resolver.call(uri, method, arg, extras)

    override fun applyBatch(authority: String, operations: ArrayList<ContentProviderOperation>) =
        resolver.applyBatch(authority, operations)
}

/** app_process 不注册 App 的 ApplicationThread，必须持有显式的外部 Provider 引用。 */
internal class RootPhoneProviderAccess(private val userId: Int) : PhoneProviderAccess {
    override val callerPackage = "root"
    private val attribution =
        AttributionSource.Builder(Process.myUid()).setPackageName(callerPackage).build()
    private val manager =
        Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null)
    private val managerType = Class.forName("android.app.IActivityManager")
    private val providerType = Class.forName("android.content.IContentProvider")

    private inner class Lease(val authority: String) : AutoCloseable {
        val token: IBinder = Binder()
        private val closed = AtomicBoolean(false)
        val provider: Any

        init {
            val holder =
                invoke(
                    managerType.getMethod(
                        "getContentProviderExternal",
                        String::class.java,
                        Int::class.javaPrimitiveType,
                        IBinder::class.java,
                        String::class.java,
                    ),
                    manager,
                    authority,
                    userId,
                    token,
                    "Eta",
                ) ?: PhoneOperation.error("PHONE_PROVIDER_UNAVAILABLE", "系统未提供该应用接口")
            provider =
                holder.javaClass.getField("provider").get(holder)
                    ?: PhoneOperation.error("PHONE_PROVIDER_UNAVAILABLE", "应用接口尚不可用")
        }

        fun operation(name: String, vararg args: Any?): Any? {
            val method =
                providerType.methods.singleOrNull {
                    it.name == name && it.parameterCount == args.size
                } ?: PhoneOperation.error("PHONE_PROTOCOL_UNSUPPORTED", "当前系统的原生接口协议不兼容")
            return invoke(method, provider, *args)
        }

        override fun close() {
            if (closed.compareAndSet(false, true))
                invoke(
                    managerType.getMethod(
                        "removeContentProviderExternalAsUser",
                        String::class.java,
                        IBinder::class.java,
                        Int::class.javaPrimitiveType,
                    ),
                    manager,
                    authority,
                    token,
                    userId,
                )
        }
    }

    private fun authority(uri: Uri): String =
        uri.authority?.substringAfter('@') ?: PhoneOperation.error("INVALID_ARGUMENT", "缺少应用接口")

    private fun withoutUser(uri: Uri) = uri.buildUpon().authority(authority(uri)).build()

    private fun queryArgs(selection: String?, args: Array<String>?, sort: String? = null) =
        Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sort)
        }

    override fun query(
        uri: Uri,
        columns: Array<String>,
        selection: String?,
        args: Array<String>?,
        sort: String?,
    ): Cursor? {
        val lease = Lease(authority(uri))
        try {
            val cursor =
                lease.operation(
                    "query",
                    attribution,
                    withoutUser(uri),
                    columns,
                    queryArgs(selection, args, sort),
                    null,
                ) as? Cursor
            if (cursor == null) {
                lease.close()
                return null
            }
            return object : CursorWrapper(cursor) {
                override fun close() {
                    try {
                        super.close()
                    } finally {
                        lease.close()
                    }
                }
            }
        } catch (failure: Throwable) {
            lease.close()
            throw failure
        }
    }

    override fun insert(uri: Uri, values: ContentValues): Uri? =
        Lease(authority(uri)).use {
            it.operation("insert", attribution, withoutUser(uri), values, Bundle()) as? Uri
        }

    override fun delete(uri: Uri, selection: String?, args: Array<String>?): Int =
        Lease(authority(uri)).use {
            it.operation("delete", attribution, withoutUser(uri), queryArgs(selection, args)) as Int
        }

    override fun call(uri: Uri, method: String, arg: String?, extras: Bundle): Bundle? =
        Lease(authority(uri)).use {
            it.operation("call", attribution, it.authority, method, arg, extras) as? Bundle
        }

    @Suppress("UNCHECKED_CAST")
    override fun applyBatch(
        authority: String,
        operations: ArrayList<ContentProviderOperation>,
    ): Array<ContentProviderResult> =
        Lease(authority.substringAfter('@')).use {
            it.operation("applyBatch", attribution, it.authority, operations)
                as Array<ContentProviderResult>
        }

    private fun invoke(
        method: java.lang.reflect.Method,
        target: Any?,
        vararg arguments: Any?,
    ): Any? =
        try {
            method.invoke(target, *arguments)
        } catch (failure: InvocationTargetException) {
            throw failure.targetException
        }
}
