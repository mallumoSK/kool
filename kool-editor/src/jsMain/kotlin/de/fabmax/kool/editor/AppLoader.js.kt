package de.fabmax.kool.editor

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class AppLoadService actual constructor(paths: ProjectPaths) {
    actual var hasAppChanged = false
        private set

    actual fun addIgnorePath(path: String) { }

    actual suspend fun buildApp() { }

    actual suspend fun loadApp(): LoadedApp {
        return PlatformFunctions.loadedApp ?: throw IllegalStateException("PlatformFunctions.initPlatform() not called")
    }
}
