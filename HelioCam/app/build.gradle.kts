plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")

}

android {
    namespace = "com.summersoft.heliocam"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.summersoft.heliocam"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    viewBinding {
        enable=true
    }
    aaptOptions {
        noCompress("tflite")
    }

    packagingOptions {
        resources {
            excludes.add("/META-INF/{AL2.0,LGPL2.1}")
            excludes.add("/META-INF/DEPENDENCIES")
            excludes.add("/META-INF/LICENSE")
            excludes.add("/META-INF/LICENSE.txt")
            excludes.add("/META-INF/license.txt")
            excludes.add("/META-INF/NOTICE")
            excludes.add("/META-INF/NOTICE.txt")
            excludes.add("/META-INF/notice.txt")
        }
    }


}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("com.google.android.material:material:1.10.0")
    implementation("androidx.activity:activity:1.8.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))

    implementation("androidx.documentfile:documentfile:1.0.1")

    // Firebase services
    implementation("com.google.firebase:firebase-database")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore:24.9.0")
    implementation ("com.google.firebase:firebase-storage:20.0.0")



    // Other dependencies
    implementation("com.google.code.gson:gson:2.10.1")
    implementation ("com.mesibo.api:webrtc:1.0.5")
    implementation("com.guolindev.permissionx:permissionx:1.6.1")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation ("androidx.lifecycle:lifecycle-service:2.8.0" )


    //navigation
    implementation("com.google.android.material:material:1.8.0")


    // Add these for YUV conversion

    // TensorFlow Lite dependencies
    implementation ("org.tensorflow:tensorflow-lite:2.12.0")

    // GPU support
    implementation ("org.tensorflow:tensorflow-lite-gpu:2.12.0")
    implementation ("org.tensorflow:tensorflow-lite-gpu-api:2.12.0")

    // Optional: Add support libraries for better performance
    implementation ("org.tensorflow:tensorflow-lite-support:0.4.2")


    implementation ("com.github.bumptech.glide:glide:4.14.2")
    annotationProcessor ("com.github.bumptech.glide:compiler:4.14.2")

}
