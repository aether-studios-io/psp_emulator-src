package app.letostudio.gameemulator.util

/**
 * Query parameters whose value must never reach a log line. Add to this list rather
 * than writing a second redactor — every logging path is expected to funnel through
 * [redactSecrets].
 */
private val SECRET_PARAMS =
    Regex("""(?i)(password|token|api_?key|secret|access_token)=[^&\s"']*""")

/** Replaces the VALUE of any known secret query parameter with `***`. */
fun redactSecrets(text: String): String =
    SECRET_PARAMS.replace(text) { match -> match.value.substringBefore('=') + "=***" }
