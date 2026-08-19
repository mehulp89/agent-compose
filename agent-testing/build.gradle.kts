plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":agent-core"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "agent-testing",
        version = project.version.toString(),
    )

    pom {
        name.set("AgentCompose testing utilities")
        description.set("Deterministic engines, recorders, and assertions for AgentCompose tests.")
        inceptionYear.set("2026")
    }
}
