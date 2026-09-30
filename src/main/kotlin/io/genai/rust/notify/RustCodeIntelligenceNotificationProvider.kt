package io.genai.rust.notify

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import io.genai.rust.lang.RustFileType
import io.genai.rust.lang.RustFiles
import io.genai.rust.lsp.CodeIntelligenceSetup
import io.genai.rust.settings.RustConfigurable
import io.genai.rust.settings.RustSettings
import io.genai.rust.sdk.RustSdkType
import java.util.function.Function
import javax.swing.JComponent

/**
 * On a `.rs` file where our language layer is active (IDEs without native Rust support), offer a
 * single click to turn on code intelligence (install rust-analyzer + LSP4IJ). Disappears once both are
 * present. On RustRover / Ultimate the native Rust support handles this, so we stay quiet.
 */
class RustCodeIntelligenceNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (file.extension?.lowercase() !in RustFiles.EXTENSIONS) return null
        if (FileTypeManager.getInstance().getFileTypeByExtension("rs") != RustFileType) return null

        val settings = RustSettings.getInstance()
        if (!settings.codeIntelligenceEnabled) return null
        if (settings.codeIntelligencePromptDismissed) return null
        val hasToolchain = settings.defaultSdk()?.homePath
            ?.let { RustSdkType.findCargoExecutable(it) } != null
        if (!hasToolchain) return null

        if (CodeIntelligenceSetup.isFullySetUp()) return null

        return Function { fileEditor ->
            EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
                text("Turn on Rust code intelligence — completion, go-to-definition and error highlighting.")
                createActionLabel("Enable code intelligence") {
                    CodeIntelligenceSetup.enable(project) {
                        EditorNotifications.getInstance(project).updateAllNotifications()
                    }
                }
                createActionLabel("Settings…") {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, RustConfigurable::class.java)
                }
                // For people who only ever want to run .rs files. Hides the prompt for good;
                // Settings ▸ Rust Portable still has the button to turn code intelligence on later.
                createActionLabel("Don't show again") {
                    RustSettings.getInstance().codeIntelligencePromptDismissed = true
                    EditorNotifications.getInstance(project).updateAllNotifications()
                }
            }
        }
    }
}
