pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "Vitreo"
include(":app")
include(":uninstallFixture")
project(":uninstallFixture").projectDir = file("test-fixtures/uninstall-app")
