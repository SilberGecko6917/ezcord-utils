package me.geckotv.ezcordutils.language

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import com.jetbrains.python.psi.PyStringLiteralExpression
import me.geckotv.ezcordutils.utils.LanguageUtils

/**
 * Provides autocomplete for language keys in Python files.
 */
class LanguageKeyCompletionContributor : CompletionContributor() {

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(PyStringLiteralExpression::class.java),
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
        val project = parameters.position.project
        val file = parameters.originalFile

        println("[DEBUG Autocomplete] File: ${file.name}")

        // Get the file prefix (e.g., "welcome" from "welcome.py")
        val filePrefix = LanguageUtils().getFilePrefix(file.name)
        println("[DEBUG Autocomplete] File prefix: $filePrefix")

        // Get all available language keys
        val resolver = LanguageResolver(project)
        val allKeys = resolver.getAllKeys()

        println("[DEBUG Autocomplete] Found ${allKeys.size} total keys")

        // Filter keys based on file prefix if available
        val relevantKeys = if (filePrefix != null) {
            allKeys.filter { it.startsWith("$filePrefix.") || it.startsWith("general.") }
        } else {
            allKeys
        }

        println("[DEBUG Autocomplete] Showing ${relevantKeys.size} relevant keys")

        // Add completion items
        relevantKeys.forEach { fullKey ->
            val translation = resolver.resolve(fullKey)

            // For general keys, keep the full key
            if (fullKey.startsWith("general.")) {
                val lookupElement = LookupElementBuilder.create(fullKey)
                    .withTypeText(translation ?: "⚠️ Not Translated yet", true)
                    .bold()
                result.addElement(lookupElement)
            } else if (filePrefix != null && fullKey.startsWith("$filePrefix.")) {
                val shortKey = fullKey.removePrefix("$filePrefix.")

                result.addElement(
                    LookupElementBuilder.create(shortKey)
                        .withTypeText(translation ?: "⚠️ Not Translated yet", true)
                        .bold()
                )

                result.addElement(
                    LookupElementBuilder.create(fullKey)
                        .withTypeText(translation ?: "⚠️ Not Translated yet", true)
                        .bold()
                )
            } else {
                // Add other keys as-is
                val lookupElement = LookupElementBuilder.create(fullKey)
                    .withTypeText(translation ?: "⚠️ Not Translated yet", true)
                    .bold()
                result.addElement(lookupElement)
            }
        }

        println("[DEBUG Autocomplete] Added ${relevantKeys.size} completion items")
    }
}

