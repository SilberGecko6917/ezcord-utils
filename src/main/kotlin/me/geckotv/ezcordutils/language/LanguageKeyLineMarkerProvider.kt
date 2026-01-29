package me.geckotv.ezcordutils.language

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import me.geckotv.ezcordutils.settings.EzCordSettings
import me.geckotv.ezcordutils.utils.LanguageUtils
import java.awt.event.MouseEvent

/**
 * Provides gutter icons for language keys that can be clicked to navigate to the definition.
 */
class LanguageKeyLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Only process leaf elements (first child is null)
        if (element.firstChild != null) return null

        val parent = element.parent ?: return null
        var targetElement: PsiElement? = null

        // helper to check class name safely
        fun isType(elem: PsiElement, typeShortName: String): Boolean {
            return elem.javaClass.simpleName.contains(typeShortName)
        }

        // Case 1: Standard String Literal
        // Structure: PyStringLiteralExpression -> [Leaf: "string"]
        if (isType(parent, "PyStringLiteralExpression")) {
            if (parent.firstChild == element) {
                targetElement = parent
            }
        }
        // Case 2: Formatted String (f-string)
        // Structure: PyStringLiteralExpression -> PyFormattedStringElement -> [Leaf: "f" or "f'"]
        else if (isType(parent, "PyFormattedStringElement")) {
            if (parent.firstChild == element) {
                val grandParent = parent.parent
                if (grandParent != null && isType(grandParent, "PyStringLiteralExpression")) {
                    targetElement = parent
                }
            }
        }

        if (targetElement == null) return null

        val utils = LanguageUtils()
        val stringValue = utils.extractStringValue(targetElement)
        if (!utils.isValidLanguageKey(stringValue)) return null


        val resolver = LanguageResolver(parent.project)
        val filePrefix = utils.getFilePrefix(parent.containingFile.name)

        // Use utility function to find all keys
        val foundKeys = utils.findAllKeysInString(stringValue, filePrefix, resolver)

        // If we found any keys, create a line marker
        if (foundKeys.isNotEmpty()) {
            // Check if any key is missing in the primary language (fallback only)
            val hasFallbackOnly = foundKeys.any { (key, _) ->
                !resolver.existsInPrimaryLanguage(key)
            }

            // Use different icon for fallback-only keys
            val icon = if (hasFallbackOnly) {
                AllIcons.General.Warning  // Warning icon for fallback translations
            } else {
                AllIcons.Gutter.ImplementedMethod  // Normal icon for primary language
            }

            val tooltipText = if (foundKeys.size == 1) {
                val fallbackWarning = if (hasFallbackOnly) " ⚠️ (fallback only)" else ""
                "Navigate to language key: ${foundKeys[0].first}$fallbackWarning"
            } else {
                val fallbackWarning = if (hasFallbackOnly) " ⚠️ (some keys are fallback only)" else ""
                "Navigate to language keys (click to choose):\n${
                    foundKeys.joinToString("\n") { (key, _) ->
                        val isFallback = !resolver.existsInPrimaryLanguage(key)
                        val marker = if (isFallback) " ⚠️" else ""
                        "  • $key$marker"
                    }
                }$fallbackWarning"
            }

            return LineMarkerInfo(
                element,
                element.textRange,
                icon,
                { tooltipText },
                { mouseEvent, _ ->
                    val settings = EzCordSettings.getInstance(parent.project)
                    val isControlDown = mouseEvent.isControlDown

                    if (foundKeys.size == 1) {
                        // Single key: navigate directly
                        val (key, location) = foundKeys[0]
                        val targetLocation = if (isControlDown) {
                            resolver.getFallbackKeyLocation(key) ?: location
                        } else {
                            location
                        }
                        utils.gotoLine(parent.project, targetLocation.file, targetLocation.lineNumber)
                    } else {
                        // Multiple keys: check setting
                        if (settings.state.showPopupForMultipleKeys) {
                            // Show popup menu
                            showNavigationPopup(mouseEvent, foundKeys, parent, utils, resolver, isControlDown)
                        } else {
                            // Jump directly to first key
                            val (key, firstLocation) = foundKeys[0]
                            val targetLocation = if (isControlDown) {
                                resolver.getFallbackKeyLocation(key) ?: firstLocation
                            } else {
                                firstLocation
                            }
                            utils.gotoLine(parent.project, targetLocation.file, targetLocation.lineNumber)
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
        element: PsiElement,
        utils: LanguageUtils,
        resolver: LanguageResolver,
        isControlDown: Boolean
    ) {
        val popupItems = keys.map { (key, location) ->
            val targetLocation = if (isControlDown) {
                resolver.getFallbackKeyLocation(key) ?: location
            } else {
                location
            }

            object {
                override fun toString(): String = key + if (isControlDown) " (Fallback)" else ""
                val keyLocation = targetLocation
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


