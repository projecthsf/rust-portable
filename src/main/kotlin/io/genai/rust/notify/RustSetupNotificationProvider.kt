package io.genai.rust.notify

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import io.genai.rust.lang.RustFiles
import io.genai.rust.sdk.RustInterpreterActions
import io.genai.rust.settings.RustSettings
import io.genai.rust.settings.RustConfigurable
import java.util.function.Function
import javax.swing.JComponent

/**
 * On a .rs file, if no Rust toolchain is configured yet, show a banner offering to download or add
 * one. Disappears automatically once a toolchain exists.
 */
class RustSetupNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        if (file.extension?.lowercase() !in RustFiles.EXTENSIONS) return null
        if (RustSettings.getInstance().defaultSdk() != null) return null

        return Function { fileEditor ->
            EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
                text("No Rust toolchain configured — download a portable one to run this file.")
                createActionLabel("Download Rust…") {
                    RustInterpreterActions.downloadInteractively(project) { refresh(project) }
                }
                createActionLabel("Add from Disk…") {
                    RustInterpreterActions.addFromDisk { refresh(project) }
                }
                createActionLabel("Settings…") {
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, RustConfigurable::class.java)
                }
            }
        }
    }

    private fun refresh(project: Project) {
        EditorNotifications.getInstance(project).updateAllNotifications()
    }
}
