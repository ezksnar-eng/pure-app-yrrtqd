package com.ez.foldercleaner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.max
import kotlin.math.min

class MainActivity : ComponentActivity() {

    private var hasAccess by mutableStateOf(false)

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            hasAccess = checkAccess()
        }

    private fun checkAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasAccess = checkAccess()
        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = when {
                Build.VERSION.SDK_INT >= 31 ->
                    if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = scheme) {
                App(hasAccess = hasAccess, onRequestAccess = ::requestAccess)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasAccess = checkAccess()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
    vm: MainViewModel = viewModel()
) {
    val snackbar = remember { SnackbarHostState() }
    var showConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(hasAccess) { if (hasAccess) vm.scan() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    BackHandler(enabled = vm.selected.isNotEmpty()) { vm.clearSelection() }

    val selectionCount = vm.selected.size

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (selectionCount == 0) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Folder Cleaner")
                            if (hasAccess && !vm.loading) {
                                Text(
                                    "${vm.folders.size} folders",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    },
                    actions = {
                        if (hasAccess) {
                            IconButton(onClick = { vm.scan() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { vm.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    title = { Text("$selectionCount selected") },
                    actions = {
                        TextButton(onClick = { vm.selectAllDisplayed() }) { Text("Select all") }
                    }
                )
            }
        },
        floatingActionButton = {
            if (selectionCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = { showConfirm = true },
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    text = { Text("Delete ($selectionCount)") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !hasAccess -> PermissionScreen(onRequestAccess)
                else -> Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FolderFilter.entries.forEach { f ->
                            FilterChip(
                                selected = vm.filter == f,
                                onClick = { vm.setFilter(f) },
                                label = { Text(f.label) }
                            )
                        }
                    }
                    if (vm.loading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                        Text(
                            "Scanning… ${vm.scannedCount} folders found",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else if (vm.displayed.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No folders to show")
                        }
                    } else {
                        FolderGrid(
                            items = vm.displayed,
                            selected = vm.selected,
                            onSelectionChange = vm::setSelection,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (showConfirm) {
        val sel = remember(showConfirm) { vm.folders.filter { it.path in vm.selected } }
        val withContent = sel.count { !it.isEmpty }
        val files = sel.sumOf { it.totalFiles }
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Delete ${sel.size} folder${if (sel.size == 1) "" else "s"}?") },
            text = {
                Text(
                    if (withContent == 0) {
                        "All selected folders are empty. This cannot be undone."
                    } else {
                        "$withContent of them contain files or subfolders (about $files files). " +
                            "Everything inside will be permanently deleted. This cannot be undone."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    vm.deleteSelected()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Cancel") } }
        )
    }

    vm.deleteProgress?.let { (done, total) ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Deleting…") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("$done / $total", modifier = Modifier.padding(top = 8.dp))
                }
            }
        )
    }
}

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📁", fontSize = 56.sp)
        Text(
            "Storage access needed",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            "To see and delete empty folders, this app needs \"All files access\". " +
                "Android's gallery APIs cannot list or remove empty folders.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        Button(onClick = onRequest) { Text("Grant access") }
    }
}

private fun LazyGridState.indexAt(o: Offset, slackPx: Float = 10f): Int? =
    layoutInfo.visibleItemsInfo.firstOrNull {
        val l = it.offset
        o.x >= l.x - slackPx && o.x < l.x + it.size.width + slackPx &&
            o.y >= l.y - slackPx && o.y < l.y + it.size.height + slackPx
    }?.index

private class DragState {
    var anchor = -1
    var last = -1
    var adding = true
    var base: Set<String> = emptySet()
}

@Composable
fun FolderGrid(
    items: List<FolderItem>,
    selected: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val currentItems by rememberUpdatedState(items)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnChange by rememberUpdatedState(onSelectionChange)
    val drag = remember { DragState() }
    var dragging by remember { mutableStateOf(false) }
    var pointer by remember { mutableStateOf(Offset.Zero) }

    // Selects the whole index range anchor..idx, so rows skipped by a fast swipe are still covered.
    // Always derived from the selection at drag start, so dragging back shrinks the range.
    fun extendTo(idx: Int) {
        val list = currentItems
        if (idx !in list.indices || drag.anchor !in list.indices) return
        drag.last = idx
        val lo = min(drag.anchor, idx)
        val hi = max(drag.anchor, idx)
        val next = HashSet<String>(drag.base)
        for (i in lo..hi) {
            if (drag.adding) next.add(list[i].path) else next.remove(list[i].path)
        }
        currentOnChange(next)
    }

    // Auto-scroll while the finger is near the top/bottom edge during a drag.
    LaunchedEffect(dragging) {
        val maxSpeed = 140f // px per frame at the very edge
        while (dragging) {
            val h = gridState.layoutInfo.viewportSize.height.toFloat()
            val zone = h * 0.2f
            val y = pointer.y
            val speed = when {
                zone <= 0f -> 0f
                y < zone -> -((zone - y) / zone).coerceIn(0f, 1f) * maxSpeed
                y > h - zone -> ((y - (h - zone)) / zone).coerceIn(0f, 1f) * maxSpeed
                else -> 0f
            }
            if (speed != 0f) {
                gridState.scrollBy(speed)
                gridState.indexAt(pointer)?.let { if (it != drag.last) extendTo(it) }
            }
            delay(16)
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = modifier.pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    pointer = offset
                    val idx = gridState.indexAt(offset)
                    val list = currentItems
                    if (idx != null && idx in list.indices) {
                        drag.anchor = idx
                        drag.last = idx
                        drag.base = currentSelected
                        drag.adding = list[idx].path !in currentSelected
                        dragging = true
                        extendTo(idx)
                    }
                },
                onDrag = { change, _ ->
                    change.consume()
                    pointer = change.position
                    gridState.indexAt(change.position)?.let { if (it != drag.last) extendTo(it) }
                },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false }
            )
        },
        userScrollEnabled = !dragging,
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(items, key = { it.path }, contentType = { "folder" }) { item ->
            FolderCell(
                item = item,
                selected = item.path in selected,
                onClick = {
                    val s = HashSet(currentSelected)
                    if (!s.add(item.path)) s.remove(item.path)
                    currentOnChange(s)
                }
            )
        }
    }
}

@Composable
fun FolderCell(item: FolderItem, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val primary = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (selected) Modifier.border(3.dp, primary, shape) else Modifier)
        ) {
            if (item.thumb != null) {
                val ctx = LocalContext.current
                val request = remember(item.thumb) {
                    ImageRequest.Builder(ctx).data(File(item.thumb)).size(256).build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("📁", fontSize = 40.sp, modifier = Modifier.align(Alignment.Center))
            }
            if (selected) {
                Box(Modifier.fillMaxSize().background(primary.copy(alpha = 0.30f)))
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Text(
            item.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            if (item.isEmpty) "Empty" else "${item.totalFiles} items",
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall,
            color = if (item.isEmpty) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
