plugins {
    id("com.android.application") version "9.2.1"
}

android {
    namespace = "app.revanced.bilibili.xposed"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "app.revanced.bilibili.xposed"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.23.3-lsposed.5"
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main").java.srcDir("../integrations/runtime/src/main/java")
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            // First-round locally signed test build. Preserve the signing key for upgrades.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging.resources.excludes += setOf("META-INF/DEPENDENCIES")
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    testImplementation("junit:junit:4.13.2")
}

// Exercise the shared Java rules without an Android test worker or device.
val policyChecks by configurations.creating
dependencies { policyChecks("junit:junit:4.13.2") }
val compilePolicyChecks by tasks.registering(JavaCompile::class) {
    source("../integrations/runtime/src/main/java", "src/test/java")
    source("src/main/java/app/revanced/bilibili/xposed/SettingsMutation.java")
    source("src/main/java/app/revanced/bilibili/xposed/SettingsMigration.java")
    source("src/main/java/app/revanced/bilibili/xposed/JsonFeatureFilter.java")
    source("src/main/java/app/revanced/bilibili/xposed/PlayerPolicy.java")
    source("src/main/java/app/revanced/bilibili/xposed/PlayerSettingsInput.java")
    source("src/main/java/app/revanced/bilibili/xposed/SubtitleText.java")
    source("src/main/java/app/revanced/bilibili/xposed/PlayerProto.java")
    source("src/main/java/app/revanced/bilibili/xposed/PlayerReflection.java")
    classpath = policyChecks
    destinationDirectory.set(layout.buildDirectory.dir("policy-checks/classes"))
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.encoding = "UTF-8"
}
tasks.register<JavaExec>("verifyBottomBarPolicy") {
    group = "verification"
    dependsOn(compilePolicyChecks)
    classpath = files(compilePolicyChecks.flatMap { it.destinationDirectory }) + policyChecks
    mainClass.set("org.junit.runner.JUnitCore")
    args("app.revanced.bilibili.runtime.BottomBarPolicyTest")
}
tasks.register<JavaExec>("verifySettingsRules") {
    group = "verification"
    dependsOn(compilePolicyChecks)
    classpath = files(compilePolicyChecks.flatMap { it.destinationDirectory }) + policyChecks
    mainClass.set("org.junit.runner.JUnitCore")
    args("app.revanced.bilibili.xposed.SettingsMutationTest", "app.revanced.bilibili.xposed.SettingsMigrationTest")
}
tasks.register<JavaExec>("verifyJsonFeatures") {
    group = "verification"
    dependsOn(compilePolicyChecks)
    classpath = files(compilePolicyChecks.flatMap { it.destinationDirectory }) + policyChecks
    mainClass.set("org.junit.runner.JUnitCore")
    args("app.revanced.bilibili.xposed.JsonFeatureFilterTest")
}

tasks.register<JavaExec>("verifyPlayerRules") {
    group = "verification"
    dependsOn(compilePolicyChecks)
    classpath = files(compilePolicyChecks.flatMap { it.destinationDirectory }) + policyChecks
    mainClass.set("org.junit.runner.JUnitCore")
    args("app.revanced.bilibili.xposed.PlayerRulesTest")
}
