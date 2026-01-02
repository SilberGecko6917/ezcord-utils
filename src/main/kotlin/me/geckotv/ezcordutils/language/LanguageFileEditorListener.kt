package me.geckotv.ezcordutils.language

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Listens for file opening events to update the language key tool window.
 */
class LanguageFileEditorListener(private val project: Project) : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (!file.name.endsWith(".py")) {
            return
        }

        ApplicationManager.getApplication().invokeLater {
            loadFileKeys(project, file)
        }
    }
}
