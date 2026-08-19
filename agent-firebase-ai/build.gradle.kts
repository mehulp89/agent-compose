plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.mehulp89.agentcompose.firebaseai"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
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
    api(libs.firebase.ai)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "agent-firebase-ai",
        version = project.version.toString(),
    )

    pom {
        name.set("AgentCompose Firebase AI adapter")
        description.set("Streaming Firebase AI Logic adapter for AgentCompose.")
        inceptionYear.set("2026")
    }
}
