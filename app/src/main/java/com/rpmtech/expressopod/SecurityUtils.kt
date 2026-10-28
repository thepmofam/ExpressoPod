package com.rpmtech.expressopod

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.io.File
import java.util.Locale

object SecurityUtils {

    private val ALLOWED_SCHEMES = setOf("http", "https", "mailto")

    /**
     * Checks if a URL string is safe to open externally or fetch.
     * Prevents arbitrary schemes like file://, content://, javascript:, or intent: redirects.
     */
    fun isSafeWebUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        val uri = try {
            Uri.parse(trimmed)
        } catch (_: Exception) {
            return false
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (!ALLOWED_SCHEMES.contains(scheme)) {
            return false
        }

        if (scheme == "http" || scheme == "https") {
            val host = uri.host
            if (host.isNullOrBlank()) return false
            if (trimmed.any { it.isISOControl() }) return false
        }

        return true
    }

    /**
     * Sanitizes and normalizes a user/feed web URL. If scheme is missing, defaults to https.
     * Returns null if the URL is invalid or unsafe.
     */
    fun sanitizeWebUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        val normalized = if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true) &&
            !trimmed.startsWith("mailto:", ignoreCase = true)
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }

        return if (isSafeWebUrl(normalized)) normalized else null
    }

    /**
     * Safely launches an external URL via Intent.ACTION_VIEW.
     * Validates scheme, adds FLAG_ACTIVITY_NEW_TASK, and catches exceptions (e.g. no browser installed).
     */
    fun openSafeUrl(context: Context, rawUrl: String?): Boolean {
        val safeUrl = sanitizeWebUrl(rawUrl)
        if (safeUrl == null) {
            Toast.makeText(context, "Invalid or unsafe link", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app available to open this link", Toast.LENGTH_SHORT).show()
            false
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Verifies that a target file's canonical path is safely contained within an expected base directory.
     * Prevents path traversal vulnerabilities (e.g. "../" escaping the sandbox).
     */
    fun isPathInsideDir(file: File, baseDir: File): Boolean {
        return try {
            val canonicalTargetPath = file.canonicalPath
            val canonicalBasePath = baseDir.canonicalPath
            canonicalTargetPath == canonicalBasePath || canonicalTargetPath.startsWith(canonicalBasePath + File.separator)
        } catch (_: Exception) {
            false
        }
    }
}
