package com.example.model

import android.net.Uri
import androidx.compose.ui.graphics.Color
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FormatCategory(
    val displayName: String,
    val iconName: String,
    val colorHex: Long,
    val extensions: Set<String>
) {
    ALL("All", "folder", 0xFF6366F1, emptySet()),
    VIDEO("Videos", "videocam", 0xFF8B5CF6, setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv", "m4v")),
    IMAGE("Images", "image", 0xFF06B6D4, setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg", "raw")),
    AUDIO("Audio", "audiotrack", 0xFFF59E0B, setOf("mp3", "wav", "flac", "m4a", "ogg", "aac", "wma", "opus")),
    DOCUMENT("Documents", "description", 0xFF10B981, setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "json", "xml", "html")),
    ARCHIVE("Archives", "archive", 0xFFEC4899, setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")),
    APK("APKs", "android", 0xFFEF4444, setOf("apk", "xapk", "apks")),
    OTHER("Others", "insert_drive_file", 0xFF64748B, emptySet());

    val composeColor: Color
        get() = Color(colorHex)

    companion object {
        fun fromExtension(ext: String): FormatCategory {
            val lower = ext.lowercase()
            return entries.firstOrNull { it != ALL && it != OTHER && it.extensions.contains(lower) } ?: OTHER
        }
    }
}

enum class SizeTier(
    val label: String,
    val minBytes: Long,
    val maxBytes: Long,
    val colorHex: Long
) {
    ALL("All Sizes", 0L, Long.MAX_VALUE, 0xFF6366F1),
    GIGANTIC("> 500 MB", 500L * 1024 * 1024, Long.MAX_VALUE, 0xFFEF4444),
    HUGE("100 - 500 MB", 100L * 1024 * 1024, 500L * 1024 * 1024, 0xFFF97316),
    LARGE("25 - 100 MB", 25L * 1024 * 1024, 100L * 1024 * 1024, 0xFFF59E0B),
    MEDIUM("5 - 25 MB", 5L * 1024 * 1024, 25L * 1024 * 1024, 0xFF3B82F6),
    SMALL("< 5 MB", 0L, 5L * 1024 * 1024, 0xFF10B981);

    val composeColor: Color
        get() = Color(colorHex)

    fun matches(size: Long): Boolean {
        if (this == ALL) return true
        return size >= minBytes && (maxBytes == Long.MAX_VALUE || size < maxBytes)
    }
}

enum class AgeFilter(val label: String, val minDays: Int, val maxDays: Int) {
    ALL("Any Age", 0, Int.MAX_VALUE),
    RECENT("Last 7 Days", 0, 7),
    MONTH("Last 30 Days", 0, 30),
    OLD_3_MONTHS("Older than 90 Days", 90, Int.MAX_VALUE),
    OLD_6_MONTHS("Older than 180 Days", 180, Int.MAX_VALUE);

    fun matches(dateModifiedMillis: Long): Boolean {
        if (this == ALL) return true
        val diffDays = (System.currentTimeMillis() - dateModifiedMillis) / (1000L * 60 * 60 * 24)
        return diffDays >= minDays && (maxDays == Int.MAX_VALUE || diffDays <= maxDays)
    }
}

enum class SortOrder(val label: String) {
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First"),
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)")
}

data class FileItem(
    val id: String,
    val name: String,
    val path: String,
    val uri: Uri?,
    val size: Long,
    val dateModified: Long,
    val extension: String,
    val formatCategory: FormatCategory,
    val parentDirectory: String,
    val isDemoFile: Boolean = false
) {
    val formattedSize: String
        get() = formatFileSize(size)

    val formattedDate: String
        get() {
            if (dateModified <= 0) return "Unknown date"
            val sdf = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())
            return sdf.format(Date(dateModified))
        }

    val sizeTier: SizeTier
        get() {
            return when {
                size >= 500L * 1024 * 1024 -> SizeTier.GIGANTIC
                size >= 100L * 1024 * 1024 -> SizeTier.HUGE
                size >= 25L * 1024 * 1024 -> SizeTier.LARGE
                size >= 5L * 1024 * 1024 -> SizeTier.MEDIUM
                else -> SizeTier.SMALL
            }
        }

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            if (digitGroups >= units.size) digitGroups = units.size - 1
            val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
            return if (digitGroups == 0) {
                "${bytes.toInt()} B"
            } else {
                String.format(Locale.US, "%.1f %s", value, units[digitGroups])
            }
        }
    }
}

data class StorageCategorySummary(
    val category: FormatCategory,
    val totalBytes: Long,
    val fileCount: Int,
    val percentage: Float
)
