import io.ktor.server.engine.*
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import java.io.File
import kotlin.test.Test

class CocoaPodsE2E {

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
    fun `cocoapods git publication e2e`() {
        val testName = "gitCocoaPodsPublicationE2E"
        val kotlinProjectDir = File("build/functionalTest/kotlin").resolve(testName)
        val consumerProjectDir = File("build/functionalTest/PodConsumer").resolve(testName).resolve("Consumer")
        val repositoryDir = File("build/functionalTest/cocoaPodsRepo").resolve(testName)

        daemon = prepareGitRepo(
            repositoryDir = repositoryDir
        )

        prepareProject(
            projectDir = kotlinProjectDir,
            kotlinVersion = kotlinVersion,
            otherPlugins = "kotlin(\"native.cocoapods\") version \"${kotlinVersion}\"",
            buildScript = """
                import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
                import store.kmpd.*
                import store.kmpd.cocoapods.*
                
                kotlin {
                    iosSimulatorArm64()
                    
                    cocoapods {
                        version = "1.0.0"
                        
                        framework {
                            baseName = "Kotlin"
                        }
                    }
                    
                    appleBinariesDeployment {
                        deployAsCocoaPodsSpec(
                            deploymentName = "Kotlin",
                            podspecDeployment = CocoaPodsSpecDeployment.GitRepository(
                                repository = "git://127.0.0.1/${testName}",
                            ),
                            xcframeworkDeployment = CocoaPodsXCFrameworkDeployment.GitDeployment(),
                            version = Version.Specific(1, 0, 0),
                        )
                    }
                }
            """.trimIndent(),
        )

        File("src/functionalTest/resources/PodConsumer").copyRecursively(consumerProjectDir)
        consumerProjectDir.resolve("Podfile").writeText(
            """
                target 'PodConsumer' do
                  use_frameworks!
                  pod '${testName}', :git => 'git://127.0.0.1/${testName}', :tag => '1.0.0'
                end
            """.trimIndent()
        )

        GradleRunner.create()
            .forwardOutput()
            .withArguments(":deployKotlinAsCocoaPodsPodspecFromPodPublishReleaseXCFramework", "--info")
            .withProjectDir(kotlinProjectDir)
            .build()

        assert(
            ProcessBuilder()
                .command(
                    "env", "COCOAPODS_SKIP_CACHE=1",
                    "pod", "install",
                )
                .directory(consumerProjectDir)
                .inheritIO().start().waitFor() == 0
        )

        assert(
            ProcessBuilder()
                .command(
                    "xcodebuild", "build",
                    "-workspace", "PodConsumer.xcworkspace",
                    "-scheme", "PodConsumer",
                    "-destination", "generic/platform=iOS Simulator",
                    "-derivedDataPath", "dd",
                    "ARCHS=arm64",
                )
                .directory(consumerProjectDir)
                .inheritIO().start().waitFor() == 0
        )
    }

    @Test
    fun `cocoapods spec publication e2e`() {
        engine = prepareUploadServer()

        val testName = "specRepositoryCocoaPodsPublicationE2E"
        val kotlinProjectDir = File("build/functionalTest/kotlin").resolve(testName)
        val consumerProjectDir = File("build/functionalTest/PodConsumer").resolve(testName).resolve("Consumer")
        val repositoryDir = File("build/functionalTest/cocoaPodsRepo").resolve(testName)
        val specsDir = File("build/functionalTest/specs").resolve(testName)

        daemon = prepareGitRepo(
            repositoryDir = repositoryDir
        )

        prepareProject(
            projectDir = kotlinProjectDir,
            kotlinVersion = kotlinVersion,
            otherPlugins = "kotlin(\"native.cocoapods\") version \"${kotlinVersion}\"",
            buildScript = """
                import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
                import store.kmpd.*
                import store.kmpd.cocoapods.*
                
                kotlin {
                    iosSimulatorArm64()
                    
                    cocoapods {
                        version = "1.0.0"
                        
                        framework {
                            baseName = "Kotlin"
                        }
                    }
                    
                    appleBinariesDeployment {
                        deployAsCocoaPodsSpec(
                            deploymentName = "Kotlin",
                            podspecDeployment = CocoaPodsSpecDeployment.SpecRepository(
                                repository = "git://127.0.0.1/${testName}",
                            ),
                            xcframeworkDeployment = CocoaPodsXCFrameworkDeployment.HttpDeployment(
                                HttpStorageDeployment.Upload(
                                    username = "foo",
                                    password = "bar",
                                    uploadDirectoryUrl = "https://127.0.0.1:8443/files",
                                )
                            ),
                            version = Version.Specific(1, 0, 0),
                        )
                    }
                }
            """.trimIndent(),
        )

        File("src/functionalTest/resources/PodConsumer").copyRecursively(consumerProjectDir)
        consumerProjectDir.resolve("Podfile").writeText(
            """
                target 'PodConsumer' do
                  use_frameworks!
                  pod '${testName}', :source => 'git://127.0.0.1/${testName}'
                end
            """.trimIndent()
        )

        GradleRunner.create()
            .forwardOutput()
            .withArguments(
                ":deployKotlinAsCocoaPodsPodspecFromPodPublishReleaseXCFramework",
                "--info",
                "-Djavax.net.ssl.trustStore=keystore.jks",
                "-Djavax.net.ssl.trustStorePassword=123456",
            )
            .withProjectDir(kotlinProjectDir)
            .build()

        assert(
            ProcessBuilder()
                .command(
                    "env", "COCOAPODS_SKIP_CACHE=1", "CP_REPOS_DIR=${specsDir.canonicalPath}",
                    "pod", "install",
                )
                .directory(consumerProjectDir)
                .inheritIO().start().waitFor() == 0
        )

        assert(
            ProcessBuilder()
                .command(
                    "xcodebuild", "build",
                    "-workspace", "PodConsumer.xcworkspace",
                    "-scheme", "PodConsumer",
                    "-destination", "generic/platform=iOS Simulator",
                    "-derivedDataPath", "dd",
                    "ARCHS=arm64",
                )
                .directory(consumerProjectDir)
                .inheritIO().start().waitFor() == 0
        )
    }

    private val kotlinVersion = "1.9.23"

}