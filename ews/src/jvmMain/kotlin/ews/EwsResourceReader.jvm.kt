package ews

internal actual fun readEwsResourceText(path: String): String {
    val classLoader = Thread.currentThread().contextClassLoader
        ?: EwsVocabularies::class.java.classLoader

    return classLoader.getResourceAsStream(path)?.use { stream ->
        stream.readBytes().decodeToString()
    } ?: throw IllegalArgumentException("Resource not found: $path")
}
