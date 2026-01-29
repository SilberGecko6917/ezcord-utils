package me.geckotv.ezcordutils.language

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.ui.awt.RelativePoint
import me.geckotv.ezcordutils.settings.EzCordSettings
import me.geckotv.ezcordutils.utils.LanguageUtils
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import java.awt.event.MouseEvent


class LanguageKeyToUsageMarker : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        return null
    }

    override fun collectSlowLineMarkers(
        elements: List<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        if (elements.isEmpty()) return

        val project = elements.first().project
        val cacheService = LanguageKeyCacheService.getInstance(project)

        for (element in elements) {
            val fullKey = getKeyIfValid(element) ?: continue

            val status = cacheService.getUsageStatus(fullKey) {
                // Compute with file tracking
                val (computedStatus, files) = hasAnyUsageWithFiles(project, fullKey)
                // Update cache with file info for smart invalidation
                cacheService.updateCacheWithFileInfo(fullKey, computedStatus, files)
                computedStatus
            }

            if (status.isUsed()) {
                // Use different icon for multiple usages
                val icon = if (status == LanguageKeyCacheService.UsageStatus.MULTIPLE) {
                    AllIcons.General.Show_to_implement
                } else {
                    AllIcons.Gutter.ImplementingMethod
                }

                result.add(
                    LineMarkerInfo(
                        element,
                        element.textRange,
                        icon,
                        { "Find usages of '$fullKey'" },
                        { e, _ -> showUsages(e, project, fullKey) },
                        GutterIconRenderer.Alignment.RIGHT,
                        { "Find usages" }
                    )
                )
            }
        }
    }

    private fun getKeyIfValid(element: PsiElement): String? {
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
        return fullKey
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
     * Checks usage count of the key in the project.
     * Returns a pair of (UsageStatus, Set<FilePath>) for smart cache invalidation.
     */
    private fun hasAnyUsageWithFiles(
        project: Project,
        fullKey: String
    ): Pair<LanguageKeyCacheService.UsageStatus, Set<String>> {
        val foundFiles = mutableSetOf<String>()
        val foundElements = mutableSetOf<PsiElement>()
        var usageCount = 0

        processKeyUsages(project, fullKey) { element ->
            // Deduplicate: same element might be visited twice by search helper
            if (foundElements.add(element)) {
                usageCount++
                val file = element.containingFile.virtualFile
                if (file != null) {
                    foundFiles.add(file.path)
                }
            }
            // Stop searching if we found at least 2 usages (we know it's MULTIPLE)
            usageCount < 2
        }

        val status = when (usageCount) {
            0 -> LanguageKeyCacheService.UsageStatus.UNUSED
            1 -> LanguageKeyCacheService.UsageStatus.SINGLE
            else -> LanguageKeyCacheService.UsageStatus.MULTIPLE
        }

        return Pair(status, foundFiles)
    }

    private fun findAllUsages(project: Project, fullKey: String): List<PsiElement> {
        val results = mutableListOf<PsiElement>()
        processKeyUsages(project, fullKey) {
            results.add(it)
            true // Continue searching
        }
        return results.distinctBy { it.textRange.startOffset }
    }

    /**
     * Scans for usages of a key (explicit and implicit) and invokes the processor.
     * @param processor Returns true to continue searching, false to stop.
     * @return True if search completed, false if stopped by processor.
     */
    private fun processKeyUsages(
        project: Project,
        fullKey: String,
        processor: (PsiElement) -> Boolean
    ): Boolean {
        val helper = PsiSearchHelper.getInstance(project)

        val scope = object : GlobalSearchScope(project) {
            private val projectFileIndex = ProjectFileIndex.getInstance(project)

            override fun contains(file: VirtualFile): Boolean {
                if (file.extension != "py") return false

                val path = file.path

                if (path.contains("/.venv/") || path.contains("\\.venv\\")) return false
                if (path.contains("/venv/") || path.contains("\\venv\\")) return false
                if (path.contains("/__pycache__/") || path.contains("\\__pycache__\\")) return false
                if (path.contains("/node_modules/") || path.contains("\\node_modules\\")) return false
                if (path.contains("/.git/") || path.contains("\\.git\\")) return false
                if (path.contains("/build/") || path.contains("\\build\\")) return false
                if (path.contains("/dist/") || path.contains("\\dist\\")) return false
                if (path.contains("/.pytest_cache/") || path.contains("\\.pytest_cache\\")) return false
                if (path.contains("/site-packages/") || path.contains("\\site-packages\\")) return false

                return projectFileIndex.isInContent(file)
            }

            override fun isSearchInModuleContent(aModule: Module): Boolean = true
            override fun isSearchInLibraries(): Boolean = false
        }

        val utils = LanguageUtils()
        val keyParts = fullKey.split(".")
        val searchWord = keyParts.maxByOrNull { it.length } ?: fullKey

        fun processElement(element: PsiElement, keyToCheck: String): Boolean {
            val literal = getParentLiteral(element) ?: return true
            val str = utils.extractStringValue(literal)
            if (str == keyToCheck || str.contains("{$keyToCheck}") || str.contains("{{$keyToCheck}}")) {
                return processor(literal)
            }
            return true
        }

        // 1. Explicit usage search - most common case
        val explicitSearchCompleted = helper.processElementsWithWord(
            { element, _ -> processElement(element, fullKey) },
            scope,
            searchWord,
            UsageSearchContext.IN_STRINGS,
            true
        )

        // If explicit search was stopped (returned false), it means we found usage
        if (!explicitSearchCompleted) {
            return false
        }

        // 2. Implicit usage via file prefixes
        if (keyParts.size > 1) {
            for (i in 1 until keyParts.size) {
                val prefix = keyParts.take(i).joinToString(".")
                val suffix = keyParts.drop(i).joinToString(".")

                val filename = "$prefix.py"
                val files = FilenameIndex.getVirtualFilesByName(filename, scope)

                // Only search if the file actually exists
                if (files.isEmpty()) continue

                for (f in files) {
                    val fileScope = GlobalSearchScope.fileScope(project, f)
                    val suffixSearchWord = keyParts.last()

                    if (!helper.processElementsWithWord(
                            { element, _ -> processElement(element, suffix) },
                            fileScope,
                            suffixSearchWord,
                            UsageSearchContext.IN_STRINGS,
                            true
                        )
                    ) return false
                }
            }
        }

        return true
    }

    private fun getParentLiteral(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null) {
            if (current.javaClass.simpleName.contains("PyStringLiteralExpression")) return current
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
}
