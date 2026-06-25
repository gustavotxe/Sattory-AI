package org.sattory.app.platform

import java.io.File

class DesktopAppDirectories(
    private val applicationName: String = "Sattory",
) {
    val storageRoot: File by lazy {
        explicitStorageRoot()
            ?: localApplicationDataRoot()
            ?: developmentStorageRoot()
    }

    val dataRoot: File by lazy {
        storageRoot
    }

    private fun explicitStorageRoot(): File? {
        return System.getProperty(STORAGE_ROOT_PROPERTY)
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
    }

    private fun localApplicationDataRoot(): File? {
        if (!isWindows()) return null
        return System.getenv(LOCAL_APP_DATA_ENV)
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it, applicationName) }
    }

    private fun developmentStorageRoot(): File = when {
        isMac() -> File(System.getProperty("user.home"), "Library/Application Support/$applicationName")
        isWindows() -> File(System.getProperty("user.home"), "AppData/Local/$applicationName")
        else -> File(System.getProperty("user.home"), ".${applicationName.lowercase()}")
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    private fun isMac(): Boolean = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)

    private companion object {
        const val STORAGE_ROOT_PROPERTY = "sattory.storage.root"
        const val LOCAL_APP_DATA_ENV = "LOCALAPPDATA"
    }
}
