plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {

    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    android {
        namespace = "io.github.johnwes7.steal.shared"
        compileSdk {
            version = release(37)
        }
        minSdk = 24

        withHostTestBuilder {
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // For iOS targets, this is also where you should
    // configure native binary output. For more information, see:
    // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

    // A step-by-step guide on how to include this library in an XCode
    // project can be found here:
    // https://developer.android.com/kotlin/multiplatform/migrate
    val xcfName = "sharedKit"

    iosX64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    iosSimulatorArm64 {
        binaries.framework {
            baseName = xcfName
        }
    }

    // Source set declarations.
    // Declaring a target automatically creates a source set with the same name. By default, the
    // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
    // common to share sources between related targets.
    // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)

                // HTTP 客户端：core + 各平台引擎（Android 是 OkHttp，iOS 是 Darwin）；
                // 用 api 是因为 androidApp 要创建并持有 HttpClient，得看得见它的类型。
                // HTTP client: core plus each platform's engine (OkHttp on Android, Darwin on iOS);
                // api because androidApp creates and holds the HttpClient, so it must see the type.
                api(libs.ktor.client.engine.defaults)
                // 让 HttpClient 用 kotlinx.serialization 编解码 JSON。
                // Lets the HttpClient encode and decode JSON with kotlinx.serialization.
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                // 打印请求与响应的插件。
                // The plugin that logs requests and responses.
                implementation(libs.ktor.client.logging)
                // DTO 的 @Serializable 与 Json / JsonElement。
                // @Serializable for the DTOs, plus Json and JsonElement.
                implementation(libs.kotlinx.serialization.json)
                // 日志；用 api 是因为 AppLog 的分类就是 Kermit 的 Logger，androidApp 的 ViewModel 也要用它打日志。
                // Logging; api because AppLog's categories are Kermit Loggers, which androidApp's view models log with too.
                api(libs.kermit)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                // 假的引擎：测试里给请求返回写好的响应，不联网。
                // A fake engine: tests answer requests with canned responses, without the network.
                implementation(libs.ktor.client.mock)
                // runTest：在测试里跑 suspend 函数。
                // runTest, for running suspend functions in tests.
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        androidMain {
            dependencies {
                // Add Android-specific dependencies here. Note that this source set depends on
                // commonMain by default and will correctly pull the Android artifacts of any KMP
                // dependencies declared in commonMain.
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.core)
                implementation(libs.androidx.junit)
                implementation(libs.androidx.runner)
            }
        }

        iosMain {
            dependencies {
                // Add iOS-specific dependencies here. This a source set created by Kotlin Gradle
                // Plugin (KGP) that each specific iOS target (e.g., iosX64) depends on as
                // part of KMP’s default source set hierarchy. Note that this source set depends
                // on common by default and will correctly pull the iOS artifacts of any
                // KMP dependencies declared in commonMain.
            }
        }
    }

}