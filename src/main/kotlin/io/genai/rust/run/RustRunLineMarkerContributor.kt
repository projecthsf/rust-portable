package io.genai.rust.run

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import io.genai.rust.lang.RustTokenTypes

/**
 * Puts a green ▶ Run marker in the gutter next to `fn main()`, so a Rust program launches with
 * one click (like RustRover). Clicking it runs the [RustRunConfiguration] for the file
 * (`cargo run` from the crate root, or a rustc compile for a loose file).
 *
 * Our PSI is flat (one leaf per lexer token), so we anchor on the `main` identifier leaf that
 * directly follows the `fn` keyword — exactly one marker per main function. Unlike Go there is
 * no `package main` clause to check: any `fn main` is a potential entry point, and for a file
 * inside a crate cargo decides which binary actually runs.
 */
class RustRunLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        if (element.firstChild != null) return null // leaves only
        if (element.node?.elementType != RustTokenTypes.IDENTIFIER) return null
        if (element.text != "main") return null
        if (PsiTreeUtil.prevVisibleLeaf(element)?.text != "fn") return null // `fn main`

        val actions = ExecutorAction.getActions(0)
        if (actions.isEmpty()) return null
        return Info(AllIcons.RunConfigurations.TestState.Run, actions) { "Run Rust program" }
    }
}
