
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import java.io.File
import java.nio.file.Files
import java.security.KeyStore
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertNotNull

class SpmE2E {

    var daemon: Process? = null

    @AfterEach
    fun destroyDaemon() {
        daemon?.destroy()
    }

    @Test
    fun `spm http publication e2e`() {
        /**
         * FIXME:
         * This test requires keystore setup in the running JVM process and in the keychain
         * 1. Export cer via "keytool -exportcert -keystore keystore.jks -alias sampleAlias -file output.cer"
         * 2. Trust in keychain "security add-trusted-cert -p ssl -e certExpired -d -k ~/Library/Keychains/login.keychain output.cer"
         */

        val keyStoreFile = File("keystore.jks")
        val keyStore = KeyStore.getInstance(keyStoreFile, "123456".toCharArray())

        val environment = applicationEngineEnvironment {
            connector {
                port = 8080
            }
            sslConnector(
                keyStore = keyStore,
                keyAlias = "sampleAlias",
                keyStorePassword = { "123456".toCharArray() },
                privateKeyPassword = { "foobar".toCharArray() }) {
                port = 8443
                keyStorePath = keyStoreFile
            }
            module {
                routing {
                    put("/files/{file}") {
                        val fileName = assertNotNull(call.parameters["file"]).toString()
                        val file = File("build/functionalTest/uploadedFiles/${fileName}")
                        file.parentFile.mkdirs()
                        call.receiveChannel().copyAndClose(file.writeChannel())
                    }
                    get("/files/{file}") {
                        val fileName = assertNotNull(call.parameters["file"]).toString()
                        val file = File("build/functionalTest/uploadedFiles/${fileName}")
                        call.respondFile(file)
                    }
                }
            }
        }
        embeddedServer(Netty, environment = environment).start(wait = false)

        val testName = "httpPublicationE2E"
        val kotlinProjectDir = File("build/functionalTest/kotlin").resolve(testName)
        val swiftProjectDir = File("build/functionalTest/swift").resolve(testName).resolve("Consumer")
        val repositoryDir = File("build/functionalTest/spmRepo").resolve(testName)

        daemon = prepareGitRepo(
            repositoryDir = repositoryDir
        )

        prepareProject(
            projectDir = kotlinProjectDir,
            kotlinVersion = kotlinVersion,
            buildScript = """
                import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
                import store.kmpd.*
                import store.kmpd.spm.*
                
                kotlin {
                    val xcf = XCFramework("Kotlin")
                    listOf(
                        iosSimulatorArm64(),
                    ).forEach {
                        it.binaries.framework {
                            baseName = "Kotlin"
                            xcf.add(this)
                        }
                    }
                    
                    appleBinariesDeployment {
                        deployAsSwiftPackage(
                            deploymentName = "Kotlin",
                            swiftPackageConfiguration = SwiftPackageConfiguration(
                                packageDeployment = SPMPackageDeployment.GitDeployment(
                                    repository = "git://127.0.0.1/${testName}"
                                ),
                                version = Version.Specific(1, 0, 0),
                                packageName = SwiftPackageName.Specific("MyFramework"),
                            ),
                            xcframeworkDeployment = SPMXCFrameworkDeployment.HttpDeployment(
                                HttpStorageDeployment.Upload(
                                    username = "foo",
                                    password = "bar",
                                    uploadDirectoryUrl = "https://127.0.0.1:8443/files",
                                )
                            )
                        )
                    }
                }
            """.trimIndent(),
        )

        prepareSpmConsumer(
            projectDir = swiftProjectDir,
            testName = testName,
        )

        GradleRunner.create()
            .forwardOutput()
            .withArguments(
                ":deployKotlinAsSwiftPackageFromAssembleKotlinDebugXCFramework",
                "--info",
                "-Djavax.net.ssl.trustStore=keystore.jks",
                "-Djavax.net.ssl.trustStorePassword=123456",
            )
            .withProjectDir(kotlinProjectDir)
            .build()

        assert(
            ProcessBuilder()
                .command(
                    "xcodebuild", "build",
                    "-scheme", "Consumer",
                    "-destination", "generic/platform=iOS Simulator",
                    "-disablePackageRepositoryCache",
                    "-derivedDataPath", "dd",
                )
                .directory(swiftProjectDir)
                .inheritIO().start().waitFor() == 0
        )

        assert(
            swiftProjectDir.resolve("dd/Build/Products/Debug-iphonesimulator/Consumer.o").exists()
        )
    }

    @Test
    fun `spm git publication e2e`() {
        val testName = "gitPublicationE2E"
        val kotlinProjectDir = File("build/functionalTest/kotlin").resolve(testName)
        val swiftProjectDir = File("build/functionalTest/swift").resolve(testName).resolve("Consumer")
        val repositoryDir = File("build/functionalTest/spmRepo").resolve(testName)

        daemon = prepareGitRepo(
            repositoryDir = repositoryDir
        )

        prepareProject(
            projectDir = kotlinProjectDir,
            kotlinVersion = kotlinVersion,
            buildScript = """
                import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
                import store.kmpd.*
                import store.kmpd.spm.*
                
                kotlin {
                    val xcf = XCFramework("Kotlin")
                    listOf(
                        iosSimulatorArm64(),
                    ).forEach {
                        it.binaries.framework {
                            baseName = "Kotlin"
                            xcf.add(this)
                        }
                    }
                    
                    appleBinariesDeployment {
                        deployAsSwiftPackage(
                            deploymentName = "Kotlin",
                            swiftPackageConfiguration = SwiftPackageConfiguration(
                                packageDeployment = SPMPackageDeployment.GitDeployment(
                                    repository = "git://127.0.0.1/${testName}"
                                ),
                                version = Version.Specific(1, 0, 0),
                                packageName = SwiftPackageName.Specific("MyFramework"),
                            ),
                            xcframeworkDeployment = SPMXCFrameworkDeployment.GitDeployment()
                        )
                    }
                }
            """.trimIndent(),
        )
        prepareSpmConsumer(
            projectDir = swiftProjectDir,
            testName = testName,
        )

        GradleRunner.create()
            .forwardOutput()
            .withArguments(":deployKotlinAsSwiftPackageFromAssembleKotlinDebugXCFramework", "--info")
            .withProjectDir(kotlinProjectDir)
            .build()

        assert(
            ProcessBuilder()
                .command(
                    "xcodebuild", "build",
                    "-scheme", "Consumer",
                    "-destination", "generic/platform=iOS Simulator",
                    "-disablePackageRepositoryCache",
                    "-derivedDataPath", "dd",
                )
                .directory(swiftProjectDir)
                .inheritIO().start().waitFor() == 0
        )

        assert(
            swiftProjectDir.resolve("dd/Build/Products/Debug-iphonesimulator/Consumer.o").exists()
        )
    }

    private val kotlinVersion = "1.9.23"

    private fun prepareGitRepo(
        repositoryDir: File,
    ): Process {
        repositoryDir.mkdirs()

        ProcessBuilder()
            .command("git", "init", "--bare")
            .directory(repositoryDir)
            .inheritIO().start().waitFor()

        repositoryDir.resolve("git-daemon-export-ok").createNewFile()

        val daemon = ProcessBuilder()
            .command("git", "daemon", "--base-path=.", "--reuseaddr", "--enable=receive-pack", "--verbose")
            .directory(repositoryDir.parentFile)
            .inheritIO().start()

        return daemon
    }

    private fun prepareProject(
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

    private fun prepareSpmConsumer(
        projectDir: File,
        testName: String
    ) {
        projectDir.mkdirs()

        projectDir.resolve("Package.swift").writeText(
            """
            // swift-tools-version: 5.10

            import PackageDescription

            let package = Package(
                name: "Consumer",
                products: [
                    .library(
                        name: "Consumer",
                        targets: ["Consumer"]
                    ),
                ],
                dependencies: [
                    .package(
                        url: "git://127.0.0.1/${testName}",
                        branch: "1.0.0"
                    )
                ],
                targets: [
                    .target(
                        name: "Consumer",
                        dependencies: [
                            .product(name: "MyFramework", package: "${testName}")
                        ]
                    ),
                ]
            )
        """.trimIndent()
        )

        val sources = projectDir.resolve("Sources/Consumer")
        sources.mkdirs()

        sources.resolve("stub.swift").writeText("import Kotlin")
    }

}