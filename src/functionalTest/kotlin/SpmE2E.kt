
import io.ktor.server.engine.*
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import java.io.File
import kotlin.test.Test

class SpmE2E {

    var daemon: Process? = null
    var engine: ApplicationEngine? = null

    @AfterEach
    fun destroySubprocesses() {
        daemon?.destroy()
        engine?.stop()
        daemon = null
        engine = null
    }

    @Test
    fun `spm http publication e2e`() {
        engine = prepareUploadServer()

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