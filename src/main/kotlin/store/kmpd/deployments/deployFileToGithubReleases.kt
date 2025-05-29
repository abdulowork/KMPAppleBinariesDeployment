package store.kmpd.deployments

import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import store.kmpd.utils.StreamContent
import java.io.File

@Serializable
class GitHubReleaseCreation(
    val tagName: String,
    val targetCommitish: String,
    val name: String,
    val body: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val generateReleaseNotes: Boolean,
)

@Serializable
class GitHubRelease(
    val id: Long,
)

@Serializable
class GitHubReleaseAsset(
    val id: Long,
)

@OptIn(ExperimentalSerializationApi::class, InternalAPI::class)
fun deployFileToGithubReleases(
    username: String,
    repository: String,
    token: String,
    targetCommitish: String,
    fileToDeploy: File,
    deployedFileName: String,
    tagName: String,
    requestTimeoutMillis: Long,
): String {
    runBlocking {
        val json = Json {
            namingStrategy = JsonNamingStrategy.SnakeCase
            ignoreUnknownKeys = true
        }
        val client = HttpClient {
            install(HttpTimeout) {
                this.requestTimeoutMillis = requestTimeoutMillis
            }
        }
        val releaseCreationResponse = client.post("https://api.github.com") {
            url {
                path("repos", username, repository, "releases")
            }
            headers {
                set("Authorization", "token $token")
                set("Accept", "application/vnd.github+json")
            }
            setBody(
                json.encodeToString(
                    GitHubReleaseCreation(
                        tagName = tagName,
                        targetCommitish = targetCommitish,
                        name = "Deployment: ${deployedFileName}",
                        body = "Deployment: ${deployedFileName}",
                        draft = false,
                        prerelease = false,
                        generateReleaseNotes = false,
                    )
                )
            )
        }
        assert(releaseCreationResponse.status == HttpStatusCode.OK)

        val responseText = releaseCreationResponse.bodyAsText()
        println(responseText)
        val releaseId = json.decodeFromString<GitHubRelease>(responseText).id

        val uploadResponse = client.post("https://uploads.github.com") {
            method = HttpMethod.Post
            url {
                path("repos", username, repository, "releases", "$releaseId", "assets")
                parameters.set("name", deployedFileName)
            }
            headers {
                set("Accept", "application/vnd.github+json")
                set("Authorization", "token $token")
            }
            body = StreamContent(fileToDeploy)
        }
        val uploadResponseText = uploadResponse.bodyAsText()
        println(uploadResponseText)
        json.decodeFromString<GitHubReleaseAsset>(uploadResponseText).id
    }

//    return "https://api.github.com/repos/${username}/${repository}/releases/assets/${id}"
    // FIXME: This only works for public repos
    return "https://github.com/${username}/${repository}/releases/download/${tagName}/${deployedFileName}"
}