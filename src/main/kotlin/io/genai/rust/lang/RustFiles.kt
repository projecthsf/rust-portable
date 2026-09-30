package io.genai.rust.lang

/** Single source of truth for what counts as a Rust file, by extension. */
object RustFiles {
    /** Extensions we treat as Rust. Used both to bind our file type (Community-only, see
     *  [RustLanguageActivation]) and by the tooling, which keys off the extension directly. */
    val EXTENSIONS: Set<String> = setOf("rs")
}
