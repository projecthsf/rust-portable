package io.genai.rust.tools

import com.intellij.execution.RunContentExecutor
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import io.genai.rust.lsp.RustAnalyzerManager
import kotlin.io.path.Path

/**
 * Runs a `cargo` command (build / test / fmt / clippy / …) with the portable Rust toolchain in a
 * Run console, using the project directory as the working dir. No system Rust needed. Environment
 * is the isolated portable setup (RUSTUP_HOME + CARGO_HOME under ~/.rust-portable, toolchain on
 * PATH).
 */
class RunCargoToolAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val base = project.basePath ?: run {
            Messages.showErrorDialog(project, "No project directory.", "Run Cargo Tool")
            return
        }

        val home = RustAnalyzerManager.defaultHome()
        val cargo = RustAnalyzerManager.defaultCargo()
        if (home == null || cargo == null) {
            Messages.showErrorDialog(
                project,
                "No Rust toolchain configured. Set one up in Settings ▸ Rust Portable.",
                "Run Cargo Tool",
            )
            return
        }

        val dialog = RunCargoToolDialog(project)
        if (!dialog.showAndGet()) return
        val args = dialog.commandArgs
        if (args.isEmpty()) return

        val cmd = GeneralCommandLine()
            .withExePath(cargo.absolutePath)
            .withParameters(args)
            .withWorkDirectory(base)
            .withEnvironment(RustAnalyzerManager.environment(home))
        val handler = OSProcessHandler(cmd)
        // cargo writes files (Cargo.lock, target/) via an external process; refresh the project
        // dir when it finishes so new files show up without a manual "Reload from Disk".
        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(event: ProcessEvent) {
                LocalFileSystem.getInstance().findFileByNioFile(Path(base))?.let {
                    VfsUtil.markDirtyAndRefresh(true, true, true, it)
                }
            }
        })
        RunContentExecutor(project, handler)
            .withTitle("cargo ${args.joinToString(" ")}")
            .withActivateToolWindow(true)
            .run()
    }
}
