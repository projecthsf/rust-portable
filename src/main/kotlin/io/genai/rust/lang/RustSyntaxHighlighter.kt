package io.genai.rust.lang

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Colors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey as key
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class RustSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = RustLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        when (tokenType) {
            RustTokenTypes.KEYWORD -> pack(KEYWORD)
            RustTokenTypes.STRING -> pack(STRING)
            RustTokenTypes.NUMBER -> pack(NUMBER)
            RustTokenTypes.LINE_COMMENT -> pack(LINE_COMMENT)
            RustTokenTypes.BLOCK_COMMENT -> pack(BLOCK_COMMENT)
            RustTokenTypes.DOC_COMMENT -> pack(DOC_COMMENT)
            RustTokenTypes.IDENTIFIER -> pack(IDENTIFIER)
            RustTokenTypes.OPERATOR -> pack(OPERATOR)
            RustTokenTypes.LIFETIME -> pack(LIFETIME)
            RustTokenTypes.ATTRIBUTE -> pack(ATTRIBUTE)
            RustTokenTypes.MACRO -> pack(MACRO)
            else -> EMPTY
        }

    companion object {
        val KEYWORD: TextAttributesKey = key("RUST_KEYWORD", Colors.KEYWORD)
        val STRING: TextAttributesKey = key("RUST_STRING", Colors.STRING)
        val NUMBER: TextAttributesKey = key("RUST_NUMBER", Colors.NUMBER)
        val LINE_COMMENT: TextAttributesKey = key("RUST_LINE_COMMENT", Colors.LINE_COMMENT)
        val BLOCK_COMMENT: TextAttributesKey = key("RUST_BLOCK_COMMENT", Colors.BLOCK_COMMENT)
        val DOC_COMMENT: TextAttributesKey = key("RUST_DOC_COMMENT", Colors.DOC_COMMENT)
        val IDENTIFIER: TextAttributesKey = key("RUST_IDENTIFIER", Colors.IDENTIFIER)
        val OPERATOR: TextAttributesKey = key("RUST_OPERATOR", Colors.OPERATION_SIGN)
        val LIFETIME: TextAttributesKey = key("RUST_LIFETIME", Colors.LABEL)
        val ATTRIBUTE: TextAttributesKey = key("RUST_ATTRIBUTE", Colors.METADATA)
        val MACRO: TextAttributesKey = key("RUST_MACRO", Colors.FUNCTION_CALL)
        private val EMPTY = emptyArray<TextAttributesKey>()
    }
}
