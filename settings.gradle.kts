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
    }
}

rootProject.name = "AgentCompose"

include(":agent-core")
include(":agent-compose")
include(":agent-firebase-ai")
include(":agent-mlkit-genai")
include(":agent-persistence-room")
include(":agent-testing")
include(":sample")
