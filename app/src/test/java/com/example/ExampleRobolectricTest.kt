package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.FileItem
import com.example.model.FormatCategory
import com.example.model.SizeTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("StorageLens", appName)
  }

  @Test
  fun `formatFileSize correctly formats bytes`() {
    assertEquals("0 B", FileItem.formatFileSize(0))
    assertEquals("512 B", FileItem.formatFileSize(512))
    assertEquals("1.0 MB", FileItem.formatFileSize(1024 * 1024))
    assertEquals("1.5 GB", FileItem.formatFileSize((1.5 * 1024 * 1024 * 1024).toLong()))
  }

  @Test
  fun `FormatCategory categorization`() {
    assertEquals(FormatCategory.VIDEO, FormatCategory.fromExtension("mp4"))
    assertEquals(FormatCategory.IMAGE, FormatCategory.fromExtension("png"))
    assertEquals(FormatCategory.AUDIO, FormatCategory.fromExtension("mp3"))
    assertEquals(FormatCategory.DOCUMENT, FormatCategory.fromExtension("pdf"))
    assertEquals(FormatCategory.ARCHIVE, FormatCategory.fromExtension("zip"))
    assertEquals(FormatCategory.APK, FormatCategory.fromExtension("apk"))
  }

  @Test
  fun `SizeTier categorization`() {
    assertTrue(SizeTier.GIGANTIC.matches(600L * 1024 * 1024))
    assertTrue(SizeTier.HUGE.matches(250L * 1024 * 1024))
    assertTrue(SizeTier.LARGE.matches(50L * 1024 * 1024))
    assertTrue(SizeTier.MEDIUM.matches(10L * 1024 * 1024))
    assertTrue(SizeTier.SMALL.matches(2L * 1024 * 1024))
  }
}
