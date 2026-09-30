package io.genai.rust.sdk

enum class OsFamily { WINDOWS, MAC, LINUX }

/**
 * A portable Rust toolchain we can install.
 *
 * Unlike Go — where `go.dev/dl` ships a self-contained archive you just unpack — Rust's
 * standalone tarballs expect their `install.sh` to be run, and Windows ships an `.msi` instead.
 * So a "release" here is a **rustup toolchain spec** (`stable`, `beta`, `nightly`, or a pinned
 * `1.89.0`), installed by the official `rustup-init` into an isolated prefix. See
 * [RustSdkDownloadTask].
 *
 * @param toolchain what we pass to `rustup-init --default-toolchain` (e.g. "stable", "1.89.0")
 * @param version   the resolved version for display/naming, when known
 */
data class RustRelease(
    val toolchain: String,
    val version: String?,
    val os: OsFamily,
    val arch: String,        // Rust target arch: x86_64 / aarch64
    val rustupInitUrl: String,
) {
    val label: String get() = when {
        version != null && toolchain != version -> "Rust $toolchain  ($version)"
        version != null -> "Rust $version"
        else -> "Rust $toolchain"
    } + "  ·  ${os.name.lowercase()}/$arch"

    override fun toString(): String = label
}
