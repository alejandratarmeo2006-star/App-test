package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import com.example.model.FileItem
import com.example.model.FormatCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class StorageScanner(private val context: Context) {

    private val tag = "StorageScanner"

    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

            StorageStats(
                totalBytes = totalBytes,
                usedBytes = usedBytes,
                freeBytes = freeBytes
            )
        } catch (e: Exception) {
            Log.e(tag, "Error reading storage stats", e)
            StorageStats(
                totalBytes = 64L * 1024 * 1024 * 1024,
                usedBytes = 32L * 1024 * 1024 * 1024,
                freeBytes = 32L * 1024 * 1024 * 1024
            )
        }
    }

    suspend fun scanAllFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        val foundFiles = mutableMapOf<String, FileItem>()

        // 1. Query MediaStore Files
        queryMediaStoreFiles(foundFiles)

        // 2. Scan standard storage directories for any files not indexed or scoped
        scanDirectoryFiles(foundFiles)

        // Sort descending by size initially
        foundFiles.values.sortedByDescending { it.size }
    }

    private fun queryMediaStoreFiles(target: MutableMap<String, FileItem>) {
        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )

            // Ignore 0-byte or directory entries
            val selection = "${MediaStore.Files.FileColumns.SIZE} > 0"

            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Files.FileColumns.SIZE} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "file_$id"
                    val path = if (dataCol != -1) cursor.getString(dataCol) ?: "" else ""
                    val size = cursor.getLong(sizeCol)
                    val dateModifiedSec = cursor.getLong(dateCol)
                    val dateModified = if (dateModifiedSec > 0) dateModifiedSec * 1000L else System.currentTimeMillis()

                    val uri = ContentUris.withAppendedId(collection, id)
                    val ext = name.substringAfterLast('.', "")
                    val category = FormatCategory.fromExtension(ext)
                    val parentDir = if (path.isNotEmpty()) {
                        File(path).parentFile?.name ?: "Storage"
                    } else "Media"

                    val item = FileItem(
                        id = "ms_$id",
                        name = name,
                        path = path,
                        uri = uri,
                        size = size,
                        dateModified = dateModified,
                        extension = ext,
                        formatCategory = category,
                        parentDirectory = parentDir,
                        isDemoFile = false
                    )

                    val key = if (path.isNotEmpty()) path else "uri_$id"
                    target[key] = item
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error querying MediaStore", e)
        }
    }

    private fun scanDirectoryFiles(target: MutableMap<String, FileItem>) {
        val rootDirs = mutableListOf<File>()

        try {
            Environment.getExternalStorageDirectory()?.let { rootDirs.add(it) }
            context.getExternalFilesDir(null)?.let { rootDirs.add(it) }
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloads != null) rootDirs.add(downloads)
            val documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (documents != null) rootDirs.add(documents)
            val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (pictures != null) rootDirs.add(pictures)
            val dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            if (dcim != null) rootDirs.add(dcim)
            val music = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (music != null) rootDirs.add(music)
            val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            if (movies != null) rootDirs.add(movies)
        } catch (e: Exception) {
            Log.e(tag, "Error collecting root directories", e)
        }

        val visitedPaths = mutableSetOf<String>()

        for (dir in rootDirs.distinctBy { it.absolutePath }) {
            scanDirectoryRecursively(dir, target, visitedPaths, maxDepth = 4, currentDepth = 0)
        }
    }

    private fun scanDirectoryRecursively(
        dir: File,
        target: MutableMap<String, FileItem>,
        visited: MutableSet<String>,
        maxDepth: Int,
        currentDepth: Int
    ) {
        if (!dir.exists() || !dir.isDirectory || currentDepth > maxDepth) return
        val canonical = try { dir.canonicalPath } catch (_: Exception) { dir.absolutePath }
        if (!visited.add(canonical)) return

        // Skip android system internal caches
        if (dir.name.startsWith(".") || dir.name.equals("cache", ignoreCase = true)) return

        val children = dir.listFiles() ?: return
        for (file in children) {
            if (file.isDirectory) {
                scanDirectoryRecursively(file, target, visited, maxDepth, currentDepth + 1)
            } else if (file.isFile && file.length() > 0) {
                val path = file.absolutePath
                if (!target.containsKey(path)) {
                    val name = file.name
                    val ext = name.substringAfterLast('.', "")
                    val isDemo = path.contains("demo_samples")
                    target[path] = FileItem(
                        id = "file_${file.hashCode()}_${file.lastModified()}",
                        name = name,
                        path = path,
                        uri = null,
                        size = file.length(),
                        dateModified = file.lastModified(),
                        extension = ext,
                        formatCategory = FormatCategory.fromExtension(ext),
                        parentDirectory = file.parentFile?.name ?: "Storage",
                        isDemoFile = isDemo
                    )
                }
            }
        }
    }

    /**
     * Creates realistic sample dummy files for immediate testing of dividing by size & format
     * and testing the delete flow.
     */
    suspend fun generateDemoFiles(): List<FileItem> = withContext(Dispatchers.IO) {
        val baseDir = File(context.getExternalFilesDir(null), "demo_samples")
        if (!baseDir.exists()) baseDir.mkdirs()

        val sampleSpecs = listOf(
            SampleSpec("4K_Drone_Footage_Hawaiian_Coast.mp4", 185L * 1024 * 1024, FormatCategory.VIDEO),
            SampleSpec("Family_Vacation_Summer_Archive.zip", 82L * 1024 * 1024, FormatCategory.ARCHIVE),
            SampleSpec("Studio_Mix_Master_Track_FLAC.wav", 44L * 1024 * 1024, FormatCategory.AUDIO),
            SampleSpec("App_Update_Debug_Bundle.apk", 36L * 1024 * 1024, FormatCategory.APK),
            SampleSpec("Financial_Quarterly_Report_2026.pdf", 14L * 1024 * 1024, FormatCategory.DOCUMENT),
            SampleSpec("Podcast_Episode_92_Full_Audio.mp3", 28L * 1024 * 1024, FormatCategory.AUDIO),
            SampleSpec("Mountain_Sunrise_RAW_Photo.jpg", 18L * 1024 * 1024, FormatCategory.IMAGE),
            SampleSpec("Project_Presentation_Slides_Final.pptx", 9L * 1024 * 1024, FormatCategory.DOCUMENT),
            SampleSpec("System_Diagnostic_Dump_Logs.txt", 4L * 1024 * 1024, FormatCategory.DOCUMENT),
            SampleSpec("Vintage_Car_Restoration_Clip.mp4", 95L * 1024 * 1024, FormatCategory.VIDEO),
            SampleSpec("HighRes_Cityscape_Wallpaper.png", 6L * 1024 * 1024, FormatCategory.IMAGE),
            SampleSpec("Old_WhatsApp_Media_Backup.tar", 62L * 1024 * 1024, FormatCategory.ARCHIVE),
            SampleSpec("Music_Concert_Rehearsal.m4a", 21L * 1024 * 1024, FormatCategory.AUDIO),
            SampleSpec("Database_Export_Customers.csv", 3L * 1024 * 1024, FormatCategory.DOCUMENT),
            SampleSpec("Large_Game_Asset_Expansion.pak", 240L * 1024 * 1024, FormatCategory.OTHER)
        )

        for (spec in sampleSpecs) {
            val file = File(baseDir, spec.name)
            if (!file.exists() || file.length() != spec.targetSize) {
                try {
                    // Use sparse or fast block allocation
                    val raf = RandomAccessFile(file, "rw")
                    raf.setLength(spec.targetSize)
                    // Write header byte
                    raf.seek(0)
                    raf.write("StorageLensDemoData".toByteArray())
                    raf.close()
                } catch (e: Exception) {
                    Log.e(tag, "Failed creating demo file ${spec.name}", e)
                }
            }
        }

        scanAllFiles()
    }

    suspend fun deleteFiles(files: List<FileItem>): DeletionResult = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        var deletedCount = 0
        val failedFiles = mutableListOf<FileItem>()

        for (item in files) {
            var deleted = false

            // Try filesystem deletion first
            if (item.path.isNotEmpty()) {
                val f = File(item.path)
                if (f.exists()) {
                    deleted = f.delete()
                }
            }

            // Try content resolver deletion if URI exists or file deletion wasn't sufficient
            if (!deleted && item.uri != null) {
                try {
                    val rows = context.contentResolver.delete(item.uri, null, null)
                    deleted = rows > 0
                } catch (e: Exception) {
                    Log.w(tag, "ContentResolver delete failed for ${item.name}", e)
                }
            }

            if (deleted) {
                deletedCount++
                freedBytes += item.size
            } else {
                // If neither succeeded (e.g. read-only or scoped media requiring user intent), record failure
                failedFiles.add(item)
            }
        }

        DeletionResult(
            deletedCount = deletedCount,
            freedBytes = freedBytes,
            failedCount = failedFiles.size
        )
    }
}

data class StorageStats(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long
) {
    val usedPercentage: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class DeletionResult(
    val deletedCount: Int,
    val freedBytes: Long,
    val failedCount: Int
)

private data class SampleSpec(
    val name: String,
    val targetSize: Long,
    val category: FormatCategory
)
