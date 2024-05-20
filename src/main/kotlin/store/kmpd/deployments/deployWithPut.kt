package store.kmpd.deployments

import io.ktor.client.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import store.kmpd.utils.StreamContent
import java.io.File

@OptIn(InternalAPI::class)
fun deployWithPut(
    username: String,
    password: String,
    uploadUrl: Url,
    file: File,
) {
    runBlocking {
        val client = HttpClient {
            install(Auth) {
                basic {
                    credentials {
                        // FIXME: Optional?
                        BasicAuthCredentials(
                            username = username,
                            password = password
                        )
                    }
                }
            }
        }

        val response = client.put(uploadUrl) {
            body = StreamContent(file)
        }
        println(response.bodyAsText())
        assert(
            response.status.isSuccess()
        )
    }
}