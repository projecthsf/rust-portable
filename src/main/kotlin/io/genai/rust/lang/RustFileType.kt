package io.genai.rust.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * `.rs` file type. Referenced by plugin.xml via fieldName="INSTANCE" — a Kotlin `object`
 * already exposes a static `INSTANCE` field, so no extra declaration needed.
 */
object RustFileType : LanguageFileType(RustLanguage) {
    private val ICON: Icon = IconLoader.getIcon("/icons/rust.svg", RustFileType::class.java.classLoader)

    override fun getName(): String = "Rust File"
    override fun getDescription(): String = "Rust source file"
    override fun getDefaultExtension(): String = "rs"
    override fun getIcon(): Icon = ICON
}
