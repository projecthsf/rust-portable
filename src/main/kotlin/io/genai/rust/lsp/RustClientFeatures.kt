package io.genai.rust.lsp

import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import io.genai.rust.settings.RustSettings

/**
 * Gates when the Rust language server is active. LSP4IJ calls [isEnabled] before starting the
 * server for a file, so this enforces the "Code intelligence" toggle and the prerequisites
 * (a toolchain is configured, rust-analyzer is installed). Returning false keeps the server
 * dormant.
 *
 * rust-analyzer is mature, so — as with gopls in the Go plugin — we keep all sub-features on:
 * its code actions (import resolution, `fill match arms`, macro expansion) are among the main
 * reasons to run it at all.
 */
class RustClientFeatures : LSPClientFeatures() {

    override fun isEnabled(file: VirtualFile): Boolean {
        val settings = RustSettings.getInstance()
        if (!settings.codeIntelligenceEnabled) return false
        return RustAnalyzerManager.defaultHome() != null && RustAnalyzerManager.isInstalled()
    }
}
