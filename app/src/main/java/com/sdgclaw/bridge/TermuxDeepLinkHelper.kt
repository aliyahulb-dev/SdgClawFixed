package com.sdgclaw.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * TermuxDeepLinkHelper — fires intent-based deep links into Termux to run a
 * command without user interaction.
 *
 * Two strategies are attempted in order:
 *  1. `com.termux.RUN_COMMAND` broadcast (requires Termux:API + allow-external-apps=true)
 *  2. `termux://` URI via [Intent.ACTION_VIEW] as a fallback
 */
object TermuxDeepLinkHelper {

    private const val TAG = "TermuxDeepLinkHelper"

    private const val TERMUX_PACKAGE           = "com.termux"
    private const val ACTION_RUN_COMMAND       = "com.termux.RUN_COMMAND"
    private const val EXTRA_COMMAND_PATH       = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_COMMAND_ARGUMENTS  = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_COMMAND_WORKDIR    = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"

    /**
     * Ask Termux to execute [command] in a background session.
     *
     * Must be called on the **main thread** (startActivity requirement).
     */
    fun runCommand(context: Context, command: String) {
        if (!isTermuxInstalled(context)) {
            Log.w(TAG, "Termux not installed — cannot fire deep link")
            return
        }

        try {
            // Strategy 1: RUN_COMMAND broadcast
            val broadcastIntent = Intent(ACTION_RUN_COMMAND).apply {
                setPackage(TERMUX_PACKAGE)
                putExtra(EXTRA_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
                putExtra(EXTRA_COMMAND_ARGUMENTS, arrayOf("-c", command))
                putExtra(EXTRA_COMMAND_WORKDIR, "/data/data/com.termux/files/home")
                putExtra(EXTRA_COMMAND_BACKGROUND, true)
            }
            context.sendBroadcast(broadcastIntent)
            Log.i(TAG, "Fired RUN_COMMAND broadcast: $command")
        } catch (e: Exception) {
            Log.w(TAG, "RUN_COMMAND broadcast failed (${e.message}), trying URI deep link")
            try {
                // Strategy 2: termux:// URI
                val uri = Uri.parse("termux://run-command?cmd=${Uri.encode(command)}")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.i(TAG, "Fired termux:// URI deep link")
            } catch (e2: Exception) {
                Log.e(TAG, "Both deep-link strategies failed", e2)
            }
        }
    }

    /** Returns true if the Termux app is installed on the device. */
    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
