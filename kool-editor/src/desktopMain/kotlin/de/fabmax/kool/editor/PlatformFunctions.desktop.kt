package de.fabmax.kool.editor

import de.fabmax.kool.*
import de.fabmax.kool.editor.data.ProjectData
import de.fabmax.kool.editor.model.EditorProject
import de.fabmax.kool.editor.ui.OkCancelBrowsePathDialog
import de.fabmax.kool.platform.Lwjgl3Context
import de.fabmax.kool.util.logD
import de.fabmax.kool.util.logE
import de.fabmax.kool.util.logW
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object PlatformFunctions {

    actual fun onEditorStarted(ctx: KoolContext) {
        ctx as Lwjgl3Context
        val wnd = ctx.backend.glfwWindow

        val posX = KeyValueStore.getInt("editor.window.posX", -1)
        val posY = KeyValueStore.getInt("editor.window.posY", -1)
        if (posX != -1 && posY != -1) {
            wnd.setWindowPos(posX, posY)
        }

        val width = KeyValueStore.getInt("editor.window.width", KoolSystem.configJvm.windowSize.x)
        val height = KeyValueStore.getInt("editor.window.height", KoolSystem.configJvm.windowSize.y)
        wnd.setWindowSize(width, height)

        val isMaximized = KeyValueStore.getBoolean("editor.window.isMaximized", false)
        if (isMaximized) {
            wnd.isMaximized = true
        }
        wnd.isVisible = true
    }

    actual fun onWindowCloseRequest(ctx: KoolContext): Boolean {
        ctx as Lwjgl3Context
        val wnd = ctx.backend.glfwWindow

        if (wnd.isMaximized) {
            KeyValueStore.setBoolean("editor.window.isMaximized", true)
        } else {
            KeyValueStore.setBoolean("editor.window.isMaximized", false)
            KeyValueStore.setInt("editor.window.posX", wnd.windowPosX)
            KeyValueStore.setInt("editor.window.posY", wnd.windowPosY)
            KeyValueStore.setInt("editor.window.width", wnd.windowWidth)
            KeyValueStore.setInt("editor.window.height", wnd.windowHeight)
        }
        return true
    }

    actual fun loadProjectModel(path: String): EditorProject? {
        val projFile = File(path)
        return try {
            val projData = Json.decodeFromString<ProjectData>(projFile.readText())
            EditorProject(projData)
        } catch (e: Exception) {
            logW { "Project not found at ${projFile.absolutePath}, creating new empty project" }
            return null
        }
    }

    actual fun saveProjectModel(path: String) {
        val modelPath = File(path)
        modelPath.parentFile.mkdirs()
        modelPath.writeText(EditorState.jsonCodec.encodeToString(EditorState.projectModel.projectData))
        logD { "Saved project to ${modelPath.absolutePath}" }
    }

    actual fun editBehavior(behaviorSourcePath: String) {
        val behaviorPath = File(behaviorSourcePath).canonicalPath
        logD { "Edit behavior source: $behaviorPath" }

        val ideaPath = KeyValueStore.loadString("editor.idea.path") ?: "idea64"

        try {
            ProcessBuilder()
                .command(ideaPath, behaviorSourcePath)
                .start()
        } catch (e: IOException) {
            logW { "IntelliJ executable not found" }

            OkCancelBrowsePathDialog("Select IntelliJ Path (idea64)", "idea64", "path/to/idea64") { path ->
                KeyValueStore.storeString("editor.idea.path", path)
                editBehavior(behaviorSourcePath)
            }

        } catch (e: Exception) {
            logE { "Failed launching IntelliJ: ${e.message}" }
        }
    }

    actual suspend fun chooseFilePath(): String? {
        val result = Assets.loadFileByUser()
        if (result.isNotEmpty()) {
            return (result[0] as LoadableFileImpl).file.path
        }
        return null
    }
}