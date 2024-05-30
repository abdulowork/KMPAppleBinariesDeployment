package store.kmpd

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.configurationcache.extensions.capitalized
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.cocoapods.CocoapodsExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFrameworkTask
import store.kmpd.cocoapods.CocoaPodsSpecDeployment
import store.kmpd.cocoapods.CocoaPodsXCFrameworkDeployment
import store.kmpd.cocoapods.DeployKotlinAsCocoaPodsPodspec
import store.kmpd.spm.DeployKotlinAsSwiftPackage
import store.kmpd.spm.SPMXCFrameworkDeployment
import store.kmpd.spm.SwiftPackageConfiguration
import javax.inject.Inject

class KMPAppleBinariesDeploymentPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            (target.kotlinExtension as ExtensionAware).extensions.create(
                AppleBinariesDeployment::class.java,
                "appleBinariesDeployment",
                AppleBinariesDeployment::class.java,
            )
        }
    }

}

abstract class AppleBinariesDeployment @Inject constructor(
    private val project: Project,
) {
    fun deployAsSwiftPackage(
        deploymentName: String = project.name,
        swiftPackageConfiguration: SwiftPackageConfiguration,
        xcframeworkDeployment: SPMXCFrameworkDeployment,
    ) {
        project.tasks.withType(XCFrameworkTask::class.java).all { xcfTask ->
            project.tasks.register(
                "deploy${deploymentName.capitalized()}AsSwiftPackageFrom${xcfTask.name.capitalized()}",
                DeployKotlinAsSwiftPackage::class.java
            ) {
                it.group = "apple binaries deployment"
                it.description = "Deploy XCFramework as a Swift Package ${swiftPackageConfiguration} and XCFramework ${xcframeworkDeployment}"

                it.dependsOn(xcfTask)
                it.xcframework.set(
                    xcfTask.outputDir
                        .resolve(xcfTask.buildType.getName())
                        .resolve("${xcfTask.baseName.get()}.xcframework")
                )
                it.swiftPackageConfiguration.set(
                    swiftPackageConfiguration
                )
                it.xcframeworkDeployment.set(
                    xcframeworkDeployment
                )
                it.deploymentName.set(
                    deploymentName
                )
            }
        }
    }

    fun deployAsCocoaPodsSpec(
        deploymentName: String = project.name,
        podspecDeployment: CocoaPodsSpecDeployment,
        xcframeworkDeployment: CocoaPodsXCFrameworkDeployment,
        version: Version = Version.UsePatchTimestamp(),
    ) {
        project.pluginManager.withPlugin("org.jetbrains.kotlin.native.cocoapods") {
            val podspecName = (project.extensions.getByType(KotlinMultiplatformExtension::class.java) as ExtensionAware).extensions.getByType(CocoapodsExtension::class.java).name
            project.tasks.withType(XCFrameworkTask::class.java).all { xcfTask ->
                val isPodXCFrameworkTask = xcfTask.name.startsWith("pod")
                if (!isPodXCFrameworkTask) return@all
                project.tasks.register("deploy${deploymentName.capitalized()}AsCocoaPodsPodspecFrom${xcfTask.name.capitalized()}", DeployKotlinAsCocoaPodsPodspec::class.java) {
                    it.group = "apple binaries deployment"
                    it.description = "Deploy XCFramework as a CocoaPods podspec ${podspecDeployment} and XCFramework ${xcframeworkDeployment}"

                    it.dependsOn(xcfTask)
                    val root = xcfTask.outputDir.resolve(xcfTask.buildType.getName())

                    it.xcframework.set(
                        root.resolve("${xcfTask.baseName.get()}.xcframework")
                    )
                    it.kgpGeneratedPodspec.set(
                        root.resolve("${podspecName}.podspec")
                    )
                    it.podspecDeployment.set(
                        podspecDeployment
                    )
                    it.xcframeworkDeployment.set(
                        xcframeworkDeployment
                    )
                    it.version.set(
                        version
                    )
                    it.deploymentName.set(
                        deploymentName
                    )
                }
            }
        }
    }

}


