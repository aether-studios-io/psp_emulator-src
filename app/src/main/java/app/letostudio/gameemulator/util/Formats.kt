package app.letostudio.gameemulator.util

import android.content.Context
import android.text.format.DateUtils
import app.letostudio.gameemulator.R
import java.util.Locale

object Formats {
    /** "12 h" / "45 min" (spec meta copy). */
    fun playTime(context: Context, seconds: Long): String {
        val minutes = (seconds / 60).toInt()
        return if (minutes >= 60) {
            context.getString(R.string.unit_hours, minutes / 60)
        } else {
            context.getString(R.string.unit_minutes, minutes.coerceAtLeast(1))
        }
    }

    /** "1.4 GB" / "820 MB". */
    fun size(bytes: Long): String {
        val gb = bytes / 1_073_741_824.0
        if (gb >= 1) return String.format(Locale.US, "%.1f GB", gb)
        val mb = bytes / 1_048_576.0
        if (mb >= 1) return String.format(Locale.US, "%.0f MB", mb)
        return String.format(Locale.US, "%.0f KB", (bytes / 1024.0).coerceAtLeast(1.0))
    }

    /** "2 h ago", "just now". */
    fun ago(at: Long, now: Long = System.currentTimeMillis()): String =
        if (now - at < 60_000) "just now" else DateUtils.getRelativeTimeSpanString(at, now, DateUtils.MINUTE_IN_MILLIS).toString().lowercase()

    /** "Today 18:42" style date time for slots. */
    fun dateTime(context: Context, at: Long): String =
        DateUtils.formatDateTime(context, at, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH)

    fun isToday(at: Long): Boolean = DateUtils.isToday(at)
}
