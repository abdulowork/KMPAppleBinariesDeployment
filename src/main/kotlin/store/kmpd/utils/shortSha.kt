package store.kmpd.utils

import org.apache.commons.codec.digest.DigestUtils
import java.io.File

fun shortSha(file: File) = DigestUtils.sha256Hex(file.inputStream()).take(7)

