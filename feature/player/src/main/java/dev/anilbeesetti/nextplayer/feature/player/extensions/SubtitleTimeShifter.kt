package dev.anilbeesetti.nextplayer.feature.player.extensions

object SubtitleTimeShifter {
    fun shiftSrt(
        input: String,
        offsetMs: Long
    ): String {
        val regex = Regex("(\\d{2}:\\d{2}:\\d{2},\\d{3})")
        return regex.replace(input) { match ->
            formatSrtTime(
                (parseSrtTime(match.value) + offsetMs).coerceAtLeast(0)
            )
        }
    }

    private fun parseSrtTime(time: String): Long {
        val (h, m, rest) = time.split(":")
        val (s, ms) = rest.split(",")
        return h.toLong() * 3_600_000 +
                m.toLong() * 60_000 +
                s.toLong() * 1_000 +
                ms.toLong()
    }

    private fun formatSrtTime(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1_000
        val millis = ms % 1_000
        return String.format(
            "%02d:%02d:%02d,%03d",
            hours, minutes, seconds, millis
        )
    }

    fun shiftAssSsa(
        input: String,
        offsetMs: Long
    ): String {
        return input.lineSequence().joinToString("\n") { line ->
            if (!line.startsWith("Dialogue:", ignoreCase = true)) return@joinToString line

            val parts = line.split(",", limit = 10)
            if (parts.size < 3) return@joinToString line

            val start = parseAssTime(parts[1])
            val end = parseAssTime(parts[2])

            parts.toMutableList().apply {
                this[1] = formatAssTime((start + offsetMs).coerceAtLeast(0))
                this[2] = formatAssTime((end + offsetMs).coerceAtLeast(0))
            }.joinToString(",")
        }
    }

    private fun parseAssTime(time: String): Long {
        val (h, m, rest) = time.split(":")
        val (s, cs) = rest.split(".")
        return h.toLong() * 3_600_000 +
                m.toLong() * 60_000 +
                s.toLong() * 1_000 +
                cs.toLong() * 10
    }

    private fun formatAssTime(ms: Long): String {
        val t = ms.coerceAtLeast(0)
        val hours = t / 3_600_000
        val minutes = (t % 3_600_000) / 60_000
        val seconds = (t % 60_000) / 1_000
        val centis = (t % 1_000) / 10
        return String.format(
            "%d:%02d:%02d.%02d",
            hours, minutes, seconds, centis
        )
    }

    fun shiftVtt(
        input: String,
        offsetMs: Long
    ): String {
        val regex = Regex("(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})")
        return regex.replace(input) { match ->
            formatVttTime(
                (parseVttTime(match.value) + offsetMs).coerceAtLeast(0)
            )
        }
    }

    private fun parseVttTime(time: String): Long {
        val (h, m, rest) = time.split(":")
        val (s, ms) = rest.split(".")
        return h.toLong() * 3_600_000 +
                m.toLong() * 60_000 +
                s.toLong() * 1_000 +
                ms.toLong()
    }

    private fun formatVttTime(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1_000
        val millis = ms % 1_000
        return String.format(
            "%02d:%02d:%02d.%03d",
            hours, minutes, seconds, millis
        )
    }

    fun shiftTtml(
        input: String,
        offsetMs: Long
    ): String {
        if (!input.contains("begin=\"")) {
            return input
        }

        val regex = Regex("""(begin|end)="([^"]+)"""")
        return regex.replace(input) { match ->
            val original = match.groupValues[2]
            val parsed = parseTtmlTime(original)
                ?: return@replace match.value

            val shifted = (parsed + offsetMs).coerceAtLeast(0)
            """${match.groupValues[1]}="${formatTtmlTime(shifted)}""""
        }
    }

    private fun parseTtmlTime(time: String): Long? {
        if (time.contains(":")) {
            val parts = time.split(":")
            if (parts.size < 3) return null

            val secPart = parts[2]
            val (s, ms) = if (secPart.contains(".")) {
                secPart.split(".", limit = 2)
            } else {
                listOf(secPart, "0")
            }

            return parts[0].toLongOrNull()?.times(3_600_000)
                ?.plus(parts[1].toLongOrNull()?.times(60_000) ?: return null)
                ?.plus(s.toLongOrNull()?.times(1_000) ?: return null)
                ?.plus(ms.padEnd(3, '0').take(3).toLong())
        }

        if (time.endsWith("s")) {
            val v = time.removeSuffix("s").toDoubleOrNull() ?: return null
            return (v * 1000).toLong()
        }

        if (time.endsWith("ms")) {
            return time.removeSuffix("ms").toLongOrNull()
        }

        return null
    }

    private fun formatTtmlTime(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        val seconds = (ms % 60_000) / 1_000
        val millis = ms % 1_000
        return String.format(
            "%02d:%02d:%02d.%03d",
            hours, minutes, seconds, millis
        )
    }
}