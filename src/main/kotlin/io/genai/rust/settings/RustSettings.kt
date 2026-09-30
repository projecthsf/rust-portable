package io.genai.rust.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.projectRoots.Sdk
import io.genai.rust.sdk.RustSdkManager
import io.genai.rust.sdk.RustSdkType

/**
 * Remembers which Rust toolchain is the "current" one. Application-level, since the toolchains
 * (SDKs) are application-level. Run configs with no explicit toolchain fall back to this.
 */
@Service(Service.Level.APP)
@State(name = "RustPortable", storages = [Storage("rust-portable.xml")])
class RustSettings : PersistentStateComponent<RustSettings.State> {

    class State {
        var defaultSdkName: String? = null
        // Code intelligence (rust-analyzer completion, navigation, errors). Default ON — the headline
        // feature beyond "run a file". Gated via RustClientFeatures.isEnabled.
        var codeIntelligenceEnabled: Boolean = true

        // The user dismissed the "turn on code intelligence" editor banner. Separate from
        // codeIntelligenceEnabled: this only hides the prompt, for people who just want to run
        // .rs files. Persisted, because editor notification panels are rebuilt on every file
        // open — a non-persistent dismiss would reappear immediately.
        var codeIntelligencePromptDismissed: Boolean = false
    }

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) {
        myState = state
    }

    var defaultSdkName: String?
        get() = myState.defaultSdkName
        set(value) { myState.defaultSdkName = value }

    var codeIntelligenceEnabled: Boolean
        get() = myState.codeIntelligenceEnabled
        set(value) { myState.codeIntelligenceEnabled = value }

    var codeIntelligencePromptDismissed: Boolean
        get() = myState.codeIntelligencePromptDismissed
        set(value) { myState.codeIntelligencePromptDismissed = value }

    /** The selected toolchain, restricted to ones that still exist on disk, falling back to
     *  the first usable install. */
    fun defaultSdk(): Sdk? {
        val usable = RustSdkManager.listSdks().filter { RustSdkType.findCargoExecutable(it.homePath) != null }
        return usable.firstOrNull { it.name == myState.defaultSdkName } ?: usable.firstOrNull()
    }

    companion object {
        fun getInstance(): RustSettings = service()
    }
}
