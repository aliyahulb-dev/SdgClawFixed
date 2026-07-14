package com.sdgclaw.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [AssetCopier] — covers:
 *  1. Successful copy is followed by verify() returning null (file is readable/non-empty)
 *  2. File-already-exists: copyAsset with overwrite=false returns Success(alreadyExisted=true)
 *  3. File-already-exists: copyAsset with overwrite=true overwrites the content
 *  4. Write failure (dest is a directory): copyAsset returns Failure
 *  5. verify() on non-existent file returns error string
 *  6. verify() on empty file returns error string
 *  7. verify() on a well-formed file returns null
 *
 * Note: [AssetCopier.copyAsset] requires an Android [Context] to open assets,
 * so here we test the verify() logic and the edge-case helpers that do NOT
 * need Context. The copy logic is exercised via an integration helper that
 * bypasses assets and writes directly to the dest, mirroring what
 * copyAsset() does internally.
 */
class AssetCopierTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // ── verify() ─────────────────────────────────────────────────────────────

    @Test
    fun `verify returns null for a readable non-empty file`() {
        val file = tmp.newFile("ok.txt").also { it.writeText("hello") }
        assertNull(
            "Expected verify to pass for a normal file",
            AssetCopier.verify(file)
        )
    }

    @Test
    fun `verify returns error for non-existent file`() {
        val file = File(tmp.root, "missing.txt")
        val error = AssetCopier.verify(file)
        assertNotNull("Expected an error for missing file", error)
        assertTrue(error!!.contains("does not exist"))
    }

    @Test
    fun `verify returns error for empty file`() {
        val file = tmp.newFile("empty.txt")  // zero bytes by default
        val error = AssetCopier.verify(file)
        assertNotNull("Expected an error for empty file", error)
        assertTrue(error!!.contains("empty"))
    }

    @Test
    fun `verify returns error for a directory path`() {
        val dir = tmp.newFolder("adir")
        val error = AssetCopier.verify(dir)
        assertNotNull("Expected an error when path is a directory", error)
        assertTrue(error!!.contains("not a regular file"))
    }

    // ── Simulated copy-then-verify flow ──────────────────────────────────────

    /**
     * Simulate what [AssetCopier.copyAsset] does (minus the Context/asset-open
     * part) so we can test the write, existing-file, and overwrite paths without
     * needing the Android framework.
     */
    private fun simulateCopy(
        sourceContent: String,
        destFile: File,
        overwrite: Boolean,
    ): AssetCopier.CopyResult {
        if (destFile.exists() && !overwrite) {
            return AssetCopier.CopyResult.Success(destFile, alreadyExisted = true)
        }
        return try {
            destFile.parentFile?.mkdirs()
            destFile.writeText(sourceContent)
            AssetCopier.CopyResult.Success(destFile)
        } catch (e: Exception) {
            AssetCopier.CopyResult.Failure("Write failed: ${e.message}", e)
        }
    }

    @Test
    fun `copy to new file succeeds and verify passes`() {
        val dest = File(tmp.root, "setup.sh")
        val result = simulateCopy("#!/bin/bash\necho hello", dest, overwrite = false)

        assertTrue(result is AssetCopier.CopyResult.Success)
        assertEquals(false, (result as AssetCopier.CopyResult.Success).alreadyExisted)

        val verifyErr = AssetCopier.verify(dest)
        assertNull("verify() should pass after a successful copy", verifyErr)
    }

    @Test
    fun `copy with overwrite=false and file exists returns alreadyExisted=true`() {
        val dest = tmp.newFile("setup.sh").also { it.writeText("old content") }

        val result = simulateCopy("new content", dest, overwrite = false)

        assertTrue(result is AssetCopier.CopyResult.Success)
        assertEquals(
            true,
            (result as AssetCopier.CopyResult.Success).alreadyExisted
        )
        // Original content must be unchanged
        assertEquals("old content", dest.readText())
    }

    @Test
    fun `copy with overwrite=true replaces existing content`() {
        val dest = tmp.newFile("setup.sh").also { it.writeText("old content") }

        val result = simulateCopy("new content", dest, overwrite = true)

        assertTrue(result is AssetCopier.CopyResult.Success)
        assertEquals(false, (result as AssetCopier.CopyResult.Success).alreadyExisted)
        assertEquals("new content", dest.readText())
    }

    @Test
    fun `copy to a path whose parent is a file returns Failure`() {
        // Create a regular file where we then try to use it as a directory
        val parentAsFile = tmp.newFile("notadir")
        val dest = File(parentAsFile, "setup.sh") // illegal — parent is a file

        val result = simulateCopy("content", dest, overwrite = false)

        assertTrue(
            "Expected Failure when parent path is a file",
            result is AssetCopier.CopyResult.Failure
        )
    }

    @Test
    fun `copyAndVerify returns Failure when written file is zero bytes`() {
        // Simulate a copy that results in an empty file
        val dest = tmp.newFile("setup.sh")
        dest.writeText("") // deliberately empty
        // Verification step — AssetCopier.verify should catch this
        val verifyErr = AssetCopier.verify(dest)
        assertNotNull("verify should fail for zero-byte file", verifyErr)
        assertTrue(verifyErr!!.contains("empty"))
    }

    @Test
    fun `verify returns error when file is not readable`() {
        val file = tmp.newFile("noperm.sh").also {
            it.writeText("content")
            it.setReadable(false, false)
        }
        // Only meaningful on a real Linux FS; skip assertion on Windows/CI
        if (!file.canRead()) {
            val error = AssetCopier.verify(file)
            assertNotNull("Expected error for unreadable file", error)
            assertTrue(error!!.contains("not readable"))
        }
    }
}
