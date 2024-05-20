package store.kmpd.cocoapods

import store.kmpd.HttpStorageDeployment

sealed class CocoaPodsSpecDeployment {
    data class SpecRepository(
        val repository: String,
        val branch: String? = null,
    ) : CocoaPodsSpecDeployment()

    data class GitRepository(
        val repository: String,
        val branch: String? = null,
    ) : CocoaPodsSpecDeployment()

    data class HttpDeployment(
        val deployment: HttpStorageDeployment
    ) : CocoaPodsSpecDeployment()
}