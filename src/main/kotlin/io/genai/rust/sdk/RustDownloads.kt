package io.genai.rust.sdk

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.io.HttpRequests
import com.intellij.util.system.CpuArch

/**
 * The catalogue of installable Rust toolchains.
 *
 * Rust has no machine-readable release index equivalent to `go.dev/dl/?mode=json`, but the
 * channel manifest at `static.rust-lang.org` names the current stable version. We read that,
 * then offer the three channels plus the previous few stable releases — Rust ships 1.N.0 every
 * six weeks with no gaps, so counting back from the current N is reliable and costs no extra
 * requests. `rustup-init --default-toolchain` accepts every one of these strings verbatim.
 */
object RustDownloads {

    private const val STABLE_MANIFEST = "https://static.rust-lang.org/dist/channel-rust-stable.toml"
    private const val RUSTUP_BASE = "https://static.rust-lang.org/rustup/dist"

    /** How many older stable releases to offer alongside the current one. */
    private const val PREVIOUS_STABLES = 5

    fun currentOs(): OsFamily = when {
        SystemInfo.isWindows -> OsFamily.WINDOWS
        SystemInfo.isMac -> OsFamily.MAC
        else -> OsFamily.LINUX
    }

    /** Rust's arch naming (x86_64 / aarch64), not Go's (amd64 / arm64). */
    private fun currentArch(): String = if (CpuArch.isArm64()) "aarch64" else "x86_64"

    /** The rustup target triple for this machine. */
    fun currentTarget(): String {
        val arch = currentArch()
        return when (currentOs()) {
            OsFamily.MAC -> "$arch-apple-darwin"
            OsFamily.LINUX -> "$arch-unknown-linux-gnu"
            OsFamily.WINDOWS -> "$arch-pc-windows-msvc"
        }
    }

    fun rustupInitUrl(): String {
        val exe = if (currentOs() == OsFamily.WINDOWS) "rustup-init.exe" else "rustup-init"
        return "$RUSTUP_BASE/${currentTarget()}/$exe"
    }

    fun fetchAvailableWithProgress(project: Project?): List<RustRelease> =
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            ThrowableComputable { fetchAvailable() },
            "Fetching Available Rust Versions…",
            true,
            project,
        )

    /** Blocking fetch — must be called off the EDT. Returns newest-first. */
    fun fetchAvailable(): List<RustRelease> {
        val stable = try {
            fetchStableVersion()
        } catch (e: Exception) {
            null
        }
        return buildList {
            add(release("stable", stable))
            stable?.let { addAll(previousStables(it).map { v -> release(v, v) }) }
            add(release("beta", null))
            add(release("nightly", null))
        }
    }

    private fun release(toolchain: String, version: String?) =
        RustRelease(
            toolchain = toolchain,
            version = version,
            os = currentOs(),
            arch = currentArch(),
            rustupInitUrl = rustupInitUrl(),
        )

    /**
     * Reads the Rust version out of the stable channel manifest.
     *
     * It must be anchored on the `[pkg.rust]` section, not just the first `version =` line: the
     * manifest lists packages alphabetically, so `[pkg.cargo]` comes first and its version is
     * cargo's own (0.x), which would produce nonsense toolchain specs. The literal `]` also keeps
     * this from matching `[pkg.rust-std]` or `[pkg.rustc]`.
     */
    private fun fetchStableVersion(): String? {
        val toml = HttpRequests.request(STABLE_MANIFEST)
            .productNameAsUserAgent()
            .readString()
        return Regex("""\[pkg\.rust]\s*\r?\nversion\s*=\s*"(\d+\.\d+\.\d+)""")
            .find(toml)?.groupValues?.get(1)
    }

    /**
     * "1.89.0" -> ["1.88.0", "1.87.0", …]. Rust's minor number increments by one every release
     * train, so walking it back never names a version that doesn't exist.
     */
    private fun previousStables(current: String): List<String> {
        val parts = current.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return emptyList()
        val (major, minor) = parts
        return (1..PREVIOUS_STABLES)
            .map { minor - it }
            .filter { it > 0 }
            .map { "$major.$it.0" }
    }
}
