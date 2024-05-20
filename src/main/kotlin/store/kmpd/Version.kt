package store.kmpd

import java.time.Instant

sealed class Version(val versionString: String) {
    data class UsePatchTimestamp(
        val major: Int = 0,
        val minor: Int = 0,
    ) : Version("$major.$minor.${Instant.now().epochSecond}")

    data class Specific(
        val major: Int,
        val minor: Int,
        val patch: Int,
    ) : Version("$major.$minor.$patch")
}