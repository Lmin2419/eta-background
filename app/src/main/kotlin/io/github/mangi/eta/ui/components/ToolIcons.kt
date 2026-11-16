package io.github.mangi.eta.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AdsClick
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ContentPasteGo
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardCommandKey
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ScreenshotMonitor
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.WebAsset
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

internal fun iconForTool(toolId: String): ImageVector = when (toolId) {
    "screen_open" -> Icons.Outlined.Layers
    "screen_state", "screen_diag" -> Icons.Outlined.AdminPanelSettings
    "screen_dump", "screen_list" -> Icons.Outlined.DocumentScanner
    "screen_shot" -> Icons.Outlined.ScreenshotMonitor
    "screen_tap", "screen_click" -> Icons.Outlined.AdsClick
    "screen_swipe", "screen_drag" -> Icons.Outlined.OpenWith
    "screen_press" -> Icons.Outlined.TouchApp
    "screen_scroll" -> Icons.Outlined.SwapVert
    "screen_text" -> Icons.Outlined.Keyboard
    "screen_wait" -> Icons.Outlined.Schedule
    "screen_finish" -> Icons.Outlined.Notifications
    "observe", "observe_screen" -> Icons.Outlined.DocumentScanner
    "click", "tap", "tap_element" -> Icons.Outlined.AdsClick
    "tap_area" -> Icons.Outlined.MyLocation
    "long_press", "long_press_element" -> Icons.Outlined.TouchApp
    "swipe" -> Icons.Outlined.OpenWith
    "scroll", "scroll_element" -> Icons.Outlined.SwapVert
    "clipboard", "paste_text" -> Icons.Outlined.ContentPasteGo
    "get_clipboard", "set_clipboard" -> Icons.Outlined.ContentPaste
    "type_text", "input_text" -> Icons.Outlined.Keyboard
    "replace_text" -> Icons.Outlined.FindReplace
    "clear_text" -> Icons.AutoMirrored.Outlined.Backspace
    "wait", "wait_text", "wait_for_text", "wait_for_package" -> Icons.Outlined.Schedule
    "search_apps" -> Icons.Outlined.Search
    "get_current_context" -> Icons.Outlined.Smartphone
    "open_app", "launch_app" -> Icons.Outlined.Apps
    "open_uri" -> Icons.AutoMirrored.Outlined.OpenInNew
    "browser_use", "网页浏览" -> Icons.Outlined.Language
    "web_search", "web_search_call", "网页搜索" -> Icons.Outlined.TravelExplore
    "browser_read" -> Icons.AutoMirrored.Outlined.MenuBook
    "fetch_url" -> Icons.AutoMirrored.Outlined.Article
    "browser_interact" -> Icons.Outlined.AdsClick
    "browser_screenshot" -> Icons.Outlined.ScreenshotMonitor
    "file_search", "file_search_call", "文件搜索" -> Icons.AutoMirrored.Outlined.ManageSearch
    "code_interpreter", "code_interpreter_call", "代码执行" -> Icons.Outlined.Terminal
    "computer", "computer_call", "计算机操作" -> Icons.Outlined.Computer
    "image_generation", "image_generation_call", "图像生成" -> Icons.Outlined.Image
    "mcp_call", "MCP 工具" -> Icons.Outlined.Extension
    "memory_get", "memory_write", "character_memory_get", "character_memory_write" ->
        Icons.Outlined.Psychology
    "release_gui" -> Icons.Outlined.Smartphone
    "common_task" -> Icons.Outlined.Apps
    "screen_back", "press_key" -> Icons.Outlined.KeyboardCommandKey
    "open_system_panel" -> Icons.Outlined.WebAsset
    "read_image" -> Icons.Outlined.Image
    "skills_list", "skills_read", "skills_read_resource",
    "skills_list_curated", "skills_inspect_github", "skills_install_from_github",
        -> Icons.Outlined.Extension
    "set_alarm", "set_timer", "list_alarms", "list_active_timers" ->
        Icons.Outlined.Alarm
    "device_status", "set_device_state", "get_device_environment" ->
        Icons.Outlined.Smartphone
    "network_info", "wifi_credentials" -> Icons.Outlined.Wifi
    "media_control" -> Icons.Outlined.PlayArrow
    "set_volume" -> Icons.AutoMirrored.Outlined.VolumeUp
    "top_memory_apps", "top_storage_apps" -> Icons.Outlined.Layers
    "read_sms_code" -> Icons.Outlined.Key
    "recent_notifications", "search_notification_history" -> Icons.Outlined.Notifications
    "get_setting", "set_setting" -> Icons.Outlined.Settings
    "inspect_app", "app_state_control" -> Icons.Outlined.AdminPanelSettings
    "get_logcat" -> Icons.Outlined.Description
    "get_current_location", "search_saved_places" -> Icons.Outlined.LocationOn
    "get_health_summary" -> Icons.Outlined.MonitorHeart
    "recent_app_activity", "app_usage_summary" -> Icons.Outlined.Insights
    "search_calendar_events" -> Icons.Outlined.CalendarMonth
    "search_contacts" -> Icons.Outlined.Contacts
    "search_call_history" -> Icons.Outlined.Phone
    "search_messages" -> Icons.Outlined.ChatBubble
    "search_media", "search_qq_chat_images", "search_wechat_chat_images" ->
        Icons.Outlined.Image
    "search_audio" -> Icons.Outlined.MusicNote
    "search_recordings", "search_coloros_recordings", "search_recording_summaries" ->
        Icons.Outlined.Mic
    "search_files" -> Icons.Outlined.FolderOpen
    "search_downloads" -> Icons.Outlined.Download
    "search_clipboard_history" -> Icons.Outlined.ContentPaste
    "search_notes", "search_coloros_notes" -> Icons.AutoMirrored.Outlined.StickyNote2
    "search_system_memories", "search_coloros_memories" -> Icons.Outlined.Psychology
    "search_personal_orders" -> Icons.Outlined.ShoppingBag
    "terminal", "terminal_job", "terminal_daemon", "run_command" -> Icons.Outlined.Terminal
    "subagent", "subagent_profile" -> Icons.Outlined.Hub
    "read_file", "stat_file" -> Icons.Outlined.Description
    "write_file", "edit_file" -> Icons.Outlined.EditNote
    "list_directory", "glob_files", "grep_files" -> Icons.Outlined.FolderOpen
    else -> when {
        toolId in io.github.mangi.eta.agent.context.PersonalSearchTools.names -> Icons.Outlined.Insights
        toolId in io.github.mangi.eta.agent.model.AgentPhoneToolCatalog.names -> when {
            "calendar" in toolId -> Icons.Outlined.CalendarMonth
            "alarm" in toolId -> Icons.Outlined.Alarm
            "note" in toolId -> Icons.AutoMirrored.Outlined.StickyNote2
            else -> Icons.Outlined.Smartphone
        }
        toolId.startsWith("mcp_") -> Icons.Outlined.Extension
        else -> Icons.Outlined.Build
    }
}
