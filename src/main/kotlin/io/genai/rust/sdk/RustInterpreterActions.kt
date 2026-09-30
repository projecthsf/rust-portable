package io.genai.rust.sdk

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files

/**
 * Interactive install flows shared by the settings panel and the editor banner.
 * All entry points run on the EDT; [onComplete] fires on the EDT after registration.
 */
object RustInterpreterActions {

    fun downloadInteractively(project: Project?, onComplete: (Sdk?) -> Unit) {
        val releases = RustDownloads.fetchAvailableWithProgress(project)
        if (releases.isEmpty()) {
            Messages.showInfoMessage("No portable Rust toolchains are listed for this OS.", "Download Rust")
            return
        }
        val dialog = RustDownloadDialog(releases)
        if (!dialog.showAndGet()) return
        val release = dialog.selected ?: return
        val home = RustSdkManager.plannedHome(release.toolchain)

        ProgressManager.getInstance().run(object : Task.Modal(project, "Downloading Rust ${release.version ?: release.toolchain}", true) {
            override fun run(indicator: ProgressIndicator) {
                RustSdkDownloadTask(release, home).doDownload(indicator)
            }

            override fun onSuccess() {
                val sdk = RustSdkManager.registerFromHome(home.toString())
                if (sdk == null) {
                    Messages.showErrorDialog(
                        "Download finished but no cargo executable was found under\n$home",
                        "Download Rust",
                    )
                }
                onComplete(sdk)
            }

            override fun onThrowable(error: Throwable) {
                Messages.showErrorDialog(error.message ?: error.toString(), "Download Rust Failed")
            }
        })
    }

    fun addFromDisk(onComplete: (Sdk?) -> Unit) {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle("Select Rust Toolchain Directory")
            .withDescription("Pick a Rust toolchain folder (contains cargo/bin/cargo, or bin/cargo).")
        val root = RustSdkManager.downloadRoot()
        Files.createDirectories(root)
        val toSelect = LocalFileSystem.getInstance().findFileByNioFile(root)
        val chosen = FileChooser.chooseFile(descriptor, null, toSelect) ?: return
        val home = chosen.path
        if (RustSdkType.findCargoExecutable(home) == null) {
            Messages.showErrorDialog("No cargo executable found under\n$home", "Add Rust Toolchain")
            return
        }
        onComplete(RustSdkManager.registerFromHome(home))
    }
}
