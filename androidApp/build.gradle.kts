import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 读仓库根的 secrets.properties（不进 git，模板是 secrets.example.properties）；文件不存在时 key 为空，照样能编译。
// Reads secrets.properties in the repository root (kept out of git; the template is secrets.example.properties);
// without the file the key is empty and the build still works.
val secretsFile = rootProject.file("secrets.properties")
val secrets = Properties()
if (secretsFile.exists()) {
    secretsFile.inputStream().use { stream -> secrets.load(stream) }
}
val itadApiKey = secrets.getProperty("ITAD_API_KEY", "")

android {
    namespace = "io.github.johnwes7.steal"
    // Compose 1.13（BOM 2026.11.01）要求用 37.1 以上的 API 编译；只影响编译，不改 targetSdk。
    // Compose 1.13 (BOM 2026.11.01) needs API 37.1 or later to compile against; targetSdk is unaffected.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "io.github.johnwes7.steal"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 生成 BuildConfig.ITAD_API_KEY，由 config/Config.kt 读。
        // Generates BuildConfig.ITAD_API_KEY, which config/Config.kt reads.
        buildConfigField("String", "ITAD_API_KEY", "\"$itadApiKey\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // AGP 默认不生成 BuildConfig，自定义字段要先打开。
        // AGP does not generate BuildConfig by default; custom fields need it turned on.
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.serialization.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}