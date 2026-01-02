package me.geckotv.ezcordutils.language

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.python.psi.PyStringLiteralExpression
import me.geckotv.ezcordutils.utils.LanguageUtils
import me.geckotv.ezcordutils.settings.EzCordSettings

/**
 * Contains translation information including fallback details.
 */
data class TranslationInfo(
    val translation: String,
    val isFromPrimaryLanguage: Boolean,
    val fallbackLanguage: String? = null
)

/**
 * Provides documentation for language keys in Python files.
 * Shows the translated text when hovering over strings like "general.test".
 */
class LanguageDocumentationProvider : AbstractDocumentationProvider() {

    @Suppress("UnstableApiUsage")
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val pyString = when {
            element is PyStringLiteralExpression -> element
            originalElement is PyStringLiteralExpression -> originalElement
            else -> { return null }
        }

        val stringValue = pyString.stringValue.trim()

        if (stringValue.isBlank()) {
            return null
        }

        if (!stringValue.contains('.')) return null

        val resolver = LanguageResolver(pyString.project)
        val utils = LanguageUtils()
        val filePrefix = utils.getFilePrefix(pyString.containingFile.name)

        // Use utility function to find all keys
        val foundKeys = utils.findAllKeysInString(stringValue, filePrefix, resolver)

        if (foundKeys.isEmpty()) {
            return null
        }

        // Build translations map from found keys
        val translations = mutableMapOf<String, TranslationInfo>()
        for ((key, _) in foundKeys) {
            val translationInfo = resolveWithFallback(resolver, key)
            if (translationInfo != null) {
                translations[key] = translationInfo
            }
        }

        if (translations.isEmpty()) {
            return null
        }

        if (translations.size == 1) {
            val (singleKey, singleTranslationInfo) = translations.entries.first()
            return buildDocumentation(pyString.project, singleKey, singleTranslationInfo)
        }

        return buildMultiDocumentation(pyString.project, translations)
    }


    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        // Check if we're hovering over a Python string or inside one
        val element = when {
            contextElement is PyStringLiteralExpression -> {
                contextElement
            }
            contextElement?.parent is PyStringLiteralExpression -> {
                contextElement.parent as PyStringLiteralExpression
            }
            else -> {
                null
            }
        }

        return element
    }

    /**
     * Resolves a language key with fallback mechanism.
     * First tries the primary language, then falls back to other available languages.
     *
     * @param resolver The language resolver instance.
     * @param key The language key to resolve.
     * @return TranslationInfo containing the translation and metadata, or null if not found.
     */
    private fun resolveWithFallback(resolver: LanguageResolver, key: String): TranslationInfo? {
        // First try the primary language
        val primaryTranslation = resolver.resolve(key)

        if (primaryTranslation != null) {
            return TranslationInfo(
                translation = primaryTranslation,
                isFromPrimaryLanguage = true
            )
        }

        // If not found in primary language, try all other languages
        val allTranslations = resolver.resolveAllLanguages(key)

        if (allTranslations.isEmpty()) {
            return null
        }

        // Get preferred fallback language from settings
        val settings = EzCordSettings.getInstance(resolver.project)
        val preferredFallback = settings.state.preferredFallbackLanguage

        // Prefer the configured fallback language, then any other language
        val fallbackEntry = allTranslations.entries.firstOrNull { it.key == preferredFallback }
            ?: allTranslations.entries.firstOrNull()


        return if (fallbackEntry != null) {
            TranslationInfo(
                translation = fallbackEntry.value,
                isFromPrimaryLanguage = false,
                fallbackLanguage = fallbackEntry.key
            )
        } else {
            null
        }
    }

    /**
     * Builds the HTML documentation to display for a single key.
     */
    private fun buildDocumentation(project: Project, key: String, translationInfo: TranslationInfo): String {
        return buildString {
            append("<div class='definition'><pre>")
            append("<b>Language Key:</b> $key")
            append("</pre></div>")
            append("<div class='content'>")

            if (!translationInfo.isFromPrimaryLanguage && translationInfo.fallbackLanguage != null) {
                val settings = EzCordSettings.getInstance(project)
                val primaryLanguage = settings.state.defaultLanguage

                append("<p style='color: #E8A317; font-weight: bold;'>")
                append("⚠️ This is currently not translated in the language $primaryLanguage")
                append("</p>")
                append("<p><b>Fallback Language (${translationInfo.fallbackLanguage}):</b></p>")
            } else {
                append("<p><b>Translation:</b></p>")
            }

            append("<p style='margin-left: 10px; font-style: italic;'>")
            append(escapeHtml(translationInfo.translation))
            append("</p>")
            append("</div>")
        }
    }

    /**
     * Builds the HTML documentation to display for multiple keys.
     */
    private fun buildMultiDocumentation(project: Project, translations: Map<String, TranslationInfo>): String {
        return buildString {
            append("<div class='definition'><pre>")
            append("<b>Language Keys Found:</b> ${translations.size}")
            append("</pre></div>")
            append("<div class='content'>")

            translations.forEach { (key, translationInfo) ->
                append("<hr style='margin: 8px 0; border: 0; border-top: 1px solid #ccc;'>")
                append("<p><b>Key:</b> $key</p>")

                if (!translationInfo.isFromPrimaryLanguage && translationInfo.fallbackLanguage != null) {
                    val settings = EzCordSettings.getInstance(project)
                    val primaryLanguage = settings.state.defaultLanguage

                    append("<p style='color: #E8A317; font-weight: bold;'>")
                    append("⚠️ This is currently not translated in the language $primaryLanguage")
                    append("</p>")
                    append("<p><b>Fallback Language (${translationInfo.fallbackLanguage}):</b></p>")
                }

                append("<p style='margin-left: 10px; font-style: italic;'>")
                append(escapeHtml(translationInfo.translation))
                append("</p>")
            }

            append("</div>")
        }
    }

    /**
     * Escapes HTML special characters.
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
