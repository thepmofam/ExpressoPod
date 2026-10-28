package com.rpmtech.expressopod

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Keep
data class Chapter(
    @SerializedName("title") val title: String = "",
    @SerializedName("startMs") val startMs: Long = 0L,
    @SerializedName("endMs") val endMs: Long = 0L,
    @SerializedName("imgUrl") val imgUrl: String? = null,
    @SerializedName("url") val url: String? = null
) : Serializable {
    val durationMs: Long
        get() = if (endMs > startMs) endMs - startMs else 0L
}

object ChapterParser {
    fun parseTimestampToMs(timeStr: String): Long {
        try {
            val trimmed = timeStr.trim().removePrefix("[").removeSuffix("]").removePrefix("(").removeSuffix(")")
            if (trimmed.contains(":")) {
                val parts = trimmed.split(":")
                return when (parts.size) {
                    3 -> {
                        val h = parts[0].toLongOrNull() ?: 0L
                        val m = parts[1].toLongOrNull() ?: 0L
                        val s = parts[2].toDoubleOrNull() ?: 0.0
                        (h * 3600_000L) + (m * 60_000L) + (s * 1000L).toLong()
                    }
                    2 -> {
                        val m = parts[0].toLongOrNull() ?: 0L
                        val s = parts[1].toDoubleOrNull() ?: 0.0
                        (m * 60_000L) + (s * 1000L).toLong()
                    }
                    else -> 0L
                }
            } else {
                val sec = trimmed.toDoubleOrNull() ?: 0.0
                return (sec * 1000.0).toLong()
            }
        } catch (_: Exception) {
            return 0L
        }
    }

    fun parseChaptersJson(jsonStr: String): List<Chapter> {
        val list = mutableListOf<Chapter>()
        try {
            val element = com.google.gson.JsonParser.parseString(jsonStr)
            val array = if (element.isJsonObject && element.asJsonObject.has("chapters")) {
                element.asJsonObject.getAsJsonArray("chapters")
            } else if (element.isJsonArray) {
                element.asJsonArray
            } else null

            array?.forEach { item ->
                if (item.isJsonObject) {
                    val obj = item.asJsonObject
                    val rawStart = obj.get("startTime")?.asString ?: "0"
                    val startMs = parseTimestampToMs(rawStart)
                    val rawEnd = obj.get("endTime")?.asString
                    val endMs = if (rawEnd != null) parseTimestampToMs(rawEnd) else 0L
                    val title = obj.get("title")?.asString?.trim() ?: ""
                    val img = obj.get("img")?.asString?.trim()?.ifBlank { null }
                    val url = obj.get("url")?.asString?.trim()?.ifBlank { null }
                    if (title.isNotBlank()) {
                        list.add(Chapter(title = title, startMs = startMs, endMs = endMs, imgUrl = img, url = url))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedBy { it.startMs }
    }

    private val CHAPTER_TIMESTAMP_REGEX = Regex(
        """(?:^|[\n\r\(\[\s•\-])(?:(\d{1,2}):)?([0-5]?\d):([0-5]\d)(?:\)|\])?\s*[-–—:]?\s*(.+?)(?=\n|\r|$)""",
        RegexOption.MULTILINE
    )

    fun parseChaptersFromText(rawText: String, totalDurationMs: Long = 0L): List<Chapter> {
        if (rawText.isBlank()) return emptyList()
        val plainText = try {
            androidx.core.text.HtmlCompat.fromHtml(rawText, androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        } catch (_: Exception) {
            rawText
        }

        val chapters = mutableListOf<Chapter>()
        val matches = CHAPTER_TIMESTAMP_REGEX.findAll(plainText).toList()
        if (matches.size >= 2) {
            for (match in matches) {
                val h = match.groupValues[1]
                val m = match.groupValues[2]
                val s = match.groupValues[3]
                var title = match.groupValues[4].trim()
                
                title = title.removePrefix("-").removePrefix("–").removePrefix("—").trim()
                if (title.isBlank()) continue
                
                val hours = h.toLongOrNull() ?: 0L
                val mins = m.toLongOrNull() ?: 0L
                val secs = s.toDoubleOrNull() ?: 0.0
                val startMs = (hours * 3600_000L) + (mins * 60_000L) + (secs * 1000L).toLong()

                chapters.add(Chapter(title = title, startMs = startMs))
            }
        }
        return chapters.distinctBy { it.startMs }.sortedBy { it.startMs }
    }

    fun finalizeChapters(chapters: List<Chapter>, totalDurationMs: Long): List<Chapter> {
        if (chapters.isEmpty()) return emptyList()
        val sorted = chapters.sortedBy { it.startMs }
        val result = mutableListOf<Chapter>()
        for (i in sorted.indices) {
            val cur = sorted[i]
            val nextStart = if (i + 1 < sorted.size) sorted[i + 1].startMs else totalDurationMs
            val end = if (cur.endMs > cur.startMs) cur.endMs else if (nextStart > cur.startMs) nextStart else totalDurationMs
            result.add(cur.copy(endMs = end))
        }
        return result
    }
}
