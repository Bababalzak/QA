// Top-level build file. Plugin versions are declared here (and applied with
// `apply false`) so the :app module can apply them without re-specifying
// versions. This is the standard Gradle "plugins DSL" pattern.
plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
