package me.geckotv.ezcordutils.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings UI for EzCord plugin configuration.
 */
class EzCordSettingsConfigurable(private val project: Project) : Configurable {

    private var languageFolderField: TextFieldWithBrowseButton? = null
    private var defaultLanguageField: JBTextField? = null
    private var preferredFallbackLanguageField: JBTextField? = null
    private var showPopupCheckBox: JBCheckBox? = null

    override fun getDisplayName(): String = "EzCord-Utils Settings"

    override fun createComponent(): JComponent {
        val settings = EzCordSettings.getInstance(project)

        // Create folder picker with browse button
        languageFolderField = TextFieldWithBrowseButton().apply {
            text = settings.state.languageFolderPath
            val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            descriptor.title = "Select Language Folder"
            descriptor.description = "Choose the folder containing your language files (.yml, .yaml)"
            addActionListener {
                val chooser = com.intellij.openapi.fileChooser.FileChooser.chooseFile(
                    descriptor,
                    project,
                    null
                )
                chooser?.let {
                    this.text = it.path
                }
            }
        }

        defaultLanguageField = JBTextField(settings.state.defaultLanguage)

        preferredFallbackLanguageField = JBTextField(settings.state.preferredFallbackLanguage).apply {
            toolTipText = "Language to use when a translation is not available in the default language"
        }

        showPopupCheckBox = JBCheckBox("Show popup menu for multiple keys", settings.state.showPopupForMultipleKeys).apply {
            toolTipText = "When enabled, shows a popup menu to choose between multiple language keys. When disabled, jumps directly to the first key."
        }

        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Language folder path:"), languageFolderField!!, 1, false)
            .addLabeledComponent(JBLabel("Default language:"), defaultLanguageField!!, 1, false)
            .addLabeledComponent(JBLabel("Preferred fallback language:"), preferredFallbackLanguageField!!, 1, false)
            .addComponent(showPopupCheckBox!!, 1)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val settings = EzCordSettings.getInstance(project)
        return languageFolderField?.text != settings.state.languageFolderPath ||
               defaultLanguageField?.text != settings.state.defaultLanguage ||
               preferredFallbackLanguageField?.text != settings.state.preferredFallbackLanguage ||
               showPopupCheckBox?.isSelected != settings.state.showPopupForMultipleKeys
    }

    override fun apply() {
        val settings = EzCordSettings.getInstance(project)
        settings.state.languageFolderPath = languageFolderField?.text ?: "local"
        settings.state.defaultLanguage = defaultLanguageField?.text ?: "en"
        settings.state.preferredFallbackLanguage = preferredFallbackLanguageField?.text ?: "en"
        settings.state.showPopupForMultipleKeys = showPopupCheckBox?.isSelected ?: true
    }

    override fun reset() {
        val settings = EzCordSettings.getInstance(project)
        languageFolderField?.text = settings.state.languageFolderPath
        defaultLanguageField?.text = settings.state.defaultLanguage
        preferredFallbackLanguageField?.text = settings.state.preferredFallbackLanguage
        showPopupCheckBox?.isSelected = settings.state.showPopupForMultipleKeys
    }
}

