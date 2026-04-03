import org.gradle.api.GradleException
import org.gradle.api.JavaVersion

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun requireNonBlank(name: String, value: String?): String {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isEmpty()) {
        throw GradleException("缺少必要参数：$name")
    }
    return trimmed
}

fun parseSemverVersionName(raw: String): String {
    val text = raw.trim()
    if (!Regex("^\\d+\\.\\d+\\.\\d+$").matches(text)) {
        throw GradleException("versionName 格式非法：'$raw'，期望格式如 1.2.3（不含 v 前缀）")
    }
    return text
}

fun parsePositiveInt(name: String, raw: String): Int {
    val text = raw.trim()
    val value = text.toIntOrNull() ?: throw GradleException("$name 格式非法：'$raw'，期望为正整数")
    if (value <= 0) {
        throw GradleException("$name 必须为正整数，实际为：$value")
    }
    return value
}

val injectedVersionName: String? = providers.gradleProperty("versionName").orNull?.trim()?.takeIf { it.isNotEmpty() }
val injectedVersionCodeText: String? = providers.gradleProperty("versionCode").orNull?.trim()?.takeIf { it.isNotEmpty() }

val versionOverrideEnabled = injectedVersionName != null || injectedVersionCodeText != null
if (versionOverrideEnabled && (injectedVersionName == null || injectedVersionCodeText == null)) {
    throw GradleException("版本注入需要同时设置：-PversionName=1.2.3 与 -PversionCode=1002003")
}

val releaseKeystorePath: String? = System.getenv("ANDROID_KEYSTORE_PATH")?.trim()?.takeIf { it.isNotEmpty() }
val releaseKeystorePassword: String? = System.getenv("ANDROID_KEYSTORE_PASSWORD")?.trim()?.takeIf { it.isNotEmpty() }
val releaseKeyAlias: String? = System.getenv("ANDROID_KEY_ALIAS")?.trim()?.takeIf { it.isNotEmpty() }
val releaseKeyPassword: String? = System.getenv("ANDROID_KEY_PASSWORD")?.trim()?.takeIf { it.isNotEmpty() }

val releaseSigningEnabled = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

val anySigningEnvProvided = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).any { !it.isNullOrBlank() }

if (anySigningEnvProvided && !releaseSigningEnabled) {
    throw GradleException("Release 签名环境变量不完整：需要 ANDROID_KEYSTORE_PATH/ANDROID_KEYSTORE_PASSWORD/ANDROID_KEY_ALIAS/ANDROID_KEY_PASSWORD 全部设置")
}

val isReleaseBuildRequested = gradle.startParameter.taskNames.any { taskName ->
    taskName.endsWith("Release", ignoreCase = true) || taskName.contains("Release", ignoreCase = true)
}
if (isReleaseBuildRequested) {
    if (!versionOverrideEnabled) {
        throw GradleException("构建 Release 需要注入版本号：-PversionName=1.2.3 -PversionCode=1002003（建议由 tag 解析得到）")
    }
    if (!releaseSigningEnabled) {
        throw GradleException("构建 Release 需要签名：请设置 ANDROID_KEYSTORE_PATH/ANDROID_KEYSTORE_PASSWORD/ANDROID_KEY_ALIAS/ANDROID_KEY_PASSWORD")
    }
}

android {
    namespace = "com.myflowhub.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.myflowhub.android"
        minSdk = 26
        targetSdk = 34
        versionCode = if (injectedVersionCodeText != null) parsePositiveInt("versionCode", injectedVersionCodeText) else 1
        versionName = if (injectedVersionName != null) parseSemverVersionName(injectedVersionName) else "0.1.0"
    }

    signingConfigs {
        create("release") {
            if (releaseSigningEnabled) {
                storeFile = file(requireNonBlank("ANDROID_KEYSTORE_PATH", releaseKeystorePath))
                storePassword = requireNonBlank("ANDROID_KEYSTORE_PASSWORD", releaseKeystorePassword)
                keyAlias = requireNonBlank("ANDROID_KEY_ALIAS", releaseKeyAlias)
                keyPassword = requireNonBlank("ANDROID_KEY_PASSWORD", releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // AND2: gomobile 生成的 AAR（若尚未生成，保持可编译，运行时会自动回退 stub）
    val aar = file("libs/myflowhub.aar")
    if (aar.exists()) {
        implementation(files(aar))
    }
}
