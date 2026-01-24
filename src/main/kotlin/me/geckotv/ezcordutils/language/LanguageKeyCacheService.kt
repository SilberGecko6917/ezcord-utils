package me.geckotv.ezcordutils.language

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.util.FileContentUtil
import java.util.concurrent.ConcurrentHashMap

/**
 * Service that manages caching for language key usages.
 *
 * Uses smart file-based caching:
 * - Tracks which keys are potentially used in which files
 * - Only invalidates affected keys when a file changes
 * - Dramatically faster than full cache invalidation
 */
@Service(Service.Level.PROJECT)
class LanguageKeyCacheService(private val project: Project) {

    private data class CacheEntry(
        val hasUsage: Boolean,
        val timestamp: Long,
        val foundInFiles: Set<String> = emptySet()
    )

    private val usageCache = ConcurrentHashMap<String, CacheEntry>()

    private val fileToKeysIndex = ConcurrentHashMap<String, MutableSet<String>>()

    @Volatile
    private var listenerInitialized = false

    companion object {
        fun getInstance(project: Project): LanguageKeyCacheService {
            return project.getService(LanguageKeyCacheService::class.java)
        }
    }

    init {
        setupFileListener()
    }

    /**
     * Gets whether a language key has any usages.
     * Returns cached result if available, otherwise computes and caches.
     */
    fun hasUsage(key: String, computeUsage: () -> Boolean): Boolean {
        val cached = usageCache[key]

        if (cached != null) {
            return cached.hasUsage
        }

        val hasUsageResult = ApplicationManager.getApplication().runReadAction<Boolean> {
            computeUsage()
        }

        usageCache[key] = CacheEntry(hasUsageResult, System.currentTimeMillis())

        return hasUsageResult
    }

    /**
     * Updates the cache entry for a key and tracks which file it was found in.
     */
    fun updateCacheWithFileInfo(key: String, hasUsage: Boolean, file: VirtualFile?) {
        val foundFiles = if (hasUsage && file != null) {
            setOf(file.path)
        } else {
            emptySet()
        }

        usageCache[key] = CacheEntry(hasUsage, System.currentTimeMillis(), foundFiles)

        if (file != null && hasUsage) {
            fileToKeysIndex.getOrPut(file.path) { ConcurrentHashMap.newKeySet() }.add(key)
        }
    }

    /**
     * Invalidates only the keys that might be affected by a file change.
     * This is MUCH faster than invalidating the entire cache!
     */
    private fun invalidateKeysInFile(filePath: String) {
        val keysToInvalidate = mutableSetOf<String>()

        // 1. Keys previously found in this file (they might have been removed)
        val fileKeys = fileToKeysIndex[filePath]
        if (fileKeys != null) {
            keysToInvalidate.addAll(fileKeys)
            fileToKeysIndex.remove(filePath)
        }

        // 2. Keys that are currently cached as "unused"
        usageCache.forEach { (key, entry) ->
            if (!entry.hasUsage) {
                keysToInvalidate.add(key)
            }
        }

        // Remove identified keys from cache
        keysToInvalidate.forEach { key ->
            usageCache.remove(key)
        }

        // Trigger YAML file reparse
        if (keysToInvalidate.isNotEmpty()) {
            triggerYamlReparse()
        }
    }

    /**
     * Invalidates all cached entries (fallback for when we can't determine specific file).
     */
    fun invalidateCache() {
        usageCache.clear()
        fileToKeysIndex.clear()
        triggerYamlReparse()
    }

    /**
     * Triggers re-analysis of open YAML files to update gutter icons.
     */
    private fun triggerYamlReparse() {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val psiManager = PsiManager.getInstance(project)

                val yamlPsiFiles = fileEditorManager.openFiles
                    .filter { it.extension == "yml" || it.extension == "yaml" }
                    .mapNotNull { psiManager.findFile(it) }

                if (yamlPsiFiles.isNotEmpty()) {
                    FileContentUtil.reparseFiles(project, yamlPsiFiles.map { it.virtualFile }, true)
                }
            }
        }
    }

    /**
     * Sets up a file listener to invalidate cache when Python files change.
     * Uses smart invalidation - only invalidates keys in the changed file!
     */
    private fun setupFileListener() {
        if (listenerInitialized) return
        listenerInitialized = true

        val connection = project.messageBus.connect()

        // Listen to VFS changes
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                for (event in events) {
                    if (event is VFileContentChangeEvent) {
                        val file = event.file
                        when (file.extension) {
                            "py" -> {
                                invalidateKeysInFile(file.path)
                            }
                            "yml", "yaml" -> {
                                invalidateCache()
                            }
                        }
                    }
                }
            }
        })

        // Listen to document saves for immediate response
        connection.subscribe(
            FileDocumentManagerListener.TOPIC,
            object : FileDocumentManagerListener {
                override fun beforeDocumentSaving(document: Document) {
                    val file = FileDocumentManager.getInstance().getFile(document)
                    when (file?.extension) {
                        "py" -> {
                            invalidateKeysInFile(file.path)
                        }
                        "yml", "yaml" -> {
                            invalidateCache()
                        }
                    }
                }
            }
        )
    }
}
