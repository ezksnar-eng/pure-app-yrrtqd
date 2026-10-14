package com.ez.foldercleaner

import android.app.Application
import android.media.MediaScannerConnection
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class FolderFilter(val label: String) {
    ALL("All"),
    EMPTY("Empty"),
    WITH_IMAGES("With images")
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    var folders by mutableStateOf<List<FolderItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var scannedCount by mutableIntStateOf(0)
        private set
    var filter by mutableStateOf(FolderFilter.ALL)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set

    /** (done, total) while a delete is running, else null. */
    var deleteProgress by mutableStateOf<Pair<Int, Int>?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    val displayed: List<FolderItem> by derivedStateOf {
        when (filter) {
            FolderFilter.ALL -> folders
            FolderFilter.EMPTY -> folders.filter { it.isEmpty }
            FolderFilter.WITH_IMAGES -> folders.filter { it.hasImages }
        }
    }

    private var scanJob: Job? = null

    fun scan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            loading = true
            scannedCount = 0
            selected = emptySet()
            try {
                folders = FolderScanner.scan { n -> if (n % 50 == 0) scannedCount = n }
            } finally {
                loading = false
            }
        }
    }

    fun setFilter(f: FolderFilter) {
        if (f == filter) return
        filter = f
        selected = emptySet()
    }

    fun setSelection(s: Set<String>) {
        selected = s
    }

    fun clearSelection() {
        selected = emptySet()
    }

    fun selectAllDisplayed() {
        selected = displayed.mapTo(HashSet(displayed.size)) { it.path }
    }

    fun deleteSelected() {
        val paths = selected.toList()
        if (paths.isEmpty() || deleteProgress != null) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            deleteProgress = 0 to paths.size
            val result = withContext(Dispatchers.IO) {
                var ok = 0
                var skipped = 0
                var failed = 0
                paths.forEachIndexed { i, p ->
                    val f = File(p)
                    when {
                        FolderScanner.isProtected(f) -> skipped++
                        !f.exists() -> ok++
                        FolderScanner.deleteTree(f) -> ok++
                        else -> failed++
                    }
                    if (i % 25 == 0) deleteProgress = i to paths.size
                }
                // Drop stale gallery entries for removed folders.
                try {
                    MediaScannerConnection.scanFile(ctx, paths.toTypedArray(), null, null)
                } catch (_: Exception) {
                }
                val remaining = folders.filter { File(it.path).exists() }
                Triple(Triple(ok, skipped, failed), remaining, Unit)
            }
            val (counts, remaining, _) = result
            folders = remaining
            selected = emptySet()
            deleteProgress = null
            val (ok, skipped, failed) = counts
            message = buildString {
                append("Deleted $ok folder${if (ok == 1) "" else "s"}")
                if (skipped > 0) append(" · $skipped protected skipped")
                if (failed > 0) append(" · $failed failed")
            }
        }
    }
}
