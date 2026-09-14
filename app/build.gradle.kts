plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "dev.herdr.handheld"
    compileSdk = 35
    defaultConfig {
        // Stable install identity: renaming it would orphan existing profiles and Keystore data.
        applicationId = "dev.herdr.handheld"
        minSdk = 31
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Optional maintainer signing. CI never receives signing material for untrusted PRs.
    val signingNames=listOf("PDX_SIGNING_STORE", "PDX_SIGNING_STORE_PASSWORD", "PDX_SIGNING_ALIAS", "PDX_SIGNING_KEY_PASSWORD")
    val signingValues=signingNames.map { providers.environmentVariable(it).orNull }
    require(signingValues.all { it==null } || signingValues.all { !it.isNullOrBlank() }) { "Provide all PDX_SIGNING_* values or none" }
    if(signingValues.all { !it.isNullOrBlank() }) {
        signingConfigs.create("maintainer") {
            storeFile=file(signingValues[0]!!);storePassword=signingValues[1]
            keyAlias=signingValues[2];keyPassword=signingValues[3]
        }
        buildTypes.getByName("release").signingConfig=signingConfigs.getByName("maintainer")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/DEPENDENCIES", "META-INF/versions/9/OSGI-INF/MANIFEST.MF") }
    testOptions { unitTests.isReturnDefaultValues = true }
    sourceSets["test"].resources.srcDir("../fixtures")
    sourceSets["androidTest"].assets.srcDir("../.tools/ssh-fixture/assets")
}
dependencyLocking { lockAllConfigurations() }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.webkit)
    implementation(libs.coroutines)
    implementation(libs.serialization)
    implementation(libs.sshj)
    implementation(libs.bouncycastle)
    implementation(libs.datastore)
    implementation(libs.slf4j.nop)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.android.junit)
    androidTestImplementation(libs.android.runner)
    androidTestImplementation(libs.compose.ui.test)
    debugImplementation(libs.compose.ui.tooling)
}
