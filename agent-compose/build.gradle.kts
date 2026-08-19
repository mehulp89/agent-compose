plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.github.mehulp89.agentcompose.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
        aarMetadata {
            minCompileSdk = 35
        }
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }
}

dependencies {
    api(project(":agent-core"))
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

mavenPublishing {
    coordinates(
        groupId = project.group.toString(),
        artifactId = "agent-compose",
        version = project.version.toString(),
    )

    pom {
        name.set("AgentCompose UI")
        description.set("Accessible, adaptive, provider-neutral AI agent UI components for Jetpack Compose.")
        inceptionYear.set("2026")
    }
}
