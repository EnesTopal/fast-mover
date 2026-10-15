package com.tpl.fast_mover.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNamesTest {

    @Test
    fun uniqueName_returnsOriginalWhenFree() {
        assertEquals("photo.jpg", FileNames.uniqueName("photo.jpg") { false })
    }

    @Test
    fun uniqueName_appendsCounterBeforeExtension() {
        val taken = setOf("photo.jpg", "photo (1).jpg")
        assertEquals("photo (2).jpg", FileNames.uniqueName("photo.jpg") { it in taken })
    }

    @Test
    fun uniqueName_handlesNamesWithoutExtension() {
        assertEquals("README (1)", FileNames.uniqueName("README") { it == "README" })
    }

    @Test
    fun uniqueName_dotFilesKeepTheirName() {
        assertEquals(".nomedia (1)", FileNames.uniqueName(".nomedia") { it == ".nomedia" })
    }

    @Test
    fun isTemporary_detectsInProgressDownloadsAndHiddenFiles() {
        assertTrue(FileNames.isTemporary("movie.mp4.crdownload"))
        assertTrue(FileNames.isTemporary("archive.zip.PART"))
        assertTrue(FileNames.isTemporary(".trashed-123"))
        assertTrue(FileNames.isTemporary(FileNames.tempNameFor("a.jpg")))
        assertFalse(FileNames.isTemporary("IMG_0001.jpg"))
        assertFalse(FileNames.isTemporary("partial-report.pdf"))
    }

    @Test
    fun extensionOf() {
        assertEquals("jpg", FileNames.extensionOf("IMG.JPG"))
        assertEquals("", FileNames.extensionOf("README"))
        assertEquals("", FileNames.extensionOf("trailing."))
        assertEquals("gz", FileNames.extensionOf("a.tar.gz"))
    }

    @Test
    fun humanSize() {
        assertEquals("512 B", FileNames.humanSize(512))
        assertEquals("?", FileNames.humanSize(-1))
        assertTrue(FileNames.humanSize(5L * 1024 * 1024).endsWith("MB"))
    }
}
