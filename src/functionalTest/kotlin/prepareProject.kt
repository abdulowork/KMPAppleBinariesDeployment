import java.io.File
import java.nio.file.Files
import kotlin.io.path.createFile
import kotlin.io.path.exists

fun prepareProject(
    projectDir: File,
    kotlinVersion: String,
    otherPlugins: String = "",
    buildScript: String,
) {
    projectDir.mkdirs()

    File(projectDir, "settings.gradle.kts").writeText(
        """
            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                }
            }
            
            pluginManagement {
                repositories {
                    maven("file://${projectDir.absoluteFile.parentFile.parentFile.parentFile.parentFile.resolve("repo").canonicalPath}")
                    gradlePluginPortal()
                }
            }
        """.trimIndent()
    )

    File(projectDir, "build.gradle.kts").writeText(
        """
            plugins {
                id("store.kmpd.plugin") version "+"
                kotlin("multiplatform") version "$kotlinVersion"
                $otherPlugins
            }

            $buildScript
        """.trimIndent()
    )

    val sources = projectDir.resolve("src/commonMain/kotlin").toPath()
    if (!sources.exists()) {
        Files.createDirectories(sources)
        sources.resolve("stub.kt").createFile()
    }
}