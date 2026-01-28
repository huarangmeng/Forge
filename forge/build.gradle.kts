plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.hrm.forge"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    implementation(libs.kotlinx.coroutines.core)
}


mavenPublishing {
    publishToMavenCentral(true)

    signAllPublications()

    coordinates("io.github.huarangmeng", "forge", rootProject.property("VERSION").toString())

    pom {
        name.set("Forge - Android Hot Update Framework")
        description.set("""
            Pure Kotlin Android hot update framework with:
            - Dynamic DEX loading (Android 7.0+)
            - Resource hot patching
            - Native SO library loading (arm64-v8a)
            - Version management and rollback
            - SHA1 integrity verification
            - Minimal and easy-to-use API
        """.trimIndent())
        inceptionYear.set("2026")
        url.set("https://github.com/huarangmeng/forge")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("huaranmeng")
                name.set("Android Framework Developer")
                url.set("https://github.com/huarangmeng/")
            }
        }
        scm {
            url.set("https://github.com/huarangmeng/forge")
            connection.set("scm:git:git://github.com/huarangmeng/forge.git")
            developerConnection.set("scm:git:ssh://git@github.com/huarangmeng/forge.git")
        }
    }
}
