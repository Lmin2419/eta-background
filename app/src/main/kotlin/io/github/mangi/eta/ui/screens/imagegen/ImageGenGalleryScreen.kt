package io.github.mangi.eta.ui.screens.imagegen

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.agent.imagegen.GeneratedImageRecord
import io.github.mangi.eta.agent.imagegen.GeneratedImageStore
import io.github.mangi.eta.ui.components.EtaIconButton
import io.github.mangi.eta.ui.components.EtaToastCenter
import io.github.mangi.eta.ui.components.EtaWindowDialog
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.LocalChatImagePreviewer
import io.github.mangi.eta.ui.components.MiuixDialogActions
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.rememberChatImageBitmap
import io.github.mangi.eta.ui.theme.EtaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

private const val GALLERY_COLUMNS = 3

/**
 * 已生成图片画廊（胖小胖样式）：三列方格，点开全屏看大图（大图里可保存、分享），长按删一张，右上角清空全部。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ImageGenGalleryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val previewer = LocalChatImagePreviewer.current
    var records by remember { mutableStateOf<List<GeneratedImageRecord>>(emptyList()) }
    var totalBytes by remember { mutableLongStateOf(0L) }
    var confirmClearAll by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<GeneratedImageRecord?>(null) }
    val itemBounds = remember { mutableStateMapOf<Int, Rect>() }

    fun reload() {
        scope.launch {
            val (list, bytes) = withContext(Dispatchers.IO) {
                GeneratedImageStore.listRecords(context) to GeneratedImageStore.totalStorageBytes(context)
            }
            records = list
            totalBytes = bytes
        }
    }
    LaunchedEffect(Unit) { reload() }
    val imagePaths = remember(records) { records.map { it.filePath } }
    val openAt: (Int) -> Unit = { idx ->
        previewer?.open(
            images = imagePaths,
            initialIndex = idx,
            groupKey = "image_gen_gallery",
            sourceBoundsProvider = { page -> itemBounds[page] },
            sourceCornerRadius = 14.dp,
        )
    }

    val resources = LocalResources.current
    MiuixScaffoldPage(
        title = stringResource(R.string.image_gen_gallery_title),
        onBack = onBack,
        actions = {
            if (records.isNotEmpty()) {
                EtaIconButton(onClick = { confirmClearAll = true }) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = stringResource(R.string.imagegen_gallery_clear_title), tint = EtaTheme.colors.textPrimary)
                }
            }
        },
        overlay = {
            EtaWindowDialog(
                show = confirmClearAll,
                title = stringResource(R.string.imagegen_gallery_clear_title),
                summary = pluralStringResource(
                    R.plurals.imagegen_gallery_clear_message,
                    records.size,
                    records.size,
                    formatImageStorageSize(totalBytes),
                ),
                onDismissRequest = { confirmClearAll = false },
            ) {
                MiuixDialogActions(
                    confirmText = stringResource(R.string.imagegen_action_clear),
                    onCancel = { confirmClearAll = false },
                    onConfirm = {
                        confirmClearAll = false
                        scope.launch {
                            val count = withContext(Dispatchers.IO) { GeneratedImageStore.clearAll(context) }
                            reload()
                            EtaToastCenter.show(
                                context,
                                if (count > 0) {
                                    resources.getQuantityString(R.plurals.imagegen_gallery_cleared, count, count)
                                } else {
                                    resources.getString(R.string.imagegen_gallery_nothing_to_clear)
                                },
                                Toast.LENGTH_SHORT,
                            )
                        }
                    },
                )
            }
            val target = pendingDelete
            EtaWindowDialog(
                show = target != null,
                title = stringResource(R.string.imagegen_gallery_delete_title),
                summary = target?.prompt?.take(80)?.ifBlank { null },
                onDismissRequest = { pendingDelete = null },
            ) {
                MiuixDialogActions(
                    confirmText = stringResource(R.string.action_delete),
                    onCancel = { pendingDelete = null },
                    onConfirm = {
                        val rec = pendingDelete
                        pendingDelete = null
                        if (rec != null) {
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { GeneratedImageStore.deleteRecord(context, rec.filePath) }
                                reload()
                                EtaToastCenter.show(
                                    context,
                                    if (ok) R.string.imagegen_gallery_deleted else R.string.imagegen_gallery_delete_failed,
                                    Toast.LENGTH_SHORT,
                                )
                            }
                        }
                    },
                )
            }
        },
    ) {
        if (records.isEmpty()) {
            item(key = "empty") {
                ListEmptyState(
                    title = stringResource(R.string.imagegen_gallery_empty_title),
                    summary = stringResource(R.string.imagegen_gallery_empty_summary),
                    icon = Icons.Outlined.Image,
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
            return@MiuixScaffoldPage
        }
        item(key = "summary") {
            Text(
                stringResource(
                    R.string.imagegen_gallery_header_with_delete,
                    pluralStringResource(
                        R.plurals.imagegen_gallery_header_summary,
                        records.size,
                        records.size,
                        formatImageStorageSize(totalBytes),
                    ),
                ),
                style = EtaTheme.type.caption,
                color = EtaTheme.colors.textTertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        val rows = records.withIndex().chunked(GALLERY_COLUMNS)
        rows.forEachIndexed { rowIndex, row ->
            item(key = "row-$rowIndex-${row.first().value.id}") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { (index, record) ->
                        GalleryTile(
                            record = record,
                            modifier = Modifier.weight(1f)
                                .onGloballyPositioned { itemBounds[index] = it.boundsInWindow() },
                            onClick = { openAt(index) },
                            onLongClick = { pendingDelete = record },
                        )
                    }
                    repeat(GALLERY_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryTile(
    record: GeneratedImageRecord,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val bitmap = rememberChatImageBitmap(record.filePath, maxDimensionPx = 480)
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(EtaTheme.colors.surfaceSunken)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = record.prompt.take(60), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
