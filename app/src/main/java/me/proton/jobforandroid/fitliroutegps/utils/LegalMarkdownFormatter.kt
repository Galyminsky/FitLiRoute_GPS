package me.proton.jobforandroid.fitliroutegps.utils

import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan

/** Formats the headings, lists and bold text used by the bundled legal documents. */
object LegalMarkdownFormatter {
    private val boldPattern = Regex("\\*\\*(.+?)\\*\\*")

    fun format(markdown: String): CharSequence {
        val result = SpannableStringBuilder()
        val lines = markdown.replace("\r\n", "\n").split('\n')
        lines.forEachIndexed { index, original ->
            val headingLevel = when {
                original.startsWith("## ") -> 2
                original.startsWith("# ") -> 1
                else -> 0
            }
            val isListItem = original.startsWith("- ")
            val line = when {
                headingLevel == 2 -> original.substring(3)
                headingLevel == 1 || isListItem -> original.substring(2)
                else -> original
            }
            val start = result.length
            if (isListItem) {
                result.append("• ")
                result.setSpan(ForegroundColorSpan(Color.rgb(255, 105, 59)), start, start + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            appendBoldText(result, line)
            if (headingLevel != 0) {
                result.setSpan(StyleSpan(Typeface.BOLD), start, result.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                result.setSpan(RelativeSizeSpan(if (headingLevel == 1) 1.45f else 1.18f),
                    start, result.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (index < lines.lastIndex) result.append('\n')
        }
        return result
    }

    private fun appendBoldText(result: SpannableStringBuilder, line: String) {
        var cursor = 0
        for (match in boldPattern.findAll(line)) {
            result.append(line, cursor, match.range.first)
            val start = result.length
            result.append(match.groupValues[1])
            result.setSpan(StyleSpan(Typeface.BOLD), start, result.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            cursor = match.range.last + 1
        }
        result.append(line, cursor, line.length)
    }
}
