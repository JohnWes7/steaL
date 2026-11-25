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
        // Compose 1.13（BOM 2026.11.01）要求用 37.1 以上的 API 编译；只影响编译，不改 targetSdk。
        // Compose 1.13 (BOM 2026.11.01) needs API 37.1 or later to compile against; targetSdk is unaffected.
        compileSdk {
            version = release(37) {
                minorApiLevel = 2
            }
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

    // 只要真机（iosArm64）和 Apple Silicon 模拟器（iosSimulatorArm64）。模板原有的 iosX64（Intel Mac 模拟器）已去掉：
    // Kotlin 把它列为 Tier 3，AndroidX Lifecycle 2.11 也不再发布这个平台（同 JetBrains 的 KMP-App-Template / KotlinConf App）。
    // Only devices (iosArm64) and Apple silicon simulators (iosSimulatorArm64). The template's iosX64 (Intel Mac
    // simulators) is gone: Kotlin lists it as Tier 3 and AndroidX Lifecycle 2.11 no longer publishes it (as in
    // JetBrains' KMP-App-Template and KotlinConf App).
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
                // 用 api 是因为 Service 的公开构造参数是 HttpClient（ItadApi(client = …)），用 shared 的模块要看得见这个类型。
                // HTTP client: core plus each platform's engine (OkHttp on Android, Darwin on iOS);
                // api because the services' public constructors take an HttpClient (ItadApi(client = …)),
                // so modules using shared must see the type.
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
                // 日志；用 api 是因为 AppLog 的公开属性就是 Kermit 的 Logger，androidApp 用 AppLog 时要看得见这个类型。
                // Logging; api because AppLog's public properties are Kermit Loggers, a type androidApp must see to use AppLog.
                api(libs.kermit)
                // ViewModel 基类、viewModelScope 与工厂 DSL；用 api 是因为 GamesViewModel 继承 ViewModel、
                // AppContainer 公开的是 ViewModelProvider.Factory，androidApp 取 ViewModel 时要看得见（同 Fruitties）。
                // The ViewModel base class, viewModelScope and the factory DSL; api because GamesViewModel extends
                // ViewModel and AppContainer exposes ViewModelProvider.Factory, which androidApp needs to see (as in Fruitties).
                api(libs.androidx.lifecycle.viewmodel)
                // StateFlow、launch 等协程 API，ViewModel 直接用到。
                // Coroutine APIs such as StateFlow and launch, used directly by the view models.
                implementation(libs.kotlinx.coroutines.core)
                // 设置的存储（Preferences DataStore）；用 api 是因为 AppContainer 的公开构造参数是 DataStore<Preferences>，
                // 平台入口要建好传进来。
                // Settings storage (Preferences DataStore); api because AppContainer's public constructor takes a
                // DataStore<Preferences>, which the platform entry point creates and passes in.
                api(libs.androidx.datastore.core)
                api(libs.androidx.datastore.preferences.core)
                // 时区与日历（按用户时区分月、数日历日）；用 api 是因为模型函数的公开参数是 TimeZone，
                // androidApp 调用时要传 TimeZone.currentSystemDefault()。
                // Time zones and calendars (month sections and calendar days in the user's time zone); api because
                // model functions take a public TimeZone parameter, which androidApp passes as TimeZone.currentSystemDefault().
                api(libs.kotlinx.datetime)
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