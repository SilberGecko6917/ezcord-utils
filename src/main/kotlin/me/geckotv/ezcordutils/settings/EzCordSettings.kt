package me.geckotv.ezcordutils.settings

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

/**
 * Persistent settings for EzCord utilities.
 */
@Service(Service.Level.PROJECT)
@State(name = "EzCordSettings", storages = [Storage("ezcord-settings.xml")])
class EzCordSettings : PersistentStateComponent<EzCordSettings.State> {

    private var myState = State()

    data class State(
        var languageFolderPath: String = "",
        var defaultLanguage: String = "en",
        var showPopupForMultipleKeys: Boolean = true
    )

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): EzCordSettings {
            return project.service<EzCordSettings>()
        }
    }
}

