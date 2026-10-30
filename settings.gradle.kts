pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Локальный репозиторий: aapt2 (офлайн-сборка без maven.google.com)
        maven { url = uri("${rootDir}/localrepo") }
        google()
        mavenCentral()
    }
}
rootProject.name = "DzenFeedWidgetDemo"
include(":app")
