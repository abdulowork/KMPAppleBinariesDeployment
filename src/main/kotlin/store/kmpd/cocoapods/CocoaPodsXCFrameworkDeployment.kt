package store.kmpd.cocoapods

import store.kmpd.HttpStorageDeployment

sealed class CocoaPodsXCFrameworkDeployment {
    class GitDeployment : CocoaPodsXCFrameworkDeployment()

    data class HttpDeployment(
        val deployment: HttpStorageDeployment
    ) : CocoaPodsXCFrameworkDeployment()
}