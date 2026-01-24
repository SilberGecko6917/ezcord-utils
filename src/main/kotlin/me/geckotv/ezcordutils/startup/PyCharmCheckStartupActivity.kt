package me.geckotv.ezcordutils.startup

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.PlatformUtils

class PyCharmCheckStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        @Suppress("UnstableApiUsage")
        if (!PlatformUtils.isPyCharm()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("EzCord Utils Notifications")
                .createNotification(
                    "EzCord Utils WARNING",
                    "Some features may only work correctly in PyCharm.",
                    NotificationType.ERROR
                )
                .notify(project)
        }
    }
}
