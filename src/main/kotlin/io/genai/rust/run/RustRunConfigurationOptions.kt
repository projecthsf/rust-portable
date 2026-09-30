package io.genai.rust.run

import com.intellij.execution.configurations.RunConfigurationOptions

/** Persisted state for a Rust run configuration. */
class RustRunConfigurationOptions : RunConfigurationOptions() {
    private val scriptPathProp = string("").provideDelegate(this, "scriptPath")
    private val sdkNameProp = string("").provideDelegate(this, "sdkName")
    private val cargoPathProp = string("").provideDelegate(this, "cargoPath")

    var scriptPath: String?
        get() = scriptPathProp.getValue(this)
        set(value) = scriptPathProp.setValue(this, value)

    var sdkName: String?
        get() = sdkNameProp.getValue(this)
        set(value) = sdkNameProp.setValue(this, value)

    var cargoPath: String?
        get() = cargoPathProp.getValue(this)
        set(value) = cargoPathProp.setValue(this, value)

    /** User environment variables for the run (e.g. RUST_LOG=debug). */
    var envs by map<String, String>()

    /** Whether to inherit the system/parent environment on top of [envs]. */
    var passParentEnvs by property(true)
}
