package io.github.mangi.eta.agent.phone

import android.content.Context
import org.json.JSONObject

internal object PhoneOperations {
    fun execute(
        context: Context,
        tool: String,
        args: JSONObject,
        userId: Int? = null,
        access: PhoneProviderAccess =
            AppPhoneProviderAccess(context.contentResolver, context.opPackageName),
    ): JSONObject {
        var mutationStarted = false
        val markMutation = { mutationStarted = true }
        return try {
                when (tool) {
                    in NativePersonalQueries.sources.keys ->
                        NativePersonalQueries(
                                access,
                                userId ?: context.applicationInfo.uid / 100000,
                            )
                            .execute(tool, args)
                    "get_night_light",
                    "get_mobile_data",
                    "set_night_light",
                    "set_mobile_data",
                    "get_hotspot",
                    "set_hotspot",
                    "set_sound_mode" ->
                        NativeSystemOperations(context, userId ?: 0, markMutation)
                            .execute(tool, args)
                    "list_calendars",
                    "read_calendar_event",
                    "search_calendar_events",
                    "create_calendar_event",
                    "create_calendar_events",
                    "update_calendar_event",
                    "delete_calendar_event" ->
                        CalendarOperations(context, userId, markMutation, access)
                            .execute(tool, args)
                    "list_alarms",
                    "set_alarm",
                    "update_alarm_time",
                    "set_alarm_enabled",
                    "delete_alarm" ->
                        ClockOperations(access, userId ?: 0, markMutation).execute(tool, args)
                    "create_note",
                    "read_note",
                    "delete_note" ->
                        NoteOperations(
                                access,
                                userId ?: 0,
                                access.callerPackage,
                                markMutation,
                            )
                            .execute(tool, args)
                    else -> PhoneOperation.failure("UNKNOWN_TOOL", "未知一方应用操作")
                }
            } catch (failure: PhoneOperationFailure) {
                PhoneOperation.failure(failure.code, failure.message ?: "操作失败")
            } catch (_: SecurityException) {
                PhoneOperation.failure("PHONE_ACCESS_DENIED", "系统未授予此操作所需权限")
            } catch (_: Exception) {
                PhoneOperation.failure("PHONE_OPERATION_FAILED", "应用接口执行或结果读取失败")
            }
            .also { result ->
                result.put("tool", tool)
                if (!result.optBoolean("ok") && mutationStarted)
                    result
                        .put("status", "unconfirmed")
                        .put("verified", false)
                        .put("message", "操作可能已执行，但未能确认完整结果；请先查询状态，不要重复创建")
            }
    }
}
