package org.sattory.app.platform

class DesktopStorageRegistry(
    private val directories: DesktopAppDirectories,
) {
    fun registerStorageRoot() {
        if (!isWindows()) return

        runCatching {
            ProcessBuilder(
                "reg",
                "add",
                REGISTRY_KEY,
                "/v",
                REGISTRY_VALUE_NAME,
                "/t",
                "REG_SZ",
                "/d",
                directories.storageRoot.absolutePath,
                "/f",
            )
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

    private companion object {
        const val REGISTRY_KEY = "HKCU\\Software\\Sattory"
        const val REGISTRY_VALUE_NAME = "LocalAppDataPath"
    }
}
