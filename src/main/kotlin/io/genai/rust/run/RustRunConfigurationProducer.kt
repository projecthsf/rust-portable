package io.genai.rust.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import io.genai.rust.lang.RustFiles

/**
 * Makes a `.rs` file runnable directly: right-click ▸ Run, and a gutter ▶ marker.
 * Auto-fills the file path; the run follows the current default toolchain unless pinned.
 *
 * Note: unlike the Go plugin this does not pre-seed an `ENV_FILE` variable. That was a
 * Go-ecosystem convention for apps that gate config loading on it; Rust's `dotenvy` and friends
 * read `.env` from the working directory on their own, and the run already starts in the crate
 * root, so seeding it would be cargo-culting. The environment variables field is still there.
 */
class RustRunConfigurationProducer : LazyRunConfigurationProducer<RustRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory =
        ConfigurationTypeUtil.findConfigurationType(RustRunConfigurationType::class.java)
            .configurationFactories[0]

    override fun setupConfigurationFromContext(
        configuration: RustRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>,
    ): Boolean {
        val file = rustFile(context) ?: return false
        configuration.scriptPath = file.path
        configuration.name = file.name
        return true
    }

    override fun isConfigurationFromContext(
        configuration: RustRunConfiguration,
        context: ConfigurationContext,
    ): Boolean {
        val file = rustFile(context) ?: return false
        val script = configuration.scriptPath ?: return false
        return FileUtil.pathsEqual(script, file.path)
    }

    private fun rustFile(context: ConfigurationContext): VirtualFile? {
        val vf = CommonDataKeys.VIRTUAL_FILE.getData(context.dataContext)
            ?: context.psiLocation?.containingFile?.virtualFile
        if (vf == null || vf.isDirectory) return null
        return if (vf.extension?.lowercase() in RustFiles.EXTENSIONS) vf else null
    }
}
