package io.genai.rust.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.util.SystemInfo
import io.genai.rust.sdk.RustSdkType
import java.io.File
import java.nio.file.Path

/**
 * Locates (and, on request, installs) rust-analyzer — the language server that powers code
 * intelligence, and the same engine RustRover runs underneath.
 *
 * It ships as an official rustup component rather than a separate download, so
 * `rustup component add rust-analyzer` puts it in the portable toolchain itself, matched to that
 * exact rustc. That keeps it honest about std and proc-macro versions in a way a standalone
 * GitHub binary would not. One-time, needs network, usually well under a minute.
 *
 * **Do not detect it via `<CARGO_HOME>/bin/rust-analyzer`.** rustup creates that shim for every
 * tool it knows about the moment the toolchain is installed, whether or not the component is
 * present, and the shim is a symlink to the `rustup` executable — so a plain "does this file
 * exist" check (even `Files.isRegularFile`, which follows the link to a real file) reports true
 * for a toolchain that has never seen the component. Running it then fails with
 * `error: Unknown binary 'rust-analyzer' in official toolchain`, which would surface as code
 * intelligence that silently never starts. The component's real binary lands in the toolchain
 * directory instead, and only once it is actually installed — that is what [analyzerBin] looks
 * for, and what we launch.
 */
object RustAnalyzerManager {

    private const val COMPONENT = "rust-analyzer"

    private fun exe(name: String): String = if (SystemInfo.isWindows) "$name.exe" else name

    /**
     * The real rust-analyzer binary inside [home]'s toolchain, or null when the component isn't
     * installed. We pin one toolchain per home, so the first match under `rustup/toolchains` is it.
     */
    fun binFor(home: String): Path? {
        val toolchains = File(RustSdkType.rustupHome(home), "toolchains")
        val dirs = toolchains.listFiles()?.filter { it.isDirectory } ?: return null
        return dirs
            .map { File(File(it, "bin"), exe(COMPONENT)) }
            .firstOrNull { it.isFile }
            ?.toPath()
    }

    /** The rust-analyzer for the currently selected toolchain, if the component is installed. */
    fun analyzerBin(): Path? = defaultHome()?.let { binFor(it) }

    fun isInstalled(): Boolean = analyzerBin() != null

    /** Environment for running cargo/rust-analyzer against the portable toolchain. */
    fun environment(home: String): Map<String, String> = RustSdkType.environment(home)

    /**
     * Add the rust-analyzer component to the toolchain at [home]. Blocking — call off the EDT.
     * Throws on failure with the command output.
     */
    fun install(home: String) {
        val rustup = RustSdkType.findRustupExecutable(home)
            ?: throw RuntimeException(
                "No rustup binary found in this toolchain, so the rust-analyzer component " +
                    "cannot be added. Download a toolchain from Settings ▸ Rust Portable.",
            )
        val cmd = GeneralCommandLine(rustup.absolutePath, "component", "add", COMPONENT)
            .withEnvironment(environment(home))
        val output = ExecUtil.execAndGetOutput(cmd)
        if (output.exitCode != 0 || binFor(home) == null) {
            throw RuntimeException(
                "rustup component add $COMPONENT failed (exit ${output.exitCode}).\n" +
                    output.stderr.ifBlank { output.stdout }.take(1000),
            )
        }
    }

    /** Home directory of the current default toolchain, or null if none configured. */
    fun defaultHome(): String? =
        io.genai.rust.settings.RustSettings.getInstance().defaultSdk()?.homePath
            ?.takeIf { RustSdkType.findCargoExecutable(it) != null }

    /** The current default toolchain's cargo binary, or null if none configured. */
    fun defaultCargo(): File? = defaultHome()?.let { RustSdkType.findCargoExecutable(it) }
}
