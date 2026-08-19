plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.mehulp89.agentcompose.mlkit"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        aarMetadata { minCompileSdk = 35 }
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":agent-core"))
    api(libs.mlkit.genai.prompt)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "agent-mlkit-genai",
        version = project.version.toString(),
    )

    pom {
        name.set("AgentCompose ML Kit GenAI adapter")
        description.set("On-device ML Kit Prompt API adapter for AgentCompose and Gemini Nano.")
        inceptionYear.set("2026")
    }
}
