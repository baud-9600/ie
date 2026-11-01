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
        // MMD (the Mudita e-ink component library) comes from the local publish of the
        // MMD checkout in ~/Documents/Projekte/MMD.
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "ie"
include(":app")
