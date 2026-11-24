plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.assignment3"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.assignment3"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("androidx.navigation:navigation-fragment:2.10.2")
    implementation("androidx.navigation:navigation-ui:2.10.2")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}