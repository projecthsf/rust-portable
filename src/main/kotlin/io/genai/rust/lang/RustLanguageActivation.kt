package io.genai.rust.lang

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Binds our lightweight Rust file type to `.rs` files — but ONLY in an IDE that has no official
 * Rust support (i.e. IntelliJ IDEA Community, the whole reason this plugin exists).
 *
 * On RustRover / IDEA Ultimate the official Rust plugin owns `.rs`, so we stay dormant: our
 * `<fileType>` is declared with no extensions (see plugin.xml), so we never statically claim
 * `.rs` and there is nothing to clash. We only ever *add* the association, at runtime, when the
 * official plugin is absent — using the supported [FileTypeManager.associate] API.
 */
class RustLanguageActivation : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (!activated.compareAndSet(false, true)) return
        if (rustExtensionAlreadyOwned()) return

        val fileTypeManager = FileTypeManager.getInstance()
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                for (ext in RustFiles.EXTENSIONS) {
                    fileTypeManager.associate(RustFileType, ExtensionFileNameMatcher(ext))
                }
            }
        }
    }

    /**
     * Is `.rs` already claimed by some other file type? On RustRover / IDEA Ultimate the official
     * Rust plugin registers it at load — before this runs — so we defer to whoever owns it.
     * `UnknownFileType` means nobody owns it (Community); our own [RustFileType] means we bound it
     * in a prior session (re-associating is harmless).
     */
    private fun rustExtensionAlreadyOwned(): Boolean {
        val existing = FileTypeManager.getInstance().getFileTypeByExtension("rs")
        return existing != UnknownFileType.INSTANCE && existing != RustFileType
    }

    companion object {
        private val activated = AtomicBoolean(false)
    }
}
