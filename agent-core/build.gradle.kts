plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "agent-core",
        version = project.version.toString(),
    )

    pom {
        name.set("AgentCompose Core")
        description.set("Provider-neutral streaming agent state and tool-call APIs for Kotlin and Android.")
        inceptionYear.set("2026")
    }
}
