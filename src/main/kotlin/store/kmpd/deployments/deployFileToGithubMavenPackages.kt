package store.kmpd.deployments

import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import store.kmpd.utils.StreamContent
import java.io.File

@OptIn(InternalAPI::class)
fun deployFileToGithubMavenPackages(
    username: String,
    repository: String,
    token: String,
    fileToDeploy: File,
    deployedFileName: String,
    packagePath: List<String>,
    requestTimeoutMillis: Long,
): String {
    runBlocking {
        val client = HttpClient {
            install(HttpTimeout) {
                this.requestTimeoutMillis = requestTimeoutMillis
            }
        }
        val response = client.put("https://maven.pkg.github.com") {
            url {
                path(username, repository, *packagePath.toTypedArray(), deployedFileName)
            }
            headers {
                set("Authorization", "token $token")
            }
            body = StreamContent(fileToDeploy)
        }
        println(response.bodyAsText())
    }
    // FIXME: Check if this requires token regardless of if the repository is public
    return "https://maven.pkg.github.com/${username}/${repository}/${packagePath.joinToString("/")}/${deployedFileName}"
}