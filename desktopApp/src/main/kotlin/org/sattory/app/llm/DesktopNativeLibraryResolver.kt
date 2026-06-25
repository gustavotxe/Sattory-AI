package org.sattory.app.llm

import java.io.File

class DesktopNativeLibraryResolver(
    private val libraryFileName: String = nativeLibraryFileName(),
) {
    fun resolve(): File {
        systemPropertyLibrary()?.let { return it }
        installedResourceLibrary()?.let { return it }

        error(
            "Biblioteca nativa $libraryFileName nao encontrada. " +
                "Execute pelo Gradle ou gere um pacote desktop com os recursos nativos.",
        )
    }

    private fun systemPropertyLibrary(): File? {
        return System.getProperty(NATIVE_LIBRARY_PROPERTY)
            ?.let(::File)
            ?.takeIf { it.isFile }
    }

    private fun installedResourceLibrary(): File? {
        return System.getProperty(COMPOSE_RESOURCES_DIR_PROPERTY)
            ?.let(::File)
            ?.resolve(nativeResourcePath())
            ?.takeIf { it.isFile }
    }

    private fun nativeResourcePath(): String = when {
        isWindows() -> "native/windows/$libraryFileName"
        isMac() -> "native/macos/$libraryFileName"
        else -> "native/linux/$libraryFileName"
    }

    private companion object {
        const val NATIVE_LIBRARY_PROPERTY = "sattory.native.library"
        const val COMPOSE_RESOURCES_DIR_PROPERTY = "compose.application.resources.dir"

        fun nativeLibraryFileName(): String = when {
            isWindows() -> "sattory_llm.dll"
            isMac() -> "libsattory_llm.dylib"
            else -> "libsattory_llm.so"
        }

        fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
        fun isMac(): Boolean = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
    }
}
