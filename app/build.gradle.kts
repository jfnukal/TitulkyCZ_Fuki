import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// [Fuki] klíč k Gemini se bere z local.properties (ten se na GitHub nenahrává):
//   gemini.key=AIza....
//   gemini.model=gemini-3.5-flash-lite   (nepovinné)
val mistni = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "cz.titulky.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "cz.titulky.tv.fuki"
        minSdk = 26
        targetSdk = 34
        // [Fuki] versionCode = 100 + číslo verze Fuki (každá nová verze musí mít vyšší číslo kvůli aktualizacím)
        versionCode = 121
        versionName = "0.20.1-fuki21"
        buildConfigField("String", "GEMINI_KEY", "\"" + mistni.getProperty("gemini.key", "") + "\"")
        buildConfigField("String", "GEMINI_MODEL", "\"" + mistni.getProperty("gemini.model", "gemini-3.5-flash-lite") + "\"")
        // jen procesory, které mají televize (ARM) – aplikace je tak o třetinu menší
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }
    buildFeatures { buildConfig = true }
    // [Fuki] podpis vydání: klíč z proměnných prostředí (GitHub Actions – Secrets) nebo z local.properties
    val klicSoubor = System.getenv("KEYSTORE_FILE") ?: mistni.getProperty("keystore.file")
    signingConfigs {
        if (klicSoubor != null && file(klicSoubor).exists()) create("vydani") {
            storeFile = file(klicSoubor)
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: mistni.getProperty("keystore.password")
            keyAlias = System.getenv("KEY_ALIAS") ?: mistni.getProperty("key.alias")
            keyPassword = System.getenv("KEY_PASSWORD") ?: mistni.getProperty("key.password")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("vydani")?.let { signingConfig = it }
        }
    }
    // kontrola "lint" při podepsaném sestavení padá na novém Javě (25) → vypnout, aplikaci neovlivní
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // Překlad přímo v televizi – zdarma, bez limitu (Google ML Kit)
    implementation("com.google.mlkit:translate:17.0.3")
    // Čtení textu z obrazu – zdarma, v televizi, bez stahování (Google ML Kit)
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Rozpoznání jazyka – překládáme jen anglický text (zdarma, v televizi)
    implementation("com.google.mlkit:language-id:17.0.6")
    // QR kód pro přihlášení k PSN přes telefon
    implementation("com.google.zxing:core:3.5.3")
    // Xbox: příjem obrazu z konzole (WebRTC, Google libwebrtc v balíčku od Stream – Apache 2.0)
    implementation("io.getstream:stream-webrtc-android:1.3.7")
}
