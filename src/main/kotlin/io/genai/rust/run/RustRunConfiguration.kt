package io.genai.rust.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.SystemInfo
import io.genai.rust.sdk.RustSdkType
import io.genai.rust.settings.RustSettings
import java.io.File
import java.nio.file.Files

class RustRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String?,
) : RunConfigurationBase<RustRunConfigurationOptions>(project, factory, name) {

    public override fun getOptions(): RustRunConfigurationOptions =
        super.getOptions() as RustRunConfigurationOptions

    var scriptPath: String?
        get() = options.scriptPath
        set(value) { options.scriptPath = value }

    var sdkName: String?
        get() = options.sdkName
        set(value) { options.sdkName = value }

    var cargoPath: String?
        get() = options.cargoPath
        set(value) { options.cargoPath = value }

    var envs: MutableMap<String, String>
        get() = options.envs
        set(value) { options.envs = value }

    var passParentEnvs: Boolean
        get() = options.passParentEnvs
        set(value) { options.passParentEnvs = value }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        RustSettingsEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return object : CommandLineState(environment) {
            @Throws(ExecutionException::class)
            override fun startProcess(): ProcessHandler {
                val script = scriptPath?.takeIf { it.isNotBlank() }
                    ?: throw ExecutionException("No Rust file specified")
                val cargo = resolveCargo()
                    ?: throw ExecutionException(
                        "No Rust toolchain configured — pick a Rust SDK or set a cargo executable path",
                    )

                val scriptFile = File(script)
                val dir = scriptFile.parentFile
                val crateRoot = dir?.let { findCrateRoot(it) }

                val env = resolveHome()?.let { RustSdkType.environment(it) } ?: emptyMap()

                val cmd = if (crateRoot != null) {
                    cargoRun(cargo, crateRoot)
                } else {
                    // No Cargo.toml anywhere above: a loose single file. Rust has no `go run`
                    // equivalent, so compile with rustc into a temp dir first and then execute
                    // the binary — two processes, but it keeps single-file scratch files runnable.
                    compileLooseFile(cargo, scriptFile, env)
                }

                cmd.withEnvironment(env)
                cmd.withEnvironment(options.envs)
                cmd.withParentEnvironmentType(
                    if (options.passParentEnvs) GeneralCommandLine.ParentEnvironmentType.CONSOLE
                    else GeneralCommandLine.ParentEnvironmentType.NONE,
                )

                val handler = OSProcessHandler(cmd)
                ProcessTerminatedListener.attach(handler)
                return handler
            }
        }
    }

    /**
     * `cargo run` from the crate root. Working dir is the crate root — not the file's directory —
     * because a program's relative paths (loading a `.env`, reading `assets/`) resolve against the
     * process CWD, and that is what `cargo run` from a terminal would give you.
     */
    private fun cargoRun(cargo: String, crateRoot: File): GeneralCommandLine {
        val cmd = GeneralCommandLine()
        cmd.exePath = cargo
        cmd.addParameter("run")
        cmd.setWorkDirectory(crateRoot)
        return cmd
    }

    /**
     * Compiles a standalone `.rs` with rustc and returns a command line for the resulting binary.
     * Throws with the compiler's own diagnostics when it fails — those are the useful part.
     */
    private fun compileLooseFile(
        cargo: String,
        scriptFile: File,
        env: Map<String, String>,
    ): GeneralCommandLine {
        val rustc = File(File(cargo).parentFile, if (SystemInfo.isWindows) "rustc.exe" else "rustc")
        if (!rustc.isFile) {
            throw ExecutionException(
                "This file is not inside a Cargo project and no rustc was found next to cargo, " +
                    "so it cannot be compiled on its own.",
            )
        }

        val outDir = Files.createTempDirectory("rust-portable-run").toFile()
        outDir.deleteOnExit()
        val baseName = scriptFile.nameWithoutExtension
        val binary = File(outDir, if (SystemInfo.isWindows) "$baseName.exe" else baseName)

        val compile = GeneralCommandLine(
            rustc.absolutePath,
            scriptFile.absolutePath,
            "-o", binary.absolutePath,
        )
            .withWorkDirectory(scriptFile.parentFile)
            .withEnvironment(env)

        val result = runCompile(compile)
        if (result.exitCode != 0 || !binary.isFile) {
            throw ExecutionException(
                "rustc failed:\n" + result.stderr.ifBlank { result.stdout }.take(4000),
            )
        }

        val cmd = GeneralCommandLine(binary.absolutePath)
        cmd.setWorkDirectory(scriptFile.parentFile)
        return cmd
    }

    /** Runs rustc under a modal progress indicator so the IDE isn't frozen during a long compile. */
    private fun runCompile(compile: GeneralCommandLine): com.intellij.execution.process.ProcessOutput {
        var output: com.intellij.execution.process.ProcessOutput? = null
        var failure: Exception? = null
        ProgressManager.getInstance().run(
            object : Task.Modal(project, "Compiling with rustc…", true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    try {
                        output = ExecUtil.execAndGetOutput(compile)
                    } catch (e: Exception) {
                        failure = e
                    }
                }
            },
        )
        failure?.let { throw ExecutionException("Could not run rustc: ${it.message}") }
        return output ?: throw ExecutionException("rustc produced no output")
    }

    /** The nearest ancestor of [dir] (inclusive) that contains a Cargo.toml, or null. */
    private fun findCrateRoot(dir: File): File? =
        generateSequence(dir) { it.parentFile }.firstOrNull { File(it, "Cargo.toml").isFile }

    /** An explicit cargo path wins; then the SDK pinned on this config; otherwise the default. */
    private fun resolveCargo(): String? {
        cargoPath?.takeIf { it.isNotBlank() }?.let { return it }

        val name = sdkName?.takeIf { it.isNotBlank() }
        val pinned = name?.let { ProjectJdkTable.getInstance().findJdk(it) }
        pinned?.homePath?.let { RustSdkType.findCargoExecutable(it) }?.let { return it.absolutePath }

        val default = RustSettings.getInstance().defaultSdk()
        return default?.homePath?.let { RustSdkType.findCargoExecutable(it)?.absolutePath }
    }

    /**
     * The SDK home backing this run, for RUSTUP_HOME / CARGO_HOME / PATH.
     *
     * Taken from the registered SDK rather than derived from the cargo path: our own installs
     * are `<home>/cargo/bin/cargo`, but an "Add from Disk…" toolchain can be flat
     * (`<home>/bin/cargo`), and walking a fixed number of parents up would land outside the
     * home and hand rustup the wrong directories. Returns null for an explicit cargo path
     * override, where there is no SDK home to speak of — PATH inheritance covers that case.
     */
    private fun resolveHome(): String? {
        if (!cargoPath.isNullOrBlank()) return null

        val name = sdkName?.takeIf { it.isNotBlank() }
        val pinned = name?.let { ProjectJdkTable.getInstance().findJdk(it) }
        pinned?.homePath?.takeIf { RustSdkType.findCargoExecutable(it) != null }?.let { return it }

        return RustSettings.getInstance().defaultSdk()?.homePath
    }
}
