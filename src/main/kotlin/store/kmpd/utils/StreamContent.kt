package store.kmpd.utils

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import java.io.File

class StreamContent(private val file: File) : OutgoingContent.ReadChannelContent() {
    override fun readFrom(): ByteReadChannel = file.readChannel()
    override val contentType = ContentType.Application.OctetStream
    override val contentLength: Long = file.length()
}