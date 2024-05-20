package store.kmpd.cocoapods

import kotlinx.serialization.json.*
import store.kmpd.Version
import java.io.File

fun preparePodspecJsonWithHttpSource(
    kgpProducedPodspec: File,
    deployedXCFramework: String,
    version: Version,
): JsonObject {
    val process = ProcessBuilder(
        "pod", "ipc", "spec", kgpProducedPodspec.canonicalPath,
    ).redirectError(ProcessBuilder.Redirect.INHERIT).start()
    val json = process.inputStream.reader().readText().dropLast(1)
    assert(process.waitFor() == 0)

    val originalPodspecJson = Json.decodeFromString<JsonObject>(json)
    val finalPodspecJson = buildJsonObject {
        originalPodspecJson.forEach {
            put(it.key, it.value)
        }
        put(
            "source",
            buildJsonObject { put("http", deployedXCFramework) },
        )
        put(
            "version",
            version.versionString,
        )
        if (originalPodspecJson["homepage"]?.jsonPrimitive?.contentOrNull.isNullOrEmpty()) {
            println("Stubbing 'homepage' value since it cannot be empty")
            put("homepage", "stub")
        }
        if (originalPodspecJson["summary"]?.jsonPrimitive?.contentOrNull.isNullOrEmpty()) {
            println("Stubbing 'summary' value since it cannot be empty")
            put("summary", "stub")
        }
    }
    return finalPodspecJson
}