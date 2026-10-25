package io.github.mangi.eta.ui.model

import androidx.compose.runtime.Immutable

internal const val LOCAL_NETWORK_PERMISSION_ITEM_ID = "local_network"

@Immutable
data class PermissionHealthUiState(
    val items: List<PermissionHealthItemUi>,
)

@Immutable
data class PermissionHealthItemUi(
    val id: String,
    val title: String,
    val summary: String,
    val status: PermissionStatusUi,
    val primaryActionLabel: String?,
)

@Immutable
enum class PermissionStatusUi {
    Available,
    Missing,
    Warning,
    Disabled,
}
