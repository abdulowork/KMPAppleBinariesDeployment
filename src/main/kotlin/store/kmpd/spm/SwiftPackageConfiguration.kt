package store.kmpd.spm

import store.kmpd.Version


data class SwiftPackageConfiguration(
    val packageDeployment: SPMPackageDeployment,
    val version: Version = Version.UsePatchTimestamp(),
    val packageName: SwiftPackageName = SwiftPackageName.FromProjectName(),
)