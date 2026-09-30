package io.genai.rust.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.project.Project

class RustConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "RustPortableRun"

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        RustRunConfiguration(project, this, "Rust")

    override fun getOptionsClass(): Class<out RunConfigurationOptions> =
        RustRunConfigurationOptions::class.java
}
