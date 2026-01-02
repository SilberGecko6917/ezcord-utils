package me.geckotv.ezcordutils.language

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import me.geckotv.ezcordutils.settings.EzCordSettings
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLKeyValue

/**
 * Contains information about a language key location.
 */
data class LanguageKeyLocation(
    val file: VirtualFile,
    val lineNumber: Int
)

/**
 * Resolves language keys to their translated text values.
 */
class LanguageResolver(val project: Project) {

    /**
     * Resolves a language key (e.g., "general.disabled") to its translated text.
     *
     * @param key The language key in dot notation.
     * @return The translated text, or null if not found.
     */
    fun resolve(key: String): String? {
        val settings = EzCordSettings.getInstance(project)
        val languageFolder = settings.state.languageFolderPath
        val language = settings.state.defaultLanguage

        val langDir = LocalFileSystem.getInstance().findFileByPath(languageFolder) ?: return null

        val langFile = langDir.findChild("$language.yml")
            ?: langDir.findChild("$language.yaml") ?: return null

        // Parse YAML and resolve the key
        return resolveKeyFromFile(langFile, key)
    }

    /**
     * Gets the location of a language key in the YAML file.
     *
     * @param key The language key in dot notation.
     * @return The location (file and line number), or null if not found.
     */
    fun getKeyLocation(key: String): LanguageKeyLocation? {
        val settings = EzCordSettings.getInstance(project)
        val languageFolder = settings.state.languageFolderPath
        val language = settings.state.defaultLanguage

        val langDir = LocalFileSystem.getInstance().findFileByPath(languageFolder) ?: return null

        val langFile = langDir.findChild("$language.yml")
            ?: langDir.findChild("$language.yaml")

        if (langFile == null) {
            return null
        }

        return getKeyLocationFromFile(langFile, key)
    }

    /**
     * Gets the location of a language key in any available language file.
     * Tries to find the key in any language, preferring the configured fallback language if available.
     *
     * @param key The language key in dot notation.
     * @return The location (file and line number), or null if not found in any language.
     */
    fun getKeyLocationInAnyLanguage(key: String): LanguageKeyLocation? {
        val settings = EzCordSettings.getInstance(project)
        val languageFolder = settings.state.languageFolderPath
        val preferredFallback = settings.state.preferredFallbackLanguage

        val langDir = LocalFileSystem.getInstance().findFileByPath(languageFolder) ?: return null

        // Try to find in preferred fallback language first
        val fallbackFile = langDir.findChild("$preferredFallback.yml")
            ?: langDir.findChild("$preferredFallback.yaml")
        if (fallbackFile != null) {
            val location = getKeyLocationFromFile(fallbackFile, key)
            if (location != null) return location
        }

        // Try all other language files
        langDir.children.forEach { file ->
            if ((file.extension == "yml" || file.extension == "yaml") && file.nameWithoutExtension != preferredFallback) {
                val location = getKeyLocationFromFile(file, key)
                if (location != null) return location
            }
        }

        return null
    }

    /**
     * Checks if a key exists in the primary language.
     *
     * @param key The language key in dot notation.
     * @return True if the key exists in the primary language, false otherwise.
     */
    fun existsInPrimaryLanguage(key: String): Boolean {
        return getKeyLocation(key) != null
    }

    /**
     * Gets the location of a key from a YAML file.
     */
    private fun getKeyLocationFromFile(file: VirtualFile, key: String): LanguageKeyLocation? {
        try {
            val psiFile = PsiManager.getInstance(project).findFile(file) as? YAMLFile ?: return null
            val documents = psiFile.documents
            if (documents.isEmpty()) return null

            val topMapping = documents[0].topLevelValue as? YAMLMapping ?: return null

            val keyValue = findNestedKeyValue(topMapping, key.split("."))
            if (keyValue != null) {
                val document = psiFile.viewProvider.document ?: return null
                val lineNumber = document.getLineNumber(keyValue.textRange.startOffset)
                return LanguageKeyLocation(file, lineNumber)
            }

            return null
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Finds the YAMLKeyValue element for a nested key.
     */
    private fun findNestedKeyValue(mapping: YAMLMapping, keyParts: List<String>): YAMLKeyValue? {
        if (keyParts.isEmpty()) return null

        val currentKey = keyParts.first()
        val keyValue = mapping.getKeyValueByKey(currentKey) ?: return null

        return when {
            keyParts.size == 1 -> keyValue
            keyValue.value is YAMLMapping -> findNestedKeyValue(keyValue.value as YAMLMapping, keyParts.drop(1))
            else -> null
        }
    }

    /**
     * Resolves a key from a YAML file.
     */
    private fun resolveKeyFromFile(file: VirtualFile, key: String): String? {
        try {
            val psiFile = PsiManager.getInstance(project).findFile(file) as? YAMLFile ?: return null
            val documents = psiFile.documents
            if (documents.isEmpty()) return null

            val topMapping = documents[0].topLevelValue as? YAMLMapping ?: return null

            return resolveNestedKey(topMapping, key.split("."))
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Resolves a nested key from a YAML mapping structure.
     */
    private fun resolveNestedKey(mapping: YAMLMapping, keyParts: List<String>): String? {
        if (keyParts.isEmpty()) return null

        val currentKey = keyParts.first()
        val keyValue = mapping.getKeyValueByKey(currentKey) ?: return null
        val value = keyValue.value ?: return null

        return when {
            keyParts.size == 1 -> value.text
            value is YAMLMapping -> resolveNestedKey(value, keyParts.drop(1))
            else -> null
        }
    }

    /**
     * Resolves a language key in all available language files.
     *
     * @param key The language key in dot notation.
     * @return A map of language code to translated text.
     */
    fun resolveAllLanguages(key: String): Map<String, String> {
        val settings = EzCordSettings.getInstance(project)
        val languageFolder = settings.state.languageFolderPath

        // Use LocalFileSystem for absolute paths (consistent with resolve())
        val langDir = LocalFileSystem.getInstance().findFileByPath(languageFolder) ?: return emptyMap()

        val result = mutableMapOf<String, String>()

        // Find all .yml and .yaml files
        langDir.children.forEach { file ->
            if (file.extension == "yml" || file.extension == "yaml") {
                val langCode = file.nameWithoutExtension
                val translation = resolveKeyFromFile(file, key)

                if (translation != null) {
                    result[langCode] = translation
                }
            }
        }

        return result
    }

    /**
     * Gets all available language keys from all language files.
     *
     * @return A set of all language keys in dot notation.
     */
    fun getAllKeys(): Set<String> {
        val settings = EzCordSettings.getInstance(project)
        val languageFolder = settings.state.languageFolderPath

        val langDir = LocalFileSystem.getInstance().findFileByPath(languageFolder)

        if (langDir == null) {
            return emptySet()
        }

        val allKeys = mutableSetOf<String>()

        // Find all .yml and .yaml files
        langDir.children.forEach { file ->
            if (file.extension == "yml" || file.extension == "yaml") {
                val keys = extractKeysFromFile(file)
                allKeys.addAll(keys)
            }
        }

        return allKeys
    }

    /**
     * Extracts all keys from a YAML file.
     */
    private fun extractKeysFromFile(file: VirtualFile): Set<String> {
        try {
            val psiFile = PsiManager.getInstance(project).findFile(file) as? YAMLFile ?: return emptySet()
            val documents = psiFile.documents
            if (documents.isEmpty()) return emptySet()

            val topMapping = documents[0].topLevelValue as? YAMLMapping ?: return emptySet()

            return extractKeysFromMapping(topMapping, "")
        } catch (_: Exception) {
            return emptySet()
        }
    }

    /**
     * Recursively extracts all keys from a YAML mapping.
     */
    private fun extractKeysFromMapping(mapping: YAMLMapping, prefix: String): Set<String> {
        val keys = mutableSetOf<String>()

        mapping.keyValues.forEach { keyValue ->
            val key = keyValue.keyText
            val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"

            val value = keyValue.value

            if (value is YAMLMapping) {
                // Recursive: add nested keys
                keys.addAll(extractKeysFromMapping(value, fullKey))
            } else {
                // Leaf node: add this key
                keys.add(fullKey)
            }
        }

        return keys
    }
}

