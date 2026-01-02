package me.geckotv.ezcordutils.language

import androidx.compose.foundation.background
import androidx.compose.foundation.border
    import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import me.geckotv.ezcordutils.settings.EzCordSettings
import me.geckotv.ezcordutils.utils.LanguageUtils
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.ui.component.*

// Data classes
/**
 * Information about a translation with location.
 */
data class LanguageTranslation(
    val value: String,
    val file: VirtualFile?,
    val lineNumber: Int
)

/**
 * Information about a language key with all translations.
 */
data class KeyInfo(
    val key: String,
    val translations: Map<String, LanguageTranslation>
)

/**
 * State for the translation viewer.
 */
data class TranslationState(
    val currentFile: String = "",
    val filePrefix: String = "",
    val allKeys: List<KeyInfo> = emptyList(),
    val generalKeys: List<KeyInfo> = emptyList(),
    val selectedKey: String? = null,
    val showDetailedView: Boolean = false
)

var currentProject: Project? = null
var updateState: MutableState<TranslationState>? = null

/**
 * Tool window factory for displaying language key translations.
 */
class LanguageKeyToolWindowFactory : ToolWindowFactory {

    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        currentProject = project

        toolWindow.addComposeTab("Language Keys", focusOnClickInside = true) {
            val state = remember { mutableStateOf(TranslationState()) }
            updateState = state

            LaunchedEffect(Unit) {
                setupLiveUpdates(project)
            }

            LanguageKeyContent(project, state)
        }
    }
}

@Composable
private fun LanguageKeyContent(project: Project, state: State<TranslationState>) {
    val currentState = state.value

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1F22))
            .padding(16.dp)
    ) {
        // Header
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF2B2D30), RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌍", fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text("Language Keys Overview", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (currentState.currentFile.isNotEmpty()) {
                    Text(
                        currentState.currentFile,
                        fontSize = 12.sp,
                        color = Color(0xFF808080),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (currentState.allKeys.isEmpty() && currentState.generalKeys.isEmpty()) {
            EmptyStateView()
        } else {
            // Keys overview or detailed view
            if (currentState.showDetailedView && currentState.selectedKey != null) {
                val allKeysCombined = currentState.allKeys + currentState.generalKeys
                val keyInfo = allKeysCombined.find { it.key == currentState.selectedKey }
                if (keyInfo != null) {
                    DetailedKeyView(
                        project = project,
                        keyInfo = keyInfo,
                        onBack = { updateState?.value = currentState.copy(showDetailedView = false, selectedKey = null) }
                    )
                }
            } else {
                KeysOverviewList(
                    project = project,
                    fileKeys = currentState.allKeys,
                    generalKeys = currentState.generalKeys,
                    filePrefix = currentState.filePrefix,
                    onKeyClick = { key ->
                        updateState?.value = currentState.copy(showDetailedView = true, selectedKey = key)
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyStateView() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📂", fontSize = 48.sp, modifier = Modifier.padding(bottom = 16.dp))
        Text(
            "No Language Keys Found",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            "Open a Python file to see its language keys",
            fontSize = 14.sp,
            color = Color(0xFF808080)
        )
    }
}

@Composable
private fun KeysOverviewList(
    project: Project,
    fileKeys: List<KeyInfo>,
    generalKeys: List<KeyInfo>,
    filePrefix: String,
    onKeyClick: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Table Header
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF2B2D30))
                .padding(12.dp)
        ) {
            Text("Key", Modifier.weight(0.5f), fontWeight = FontWeight.Bold)
            Text("Translations", Modifier.weight(0.35f), fontWeight = FontWeight.Bold)
            Text("Jump", Modifier.weight(0.15f), fontWeight = FontWeight.Bold)
        }

        var rowIndex = 0

        // File-specific keys section
        if (fileKeys.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2B2D30))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    "${filePrefix.uppercase()} Keys",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6897BB)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "(${fileKeys.size})",
                    fontSize = 13.sp,
                    color = Color(0xFF808080)
                )
            }

            fileKeys.forEach { keyInfo ->
                KeyOverviewRow(
                    project = project,
                    keyInfo = keyInfo,
                    isEven = rowIndex % 2 == 0,
                    onClick = { onKeyClick(keyInfo.key) }
                )
                rowIndex++
            }
        }

        // General keys section
        if (generalKeys.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2B2D30))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    "GENERAL Keys",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6897BB)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "(${generalKeys.size})",
                    fontSize = 13.sp,
                    color = Color(0xFF808080)
                )
            }

            generalKeys.forEach { keyInfo ->
                KeyOverviewRow(
                    project = project,
                    keyInfo = keyInfo,
                    isEven = rowIndex % 2 == 0,
                    onClick = { onKeyClick(keyInfo.key) }
                )
                rowIndex++
            }
        }
    }
}

@Composable
private fun KeyOverviewRow(
    project: Project,
    keyInfo: KeyInfo,
    isEven: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isEven) Color(0xFF1E1F22) else Color(0xFF25262A)
    val utils = LanguageUtils()
    val resolver = LanguageResolver(project)

    // Get ALL available languages to check for missing translations
    val allLanguages = resolver.getAllAvailableLanguages()

    // Check if ANY language is missing a translation (not just primary)
    val hasMissingTranslations = keyInfo.translations.isEmpty() ||
        allLanguages.any { lang -> !keyInfo.translations.containsKey(lang) }

    Row(
        Modifier
            .fillMaxWidth()
            .background(bgColor)
            .border(1.dp, Color(0xFF3C3F41))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Key name with warning
        Row(
            Modifier.weight(0.5f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasMissingTranslations) {
                Text("⚠️ ", fontSize = 16.sp)
            }
            Text(
                keyInfo.key,
                fontWeight = FontWeight.Medium,
                color = if (hasMissingTranslations) Color(0xFFCC7832) else Color.White
            )
        }

        // Translation count
        Text(
            "${keyInfo.translations.size} lang(s)",
            Modifier.weight(0.35f),
            color = Color(0xFF6AAB73),
            fontSize = 14.sp
        )

        // Jump to source button
        Row(
            Modifier.weight(0.15f),
            horizontalArrangement = Arrangement.End
        ) {
            val firstTranslation = keyInfo.translations.values.firstOrNull()
            if (firstTranslation != null && firstTranslation.file != null) {
                IconButton(
                    onClick = {
                        utils.gotoLine(project, firstTranslation.file, firstTranslation.lineNumber)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("📍", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun DetailedKeyView(
    project: Project,
    keyInfo: KeyInfo,
    onBack: () -> Unit
) {
    val settings = EzCordSettings.getInstance(project)
    val resolver = LanguageResolver(project)
    val utils = LanguageUtils()

    val allLanguages = resolver.getAllAvailableLanguages()
    val primaryLang = settings.state.defaultLanguage

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Back button and key name
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF2B2D30), RoundedCornerShape(6.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp)
            ) {
                Text("◀", fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    keyInfo.key,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6897BB)
                )
                Text(
                    "${keyInfo.translations.size} of ${allLanguages.size} languages",
                    fontSize = 12.sp,
                    color = Color(0xFF808080),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // All languages table
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF2B2D30))
                .padding(12.dp)
        ) {
            Text("Language", Modifier.weight(0.25f), fontWeight = FontWeight.Bold)
            Text("Translation", Modifier.weight(0.6f), fontWeight = FontWeight.Bold)
            Text("Jump", Modifier.weight(0.15f), fontWeight = FontWeight.Bold)
        }

        allLanguages.forEachIndexed { index, language ->
            val translation = keyInfo.translations[language]
            val isMissing = translation == null
            val isPrimary = language == primaryLang
            val bgColor = if (index % 2 == 0) Color(0xFF1E1F22) else Color(0xFF25262A)

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .border(1.dp, Color(0xFF3C3F41))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Language name
                Row(
                    Modifier.weight(0.25f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPrimary) {
                        Text("★ ", fontSize = 14.sp, color = Color(0xFFFFD700))
                    }
                    Text(
                        language,
                        fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Normal
                    )
                }

                // Translation or warning
                if (isMissing) {
                    Row(
                        Modifier.weight(0.6f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️ ", fontSize = 16.sp)
                        Text(
                            "Missing translation",
                            color = Color(0xFFCC7832),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                } else {
                    Text(
                        translation.value,
                        Modifier.weight(0.6f),
                        color = Color(0xFF6AAB73)
                    )
                }

                // Jump button
                Row(
                    Modifier.weight(0.15f),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (!isMissing && translation.file != null) {
                        IconButton(
                            onClick = {
                                utils.gotoLine(project, translation.file, translation.lineNumber)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("📍", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun setupLiveUpdates(project: Project) {
    val connection = project.messageBus.connect()

    // Listen for file editor changes (opening/switching files)
    connection.subscribe(
        com.intellij.openapi.fileEditor.FileEditorManagerListener.FILE_EDITOR_MANAGER,
        object : com.intellij.openapi.fileEditor.FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                if (file.name.endsWith(".py")) {
                    loadFileKeys(project, file)
                } else if (file.extension == "yml" || file.extension == "yaml") {
                    // When YAML file is opened, setup document listener for live updates
                    setupYamlDocumentListener(project, file, connection)
                }
            }

            override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
                val newFile = event.newFile
                if (newFile != null && newFile.name.endsWith(".py")) {
                    loadFileKeys(project, newFile)
                }
            }
        }
    )

    // Listen for file changes (saves) to refresh when YAML files are edited
    connection.subscribe(
        com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES,
        object : com.intellij.openapi.vfs.newvfs.BulkFileListener {
            override fun after(events: MutableList<out com.intellij.openapi.vfs.newvfs.events.VFileEvent>) {
                events.forEach { event ->
                    val file = event.file
                    if (file != null && (file.extension == "yml" || file.extension == "yaml")) {
                        refreshCurrentPythonFile(project)
                    }
                }
            }
        }
    )
}

private fun setupYamlDocumentListener(project: Project, yamlFile: VirtualFile, parentConnection: com.intellij.util.messages.MessageBusConnection) {
    ApplicationManager.getApplication().runReadAction {
        val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(yamlFile) ?: return@runReadAction
        val document = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return@runReadAction

        // Use EditorFactory to listen for document changes
        val editorFactory = com.intellij.openapi.editor.EditorFactory.getInstance()
        val listener = object : com.intellij.openapi.editor.event.DocumentListener {
            private var updateScheduled = false

            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
                // Only handle changes to this specific document
                if (event.document != document) return

                // Debounce updates - only refresh after 500ms of no typing
                if (!updateScheduled) {
                    updateScheduled = true
                    ApplicationManager.getApplication().executeOnPooledThread {
                        Thread.sleep(500) // Wait 500ms for user to finish typing
                        refreshCurrentPythonFile(project)
                        updateScheduled = false
                    }
                }
            }
        }

        // Add listener via EditorFactory's event multicaster with proper disposable
        editorFactory.eventMulticaster.addDocumentListener(listener, parentConnection)
    }
}

private fun refreshCurrentPythonFile(project: Project) {
    val currentFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
    if (currentFile != null && currentFile.name.endsWith(".py")) {
        loadFileKeys(project, currentFile)
    }
}

fun loadFileKeys(project: Project, file: VirtualFile) {
    // Run on background thread to avoid blocking EDT
    ApplicationManager.getApplication().executeOnPooledThread {
        try {
            val utils = LanguageUtils()
            val filePrefix = ApplicationManager.getApplication().runReadAction<String?> {
                utils.getFilePrefix(file.name)
            } ?: return@executeOnPooledThread

            val resolver = LanguageResolver(project)

            // Run all read operations in read action
            val (keyInfoList, generalKeyInfoList) = ApplicationManager.getApplication().runReadAction<Pair<List<KeyInfo>, List<KeyInfo>>> {
                val allKeys = resolver.getAllKeys()

                // File-specific keys
                val relevantKeys = allKeys.filter { it.startsWith("$filePrefix.") }

                // General keys
                val generalKeys = allKeys.filter { it.startsWith("general.") }

                // Build KeyInfo list for file keys
                val fileKeys = relevantKeys.map { fullKey ->
                    val allTranslations = resolver.resolveAllLanguages(fullKey)
                    val translationMap = mutableMapOf<String, LanguageTranslation>()

                    allTranslations.forEach { (language, value) ->
                        val location = resolver.getKeyLocationForLanguage(fullKey, language)
                        translationMap[language] = LanguageTranslation(
                            value = value,
                            file = location?.file,
                            lineNumber = location?.lineNumber ?: 0
                        )
                    }

                    KeyInfo(
                        key = fullKey,
                        translations = translationMap
                    )
                }

                // Build KeyInfo list for general keys
                val genKeys = generalKeys.map { fullKey ->
                    val allTranslations = resolver.resolveAllLanguages(fullKey)
                    val translationMap = mutableMapOf<String, LanguageTranslation>()

                    allTranslations.forEach { (language, value) ->
                        val location = resolver.getKeyLocationForLanguage(fullKey, language)
                        translationMap[language] = LanguageTranslation(
                            value = value,
                            file = location?.file,
                            lineNumber = location?.lineNumber ?: 0
                        )
                    }

                    KeyInfo(
                        key = fullKey,
                        translations = translationMap
                    )
                }

                Pair(fileKeys, genKeys)
            }

            // Update UI on EDT
            ApplicationManager.getApplication().invokeLater {
                updateState?.value = TranslationState(
                    currentFile = file.name,
                    filePrefix = filePrefix,
                    allKeys = keyInfoList,
                    generalKeys = generalKeyInfoList
                )
            }
        } catch (e: Exception) {
            println("[DEBUG] Error loading file keys: ${e.message}")
        }
    }
}
