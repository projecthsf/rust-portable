package io.genai.rust.lang

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/**
 * A small hand-written lexer for basic Rust highlighting. It is intentionally not a full Rust
 * grammar — it covers comments, strings, numbers, keywords and identifiers. Semantic
 * understanding comes from rust-analyzer via LSP; this is just the local highlighting/PSI
 * skeleton the platform expects, and what you see before code intelligence is enabled.
 *
 * Three things here genuinely differ from a C-like lexer and are worth not getting wrong:
 *  - **Lifetimes.** `'a` has no closing quote. Treating it as a char literal would swallow the
 *    rest of the file as one string, so [quoteOrLifetime] looks ahead before deciding.
 *  - **Nested block comments.** Rust lets a block comment contain another one, so the opener
 *    and closer have to be counted rather than scanned for.
 *  - **Raw strings.** `r"…"`, `r#"…"#`, `r##"…"##` (and the `b`-prefixed byte variants) end only
 *    on a quote followed by the same number of hashes.
 */
class RustLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var endOffset = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.endOffset = endOffset
        this.tokenStart = startOffset
        locateToken()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        tokenStart = tokenEnd
        locateToken()
    }

    private fun locateToken() {
        if (tokenStart >= endOffset) {
            tokenType = null
            tokenEnd = tokenStart
            return
        }
        val c = buffer[tokenStart]
        when {
            c.isWhitespace() -> whitespace()
            c == '/' && peek(tokenStart + 1) == '/' -> lineComment()
            c == '/' && peek(tokenStart + 1) == '*' -> blockComment()
            // r"…" / r#"…"# / br"…" / b"…" — check before the plain identifier path.
            isRawStringStart(tokenStart) -> rawString()
            isByteStringStart(tokenStart) -> { tokenEnd = tokenStart + 1; quoted('"', from = tokenStart + 1) }
            c == '"' -> quoted('"')
            c == '\'' -> quoteOrLifetime()
            c == '#' -> attribute()
            c.isDigit() -> number()
            c == '_' || c.isLetter() -> word()
            else -> {
                tokenType = RustTokenTypes.OPERATOR
                tokenEnd = tokenStart + 1
            }
        }
    }

    private fun whitespace() {
        var i = tokenStart
        while (i < endOffset && buffer[i].isWhitespace()) i++
        tokenType = TokenType.WHITE_SPACE
        tokenEnd = i
    }

    /** `//` line comment; `///` and `//!` are doc comments. */
    private fun lineComment() {
        val third = peek(tokenStart + 2)
        val isDoc = (third == '/' && peek(tokenStart + 3) != '/') || third == '!'
        var i = tokenStart
        while (i < endOffset && buffer[i] != '\n') i++
        tokenType = if (isDoc) RustTokenTypes.DOC_COMMENT else RustTokenTypes.LINE_COMMENT
        tokenEnd = i
    }

    /** Block comment, balancing the nesting Rust allows. A `**` or `!` opener means doc comment. */
    private fun blockComment() {
        val third = peek(tokenStart + 2)
        val isDoc = (third == '*' && peek(tokenStart + 3) != '/') || third == '!'
        var i = tokenStart + 2
        var depth = 1
        while (i < endOffset && depth > 0) {
            when {
                buffer[i] == '/' && peek(i + 1) == '*' -> { depth++; i += 2 }
                buffer[i] == '*' && peek(i + 1) == '/' -> { depth--; i += 2 }
                else -> i++
            }
        }
        tokenType = if (isDoc) RustTokenTypes.DOC_COMMENT else RustTokenTypes.BLOCK_COMMENT
        tokenEnd = i.coerceAtMost(endOffset)
    }

    /** `r"`, `r#"`, `br"`, `br##"` … — a raw-string prefix at [index]? */
    private fun isRawStringStart(index: Int): Boolean {
        var i = index
        if (peek(i) == 'b') i++
        if (peek(i) != 'r') return false
        i++
        while (peek(i) == '#') i++
        return peek(i) == '"'
    }

    /** `b"…"` byte string (but not `br"…"`, which the raw path handles). */
    private fun isByteStringStart(index: Int): Boolean =
        peek(index) == 'b' && peek(index + 1) == '"'

    /**
     * Raw string literal — no escapes, may span lines, terminated by `"` plus exactly the
     * number of `#` that opened it.
     */
    private fun rawString() {
        var i = tokenStart
        if (peek(i) == 'b') i++
        i++ // 'r'
        var hashes = 0
        while (peek(i) == '#') { hashes++; i++ }
        i++ // opening quote
        while (i < endOffset) {
            if (buffer[i] == '"' && hashesFollow(i + 1, hashes)) {
                i += 1 + hashes
                break
            }
            i++
        }
        tokenType = RustTokenTypes.STRING
        tokenEnd = i.coerceAtMost(endOffset)
    }

    private fun hashesFollow(index: Int, count: Int): Boolean {
        for (k in 0 until count) if (peek(index + k) != '#') return false
        return true
    }

    /** Interpreted string — honours backslash escapes and may span lines. */
    private fun quoted(quote: Char, from: Int = tokenStart) {
        var i = from + 1
        while (i < endOffset) {
            val ch = buffer[i]
            if (ch == '\\') { i += 2; continue }
            if (ch == quote) { i++; break }
            i++
        }
        tokenType = RustTokenTypes.STRING
        tokenEnd = i.coerceAtMost(endOffset)
    }

    /**
     * `'` starts either a char literal (`'a'`, `'\n'`, `'\u{1F600}'`) or a lifetime (`'a`,
     * `'static`). We look ahead for the closing quote: an escape, or a single character
     * followed by `'`, means char literal; anything else is a lifetime.
     */
    private fun quoteOrLifetime() {
        val next = peek(tokenStart + 1)
        val isChar = when {
            next == '\\' -> true                      // '\n', '\u{…}' — always a char literal
            peek(tokenStart + 2) == '\'' -> true      // 'x' — exactly one char then a quote
            else -> false
        }
        if (isChar) {
            quoted('\'')
            return
        }
        // Lifetime: 'ident
        var i = tokenStart + 1
        while (i < endOffset && (buffer[i] == '_' || buffer[i].isLetterOrDigit())) i++
        tokenType = if (i > tokenStart + 1) RustTokenTypes.LIFETIME else RustTokenTypes.OPERATOR
        tokenEnd = if (i > tokenStart + 1) i else tokenStart + 1
    }

    /** `#[…]` / `#![…]` — we only mark the `#`/`#!`; the body lexes as ordinary tokens. */
    private fun attribute() {
        val next = peek(tokenStart + 1)
        if (next == '[' || (next == '!' && peek(tokenStart + 2) == '[')) {
            tokenType = RustTokenTypes.ATTRIBUTE
            tokenEnd = if (next == '!') tokenStart + 2 else tokenStart + 1
        } else {
            tokenType = RustTokenTypes.OPERATOR
            tokenEnd = tokenStart + 1
        }
    }

    /** Decimal/hex/octal/binary with `_` separators and type suffixes (`1_000u64`, `0xFFu8`). */
    private fun number() {
        var i = tokenStart
        if (buffer[i] == '0' && (peek(i + 1) == 'x' || peek(i + 1) == 'o' || peek(i + 1) == 'b')) {
            i += 2
            while (i < endOffset && (buffer[i].isLetterOrDigit() || buffer[i] == '_')) i++
        } else {
            while (i < endOffset) {
                val ch = buffer[i]
                when {
                    ch.isDigit() || ch == '_' -> i++
                    // A single '.' continues a float, but '..' is the range operator.
                    ch == '.' && peek(i + 1) != '.' && peek(i + 1) != '_' -> i++
                    ch == 'e' || ch == 'E' -> {
                        i++
                        if (peek(i) == '+' || peek(i) == '-') i++
                    }
                    // Type suffix: 1u8, 3.0f64, 5usize
                    ch.isLetter() -> {
                        while (i < endOffset && (buffer[i].isLetterOrDigit() || buffer[i] == '_')) i++
                        break
                    }
                    else -> break
                }
            }
        }
        tokenType = RustTokenTypes.NUMBER
        tokenEnd = i.coerceAtMost(endOffset)
    }

    private fun word() {
        var i = tokenStart
        while (i < endOffset && (buffer[i] == '_' || buffer[i].isLetterOrDigit())) i++
        val text = buffer.subSequence(tokenStart, i).toString()
        // `println!` / `vec!` — the bang is part of the macro name (but not `!=`).
        if (i < endOffset && buffer[i] == '!' && peek(i + 1) != '=') {
            tokenType = RustTokenTypes.MACRO
            tokenEnd = i + 1
            return
        }
        tokenType = when (text) {
            in RustTokenTypes.KEYWORDS -> RustTokenTypes.KEYWORD
            in RustTokenTypes.PRIMITIVES -> RustTokenTypes.KEYWORD
            else -> RustTokenTypes.IDENTIFIER
        }
        tokenEnd = i
    }

    private fun peek(index: Int): Char = if (index < endOffset) buffer[index] else ' '
}
