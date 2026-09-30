package io.genai.rust.lang

import com.intellij.lang.Language

/**
 * The Rust language for this plugin.
 *
 * The ID is deliberately **"RustPortable"**, NOT "Rust": IntelliJ requires language IDs to be
 * globally unique, and the official JetBrains Rust plugin (com.jetbrains.rust, on RustRover /
 * IDEA Ultimate / CLion) already registers ID "Rust". A distinct ID lets us coexist quietly;
 * the display name is still "Rust". The ID must match the `language=` attributes in plugin.xml.
 */
object RustLanguage : Language("RustPortable") {
    override fun getDisplayName(): String = "Rust"
}
