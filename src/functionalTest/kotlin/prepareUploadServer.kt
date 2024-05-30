import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import java.io.File
import java.security.KeyStore
import kotlin.test.assertNotNull

fun prepareUploadServer(): ApplicationEngine {
    /**
     * FIXME:
     * This test requires keystore setup in the running JVM process and in the keychain
     * 1. Export cer via "keytool -exportcert -keystore keystore.jks -alias sampleAlias -file output.cer"
     * 2. Trust in keychain "security add-trusted-cert -p ssl -e certExpired -d -k ~/Library/Keychains/login.keychain output.cer"
     * 3. Pass "-Djavax.net.ssl.trustStore=keystore.jks -Djavax.net.ssl.trustStorePassword=123456" to Gradle
     */

    val keyStoreFile = File("keystore.jks")
    val keyStore = KeyStore.getInstance(keyStoreFile, "123456".toCharArray())

    val environment = applicationEngineEnvironment {
        connector {
            port = 8080
        }
        sslConnector(
            keyStore = keyStore,
            keyAlias = "sampleAlias",
            keyStorePassword = { "123456".toCharArray() },
            privateKeyPassword = { "foobar".toCharArray() }) {
            port = 8443
            keyStorePath = keyStoreFile
        }
        module {
            routing {
                put("/files/{file}") {
                    val fileName = assertNotNull(call.parameters["file"]).toString()
                    val file = File("build/functionalTest/uploadedFiles/${fileName}")
                    file.parentFile.mkdirs()
                    call.receiveChannel().copyAndClose(file.writeChannel())
                }
                get("/files/{file}") {
                    val fileName = assertNotNull(call.parameters["file"]).toString()
                    val file = File("build/functionalTest/uploadedFiles/${fileName}")
                    call.respondFile(file)
                }
            }
        }
    }
    return embeddedServer(Netty, environment = environment).start(wait = false)
}