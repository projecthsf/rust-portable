package io.genai.rust.lsp

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.server.ProcessStreamConnectionProvider

/**
 * Launches rust-analyzer (`~/.rust-portable/<toolchain>/cargo/bin/rust-analyzer`) talking LSP
 * over stdio, with an environment that points at the portable toolchain (isolated RUSTUP_HOME /
 * CARGO_HOME, and `cargo/bin` on PATH so rust-analyzer can invoke `cargo` for `cargo check` and
 * metadata). [RustClientFeatures.isEnabled] gates this up front, so if no toolchain or
 * rust-analyzer is available we never get here.
 */
class RustAnalyzerConnectionProvider(project: Project) : ProcessStreamConnectionProvider() {
    init {
        val home = RustAnalyzerManager.defaultHome()
        val analyzer = RustAnalyzerManager.analyzerBin()
        if (home != null && analyzer != null) {
            setCommands(listOf(analyzer.toString()))
            project.basePath?.let { setWorkingDirectory(it) }
            setIncludeSystemEnvironmentVariables(true)
            setUserEnvironmentVariables(RustAnalyzerManager.environment(home))
        }
    }
}
