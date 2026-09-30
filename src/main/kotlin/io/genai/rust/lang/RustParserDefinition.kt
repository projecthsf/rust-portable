package io.genai.rust.lang

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

/**
 * A minimal parser definition. It builds a FLAT PSI tree (one leaf per lexer token) so the file
 * has real word-level structure — what the editor needs for navigation/selection (e.g. Cmd-hover
 * underlines the token under the cursor, not the whole file). Semantics come from rust-analyzer via LSP.
 */
class RustParserDefinition : ParserDefinition {

    override fun createLexer(project: Project?): Lexer = RustLexer()

    override fun createParser(project: Project?): PsiParser = RustParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getWhitespaceTokens(): TokenSet = WHITESPACE

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getStringLiteralElements(): TokenSet = STRINGS

    override fun createElement(node: ASTNode): PsiElement = ASTWrapperPsiElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = RustFile(viewProvider)

    private class RustParser : PsiParser {
        override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
            val rootMarker = builder.mark()
            while (!builder.eof()) builder.advanceLexer()
            rootMarker.done(root)
            return builder.treeBuilt
        }
    }

    private class RustFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, RustLanguage) {
        override fun getFileType(): FileType = RustFileType
        override fun toString(): String = "Rust File"
    }

    companion object {
        private val FILE = IFileElementType(RustLanguage)
        private val WHITESPACE = TokenSet.create(TokenType.WHITE_SPACE)
        private val COMMENTS = TokenSet.create(RustTokenTypes.LINE_COMMENT, RustTokenTypes.BLOCK_COMMENT)
        private val STRINGS = TokenSet.create(RustTokenTypes.STRING)
    }
}
