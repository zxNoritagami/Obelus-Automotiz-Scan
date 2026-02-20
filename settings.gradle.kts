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
        google()
        mavenCentral()
        // Eclipse Paho MQTT - no está en Maven Central
        maven { url = uri("https://repo.eclipse.org/content/repositories/paho-releases/") }
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Obelus"
include(":app")
