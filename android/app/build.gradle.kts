import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// FCM (T-101): el plugin google-services SOLO se aplica si existe el fichero
// `app/google-services.json` del proyecto Firebase real. Así el build de CI
// (que no versiona ese fichero, ver .gitignore) compila igualmente y la
// mensajería push queda inerte hasta que se añada el JSON. Evita el fallo
// "File google-services.json is missing" que el plugin lanza al aplicarse
// sin el fichero. Ver android/README.md §Notificaciones push.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// Lee configuración de Supabase desde local.properties (no versionado).
// Cada desarrollador apunta a su propio proyecto Supabase para evitar tocar
// datos compartidos durante el desarrollo.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localOrEnv(key: String, default: String = ""): String =
    localProperties.getProperty(key)
        ?: System.getenv(key)
        ?: default

android {
    namespace = "com.recre.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.recre.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField(
            type = "String",
            name = "SUPABASE_URL",
            value = "\"${localOrEnv("SUPABASE_URL", "https://example.supabase.co")}\"",
        )
        buildConfigField(
            type = "String",
            name = "SUPABASE_ANON_KEY",
            value = "\"${localOrEnv("SUPABASE_ANON_KEY", "")}\"",
        )

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/INDEX.LIST",
                "/META-INF/io.netty.versions.properties",
                "/META-INF/LICENSE.md",
                "/META-INF/LICENSE-notice.md",
                "/META-INF/LICENSE",
                "/META-INF/NOTICE.md",
                "/META-INF/NOTICE",
            )
        }
    }
}

dependencies {
    // AndroidX core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    // Material Components (tema XML Material3 del manifest/splash)
    implementation(libs.google.android.material)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Serialization (DTOs Postgrest, parámetros de empresa, etc.)
    implementation(libs.kotlinx.serialization.json)

    // DI
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)

    // Supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)

    // Logging
    implementation(libs.timber)

    // Imágenes
    implementation(libs.coil.compose)

    // OCR on-device — foto de contadores (T-100)
    implementation(libs.mlkit.text.recognition)

    // Firebase Cloud Messaging — notificaciones push (T-101).
    // El BoM alinea versiones; firebase-messaging compila sin
    // google-services.json (el plugin solo es necesario en runtime para
    // inicializar FirebaseApp a partir del fichero del proyecto).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit)
    androidTestImplementation(libs.mockk.android)
}
