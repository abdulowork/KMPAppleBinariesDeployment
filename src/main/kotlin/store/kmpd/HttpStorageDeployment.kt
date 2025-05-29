package store.kmpd

sealed class HttpStorageDeployment {

    abstract val requestTimeoutMillis: Long

    class GithubMavenPackage(
        val token: String,
        val username: String,
        val repository: String,
        val packagePath: List<String> = listOf("publication", "files", "all"),
        override val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    ) : HttpStorageDeployment()

    class GithubReleases(
        val token: String,
        val username: String,
        val repository: String,
        val targetCommitish: CommitishSource = CommitishSource.RemoteHEAD(),
        override val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    ) : HttpStorageDeployment()

    class Upload(
        val username: String,
        val password: String,
        val uploadDirectoryUrl: String,
        override val requestTimeoutMillis: Long = DEFAULT_REQUEST_TIMEOUT_MILLIS,
    ) : HttpStorageDeployment()

    companion object {
        // Default timeout of ktor HTTP client is 15 seconds,
        // which can be too short for large files and bad network conditions.
        // see more https://github.com/ktorio/ktor/blob/f8f8fe8a36744fc4b8a5adef79897f2d327743b9/ktor-client/ktor-client-cio/jvmAndNix/src/io/ktor/client/engine/cio/CIOEngineConfig.kt#L37
        const val DEFAULT_REQUEST_TIMEOUT_MILLIS: Long = 60_000L
    }
}