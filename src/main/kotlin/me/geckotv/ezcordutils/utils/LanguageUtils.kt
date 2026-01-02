package me.geckotv.ezcordutils.utils

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import me.geckotv.ezcordutils.language.LanguageKeyLocation
import me.geckotv.ezcordutils.language.LanguageResolver

class LanguageUtils {
    /**
     * Extracts the prefix from a filename (e.g., "welcome" from "welcome.py").
     * Handles formats like "welcome.py", "welcome.container.py", etc.
     */
    fun getFilePrefix(filename: String): String? {
        if (!filename.endsWith(".py")) return null

        val nameWithoutExtension = filename.removeSuffix(".py")

        // If the filename contains dots, take everything before the last component
        // e.g., "welcome.container.py" -> "welcome.container"
        // e.g., "welcome.py" -> "welcome"
        return nameWithoutExtension.ifEmpty { null }
    }

    /**
     * Navigates to a specific line in a file.
     *
     * @param project The current project.
     * @param file The file to navigate to.
     * @param lineNumber The line number to navigate to (0-based).
     * @return True if navigation was successful, false otherwise.
     */
    fun gotoLine(project: Project, file: VirtualFile, lineNumber: Int): Boolean {
        return try {
            val descriptor = OpenFileDescriptor(project, file, lineNumber, 0)
            descriptor.navigate(true)
            true
        } catch (e: Exception) {
            println("[DEBUG LanguageUtils] ❌ Error navigating to line: ${e.message}")
            false
        }
    }

    /**
     * Tries to resolve a language key with and without file prefix.
     *
     * @param key The key to resolve.
     * @param filePrefix The optional file prefix (e.g., "welcome").
     * @param resolver The language resolver instance.
     * @return Pair of (resolved key, location) or null if not found.
     */
    fun resolveKeyWithPrefix(
        key: String,
        filePrefix: String?,
        resolver: LanguageResolver
    ): Pair<String, LanguageKeyLocation>? {
        // Try direct resolution first
        var location = resolver.getKeyLocation(key)
        if (location != null) {
            return Pair(key, location)
        }

        // Try with file prefix
        if (filePrefix != null) {
            val fullKey = "$filePrefix.$key"
            location = resolver.getKeyLocation(fullKey)
            if (location != null) {
                return Pair(fullKey, location)
            }
        }

        return null
    }

    /**
     * Finds all language keys in a string value.
     * Supports both direct keys ("general.test") and {key} patterns ("{embed.title}").
     *
     * @param stringValue The string to search in.
     * @param filePrefix The optional file prefix.
     * @param resolver The language resolver instance.
     * @return List of (resolved key, location) pairs.
     */
    fun findAllKeysInString(
        stringValue: String,
        filePrefix: String?,
        resolver: LanguageResolver
    ): List<Pair<String, LanguageKeyLocation>> {
        val foundKeys = mutableListOf<Pair<String, LanguageKeyLocation>>()

        // Check if string contains {key} pattern(s)
        val keyPattern = Regex("""[{]([a-zA-Z0-9_.]+)[}]""")
        val matches = keyPattern.findAll(stringValue).toList()

        if (matches.isNotEmpty()) {
            // Extract all keys from {key} format
            for (match in matches) {
                val extractedKey = match.groupValues[1]
                val resolved = resolveKeyWithPrefix(extractedKey, filePrefix, resolver)
                if (resolved != null) {
                    foundKeys.add(resolved)
                }
            }
        } else {
            // No {key} pattern found, try direct string value
            val resolved = resolveKeyWithPrefix(stringValue, filePrefix, resolver)
            if (resolved != null) {
                foundKeys.add(resolved)
            }
        }

        return foundKeys
    }
}

