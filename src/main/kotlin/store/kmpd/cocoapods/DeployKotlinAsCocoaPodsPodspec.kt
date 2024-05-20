package store.kmpd.cocoapods

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import store.kmpd.Version
import store.kmpd.deployments.deployFile
import store.kmpd.utils.shortSha
import store.kmpd.utils.zipFolder
import java.io.File
import java.time.Instant
import javax.inject.Inject

abstract class DeployKotlinAsCocoaPodsPodspec @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {

    @get:InputDirectory
    abstract val xcframework: Property<File>

    @get:InputFile
    abstract val kgpGeneratedPodspec: Property<File>

    @get:Input
    abstract val xcframeworkDeployment: Property<CocoaPodsXCFrameworkDeployment>

    @get:Input
    abstract val podspecDeployment: Property<CocoaPodsSpecDeployment>

    @get:Input
    abstract val version: Property<Version>

    @get:Input
    abstract val deploymentName: Property<String>

    @get:Internal
    val temporaryDirectory: Property<File> = project.objects.property(
        File::class.java
    ).convention(
        project.layout.buildDirectory.dir("KMPAppleBinariesDeployment/DeployAsCocoaPodsTmp").flatMap { directory ->
            deploymentName.map { directory.asFile.resolve(it) }
        }
    )

    @get:Internal
    val cloneBase: Property<File> = project.objects.property(File::class.java).convention(
        project.layout.buildDirectory.dir("KMPAppleBinariesDeployment/CloneBaseCocoaPods").flatMap { directory ->
            deploymentName.map { directory.asFile.resolve(it) }
        }
    )

    @TaskAction
    fun run() {
        val temporaryDirectory = temporaryDirectory.get()
        val xcframework = xcframework.get()
        val time = Instant.now().epochSecond

        val xcframeworkDeployment = xcframeworkDeployment.get()
        when (xcframeworkDeployment) {
            is CocoaPodsXCFrameworkDeployment.GitDeployment -> {
                assert(
                    podspecDeployment.get() is CocoaPodsSpecDeployment.GitRepository,
                    { "Deploying XCFramework in a repository only works if podspec deployment is CocoaPodsSpecDeployment.GitRepository" }
                )
                val podspecDeployment = (podspecDeployment.get() as CocoaPodsSpecDeployment.GitRepository)

                createCocoaPodsGitRelease(
                    podspecDeployment = podspecDeployment,
                    kgpProducedPodspec = kgpGeneratedPodspec.get(),
                    xcframework = xcframework,
                    base = podspecDeployment.branch,
                    version = version.get(),
                    cloneBase = cloneBase.get().toPath(),
                    execOperations = execOperations,
                )
            }
            is CocoaPodsXCFrameworkDeployment.HttpDeployment -> {
                if (temporaryDirectory.exists()) { temporaryDirectory.deleteRecursively() }
                temporaryDirectory.mkdirs()

                val xcframeworkWrapperDir = temporaryDirectory.resolve("wrap")
                xcframework.copyRecursively(xcframeworkWrapperDir.resolve(xcframework.name))

                val zippedXCFramework = temporaryDirectory.resolve("xcframework.zip")
                zipFolder(
                    sourceFolderPath = xcframeworkWrapperDir.toPath(),
                    zipPath = zippedXCFramework.toPath(),
                )

                val binaryUrl = deployFile(
                    deployment = xcframeworkDeployment.deployment,
                    deployedFileName = "${shortSha(zippedXCFramework)}-${time}.xcframework.zip",
                    file = zippedXCFramework,
                    time = time,
                )

                val podspecDeployment = podspecDeployment.get()
                when (podspecDeployment) {
                    is CocoaPodsSpecDeployment.GitRepository -> error("Should be unreachable")
                    is CocoaPodsSpecDeployment.HttpDeployment -> {
                        createCocoaPodsHttpRelease(
                            podspecDeployment = podspecDeployment,
                            kgpProducedPodspec = kgpGeneratedPodspec.get(),
                            deployedXCFramework = binaryUrl,
                            podspecName = kgpGeneratedPodspec.get().nameWithoutExtension,
                            time = time,
                            temp = temporaryDirectory.toPath(),
                            version = version.get()
                        )
                    }
                    is CocoaPodsSpecDeployment.SpecRepository -> {
                        createCocoaPodsSpecRepoRelease(
                            podspecDeployment = podspecDeployment,
                            deployedXCFramework = binaryUrl,
                            kgpProducedPodspec = kgpGeneratedPodspec.get(),
                            base = podspecDeployment.branch,
                            version = version.get(),
                            cloneBase = cloneBase.get().toPath(),
                            execOperations = execOperations,
                        )
                    }
                }
            }
        }
    }
}