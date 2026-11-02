plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "pl.gymbro"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.gymbro"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1-faza0"
    }

    buildTypes {
        release {
            // R8 usuwa nieużywany kod i zasoby. Najwięcej zyskuje tu zestaw
            // material-icons-extended: w kodzie sięgamy po pięć ikon, a bez
            // minifikacji do pakietu trafiał cały zestaw.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            // Podpis z konfiguracji debug, żeby wariant release dało się uruchomić
            // lokalnie bez zakładania własnego keystore'a.
            //
            // To nie jest wersja do publikacji - służy do mierzenia wydajności.
            // Compose w buildzie debug działa kilkukrotnie wolniej: kompilator
            // pracuje w trybie diagnostycznym, a ART nie optymalizuje kodu
            // oznaczonego jako debuggable. Płynność trzeba oceniać na release.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.health.connect.client)

    // Skaner kodów kreskowych Google. Nie wymaga uprawnienia do kamery:
    // skanuje Google Play Services we własnym oknie i oddaje tylko wynik,
    // a moduł dociąga się przy pierwszym użyciu. To jedna zależność zamiast
    // CameraX plus ML Kit plus własny ekran podglądu.
    implementation(libs.play.services.code.scanner)

    debugImplementation(libs.androidx.ui.tooling)

    // Testy czystej logiki - uruchamiane na JVM, bez telefonu i emulatora.
    testImplementation(libs.junit)

    // org.json jest częścią Androida, ale nie JVM-a - bez tego każde
    // odwołanie do JSONObject w teście zwracało null zamiast działać.
    // Potrzebne, żeby mapowanie odpowiedzi Open Food Facts dało się
    // sprawdzić na prawdziwych odpowiedziach serwera.
    testImplementation(libs.org.json)
}
