// PC-side tool that builds foods.db. Run from the repo root: ./gradlew :sync:run --args="lv"
plugins {
    id("org.jetbrains.kotlin.jvm")
    application
}

kotlin { jvmToolchain(17) }

application { mainClass.set("com.food.sync.MainKt") }

tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }

dependencies {
    implementation(project(":core"))
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")
    testImplementation("junit:junit:4.13.2")
}

tasks.test { workingDir = rootProject.projectDir }
