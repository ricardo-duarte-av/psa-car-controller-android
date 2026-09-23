package pt.aguiarvieira.psacc.util

/**
 * How the app's release compares with its server's. The app and the forked daemon release in step
 * (the same `X.Y.Z` is made for each other), so a difference means one of them should be updated.
 */
enum class VersionMatch { SAME, APP_BEHIND, SERVER_BEHIND, UNKNOWN }

object Versions {

    /** `0.1.24`, `v0.1.24` or `0.1.24-debug` → [0, 1, 24]; null when it isn't a release number. */
    fun parse(version: String?): List<Int>? {
        val core = version?.trim()?.removePrefix("v")?.substringBefore('-')?.substringBefore('+') ?: return null
        val parts = core.split('.').map { it.toIntOrNull() ?: return null }
        return parts.takeIf { it.size == 3 }
    }

    fun compare(app: String?, server: String?): VersionMatch {
        val a = parse(app) ?: return VersionMatch.UNKNOWN
        val s = parse(server) ?: return VersionMatch.UNKNOWN
        // A daemon run from its sources reports 0.0.0 (or "unknown"): nothing to compare with.
        if (s.all { it == 0 }) return VersionMatch.UNKNOWN
        for (i in a.indices) {
            if (a[i] != s[i]) return if (a[i] < s[i]) VersionMatch.APP_BEHIND else VersionMatch.SERVER_BEHIND
        }
        return VersionMatch.SAME
    }
}
