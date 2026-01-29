package me.geckotv.ezcordutils.language

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import me.geckotv.ezcordutils.settings.EzCordSettings
import me.geckotv.ezcordutils.utils.LanguageUtils

/**
 * Provides autocomplete for language keys in Python files.
 */
class LanguageKeyCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            LanguageKeyCompletionProvider()
        )
    }
}

class LanguageKeyCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val element = parameters.position
        val parent = element.parent
        val isStringLiteral = parent?.javaClass?.simpleName?.contains("PyStringLiteralExpression") == true ||
                parent?.parent?.javaClass?.simpleName?.contains("PyStringLiteralExpression") == true

        if (!isStringLiteral) {
            return
        }

        val currentOffset = parameters.offset
        val elementText = element.text
        val elementStart = element.textRange.startOffset
        val relativeOffset = currentOffset - elementStart

        var prefix = ""
        if (relativeOffset >= 0 && relativeOffset <= elementText.length) {
            var start = relativeOffset
            while (start > 0) {
                val char = elementText[start - 1]
                if (!char.isLetterOrDigit() && char != '.' && char != '_') {
                    break
                }
                start--
            }
            prefix = elementText.substring(start, relativeOffset)
        }

        val newResult = result.withPrefixMatcher(prefix)

        val project = parameters.position.project
        val file = parameters.originalFile
        val settings = EzCordSettings.getInstance(project)

        // Get the file prefix (e.g., "welcome" from "welcome.py")
        val filePrefix = LanguageUtils().getFilePrefix(file.name)

        // Get all available language keys
        val resolver = LanguageResolver(project)
        val allKeys = resolver.getAllKeys()

        val relevantKeys = if (settings.state.autoCompleteEverything) {
            allKeys
        } else if (filePrefix != null) {
            allKeys.filter { it.startsWith("$filePrefix.") || it.startsWith("general.") }
        } else {
            allKeys
        }

        // Add completion items
        relevantKeys.forEach { fullKey ->
            val translation = resolver.resolve(fullKey)

            // For general keys, keep the full key
            if (fullKey.startsWith("general.")) {
                val lookupElement = LookupElementBuilder.create(fullKey)
                    .withTypeText(translation ?: "⚠️ Not Translated yet", true)
                    .bold()
                newResult.addElement(lookupElement)
            } else if (filePrefix != null && fullKey.startsWith("$filePrefix.")) {
                val shortKey = fullKey.removePrefix("$filePrefix.")

                newResult.addElement(
                    LookupElementBuilder.create(shortKey)
                        .withTypeText(translation ?: "⚠️ Not Translated yet", true)
                        .bold()
                )

                newResult.addElement(
                    LookupElementBuilder.create(fullKey)
                        .withTypeText(translation ?: "⚠️ Not Translated yet", true)
                        .bold()
                )
            } else {
                // Add other keys as-is
                val lookupElement = LookupElementBuilder.create(fullKey)
                    .withTypeText(translation ?: "⚠️ Not Translated yet", true)
                    .bold()
                newResult.addElement(lookupElement)
            }
        }
    }
}
