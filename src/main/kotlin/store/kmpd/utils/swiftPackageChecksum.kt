package store.kmpd.utils

import java.io.File

internal fun swiftPackageChecksum(
    file: File,
): String {
    val process = ProcessBuilder("swift", "package", "compute-checksum", file.path)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    val checksum = process.inputStream.reader().readText().dropLast(1)
    assert(process.waitFor() == 0)
    return checksum
}