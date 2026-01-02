package me.geckotv.ezcordutils.language

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.python.psi.PyStringLiteralExpression
import me.geckotv.ezcordutils.settings.EzCordSettings
import me.geckotv.ezcordutils.utils.LanguageUtils
import java.awt.event.MouseEvent

/**
 * Provides gutter icons for language keys that can be clicked to navigate to the definition.
 */
class LanguageKeyLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Only process leaf elements (first child) of Python string literals for performance
        val parent = element.parent
        if (parent !is PyStringLiteralExpression) return null
        if (parent.firstChild != element) return null  // Only register on the first leaf child

        @Suppress("UnstableApiUsage")
        val stringValue = parent.stringValue.trim()
        if (stringValue.isBlank()) return null

        val resolver = LanguageResolver(parent.project)
        val utils = LanguageUtils()
        val filePrefix = utils.getFilePrefix(parent.containingFile.name)

        // Use utility function to find all keys
        val foundKeys = utils.findAllKeysInString(stringValue, filePrefix, resolver)

        // If we found any keys, create a line marker
        if (foundKeys.isNotEmpty()) {
            val tooltipText = if (foundKeys.size == 1) {
                "Navigate to language key: ${foundKeys[0].first}"
            } else {
                "Navigate to language keys (click to choose):\n${foundKeys.joinToString("\n") { "  • ${it.first}" }}"
            }

            return LineMarkerInfo(
                element,
                element.textRange,
                AllIcons.Gutter.ImplementedMethod,
                { tooltipText },
                { mouseEvent, _ ->
                    val settings = EzCordSettings.getInstance(parent.project)

                    if (foundKeys.size == 1) {
                        // Single key: navigate directly
                        val (_, location) = foundKeys[0]
                        utils.gotoLine(parent.project, location.file, location.lineNumber)
                    } else {
                        // Multiple keys: check setting
                        if (settings.state.showPopupForMultipleKeys) {
                            // Show popup menu
                            showNavigationPopup(mouseEvent, foundKeys, parent, utils)
                        } else {
                            // Jump directly to first key
                            val (_, firstLocation) = foundKeys[0]
                            utils.gotoLine(parent.project, firstLocation.file, firstLocation.lineNumber)
                        }
                    }
                },
                GutterIconRenderer.Alignment.RIGHT,
                { "Navigate to language key" }
            )
        }

        return null
    }

    /**
     * Shows a popup menu to choose which key to navigate to.
     */
    private fun showNavigationPopup(
        mouseEvent: MouseEvent,
        keys: List<Pair<String, LanguageKeyLocation>>,
        element: PyStringLiteralExpression,
        utils: LanguageUtils
    ) {
        val popupItems = keys.map { (key, location) ->
            object {
                override fun toString(): String = key
                val keyLocation = location
            }
        }

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(popupItems)
            .setTitle("Select Language Key to Navigate")
            .setItemChosenCallback { selected ->
                utils.gotoLine(element.project, selected.keyLocation.file, selected.keyLocation.lineNumber)
            }
            .createPopup()
            .show(RelativePoint(mouseEvent))
    }
}


