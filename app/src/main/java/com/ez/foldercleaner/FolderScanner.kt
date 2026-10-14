package com.ez.foldercleaner

import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

data class FolderItem(
    val path: String,
    val name: String,
    val parentLabel: String,
    val directFiles: Int,
    val subDirs: Int,
    /** Number of real (non-hidden) files in the whole subtree. Unknown/unreadable content counts as 1. */
    val totalFiles: Int,
    val thumb: String?
) {
    val isEmpty: Boolean get() = totalFiles == 0
    val hasImages: Boolean get() = thumb != null
}

object FolderScanner {

    private const val MAX_DEPTH = 10

    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

    /** Top-level system folders that can never be deleted by the app. */
    private val protectedTopLevel = setOf(
        "dcim", "pictures", "download", "downloads", "movies", "music", "documents",
        "android", "alarms", "notifications", "ringtones", "podcasts", "audiobooks",
        "recordings", "screenshots"
    )

    fun root(): File = Environment.getExternalStorageDirectory()

    fun isProtected(f: File): Boolean {
        val root = root()
        if (f.absolutePath == root.absolutePath) return true
        val parent = f.parentFile ?: return true
        return parent.absolutePath == root.absolutePath && f.name.lowercase() in protectedTopLevel
    }

    private fun isSymlink(f: File): Boolean = try {
        Files.isSymbolicLink(f.toPath())
    } catch (_: Exception) {
        false
    }

    suspend fun scan(onProgress: (Int) -> Unit): List<FolderItem> = withContext(Dispatchers.IO) {
        val root = root()
        val out = ArrayList<FolderItem>(4096)
        walk(root, 0, root, out, onProgress)
        out.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        out
    }

    private fun CoroutineScope.walk(
        dir: File,
        depth: Int,
        root: File,
        out: MutableList<FolderItem>,
        tick: (Int) -> Unit
    ): Int {
        ensureActive()
        val children = dir.listFiles() ?: return 1 // unreadable -> treat as non-empty (safe)
        val isRoot = dir.path == root.path
        var direct = 0
        var subs = 0
        var total = 0
        var thumb: String? = null

        for (c in children) {
            if (isSymlink(c)) {
                total++ // never follow links; count as content so parents are not treated as empty
                continue
            }
            if (c.isDirectory) {
                if (isRoot && c.name == "Android") {
                    continue // app-private area, not scanned
                }
                subs++
                total += if (depth >= MAX_DEPTH) 1 else walk(c, depth + 1, root, out, tick)
            } else if (!c.name.startsWith(".")) {
                direct++
                total++
                if (thumb == null && c.extension.lowercase() in imageExt) thumb = c.path
            }
        }

        if (!isRoot && !dir.name.startsWith(".")) {
            val parentLabel = dir.parentFile?.path.orEmpty().removePrefix(root.path).trimStart('/')
            out.add(FolderItem(dir.path, dir.name, parentLabel, direct, subs, total, thumb))
            tick(out.size)
        }
        return total
    }

    /** Deletes a folder tree without ever following symbolic links. */
    fun deleteTree(f: File): Boolean {
        if (isSymlink(f)) return f.delete()
        if (f.isDirectory) {
            val kids = f.listFiles()
            if (kids != null) for (k in kids) deleteTree(k)
        }
        return f.delete() || !f.exists()
    }
}
