import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// release 签名通过 Gradle 属性提供（建议写在 ~/.gradle/gradle.properties，不入库）：
// MONITOR_KEYSTORE_PATH / MONITOR_KEYSTORE_PASSWORD / MONITOR_KEY_ALIAS / MONITOR_KEY_PASSWORD
val releaseKeystorePath: String? = providers.gradleProperty("MONITOR_KEYSTORE_PATH").orNull

android {
    namespace = "com.t2m.monitor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.t2m.monitor"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // 供界面展示 SDK 范围，避免在界面层硬编码版本号。
        buildConfigField("int", "MIN_SDK", "$minSdk")
        buildConfigField("int", "TARGET_SDK", "$targetSdk")
    }

    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = providers.gradleProperty("MONITOR_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("MONITOR_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("MONITOR_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 未提供签名配置时输出未签名 APK，便于 CI 校验构建产物。
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = false
    }

    lint {
        // lint 分析测试源在 release 变体会触发 AGP/KSP 生成源缺失的内部错误；
        // 测试代码由编译与单测任务覆盖，这里关闭 lint 的测试源码分析。
        checkTestSources = false
        // 依赖版本按 docs/plan.md §4.1 的约束锁定（compileSdk 36 上限），不做升级提示。
        disable += setOf(
            "GradleDependency",
            "NewerVersionAvailable",
            "AndroidGradlePluginVersion",
            // 自适应图标必须放在 v26 目录，minSdk 已是 26，该提示无实际意义。
            "ObsoleteSdkInt",
            // 占位符统一使用 "--"，与 FormatUtils 的 UNAVAILABLE 保持一致。
            "TypographyDashes",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.shizuku.aidl)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
