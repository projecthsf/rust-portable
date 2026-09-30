package io.genai.rust.sdk

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/** Lets the user pick which portable Rust toolchain to download. */
class RustDownloadDialog(releases: List<RustRelease>) : DialogWrapper(true) {
    private val combo = ComboBox(releases.toTypedArray())

    var selected: RustRelease? = null
        private set

    init {
        title = "Download Rust Toolchain"
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Toolchain:") {
            cell(combo).align(AlignX.FILL)
        }
        row {
            comment("Downloaded to ~/.rust-portable and registered as a Rust SDK.")
        }
    }

    override fun doOKAction() {
        selected = combo.selectedItem as? RustRelease
        super.doOKAction()
    }
}
