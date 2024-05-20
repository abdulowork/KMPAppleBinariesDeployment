package store.kmpd.spm

import org.gradle.process.ExecOperations
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.writeText


internal fun createSpmGitRelease(
    repository: String,
    base: String?,
    version: String,
    cloneBase: Path,
    packageName: String,
    xcframeworkLocation: SPMXCFrameworkLocation,
    execOperations: ExecOperations,
) {
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

    val packagePath = repoPath.resolve("Package.swift")
    packagePath.writeText(
        defaultPackageTemplate(
            packageName = packageName,
            xcframeworkLocation = xcframeworkLocation
        )
    )

    when (xcframeworkLocation) {
        is SPMXCFrameworkLocation.EmbedInGitRepository -> {
            xcframeworkLocation.xcframeworkPath.copyRecursively(
                repoPath.resolve(xcframeworkLocation.xcframeworkPath.name).toFile(),
                overwrite = true,
            )
        }
        else -> {}
    }

    execOperations.exec {
        it.commandLine("git", "add", "-A")
        it.workingDir = repoPath.toFile()
    }

    execOperations.exec {
        it.commandLine("git", "commit", "-m", "XCFramework publication: ${xcframeworkLocation}, $version")
        it.workingDir = repoPath.toFile()
    }

    execOperations.exec {
        it.commandLine("git", "tag", version)
        it.workingDir = repoPath.toFile()
    }

    execOperations.exec {
        it.commandLine("git", "branch", version)
        it.workingDir = repoPath.toFile()
    }

    execOperations.exec {
        it.commandLine(
            "git", "push", "origin",
            "refs/heads/${version}",
            "refs/tags/${version}",
        )
        it.workingDir = repoPath.toFile()
    }
}

private fun defaultPackageTemplate(
    packageName: String,
    xcframeworkLocation: SPMXCFrameworkLocation,
): String {
    val xcframeworkTargetParameters = when (xcframeworkLocation) {
        is SPMXCFrameworkLocation.EmbedInGitRepository -> """
            path: "${xcframeworkLocation.xcframeworkPath.name}"
        """.trimIndent()
        is SPMXCFrameworkLocation.HttpDeployment -> """
            url: "${xcframeworkLocation.binaryUrl}",
            checksum: "${xcframeworkLocation.checksum}"
        """.trimIndent()
    }

    return """
        // swift-tools-version: 5.7
        
        import PackageDescription

        let package = Package(
            name: "${packageName}",
            products: [
                .library(
                    name: "${packageName}",
                    targets: ["${packageName}"]
                ),
            ],
            targets: [
                .binaryTarget(
                    name: "${packageName}",
                    ${xcframeworkTargetParameters}
                )
            ]
        )
    """.trimIndent()
}