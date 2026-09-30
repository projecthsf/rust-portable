package io.genai.rust.run

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.genai.rust.sdk.RustSdkType
import javax.swing.JComponent

class RustSettingsEditor(project: Project) : SettingsEditor<RustRunConfiguration>() {

    private val sdkCombo = ComboBox<String>()
    private val cargoField = TextFieldWithBrowseButton()
    private val scriptField = TextFieldWithBrowseButton()
    private val envComponent = EnvironmentVariablesComponent()

    init {
        sdkCombo.addItem(NONE)
        ProjectJdkTable.getInstance().getSdksOfType(RustSdkType.getInstance()).forEach {
            sdkCombo.addItem(it.name)
        }
        scriptField.addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle("Select Rust File"),
                project,
            ),
        )
        cargoField.addBrowseFolderListener(
            TextBrowseFolderListener(
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle("Select cargo Executable"),
                project,
            ),
        )
    }

    override fun createEditor(): JComponent = panel {
        row("Rust SDK:") {
            cell(sdkCombo).align(AlignX.FILL)
        }
        row("Or cargo executable:") {
            cell(cargoField).align(AlignX.FILL)
        }.rowComment("Overrides the SDK above when set.")
        row("Rust file:") {
            cell(scriptField).align(AlignX.FILL)
        }
        row {
            cell(envComponent).align(AlignX.FILL)
        }
    }

    override fun resetEditorFrom(s: RustRunConfiguration) {
        sdkCombo.selectedItem = s.sdkName?.takeIf { it.isNotBlank() } ?: NONE
        cargoField.text = s.cargoPath.orEmpty()
        scriptField.text = s.scriptPath.orEmpty()
        envComponent.envData = EnvironmentVariablesData.create(s.envs, s.passParentEnvs)
    }

    override fun applyEditorTo(s: RustRunConfiguration) {
        val selected = sdkCombo.selectedItem as? String
        s.sdkName = if (selected == null || selected == NONE) "" else selected
        s.cargoPath = cargoField.text.trim()
        s.scriptPath = scriptField.text.trim()
        val data = envComponent.envData
        s.envs = HashMap(data.envs)
        s.passParentEnvs = data.isPassParentEnvs
    }

    companion object {
        private const val NONE = "<none>"
    }
}
