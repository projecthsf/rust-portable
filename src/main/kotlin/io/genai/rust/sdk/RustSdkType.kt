package io.genai.rust.sdk

import com.intellij.openapi.projectRoots.AdditionalDataConfigurable
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.SdkModel
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import org.jdom.Element
import java.io.File
import java.nio.file.Path
import javax.swing.Icon

/**
 * The "Rust Portable" SDK type. Downloading/switching toolchains is handled by our own UI
 * (Settings ▸ Rust Portable), so we keep this type out of the platform's Java-oriented SDK
 * combos: `allowCreationByUser() = false` removes both the "Add" and "Download" actions there.
 */
class RustSdkType : SdkType("Rust Portable") {

    override fun suggestHomePath(): String? = null

    override fun isValidSdkHome(path: String): Boolean = findCargoExecutable(path) != null

    override fun getVersionString(sdkHome: String): String? {
        val rustc = findRustcExecutable(sdkHome) ?: return null
        return try {
            val process = ProcessBuilder(rustc.absolutePath, "--version")
                .redirectErrorStream(true)
                .also { it.environment().putAll(environment(sdkHome)) }
                .start()
            val out = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            // "rustc 1.89.0 (abc1234 2026-08-07)" -> "1.89.0"
            Regex("""rustc\s+(\d+(?:\.\d+)+)""").find(out)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    override fun suggestSdkName(currentSdkName: String?, sdkHome: String): String {
        val version = getVersionString(sdkHome)
        return if (version != null) "Rust $version" else "Rust"
    }

    override fun createAdditionalDataConfigurable(
        sdkModel: SdkModel,
        sdkModificator: SdkModificator,
    ): AdditionalDataConfigurable? = null

    override fun saveAdditionalData(additionalData: SdkAdditionalData, additional: Element) {}

    override fun getPresentableName(): String = "Rust Portable"

    override fun getIcon(): Icon = ICON

    override fun allowCreationByUser(): Boolean = false

    companion object {
        private val ICON: Icon = IconLoader.getIcon("/icons/rust.svg", RustSdkType::class.java.classLoader)

        fun getInstance(): RustSdkType = SdkType.findInstance(RustSdkType::class.java)

        private fun exe(name: String): String = if (SystemInfo.isWindows) "$name.exe" else name

        /** CARGO_HOME for an SDK home — rustup puts the shims we invoke in `<home>/cargo/bin`. */
        fun cargoHome(home: String): File = File(home, "cargo")

        /** RUSTUP_HOME for an SDK home — where the real toolchains live. */
        fun rustupHome(home: String): File = File(home, "rustup")

        fun findCargoExecutable(home: String?): File? = findBinary(home, "cargo")

        fun findRustcExecutable(home: String?): File? = findBinary(home, "rustc")

        /** The `rustup` shim itself — used to add components such as rust-analyzer. */
        fun findRustupExecutable(home: String?): File? = findBinary(home, "rustup")

        /**
         * Locate a toolchain binary within an SDK home. Our rustup installs put them in
         * `<home>/cargo/bin`, but we also accept a flat `<home>/bin` or a single nested level
         * so an "Add from Disk…" pointed at an existing rustup or unpacked toolchain works too.
         */
        private fun findBinary(home: String?, name: String): File? {
            if (home.isNullOrBlank()) return null
            val root = File(home)
            if (!root.exists()) return null
            val fileName = exe(name)
            val candidates = mutableListOf(
                File(File(cargoHome(home), "bin"), fileName),
                File(File(root, "bin"), fileName),
                File(root, fileName),
            )
            root.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
                candidates += File(File(sub, "bin"), fileName)
                candidates += File(sub, fileName)
            }
            return candidates.firstOrNull { it.isFile }
        }

        /**
         * Environment used while `rustup-init` is installing into [homeDir]. Kept separate from
         * [environment] because at install time nothing exists on disk yet to probe.
         */
        fun installEnvironment(homeDir: Path): Map<String, String> = mapOf(
            "RUSTUP_HOME" to homeDir.resolve("rustup").toString(),
            "CARGO_HOME" to homeDir.resolve("cargo").toString(),
            // rustup-init prompts unless it knows it's non-interactive.
            "RUSTUP_INIT_SKIP_PATH_CHECK" to "yes",
        )

        /**
         * Environment for running cargo/rustc/rust-analyzer against the toolchain at [home]:
         * the isolated RUSTUP_HOME/CARGO_HOME plus `cargo/bin` on PATH, so the shims can find
         * each other and tools that shell out to `cargo` resolve the portable one first.
         */
        fun environment(home: String?): Map<String, String> {
            if (home.isNullOrBlank()) return emptyMap()
            val binDir = File(cargoHome(home), "bin")
            val existingPath = System.getenv("PATH").orEmpty()
            val path = listOfNotNull(binDir.absolutePath, existingPath.ifBlank { null })
                .joinToString(File.pathSeparator)
            return mapOf(
                "RUSTUP_HOME" to rustupHome(home).absolutePath,
                "CARGO_HOME" to cargoHome(home).absolutePath,
                "PATH" to path,
            )
        }
    }
}
