package io.genai.rust.lang

import com.intellij.psi.tree.IElementType

object RustTokenTypes {
    @JvmField val KEYWORD = IElementType("RUST_KEYWORD", RustLanguage)
    @JvmField val IDENTIFIER = IElementType("RUST_IDENTIFIER", RustLanguage)
    @JvmField val STRING = IElementType("RUST_STRING", RustLanguage)
    @JvmField val NUMBER = IElementType("RUST_NUMBER", RustLanguage)
    @JvmField val LINE_COMMENT = IElementType("RUST_LINE_COMMENT", RustLanguage)
    @JvmField val BLOCK_COMMENT = IElementType("RUST_BLOCK_COMMENT", RustLanguage)
    @JvmField val DOC_COMMENT = IElementType("RUST_DOC_COMMENT", RustLanguage)
    @JvmField val OPERATOR = IElementType("RUST_OPERATOR", RustLanguage)

    /** `'a` in `&'a str` — lexically close to a char literal, so it gets its own type. */
    @JvmField val LIFETIME = IElementType("RUST_LIFETIME", RustLanguage)

    /** The `#` / `#!` of an attribute such as `#[derive(Debug)]`. */
    @JvmField val ATTRIBUTE = IElementType("RUST_ATTRIBUTE", RustLanguage)

    /** A macro invocation name including the bang, e.g. `println!`. */
    @JvmField val MACRO = IElementType("RUST_MACRO", RustLanguage)

    /** Rust's strict and reserved keywords (2021 edition), plus the weak `union`/`dyn`. */
    val KEYWORDS: Set<String> = setOf(
        // strict
        "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum",
        "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod",
        "move", "mut", "pub", "ref", "return", "self", "Self", "static", "struct", "super",
        "trait", "true", "type", "unsafe", "use", "where", "while",
        // reserved for future use
        "abstract", "become", "box", "do", "final", "macro", "override", "priv", "try",
        "typeof", "unsized", "virtual", "yield",
        // contextual
        "union",
    )

    /** Built-in scalar/primitive types — highlighted as keywords, as most Rust editors do. */
    val PRIMITIVES: Set<String> = setOf(
        "bool", "char", "str", "f32", "f64",
        "i8", "i16", "i32", "i64", "i128", "isize",
        "u8", "u16", "u32", "u64", "u128", "usize",
    )
}
