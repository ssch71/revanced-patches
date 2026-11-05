plugins {
    alias(libs.plugins.agp.app)
}

android {
    namespace = "dev.ssch71.ample.xposed"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "dev.ssch71.ample.xposed"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            // 갤럭시 실기기용. 에뮬레이터(x86_64)가 필요하면 추가.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            signingConfig = signingConfigs["debug"]
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        // DexKit의 libdexkit.so를 hook 대상 프로세스에서 System.load()로 불러오려면
        // APK 안에 압축된 채로 두지 않고 nativeLibraryDir에 풀려 있어야 한다.
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            merges += "META-INF/xposed/*"
            excludes += "**"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.dexkit)
}
