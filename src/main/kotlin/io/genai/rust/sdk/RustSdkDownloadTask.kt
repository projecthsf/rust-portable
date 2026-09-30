package io.genai.rust.sdk

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.util.io.HttpRequests
import java.nio.file.Files
import java.nio.file.Path

/**
 * Installs a portable Rust toolchain into [homeDir] using the official `rustup-init`.
 *
 * Why not "download and unpack" like the Go plugin: Rust's standalone tarballs are component
 * trees that expect `install.sh` to stitch them into a prefix, and Windows ships an `.msi`
 * instead — neither is a plain extract. `rustup-init` is the supported cross-platform path and
 * gives us `rustup component add rust-analyzer` for free later (see RustAnalyzerManager).
 *
 * The install stays fully isolated: RUSTUP_HOME and CARGO_HOME are pinned under [homeDir] and
 * `--no-modify-path` stops rustup touching the user's shell profile, so nothing leaks into the
 * system the way a normal `rustup` install would. Layout ends up as:
 *
 *     <homeDir>/cargo/bin/{cargo,rustc,rustup,…}   (CARGO_HOME — the shims we run)
 *     <homeDir>/rustup/toolchains/<name>/…         (RUSTUP_HOME — the real toolchain)
 */
class RustSdkDownloadTask(
    private val release: RustRelease,
    private val homeDir: Path,
) : SdkDownloadTask {

    override fun getSuggestedSdkName(): String =
        release.version?.let { "Rust $it" } ?: "Rust ${release.toolchain}"

    override fun getPlannedHomeDir(): String = homeDir.toString()
    override fun getPlannedVersion(): String = release.version ?: release.toolchain

    override fun doDownload(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false
        indicator.text = "Downloading rustup…"
        Files.createDirectories(homeDir)

        val initExe = downloadRustupInit(indicator)
        try {
            indicator.isIndeterminate = true
            indicator.text = "Downloading Rust ${release.version ?: release.toolchain}…"
            runRustupInit(initExe, indicator)
        } finally {
            Files.deleteIfExists(initExe)
        }

        if (RustSdkType.findCargoExecutable(homeDir.toString()) == null) {
            throw RuntimeException(
                "rustup finished but no cargo binary was found under $homeDir.",
            )
        }
    }

    private fun downloadRustupInit(indicator: ProgressIndicator): Path {
        val suffix = if (release.os == OsFamily.WINDOWS) ".exe" else ""
        val tmp = Files.createTempFile("rustup-init-", suffix)
        HttpRequests.request(release.rustupInitUrl).saveToFile(tmp.toFile(), indicator)
        if (release.os != OsFamily.WINDOWS) {
            tmp.toFile().setExecutable(true)
        }
        return tmp
    }

    /**
     * Runs the installer and streams its output into the progress indicator — the toolchain
     * download is a few hundred MB, so without this the UI would sit silent for minutes.
     */
    private fun runRustupInit(initExe: Path, indicator: ProgressIndicator) {
        val process = ProcessBuilder(
            initExe.toString(),
            "-y",
            "--no-modify-path",
            "--default-toolchain", release.toolchain,
            "--profile", "minimal",
            // rust-analyzer needs the std sources to offer completion/navigation into std.
            "--component", "rust-src",
        )
            .directory(homeDir.toFile())
            .redirectErrorStream(true)
            .also { it.environment().putAll(RustSdkType.installEnvironment(homeDir)) }
            .start()

        val tail = StringBuilder()
        process.inputStream.bufferedReader().forEachLine { line ->
            if (indicator.isCanceled) process.destroy()
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) {
                indicator.text2 = trimmed.take(120)
                tail.append(trimmed).append('\n')
                if (tail.length > 4000) tail.delete(0, tail.length - 4000)
            }
        }

        val exit = process.waitFor()
        if (exit != 0) {
            throw RuntimeException("rustup-init failed (exit $exit).\n${tail.toString().take(1000)}")
        }
    }
}
