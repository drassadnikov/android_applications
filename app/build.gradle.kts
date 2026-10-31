// «Дзен.Новости»: отдельное приложение-компаньон (виджет + экран статуса)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.zen.android.widget"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.zen.android.widget"
        minSdk = 26
        targetSdk = 35
        // Отдельный пакет: сосуществует с оригинальным Дзен (ru.zen.android)
        versionCode = 309273559
        versionName = "18.5"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
    }
}

// stdlib фиксируем на 1.9.23 — единственный jar, доступный в офлайн-кэше
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.23")
}
