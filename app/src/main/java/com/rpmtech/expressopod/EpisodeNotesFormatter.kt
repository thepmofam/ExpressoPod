package com.rpmtech.expressopod

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.widget.Toast
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat

object EpisodeNotesFormatter {

    private val SCRIPT_STYLE_REGEX = Regex("""<(?:script|style)[^>]*>[\s\S]*?</(?:script|style)>""", RegexOption.IGNORE_CASE)
    private val HAS_HTML_REGEX = Regex("""<(?:p|div|br|h[1-6]|ul|ol|li)\b""", RegexOption.IGNORE_CASE)
    private val MD_LINK_REGEX = Regex("""\[([^\]\r\n]+)\]\((https?://[^\s)]+)\)""")
    private val MD_BOLD_REGEX = Regex("""(?:\*\*|__)(.*?)(?:\*\*|__)""")
    private val MD_ITALIC_STAR_REGEX = Regex("""(?<!\*)\*([^*\r\n]+)\*(?!\*)""")
    private val MD_ITALIC_UNDERSCORE_REGEX = Regex("""(?<!_)_([^_\r\n]+)_(?!_)""")
    private val MD_HEADING_REGEX = Regex("""(?m)^#{1,6}\s*(.+)$""")
    private val BOLD_TITLE_REGEX = Regex("""(?mi)(?:<p>\s*)?<(?:b|strong)>([A-Z0-9][^<\n\r]{1,80}?)</(?:b|strong)>(?:\s*</p>|\s*<br\s*/?>)?""")
    private val MD_BULLET_REGEX = Regex("""(?m)^[*\-]\s+(.+)$""")
    private val MD_NUMBERED_REGEX = Regex("""(?m)^(\d+\.)\s+(.+)$""")
    private val DOUBLE_NEWLINE_REGEX = Regex("""(\r?\n\s*){2,}""")
    private val SINGLE_NEWLINE_REGEX = Regex("""\r?\n""")
    private val EXCESS_BR_REGEX = Regex("""(?:<br\s*/?>\s*){3,}""", RegexOption.IGNORE_CASE)
    private val EMPTY_P_REGEX = Regex("""<p>\s*(?:&nbsp;|<br\s*/?>|\s)*</p>""", RegexOption.IGNORE_CASE)
    private val HEADING_BR_START_REGEX = Regex("""(?:<br\s*/?>|\s)*<h([1-6])[^>]*>""", RegexOption.IGNORE_CASE)
    private val HEADING_BR_END_REGEX = Regex("""</h([1-6])>(?:<br\s*/?>|\s)*""", RegexOption.IGNORE_CASE)
    private val P_END_BR_REGEX = Regex("""(?:<br\s*/?>|\s)*</p>""", RegexOption.IGNORE_CASE)
    private val P_START_BR_REGEX = Regex("""<p>(?:<br\s*/?>|\s)*""", RegexOption.IGNORE_CASE)
    private val RAW_URL_REGEX = Regex("""https?://[a-zA-Z0-9.\-_~:/?#\[\]@!$&'()*+,;=%]+[a-zA-Z0-9/_\-#]""")
    private val TIMESTAMP_REGEX = Regex("""(?:\(|\[)?\b(\d{1,2}:\d{2}(?::\d{2})?)\b(?:\)|\])?""")

    /**
     * Cleans and normalizes raw show notes that may be HTML, Markdown, or plain text
     * into well-formed HTML suitable for Spanned parsing, ensuring clean separation
     * and visible spacing around headers and between paragraphs.
     */
    fun cleanRawNotesHtml(rawText: String): String {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return ""

        // 1. Remove dangerous or non-content tags
        var s = trimmed.replace(SCRIPT_STYLE_REGEX, "")

        // 2. Check if the text is predominantly plain text (lacks HTML block tags)
        val hasHtml = s.contains(HAS_HTML_REGEX)

        // 3. Normalize common Markdown syntax in both HTML and plain text
        // Markdown Links: [Anchor](https://...) -> <a href="https://...">Anchor</a>
        s = s.replace(MD_LINK_REGEX) { match ->
            val anchor = match.groupValues[1]
            val url = match.groupValues[2]
            "<a href=\"$url\">$anchor</a>"
        }

        // Markdown Bold: **text** or __text__ -> <b>text</b>
        s = s.replace(MD_BOLD_REGEX) { match ->
            "<b>${match.groupValues[1]}</b>"
        }

        // Markdown Italics: *text* or _text_ -> <i>text</i>
        s = s.replace(MD_ITALIC_STAR_REGEX) { match ->
            "<i>${match.groupValues[1]}</i>"
        }
        s = s.replace(MD_ITALIC_UNDERSCORE_REGEX) { match ->
            "<i>${match.groupValues[1]}</i>"
        }

        // Markdown Headings: # Heading -> <h3>Heading</h3>
        s = s.replace(MD_HEADING_REGEX) { match ->
            "<h3>${match.groupValues[1].trim()}</h3>"
        }

        // Standalone bold section titles/headers (e.g. <p><b>SPONSORS:</b></p> or <b>Links:</b><br/> or **Topics:**)
        s = s.replace(BOLD_TITLE_REGEX) { match ->
            "<h4>${match.groupValues[1].trim()}</h4>"
        }

        if (!hasHtml) {
            // For Markdown bullet lists
            s = s.replace(MD_BULLET_REGEX) { match ->
                "• ${match.groupValues[1]}"
            }
            // For Markdown numbered lists
            s = s.replace(MD_NUMBERED_REGEX) { match ->
                "${match.groupValues[1]} ${match.groupValues[2]}"
            }
            // Plain text paragraph formatting
            s = s.replace(DOUBLE_NEWLINE_REGEX, "</p><p>")
            s = s.replace(SINGLE_NEWLINE_REGEX, "<br/>")
            s = "<p>$s</p>"
        } else {
            // Clean up excessive HTML breaks and empty paragraphs
            s = s.replace(EXCESS_BR_REGEX, "<br/><br/>")
            s = s.replace(EMPTY_P_REGEX, "")
        }

        // Strip tight <br> around heading tags so LEGACY mode produces clean, balanced empty lines around headers
        s = s.replace(HEADING_BR_START_REGEX) { match ->
            "<h${match.groupValues[1]}>"
        }
        s = s.replace(HEADING_BR_END_REGEX) { match ->
            "</h${match.groupValues[1]}>"
        }
        s = s.replace(P_END_BR_REGEX, "</p>")
        s = s.replace(P_START_BR_REGEX, "<p>")

        return s.trim()
    }

    /**
     * Converts raw show notes (HTML, Markdown, or plain text) into an AnnotatedString
     * with preserved paragraphs, bold/italic text styles, spaced headings, and clickable, styled links.
     */
    fun parseNotesToAnnotatedString(
        rawText: String,
        linkColor: Color,
        context: Context,
        onSeekToMs: ((Long) -> Unit)? = null
    ): AnnotatedString {
        if (rawText.isBlank()) return AnnotatedString("")

        val cleanedHtml = cleanRawNotesHtml(rawText)
        if (cleanedHtml.isBlank()) return AnnotatedString("")

        // Use FROM_HTML_MODE_LEGACY: ensures 2 newlines (\n\n) around headers (<h1-h6>) and paragraphs (<p>)
        val spanned: Spanned = try {
            HtmlCompat.fromHtml(cleanedHtml, HtmlCompat.FROM_HTML_MODE_LEGACY)
        } catch (_: Exception) {
            HtmlCompat.fromHtml(rawText, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }

        val fullText = spanned.toString()
        if (fullText.isBlank()) return AnnotatedString("")

        // Calculate trimmed boundaries so leading/trailing whitespace doesn't distort presentation
        val trimmedStart = fullText.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
        val trimmedEnd = (fullText.indexOfLast { !it.isWhitespace() } + 1).coerceAtMost(fullText.length)
        if (trimmedStart >= trimmedEnd) return AnnotatedString("")

        val trimmedText = fullText.substring(trimmedStart, trimmedEnd)
        val trimmedLength = trimmedText.length
        val builder = AnnotatedString.Builder(trimmedText)

        val linkStyles = TextLinkStyles(
            style = SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline,
                fontWeight = FontWeight.SemiBold
            )
        )

        // Track ranges that are already covered by links
        val coveredRanges = mutableListOf<IntRange>()

        // Process all Spanned markup elements
        val spans = spanned.getSpans(0, spanned.length, Any::class.java)
        for (span in spans) {
            val rawStart = spanned.getSpanStart(span)
            val rawEnd = spanned.getSpanEnd(span)
            val start = (rawStart - trimmedStart).coerceIn(0, trimmedLength)
            val end = (rawEnd - trimmedStart).coerceIn(0, trimmedLength)
            if (start >= end) continue

            when (span) {
                is URLSpan -> {
                    val rawUrl = span.url
                    val safeUrl = SecurityUtils.sanitizeWebUrl(rawUrl) ?: rawUrl

                    builder.addLink(
                        LinkAnnotation.Url(
                            url = safeUrl,
                            styles = linkStyles,
                            linkInteractionListener = { link ->
                                val target = (link as? LinkAnnotation.Url)?.url ?: safeUrl
                                SecurityUtils.openSafeUrl(context, target)
                            }
                        ),
                        start,
                        end
                    )
                    coveredRanges.add(start until end)
                }

                is StyleSpan -> {
                    when (span.style) {
                        Typeface.BOLD -> builder.addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                        Typeface.ITALIC -> builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                        Typeface.BOLD_ITALIC -> builder.addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                            start,
                            end
                        )
                    }
                }

                is UnderlineSpan -> {
                    builder.addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                }

                is RelativeSizeSpan -> {
                    if (span.sizeChange > 1.05f) {
                        builder.addStyle(
                            SpanStyle(
                                fontSize = (16.5f * span.sizeChange.coerceIn(1.15f, 1.45f)).sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            start,
                            end
                        )
                    }
                }

                is TypefaceSpan -> {
                    val fam = span.family
                    if (fam != null && (fam.contains("mono", ignoreCase = true) || fam.contains("courier", ignoreCase = true))) {
                        builder.addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, end)
                    }
                }
            }
        }

        // Autolink unlinked URLs in the text (e.g. plain "https://...")
        for (match in RAW_URL_REGEX.findAll(trimmedText)) {
            val sStart = match.range.first
            val sEnd = match.range.last + 1
            val isOverlapping = coveredRanges.any { it.first < sEnd && it.last >= sStart }
            if (!isOverlapping) {
                val matchedUrl = match.value
                val safeUrl = SecurityUtils.sanitizeWebUrl(matchedUrl) ?: matchedUrl
                builder.addLink(
                    LinkAnnotation.Url(
                        url = safeUrl,
                        styles = linkStyles,
                        linkInteractionListener = { link ->
                            val target = (link as? LinkAnnotation.Url)?.url ?: safeUrl
                            SecurityUtils.openSafeUrl(context, target)
                        }
                    ),
                    sStart,
                    sEnd
                )
                coveredRanges.add(sStart until sEnd)
            }
        }

        // Interactive Timestamp seeking: If onSeekToMs is provided, turn timestamps (e.g. 05:24 or 1:02:15) into clickable seek links
        if (onSeekToMs != null) {
            val timestampLinkStyles = TextLinkStyles(
                style = SpanStyle(
                    color = linkColor,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )
            )

            for (match in TIMESTAMP_REGEX.findAll(trimmedText)) {
                val timeStr = match.groups[1]?.value ?: continue
                val range = match.groups[1]!!.range
                val tStart = range.first
                val tEnd = range.last + 1

                val isOverlapping = coveredRanges.any { it.first < tEnd && it.last >= tStart }
                if (!isOverlapping) {
                    val ms = ChapterParser.parseTimestampToMs(timeStr)
                    if (ms >= 0L) {
                        builder.addLink(
                            LinkAnnotation.Clickable(
                                tag = "timestamp_$ms",
                                styles = timestampLinkStyles,
                                linkInteractionListener = {
                                    onSeekToMs(ms)
                                }
                            ),
                            tStart,
                            tEnd
                        )
                        coveredRanges.add(tStart until tEnd)
                    }
                }
            }
        }

        return builder.toAnnotatedString()
    }
}

@Composable
fun FormattedEpisodeNotes(
    notesText: String,
    modifier: Modifier = Modifier,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    onSeekToMs: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val annotatedNotes = remember(notesText, linkColor) {
        EpisodeNotesFormatter.parseNotesToAnnotatedString(
            rawText = notesText,
            linkColor = linkColor,
            context = context,
            onSeekToMs = onSeekToMs
        )
    }

    if (annotatedNotes.text.isNotBlank()) {
        SelectionContainer {
            Text(
                text = annotatedNotes,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = modifier
            )
        }
    }
}
