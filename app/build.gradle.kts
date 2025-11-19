plugins {
    // 안드로이드 앱 모듈임을 표시 (AGP 사용)
    alias(libs.plugins.android.application)
    // Kotlin Android 플러그인
    alias(libs.plugins.kotlin.android)
    // Kotlin + Compose 연동
    alias(libs.plugins.kotlin.compose)
    // Firebase / Google 서비스 연동
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.moodmanager.watch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moodmanager.watch"
        minSdk = 30
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // 디버깅 편의를 위해 난독화/최적화 비활성화
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Java 11 사용
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Kotlin도 JVM 11 타깃하도록 설정
    kotlinOptions {
        jvmTarget = "11"
    }

    // Compose UI 활성화
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Wear OS 기기 간 통신 (필요한 경우 사용, 아마 안 쓸 것 같다)
    implementation(libs.play.services.wearable)
    // Compose BOM (버전 묶음)
    implementation(platform(libs.compose.bom))
    // Firebase BOM (Firestore 등)
    implementation(platform(libs.firebase.bom))
    // Firestore (원격 DB, raw_periodic / raw_events 저장)
    implementation(libs.firebase.firestore.ktx)
    // Firestore audio 업로드
    implementation(libs.firebase.storage.ktx)
    // ViewModel + Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // 기본 Compose UI
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    // Wear OS용 Material / Foundation (원형 UI 컴포넌트)
    implementation(libs.compose.material)
    implementation(libs.compose.foundation)
    implementation(libs.wear.tooling.preview)
    // Activity + Compose
    implementation(libs.activity.compose)
    // 스플래시 스크린
    implementation(libs.core.splashscreen)
    // Health Services (Wear OS 센서 직접 접근)
    implementation(libs.androidx.health.services.client)

    // 테스트용 의존성
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}
