package store.kmpd.spm

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import store.kmpd.CommitishSource
import store.kmpd.deployments.deployFile
import store.kmpd.utils.shortSha
import store.kmpd.utils.swiftPackageChecksum
import store.kmpd.utils.zipFolder
import java.io.File
import java.time.Instant
import javax.inject.Inject

abstract class DeployKotlinAsSwiftPackage @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {

    @get:InputDirectory
    abstract val xcframework: Property<File>

    @get:Input
    abstract val xcframeworkDeployment: Property<SPMXCFrameworkDeployment>

    @get:Input
    abstract val swiftPackageConfiguration: Property<SwiftPackageConfiguration>

    @get:Input
    abstract val deploymentName: Property<String>

    @get:Input
    val targetCommitish: Property<CommitishSource> = project.objects.property(
        CommitishSource::class.java
    ).convention(CommitishSource.RemoteHEAD())

    @get:Internal
    val temporaryDirectory: Property<File> = project.objects.property(
        File::class.java
    ).convention(
        project.layout.buildDirectory.dir("KMPAppleBinariesDeployment/DeployAsSwiftPackageTmp").flatMap { directory ->
            deploymentName.map { directory.asFile.resolve(it) }
        }
    )

    @get:Internal
    val cloneBase: Property<File> = project.objects.property(File::class.java).convention(
        project.layout.buildDirectory.dir("KMPAppleBinariesDeployment/CloneBaseSwiftPackage").flatMap { directory ->
            deploymentName.map { directory.asFile.resolve(it) }
        }
    )

    private val projectName: String = project.name

    @TaskAction
    fun run() {
        val cloneBase = cloneBase.get()
        if (cloneBase.exists()) {
            cloneBase.deleteRecursively()
        }

        val temporaryDirectory = temporaryDirectory.get()
        if (temporaryDirectory.exists()) {
            temporaryDirectory.deleteRecursively()
        }
        temporaryDirectory.mkdirs()

        val xcframeworkWrapperDir = temporaryDirectory.resolve("wrap")

        val targetCommitish = targetCommitish.get()
        val swiftPackageConfiguration = swiftPackageConfiguration.get()

        val xcframework = xcframework.get()
        xcframework.copyRecursively(xcframeworkWrapperDir.resolve(xcframework.name))
        val time: Long = Instant.now().epochSecond

        val repository = when (swiftPackageConfiguration.packageDeployment) {
            is SPMPackageDeployment.GitDeployment -> swiftPackageConfiguration.packageDeployment.repository
        }

        val xcframeworkDeployment = xcframeworkDeployment.get()
        val xcframeworkLocation: SPMXCFrameworkLocation = when (xcframeworkDeployment) {
            is SPMXCFrameworkDeployment.HttpDeployment -> {
                val zippedXCFramework = temporaryDirectory.resolve("xcframework.zip")
                zipFolder(
                    sourceFolderPath = xcframeworkWrapperDir.toPath(),
                    zipPath = zippedXCFramework.toPath(),
                )
                SPMXCFrameworkLocation.HttpDeployment(
                    binaryUrl = deployFile(
                        deployment = xcframeworkDeployment.deployment,
                        deployedFileName = "${shortSha(zippedXCFramework)}-${time}.xcframework.zip",
                        file = zippedXCFramework,
                        time = time,
                    ),
                    checksum = swiftPackageChecksum(zippedXCFramework),
                )
            }
            is SPMXCFrameworkDeployment.GitDeployment -> {
                SPMXCFrameworkLocation.EmbedInGitRepository(
                    xcframeworkPath = xcframework
                )
            }
        }

        val packageName = when (swiftPackageConfiguration.packageName) {
            is SwiftPackageName.FromProjectName -> projectName
            is SwiftPackageName.Specific -> swiftPackageConfiguration.packageName.value
        }

        val base: String? = when (targetCommitish) {
            is CommitishSource.RemoteHEAD -> null
            is CommitishSource.SpecificCommitish -> targetCommitish.value
        }

        createSpmGitRelease(
            repository = repository,
            version = swiftPackageConfiguration.version.versionString,
            base = base,
            packageName = packageName,
            cloneBase = cloneBase.toPath(),
            xcframeworkLocation = xcframeworkLocation,
            execOperations = execOperations
        )
    }
}