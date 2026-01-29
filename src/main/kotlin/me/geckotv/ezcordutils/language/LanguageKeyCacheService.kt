package me.geckotv.ezcordutils.language

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.util.FileContentUtil
import java.util.concurrent.ConcurrentHashMap

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.util.Alarm
import me.geckotv.ezcordutils.utils.LanguageUtils

/**
 * Service that manages caching for language key usages.
 *
 * Uses smart file-based caching:
 * - Tracks which keys are potentially used in which files
 * - Only invalidates affected keys when a file changes
 * - Dramatically faster than full cache invalidation
 */
@Service(Service.Level.PROJECT)
class LanguageKeyCacheService(private val project: Project) : Disposable {

    override fun dispose() {
        // Resources are cleaned up by parent disposable
    }

    enum class UsageStatus {
        UNUSED,
        SINGLE,
        MULTIPLE;

        fun isUsed(): Boolean = this != UNUSED
    }

    private data class CacheEntry(
        val status: UsageStatus,
        val timestamp: Long,
        val foundInFiles: Set<String> = emptySet()
    )

    private val usageCache = ConcurrentHashMap<String, CacheEntry>()

    private val fileToKeysIndex = ConcurrentHashMap<String, MutableSet<String>>()

    private val debounceAlarm = Alarm(this)

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
     * Gets the usage status of a language key.
     * Returns cached result if available, otherwise computes and caches.
     */
    fun getUsageStatus(key: String, computeStatus: () -> UsageStatus): UsageStatus {
        val cached = usageCache[key]

        if (cached != null) {
            return cached.status
        }

        val statusResult = ApplicationManager.getApplication().runReadAction<UsageStatus> {
            computeStatus()
        }

        usageCache[key] = CacheEntry(statusResult, System.currentTimeMillis())

        return statusResult
    }

    /**
     * Updates the cache entry for a key and tracks which files it was found in.
     */
    fun updateCacheWithFileInfo(key: String, status: UsageStatus, files: Set<String>) {
        val foundFiles = if (status.isUsed()) files else emptySet()

        usageCache[key] = CacheEntry(status, System.currentTimeMillis(), foundFiles)

        if (status.isUsed()) {
            files.forEach { filePath ->
                fileToKeysIndex.getOrPut(filePath) { ConcurrentHashMap.newKeySet() }.add(key)
            }
        }
    }

    /**
     * Invalidates only the keys that might be affected by a file change.
     * This is MUCH faster than invalidating the entire cache!
     *
     * @param file The file that changed.
     * @param document Optional document instance. If provided, we use its text (useful for live updates).
     */
    private fun invalidateKeysInFile(file: VirtualFile, document: Document? = null) {
        val filePath = file.path
        val keysToInvalidate = mutableSetOf<String>()

        // 1. Keys previously found in this file (they might have been removed)
        val fileKeys = fileToKeysIndex[filePath]
        if (fileKeys != null) {
            keysToInvalidate.addAll(fileKeys)
            fileToKeysIndex.remove(filePath)
        }

        // 2. Read file content to find NEW usages
        val fileContent = document?.text ?: try {
            String(file.contentsToByteArray())
        } catch (_: Exception) {
            ""
        }

        val utils = LanguageUtils()
        val filePrefix = utils.getFilePrefix(file.name)

        // Check ALL keys against content if they are not already invalidated
        usageCache.forEach { (key, _) ->
            if (!keysToInvalidate.contains(key)) {
                // Case A: Explicit usage ("prefix.suffix")
                if (fileContent.contains(key)) {
                    keysToInvalidate.add(key)
                }
                // Case B: Implicit usage (in "prefix.py", check for "suffix")
                else if (filePrefix != null && key.startsWith("$filePrefix.")) {
                    val suffix = key.substring(filePrefix.length + 1)
                    if (fileContent.contains(suffix)) {
                        keysToInvalidate.add(key)
                    }
                }
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
                                invalidateKeysInFile(file)
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
                            invalidateKeysInFile(file, document)
                        }

                        "yml", "yaml" -> {
                            invalidateCache()
                        }
                    }
                }
            }
        )

        // Listens to typing events and triggers invalidation after a short delay (debounce)
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val document = event.document
                val file = FileDocumentManager.getInstance().getFile(document) ?: return

                val ext = file.extension
                if (ext != "py" && ext != "yml" && ext != "yaml") return

                if (project.isDisposed || !file.isValid) return
                if (!ProjectFileIndex.getInstance(project).isInContent(file)) return

                // Debounce the update
                debounceAlarm.cancelAllRequests()
                debounceAlarm.addRequest({
                    ApplicationManager.getApplication().runReadAction {
                        if (project.isDisposed || !file.isValid) return@runReadAction

                        if (ext == "py") {
                            invalidateKeysInFile(file, document)
                        } else {
                            invalidateCache()
                        }
                    }
                }, 2000)
            }
        }, this)
    }
}
