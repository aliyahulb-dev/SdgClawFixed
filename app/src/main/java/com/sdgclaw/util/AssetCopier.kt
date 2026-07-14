package com.sdgclaw.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Utility for copying bundled asset files to writable device storage.
 *
 * All public functions are safe to call from any thread; they are purely
 * blocking I/O and should be dispatched on [kotlinx.coroutines.Dispatchers.IO]
 * by the caller.
 */
object AssetCopier {

    private const val TAG = "AssetCopier"

    // ── Public result type ────────────────────────────────────────────────────

    sealed class CopyResult {
        /** File was successfully written (or already existed and [overwrite] was false). */
        data class Success(val file: File, val alreadyExisted: Boolean = false) : CopyResult()

        /** An error occurred; the destination file has not been modified. */
        data class Failure(val message: String, val cause: Throwable? = null) : CopyResult()
    }

    // ── Core copy function ────────────────────────────────────────────────────

    /**
     * Copy [assetPath] from the APK assets directory to [destFile].
     *
     * @param context    Application or Activity context used to open assets.
     * @param assetPath  Relative path inside `assets/`, e.g. `"setup.sh"`.
     * @param destFile   Target [File] on the filesystem.
     * @param overwrite  If `false` and [destFile] already exists the copy is
     *                   skipped and [CopyResult.Success] is returned with
     *                   `alreadyExisted = true`.  If `true` the file is always
     *                   overwritten.
     * @return [CopyResult.Success] on success, [CopyResult.Failure] otherwise.
     */
    fun copyAsset(
        context: Context,
        assetPath: String,
        destFile: File,
        overwrite: Boolean = false,
    ): CopyResult {
        Log.d(TAG, "copyAsset: $assetPath → ${destFile.absolutePath} (overwrite=$overwrite)")

        // ── Already exists? ───────────────────────────────────────────────────
        if (destFile.exists() && !overwrite) {
            Log.d(TAG, "Destination already exists, skipping copy.")
            return CopyResult.Success(destFile, alreadyExisted = true)
        }

        // ── Ensure parent directory exists ────────────────────────────────────
        val parentDir = destFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            Log.d(TAG, "Creating parent directory: ${parentDir.absolutePath}")
            if (!parentDir.mkdirs()) {
                val msg = "Could not create directory: ${parentDir.absolutePath}"
                Log.e(TAG, msg)
                return CopyResult.Failure(msg)
            }
        }

        // ── Open asset stream ─────────────────────────────────────────────────
        val inputStream: InputStream = try {
            context.assets.open(assetPath)
        } catch (e: IOException) {
            val msg = "Asset not found: $assetPath — ${e.message}"
            Log.e(TAG, msg, e)
            return CopyResult.Failure(msg, e)
        }

        // ── Atomic write: write to .tmp then rename ───────────────────────────
        val tmpFile = File(destFile.absolutePath + ".tmp")

        return try {
            FileOutputStream(tmpFile).use { out ->
                inputStream.use { ins ->
                    ins.copyTo(out, bufferSize = 8 * 1024)
                }
                out.flush()
                out.fd.sync() // fsync so data is durable before rename
            }

            // Rename tmp → final
            if (!tmpFile.renameTo(destFile)) {
                // renameTo can fail across filesystems; fall back to copy-then-delete
                tmpFile.copyTo(destFile, overwrite = true)
                tmpFile.delete()
            }

            Log.d(TAG, "Copy succeeded: ${destFile.absolutePath} (${destFile.length()} bytes)")
            CopyResult.Success(destFile)
        } catch (e: IOException) {
            tmpFile.delete() // clean up partial file
            val msg = "Write failed: ${e.message}"
            Log.e(TAG, msg, e)
            CopyResult.Failure(msg, e)
        } catch (e: SecurityException) {
            tmpFile.delete()
            val msg = "Permission denied writing to ${destFile.absolutePath}: ${e.message}"
            Log.e(TAG, msg, e)
            CopyResult.Failure(msg, e)
        } finally {
            // inputStream is already closed by use{} above, but guard anyway
            runCatching { inputStream.close() }
        }
    }

    // ── Post-copy verification ────────────────────────────────────────────────

    /**
     * Verify that [file] exists, is readable, and has a non-zero size.
     *
     * @return `null` if the file is healthy; a human-readable error string otherwise.
     */
    fun verify(file: File): String? {
        return when {
            !file.exists()   -> "File does not exist: ${file.absolutePath}"
            !file.isFile     -> "Path is not a regular file: ${file.absolutePath}"
            !file.canRead()  -> "File is not readable: ${file.absolutePath}"
            file.length() == 0L -> "File is empty (0 bytes): ${file.absolutePath}"
            else -> null // all good
        }
    }

    // ── Convenience: copy + verify in one call ────────────────────────────────

    /**
     * Copy [assetPath] to [destFile] and then immediately verify the result.
     *
     * Returns [CopyResult.Success] only when the file is present, readable,
     * and non-empty after the write.
     */
    fun copyAndVerify(
        context: Context,
        assetPath: String,
        destFile: File,
        overwrite: Boolean = false,
    ): CopyResult {
        val result = copyAsset(context, assetPath, destFile, overwrite)
        if (result is CopyResult.Failure) return result

        val verifyError = verify(destFile)
        return if (verifyError != null) {
            Log.e(TAG, "Post-copy verification failed: $verifyError")
            CopyResult.Failure(verifyError)
        } else {
            Log.d(TAG, "Post-copy verification passed.")
            result
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Return the default destination path for `setup.sh` in the app's
     * external files directory (world-readable on most Android versions when
     * the user manually navigates there, and not subject to scoped-storage
     * restrictions for the owning app).
     *
     * Falls back to internal files dir if external storage is unavailable.
     */
    fun defaultSetupScriptDest(context: Context): File {
        val externalDir = context.getExternalFilesDir(null)
            ?: context.filesDir
        return File(externalDir, "setup.sh")
    }
}
