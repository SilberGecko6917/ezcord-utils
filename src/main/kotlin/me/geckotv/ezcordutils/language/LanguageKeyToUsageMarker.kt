package me.geckotv.ezcordutils.language

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.ui.awt.RelativePoint
import com.jetbrains.python.psi.PyStringLiteralExpression
import me.geckotv.ezcordutils.settings.EzCordSettings
import me.geckotv.ezcordutils.utils.LanguageUtils
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import java.awt.event.MouseEvent


class LanguageKeyToUsageMarker : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val parent = element.parent
        if (parent !is YAMLKeyValue) return null
        if (parent.key != element) return null

        if (parent.value is YAMLMapping) return null

        val project = element.project
        val settings = EzCordSettings.getInstance(project)
        val file = element.containingFile.virtualFile ?: return null

        val languageFolder = settings.state.languageFolderPath

        if (languageFolder.isEmpty()) return null

        val langDir = LocalFileSystem.getInstance().findFileByPath(languageFolder)
        if (langDir == null || !file.path.startsWith(langDir.path)) return null

        if (settings.state.excludedLanguageFiles.contains(file.nameWithoutExtension)) return null

        val fullKey = getFullKey(parent)
        if (fullKey.isEmpty()) return null

        if (hasAnyUsage(project, fullKey)) {
            return LineMarkerInfo(
                element,
                element.textRange,
                AllIcons.Gutter.ImplementingMethod,
                { "Find usages of '$fullKey'" },
                { e, _ -> showUsages(e, project, fullKey) },
                GutterIconRenderer.Alignment.RIGHT,
                { "Find usages" }
            )
        }

        return null
    }

    /**
     * Reconstructs the full dot-notation key from the YAML element.
     */
    private fun getFullKey(element: YAMLKeyValue): String {
        val keys = mutableListOf<String>()
        var current: PsiElement? = element
        while (current is YAMLKeyValue) {
            keys.add(current.keyText)
            val parent = current.parent
            current = if (parent is YAMLMapping) {
                val grandParent = parent.parent
                grandParent as? YAMLKeyValue
            } else {
                null // Stop if we hit top level (parent is probably YAMLDocument or similar)
            }
        }
        return keys.reversed().joinToString(".")
    }

    /**
     * Checks if there is at least one usage of the key in the project.
     */
    private fun hasAnyUsage(project: Project, fullKey: String): Boolean {
        val helper = PsiSearchHelper.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        val utils = LanguageUtils()

        // 1. Explicit usage search
        val keyParts = fullKey.split(".")
        val searchWord = keyParts.lastOrNull() ?: fullKey

        var found = false

        helper.processElementsWithWord(
            { element, _ ->
                val literal = getParentLiteral(element) ?: return@processElementsWithWord true
                val str = utils.extractStringValue(literal)
                if (str == fullKey || str.contains("{$fullKey}") || str.contains("{{$fullKey}}")) {
                    found = true; false
                } else true
            },
            scope,
            searchWord,
            UsageSearchContext.IN_STRINGS,
            true
        )
        if (found) return true

        // 2. Implicit usage via file prefixes
        if (keyParts.size > 1) {
            for (i in 1 until keyParts.size) {
                val prefix = keyParts.take(i).joinToString(".")
                val suffix = keyParts.drop(i).joinToString(".")

                val filename = "$prefix.py"
                val files = FilenameIndex.getVirtualFilesByName(filename, scope)

                for (f in files) {
                    val fileScope = GlobalSearchScope.fileScope(project, f)
                    val suffixSearchWord = keyParts.last()

                    helper.processElementsWithWord(
                        { element, _ ->
                            val literal = getParentLiteral(element) ?: return@processElementsWithWord true
                            val str = utils.extractStringValue(literal)
                            if (str == suffix || str.contains("{$suffix}") || str.contains("{{$suffix}}")) {
                                found = true; false
                            } else true
                        },
                        fileScope,
                        suffixSearchWord,
                        UsageSearchContext.IN_STRINGS,
                        true
                    )
                    if (found) return true
                }
                if (found) return true
            }
        }

        return false
    }

    private fun getParentLiteral(element: PsiElement): PyStringLiteralExpression? {
        var current: PsiElement? = element
        while (current != null) {
            if (current is PyStringLiteralExpression) return current
            if (current is PsiFile) return null
            current = current.parent
        }
        return null
    }

    private fun showUsages(e: MouseEvent, project: Project, key: String) {
        val usages = findAllUsages(project, key)
        if (usages.isEmpty()) return

        val utils = LanguageUtils()
        if (usages.size == 1) {
            val element = usages[0]
            val doc = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
            val line = doc?.getLineNumber(element.textRange.startOffset) ?: 0
            utils.gotoLine(project, element.containingFile.virtualFile, line)
        } else {
            val popupItems = usages.map { element ->
                val file = element.containingFile.name
                val doc = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
                val line = (doc?.getLineNumber(element.textRange.startOffset) ?: 0) + 1
                val text = element.text.take(50)
                object {
                    override fun toString() = "$file:$line - $text"
                    val fileObj = element.containingFile.virtualFile
                    val lineNum = line - 1
                }
            }

            JBPopupFactory.getInstance()
                .createPopupChooserBuilder(popupItems)
                .setTitle("Usages of $key")
                .setItemChosenCallback { selected ->
                    utils.gotoLine(project, selected.fileObj, selected.lineNum)
                }
                .createPopup()
                .show(RelativePoint(e))
        }
    }

    private fun findAllUsages(project: Project, fullKey: String): List<PsiElement> {
        val results = mutableListOf<PsiElement>()
        val helper = PsiSearchHelper.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        val utils = LanguageUtils()

        val keyParts = fullKey.split(".")
        val searchWord = keyParts.lastOrNull() ?: fullKey

        // 1. Explicit
        helper.processElementsWithWord(
            { element, _ ->
                val literal = getParentLiteral(element)
                if (literal != null) {
                    val str = utils.extractStringValue(literal)
                    if (str == fullKey || str.contains("{$fullKey}") || str.contains("{{$fullKey}}")) {
                        results.add(literal)
                    }
                }
                true
            },
            scope,
            searchWord,
            UsageSearchContext.IN_STRINGS,
            true
        )

        // 2. Implicit
        if (keyParts.size > 1) {
            for (i in 1 until keyParts.size) {
                val prefix = keyParts.take(i).joinToString(".")
                val suffix = keyParts.drop(i).joinToString(".")
                val filename = "$prefix.py"
                val files = FilenameIndex.getVirtualFilesByName(filename, scope)

                for (f in files) {
                    val fileScope = GlobalSearchScope.fileScope(project, f)
                    val suffixSearchWord = keyParts.last()

                    helper.processElementsWithWord(
                        { element, _ ->
                            val literal = getParentLiteral(element)
                            if (literal != null) {
                                val str = utils.extractStringValue(literal)
                                if (str == suffix || str.contains("{$suffix}") || str.contains("{{$suffix}}")) {
                                    results.add(literal)
                                }
                            }
                            true
                        },
                        fileScope,
                        suffixSearchWord,
                        UsageSearchContext.IN_STRINGS,
                        true
                    )
                }
            }
        }
        // Deduplicate using offset
        return results.distinctBy { it.textRange.startOffset }
    }
}