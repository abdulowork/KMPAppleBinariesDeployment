package store.kmpd.utils

import java.io.FileOutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


internal fun zipFolder(sourceFolderPath: Path, zipPath: Path) {
    ZipOutputStream(FileOutputStream(zipPath.toFile())).use { zipOutputStream ->
        Files.walkFileTree(sourceFolderPath, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                zipOutputStream.putNextEntry(ZipEntry(sourceFolderPath.relativize(file).toString()))
                Files.copy(file, zipOutputStream)
                zipOutputStream.closeEntry()
                return FileVisitResult.CONTINUE
            }
        })
    }
}