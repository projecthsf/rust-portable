package io.genai.rust.run

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NotNullLazyValue

class RustRunConfigurationType : ConfigurationTypeBase(
    "RustPortableRunConfiguration",
    // "(Portable)" so it's distinct from the official Rust plugin's run type on RustRover / Ultimate.
    "Rust File (Portable)",
    "Run a Rust file with a portable Rust toolchain",
    NotNullLazyValue.createValue { AllIcons.Actions.Execute },
) {
    init {
        addFactory(RustConfigurationFactory(this))
    }
}
