import org.gradle.kotlin.dsl.intellijPlatform

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20"
}

group = "me.geckotv"
version = "1.0.0"

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
            sinceBuild = "252.25557"
        }

        changeNotes = """
            <h3>Version 1.0.0 - Initial Release 🎉</h3>
            <ul>
                <li><b>Smart Language Key Autocomplete</b> - Get intelligent suggestions for all available language keys as you type</li>
                <li><b>Quick Documentation on Hover</b> - Instantly see translations when hovering over language keys in your Python code</li>
                <li><b>One-Click Navigation with Gutter Icons</b> - Jump directly to YAML language file definitions with clickable icons</li>
                <li><b>Automatic File Prefix Detection</b> - Resolves keys with file-based prefixes automatically</li>
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
