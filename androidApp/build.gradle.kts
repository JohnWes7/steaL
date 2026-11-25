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
        // minSdk 是 24，而 shared 用的 kotlinx-datetime 在 JVM 上靠 java.time（API 26 起才有）：打开 desugaring，
        // 由构建把 java.time 补进 APK，否则 Android 7.x 上一换算时区就崩。
        // minSdk is 24, while shared's kotlinx-datetime relies on java.time on the JVM (API 26 and up): desugaring lets
        // the build bundle java.time into the APK; without it, time zone conversions crash on Android 7.x.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        // AGP 默认不生成 BuildConfig，自定义字段要先打开。
        // AGP does not generate BuildConfig by default; custom fields need it turned on.
        buildConfig = true
    }
    // 按 res/values-* 目录自动生成 App 支持的语言列表（Android 13+ 系统设置里给 App 选语言用），默认语言写在
    // res/resources.properties；加了新的翻译目录就会自动出现在列表里。
    // Generates the app's language list (for picking the app language in Android 13+ system settings) from the
    // res/values-* folders, with the default language in res/resources.properties; a new translation folder shows
    // up in the list automatically.
    androidResources {
        generateLocaleConfig = true
    }
    // App Bundle 默认按语言拆分，手机只下载系统语言的资源；App 里能单独选语言，所以所有语言都要打进去。
    // App Bundles split by language by default, so phones download only the system language's resources; the app
    // can pick its own language, so every language has to be included.
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    // 上面 compileOptions 打开的 desugaring 用的库。
    // The library behind the desugaring turned on in compileOptions above.
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":shared"))
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.androidx.core.splashscreen)
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