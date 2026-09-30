package io.genai.rust.tools

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * Prompts for a `cargo` command: a dropdown of common commands, plus a crate field that only
 * appears for the commands that take one (`add`, `install`).
 */
class RunCargoToolDialog(project: Project) : DialogWrapper(project) {

    private val commandCombo = ComboBox(COMMANDS)
    private val crateField = JBTextField()
    private lateinit var crateRow: Row

    init {
        title = "Run Cargo Tool"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = panel {
            row("Command:") { cell(commandCombo) }
            row("Crate:") {
                cell(crateField).align(AlignX.FILL)
                    .comment("e.g. serde --features derive")
            }.also { crateRow = it }
        }
        commandCombo.addActionListener { syncCrateRow() }
        syncCrateRow()
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent = commandCombo

    private fun syncCrateRow() = crateRow.visible(needsCrate())

    private fun needsCrate(): Boolean =
        (commandCombo.selectedItem as? String) in setOf("add", "install")

    /** The cargo argv, e.g. ["build"] or ["add","serde","--features","derive"]. */
    val commandArgs: List<String>
        get() {
            val cmd = commandCombo.selectedItem as? String ?: return emptyList()
            val parts = cmd.split(" ")
            val crate = crateField.text.trim()
            return if (needsCrate() && crate.isNotEmpty()) parts + crate.split(Regex("\\s+")) else parts
        }

    companion object {
        private val COMMANDS = arrayOf(
            "build", "check", "test", "run", "add", "update", "fmt", "clippy",
            "tree", "clean", "install", "--version",
        )
    }
}
