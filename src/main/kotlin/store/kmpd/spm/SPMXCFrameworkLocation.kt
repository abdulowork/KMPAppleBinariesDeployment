package store.kmpd.spm

import java.io.File

sealed class SPMXCFrameworkLocation {
    data class EmbedInGitRepository(
        val xcframeworkPath: File
    ) : SPMXCFrameworkLocation()

    data class HttpDeployment(
        val binaryUrl: String,
        val checksum: String,
    ) : SPMXCFrameworkLocation()
}