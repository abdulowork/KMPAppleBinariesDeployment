import java.io.File

fun prepareGitRepo(
    repositoryDir: File,
): Process {
    repositoryDir.mkdirs()

    ProcessBuilder()
        .command("git", "init", "--bare")
        .directory(repositoryDir)
        .inheritIO().start().waitFor()

    repositoryDir.resolve("git-daemon-export-ok").createNewFile()

    val daemon = ProcessBuilder()
        .command("git", "daemon", "--base-path=.", "--reuseaddr", "--enable=receive-pack", "--verbose")
        .directory(repositoryDir.parentFile)
        .inheritIO().start()

    return daemon
}