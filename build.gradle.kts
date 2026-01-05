import org.gradle.kotlin.dsl.intellijPlatform

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

group = "me.geckotv"
version = "1.0.2"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        pycharm("2025.3.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:
        @Suppress("UnstableApiUsage")
        composeUI()

        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("PythonCore")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253"
        }

        changeNotes = """
            <h3>Version 1.0.2 - Stability & Compatibility 🔧</h3>
            <ul>
                <li><b>Code Cleanup</b> - Removed code duplication and improved maintainability</li>
                <li><b>Stable API Usage</b> - Replaced experimental APIs with stable alternatives</li>
            </ul>
            
            <h3>Version 1.0.1 - New Features 🎉</h3>
            <ul>
                <li><b>Live Templates</b> - Speed up your coding with pre-defined templates for common discord features</li>
                <li><b>Enhanced Autocomplete</b> - Now supports both shortened and full language key formats (e.g., both `embed.title` and `base.embed.title`)</li>
                <li><b>Bug fixes and improvements</b></li>
            </ul>
            
            <h3>Version 1.0.0 - Initial Release 🎉</h3>
            <ul>
                <li><b>Smart Language Key Autocomplete</b> - Get intelligent suggestions for all available language keys as you type</li>
                <li><b>Quick Documentation on Hover</b> - Instantly see translations when hovering over language keys in your Python code</li>
                <li><b>One-Click Navigation with Gutter Icons</b> - Jump directly to YAML language file definitions with clickable icons</li>
                <li><b>Language Keys Tool Window</b> - View all language keys and translations in a dedicated tool window with detailed information</li>
            </ul>
            <p><i>Happy Discord bot development! 🤖</i></p>
        """.trimIndent()
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
