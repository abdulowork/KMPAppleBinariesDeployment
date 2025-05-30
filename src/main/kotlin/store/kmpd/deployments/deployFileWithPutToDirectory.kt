package store.kmpd.deployments

import io.ktor.http.*
import java.io.File

fun deployFileWithPutToDirectory(
    username: String,
    password: String,
    uploadDirectoryUrl: String,
    fileToDeploy: File,
    deployedFileName: String,
    requestTimeoutMillis: Long,
): String {
    val uploadUrl = URLBuilder(uploadDirectoryUrl).appendPathSegments(deployedFileName).build()
    deployWithPut(
        username = username,
        password = password,
        uploadUrl = uploadUrl,
        file = fileToDeploy,
        requestTimeoutMillis = requestTimeoutMillis,
    )
    return uploadUrl.toString()
}

