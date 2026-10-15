plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.food.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.food.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.3"
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
    buildFeatures {
        compose = true
    }
    // foods.db is built by the sync tool and bundled from data/.
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/foodsAsset"))
}

ksp {
    // Kept in git so later schema changes can be migrated automatically.
    arg("room.schemaLocation", "$projectDir/schemas")
}

val foodsAsset = tasks.register<Copy>("foodsAsset") {
    val data = rootProject.file("data")
    doFirst {
        check(File(data, "foods.db").exists() && File(data, "foods.version").exists()) {
            "data/foods.db is missing: run ./gradlew :sync:run --args=\"lv\" first"
        }
    }
    from(data) { include("foods.db", "foods.version") }
    into(layout.buildDirectory.dir("generated/foodsAsset"))
}
tasks.named("preBuild") { dependsOn(foodsAsset) }

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Barcode scanning: CameraX with ML Kit's model bundled in the app, so it works offline.
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("androidx.camera:camera-mlkit-vision:1.4.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // CameraX hands back ListenableFutures; Health Connect pulls in an empty stand-in for them.
    implementation("com.google.guava:guava:33.3.1-android")

    // Activity (steps, calories burned) from Fitbit via Health Connect. 1.1.0 stable needs compileSdk 36.
    implementation("androidx.health.connect:connect-client:1.1.0-beta01")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
