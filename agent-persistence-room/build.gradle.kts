plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.mehulp89.agentcompose.persistence.room"
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

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":agent-core"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "agent-persistence-room",
        version = project.version.toString(),
    )

    pom {
        name.set("AgentCompose Room persistence")
        description.set("Room-backed conversation persistence for AgentCompose.")
        inceptionYear.set("2026")
    }
}
