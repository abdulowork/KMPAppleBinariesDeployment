package store.kmpd.spm

import store.kmpd.HttpStorageDeployment

sealed class SPMXCFrameworkDeployment {
    class GitDeployment : SPMXCFrameworkDeployment()
    data class HttpDeployment(
        val deployment: HttpStorageDeployment
    ) : SPMXCFrameworkDeployment()
}