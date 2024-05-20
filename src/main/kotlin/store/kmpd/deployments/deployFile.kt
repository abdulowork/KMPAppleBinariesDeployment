package store.kmpd.deployments

import store.kmpd.CommitishSource
import store.kmpd.HttpStorageDeployment
import store.kmpd.utils.shortSha
import java.io.File

fun deployFile(
    deployment: HttpStorageDeployment,
    deployedFileName: String,
    file: File,
    time: Long,
    tagNameProvider: (File) -> String = { "${shortSha(file)}-${time}" }
): String {
    return when (deployment) {
        is HttpStorageDeployment.GithubMavenPackage -> {
            deployFileToGithubMavenPackages(
                username = deployment.username,
                repository = deployment.repository,
                token = deployment.token,
                fileToDeploy = file,
                deployedFileName = deployedFileName,
                packagePath = deployment.packagePath,
            )
        }

        is HttpStorageDeployment.GithubReleases -> {
            val targetCommitishString: String = when (deployment.targetCommitish) {
                is CommitishSource.RemoteHEAD -> {
                    // FIXME: Is this consistent with git clone's default branch
                    val process = ProcessBuilder(
                        "git", "ls-remote", deployment.repository, "HEAD", "--exit-code",
                    ).inheritIO().start()
                    val headSha = process.inputStream.reader().readText().dropLast(1).split(" ")[0]
                    assert(process.waitFor() == 0)
                    assert(headSha.isNotBlank())
                    headSha
                }

                is CommitishSource.SpecificCommitish -> deployment.targetCommitish.value
            }

            deployFileToGithubReleases(
                username = deployment.username,
                repository = deployment.repository,
                token = deployment.token,
                fileToDeploy = file,
                targetCommitish = targetCommitishString,
                deployedFileName = deployedFileName,
                tagName = tagNameProvider(file),
            )
        }

        is HttpStorageDeployment.Upload -> {
            deployFileWithPutToDirectory(
                username = deployment.username,
                password = deployment.password,
                uploadDirectoryUrl = deployment.uploadDirectoryUrl,
                fileToDeploy = file,
                deployedFileName = deployedFileName,
            )
        }
    }
}