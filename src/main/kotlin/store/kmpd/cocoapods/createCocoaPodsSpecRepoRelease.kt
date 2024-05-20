package store.kmpd.cocoapods

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.gradle.process.ExecOperations
import store.kmpd.Version
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.writeText

fun createCocoaPodsSpecRepoRelease(
    podspecDeployment: CocoaPodsSpecDeployment.SpecRepository,
    deployedXCFramework: String,
    kgpProducedPodspec: File,
    base: String?,
    cloneBase: Path,
    version: Version,
    execOperations: ExecOperations,
) {
    // FIXME: Validate with "pod spec lint"?
    val finalPodspecJson = preparePodspecJsonWithHttpSource(
        kgpProducedPodspec = kgpProducedPodspec,
        deployedXCFramework = deployedXCFramework,
        version = version,
    )

    val repository = podspecDeployment.repository
    val repoDirName = "repo"
    val repoPath = cloneBase.resolve(repoDirName)

    execOperations.exec {
        it.commandLine(
            "git",
            "clone",
            "--depth=1",
            "--",
            repository,
            repoPath.pathString
        )
    }

    if (base != null) {
        execOperations.exec {
            it.commandLine("git", "fetch", "--depth=1", "origin", "${base}:${base}")
            it.workingDir = repoPath.toFile()
        }
        execOperations.exec {
            it.commandLine("git", "checkout", base)
            it.workingDir = repoPath.toFile()
        }
    }

    val podspecName = kgpProducedPodspec.nameWithoutExtension

    val podspecVersionRoot = repoPath
        .resolve(podspecName)
        .resolve(version.versionString)
    podspecVersionRoot.createDirectories()

    podspecVersionRoot.resolve("${podspecName}.podspec.json").writeText(
        Json{ prettyPrint = true }.encodeToString(finalPodspecJson)
    )

    execOperations.exec {
        it.commandLine("git", "add", "-A")
        it.workingDir = repoPath.toFile()
    }

    execOperations.exec {
        it.commandLine("git", "commit", "-m", "Podspec publication: ${podspecDeployment}, $version")
        it.workingDir = repoPath.toFile()
    }

    execOperations.exec {
        it.commandLine(
            "git", "push", "origin", "HEAD",
        )
        it.workingDir = repoPath.toFile()
    }
}