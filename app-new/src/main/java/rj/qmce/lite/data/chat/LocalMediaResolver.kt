package rj.qmce.lite.data.chat

import java.io.File

/** Resolves only regular, locally readable media files; remote and stale paths safely fall back. */
object LocalMediaResolver {
    fun resolveFile(path: String?): File? = path
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?.removePrefix("file://")
        ?.let(::File)
        ?.takeIf(File::isFile)

    fun firstAvailable(paths: Iterable<String?>): File? = paths
        .asSequence()
        .mapNotNull(::resolveFile)
        .firstOrNull()

    fun hasAvailableFile(paths: Iterable<String?>): Boolean = firstAvailable(paths) != null
}
